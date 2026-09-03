/*
 * Copyright 2024 the original author or authors.
 * <p>
 * Licensed under the Moderne Source Available License (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * <p>
 * https://docs.moderne.io/licensing/moderne-source-available-license
 * <p>
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.openrewrite.staticanalysis;

import lombok.Getter;
import org.jspecify.annotations.Nullable;
import org.openrewrite.*;
import org.openrewrite.java.JavaIsoVisitor;
import org.openrewrite.java.JavaTemplate;
import org.openrewrite.java.JavaVisitor;
import org.openrewrite.java.RemoveUnusedImports;
import org.openrewrite.java.cleanup.UnnecessaryParenthesesVisitor;
import org.openrewrite.java.tree.*;
import org.openrewrite.marker.Markers;
import org.openrewrite.staticanalysis.table.AnonymousFunctionalInterfaceImplementations;

import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;

import static java.util.Collections.emptyList;
import static java.util.Collections.singleton;
import static java.util.stream.Collectors.joining;
import static java.util.stream.Collectors.toList;

public class UseLambdaForFunctionalInterface extends Recipe {
    @Getter
    final String displayName = "Use lambda expressions instead of anonymous classes";

    @Getter
    final String description = "Instead of anonymous class declarations, use a lambda where possible. Using lambdas to replace " +
            "anonymous classes can lead to more expressive and maintainable code, improve code readability, reduce " +
            "code duplication, and achieve better performance in some cases.";

    @Getter
    final Set<String> tags = singleton("RSPEC-S1604");

    transient AnonymousFunctionalInterfaceImplementations report = new AnonymousFunctionalInterfaceImplementations(this);

    @Override
    public TreeVisitor<?, ExecutionContext> getVisitor() {
        TreeVisitor<?, ExecutionContext> convert = Repeat.repeatUntilStable(new JavaVisitor<ExecutionContext>() {
            @Override
            public J visitClassDeclaration(J.ClassDeclaration classDecl, ExecutionContext ctx) {
                // Don't convert anonymous classes to lambdas when located in an enum class, to avoid `Accessing static field from enum constructor is not allowed` errors.
                if (classDecl.getKind() == J.ClassDeclaration.Kind.Type.Enum) {
                    return classDecl;
                }
                return super.visitClassDeclaration(classDecl, ctx);
            }

            @Override
            public J visitVariableDeclarations(J.VariableDeclarations multiVariable, ExecutionContext ctx) {
                // `var` borrows its type from the right-hand side, but a lambda is a poly expression that
                // needs an explicit target type (JLS 14.4.1, 15.27.1). If we are about to convert the
                // initializer to a lambda, replace `var` with the anonymous class's interface type.
                TypeTree replacementType = null;
                TypeTree typeExpression = multiVariable.getTypeExpression();
                if (typeExpression != null && typeExpression.getMarkers().findFirst(JavaVarKeyword.class).isPresent() &&
                    multiVariable.getVariables().size() == 1 &&
                    multiVariable.getVariables().get(0).getInitializer() instanceof J.NewClass) {
                    J.NewClass nc = (J.NewClass) multiVariable.getVariables().get(0).getInitializer();
                    if (nc.getClazz() != null) {
                        replacementType = nc.getClazz();
                    }
                }
                J.VariableDeclarations result = (J.VariableDeclarations) super.visitVariableDeclarations(multiVariable, ctx);
                if (result.getVariables().isEmpty()) {
                    return result;
                }
                Expression newInitializer = result.getVariables().get(0).getInitializer();
                if (replacementType != null && newInitializer instanceof J.Lambda) {
                    TypeTree resolved = resolveDiamond(replacementType, (J.Lambda) newInitializer);
                    return result.withTypeExpression(resolved.withPrefix(typeExpression.getPrefix()));
                }
                return result;
            }

            @Override
            public J visitNewClass(J.NewClass newClass, ExecutionContext ctx) {
                // Check if this anonymous class should be converted to a lambda.
                // We must determine this BEFORE calling super.visitNewClass() to avoid
                // cursor invalidation issues when nested anonymous classes are transformed.
                // See https://github.com/openrewrite/rewrite/issues/1828
                if (shouldConvertToLambda(newClass)) {
                    return convertToLambda(newClass, ctx);
                }
                return super.visitNewClass(newClass, ctx);
            }

            private boolean shouldConvertToLambda(J.NewClass n) {
                return samMethod(n) != null && conversionBlocker(n, getCursor()) == null;
            }

            private J convertToLambda(J.NewClass n, ExecutionContext ctx) {
                JavaType.FullyQualified type = TypeUtils.asFullyQualified(n.getClazz().getType());
                JavaType.FullyQualified anonymousClass = TypeUtils.asFullyQualified(n.getType());
                JavaType.FullyQualified typedInterface = anonymousClass.getInterfaces().stream()
                        .filter(i -> i.getFullyQualifiedName().equals(type.getFullyQualifiedName()))
                        .findFirst()
                        .orElse(null);
                JavaType.Method sam = getSamCompatible(type);

                StringBuilder templateBuilder = new StringBuilder();
                J.MethodDeclaration methodDeclaration = (J.MethodDeclaration) n.getBody().getStatements().get(0);

                if (methodDeclaration.getParameters().get(0) instanceof J.Empty) {
                    templateBuilder.append("() -> {");
                } else {
                    templateBuilder.append(methodDeclaration.getParameters().stream()
                            .map(param -> ((J.VariableDeclarations) param).getVariables().get(0).getSimpleName())
                            .collect(joining(",", "(", ") -> {")));
                }

                JavaType returnType = sam.getReturnType();
                if (JavaType.Primitive.Void != returnType) {
                    templateBuilder.append("return ").append(valueOfType(returnType)).append(';');
                }
                templateBuilder.append('}');

                J.Lambda lambda = JavaTemplate.builder(templateBuilder.toString())
                        .contextSensitive()
                        .build()
                        .apply(getCursor(), n.getCoordinates().replace());
                lambda = lambda.withType(typedInterface);
                lambda = (J.Lambda) new UnnecessaryParenthesesVisitor<>()
                        .visitNonNull(lambda, ctx, getCursor().getParentOrThrow());

                J.Block lambdaBody = methodDeclaration.getBody();
                assert lambdaBody != null;

                lambda = lambda.withBody(lambdaBody.withPrefix(Space.format(" ")));

                lambda = (J.Lambda) new LambdaBlockToExpression().getVisitor().visitNonNull(lambda, ctx, getCursor().getParentOrThrow());
                doAfterVisit(new RemoveUnusedImports().getVisitor());

                return autoFormat(maybeAddCast(lambda, n), ctx);
            }

            private J maybeAddCast(J.Lambda lambda, J.NewClass original) {
                J parent = getCursor().getParentTreeCursor().getValue();

                if (parent instanceof MethodCall) {
                    MethodCall method = (MethodCall) parent;
                    List<Expression> arguments = method.getArguments();
                    for (int i = 0; i < arguments.size(); i++) {
                        Expression argument = arguments.get(i);
                        if (argument == original && methodArgumentRequiresCast(lambda, method, i) &&
                            original.getClazz() != null) {
                            // The diamond operator is valid after `new` (JLS 15.9) but not in a cast (JLS 15.16),
                            // so rebuild the type with the resolved interface's type arguments.
                            TypeTree castType = resolveDiamond(original.getClazz(), lambda);
                            return new J.TypeCast(
                                    Tree.randomId(),
                                    lambda.getPrefix(),
                                    Markers.EMPTY,
                                    new J.ControlParentheses<>(
                                            Tree.randomId(),
                                            Space.EMPTY,
                                            Markers.EMPTY,
                                            JRightPadded.build(castType)
                                    ),
                                    lambda.withPrefix(Space.format(" "))
                            );
                        }
                    }
                }

                return lambda;
            }

            private TypeTree resolveDiamond(TypeTree type, J.Lambda lambda) {
                if (!(type instanceof J.ParameterizedType) || !hasDiamond((J.ParameterizedType) type)) {
                    return type;
                }
                J.ParameterizedType pt = (J.ParameterizedType) type;
                JavaType lambdaType = lambda.getType();
                JContainer<Expression> resolved = lambdaType instanceof JavaType.Parameterized ?
                        buildTypeParameters(((JavaType.Parameterized) lambdaType).getTypeParameters()) :
                        null;
                return resolved != null ? pt.withTypeParameters(resolved.getElements()) : (TypeTree) pt.getClazz();
            }

            private boolean hasDiamond(J.ParameterizedType pt) {
                List<Expression> typeParams = pt.getTypeParameters();
                return typeParams != null && typeParams.size() == 1 && typeParams.get(0) instanceof J.Empty;
            }

            private @Nullable JContainer<Expression> buildTypeParameters(@Nullable List<JavaType> typeParameters) {
                if (typeParameters == null || typeParameters.isEmpty()) {
                    return null;
                }
                List<JRightPadded<Expression>> expressions = new ArrayList<>(typeParameters.size());
                for (JavaType t : typeParameters) {
                    TypeTree tree = buildTypeTree(t);
                    if (tree == null) {
                        return null;
                    }
                    expressions.add(JRightPadded.build((Expression) tree));
                }
                return JContainer.build(Space.EMPTY, expressions, Markers.EMPTY);
            }

            private @Nullable TypeTree buildTypeTree(@Nullable JavaType type) {
                JavaType.FullyQualified fq = TypeUtils.asFullyQualified(type);
                if (fq == null) {
                    return null;
                }
                List<JavaType> fqTypeParameters = fq.getTypeParameters();
                J.Identifier identifier = new J.Identifier(Tree.randomId(), Space.EMPTY, Markers.EMPTY,
                        emptyList(), fq.getClassName(),
                        type instanceof JavaType.Parameterized ? ((JavaType.Parameterized) type).getType() : type, null);
                if (fqTypeParameters.isEmpty()) {
                    maybeAddImport(fq);
                    return identifier;
                }
                JContainer<Expression> typeParameters = buildTypeParameters(fqTypeParameters);
                if (typeParameters == null) {
                    return null;
                }
                maybeAddImport(fq);
                return new J.ParameterizedType(Tree.randomId(), Space.EMPTY, Markers.EMPTY, identifier, typeParameters,
                        new JavaType.Parameterized(null, fq, fqTypeParameters));
            }

            private boolean methodArgumentRequiresCast(J.Lambda lambda, MethodCall method, int argumentIndex) {
                JavaType.FullyQualified lambdaType = TypeUtils.asFullyQualified(lambda.getType());
                if (lambdaType == null) {
                    return false;
                }
                String lambdaFqn = lambdaType.getFullyQualifiedName();

                JavaType.Method methodType = method.getMethodType();
                if (methodType == null) {
                    return false;
                }
                JavaType parameterType = parameterTypeAt(methodType, argumentIndex);
                if (parameterType == null) {
                    return false;
                }
                if (!TypeUtils.isOfClassType(parameterType, lambdaFqn)) {
                    return true;
                }

                // look for ambiguous methods
                int count = 0;
                for (JavaType.Method maybeAmbiguous : methodType.getDeclaringType().getMethods()) {
                    if (methodType.getName().equals(maybeAmbiguous.getName()) &&
                        methodType.getParameterTypes().size() == maybeAmbiguous.getParameterTypes().size()) {
                        if (areMethodsAmbiguous(
                                getSamCompatible(parameterType),
                                getSamCompatible(parameterTypeAt(maybeAmbiguous, argumentIndex)))) {
                            count++;
                        }
                    }
                }
                if (count >= 2) {
                    return true;
                }

                return hasGenerics(lambda);
            }

            private @Nullable JavaType parameterTypeAt(JavaType.Method methodType, int argumentIndex) {
                List<JavaType> parameterTypes = methodType.getParameterTypes();
                if (parameterTypes.isEmpty()) {
                    return null;
                }
                int index = Math.min(argumentIndex, parameterTypes.size() - 1);
                JavaType parameterType = parameterTypes.get(index);
                if (index == parameterTypes.size() - 1) {
                    JavaType.Array array = TypeUtils.asArray(parameterType);
                    if (array != null) {
                        return array.getElemType();
                    }
                }
                return parameterType;
            }

            private boolean areMethodsAmbiguous(JavaType.@Nullable Method m1, JavaType.@Nullable Method m2) {
                if (m1 == null || m2 == null || m1.getParameterTypes().size() != m2.getParameterTypes().size()) {
                    return false;
                }
                if (m1 == m2) {
                    return true;
                }
                for (int i = 0; i < m1.getParameterTypes().size(); i++) {
                    JavaType m1i = m1.getParameterTypes().get(i);
                    JavaType m2i = m2.getParameterTypes().get(i);
                    if (!TypeUtils.isAssignableTo(m1i, m2i) &&
                        !TypeUtils.isAssignableTo(m2i, m1i)) {
                        return false;
                    }
                }
                return true;
            }

            private String valueOfType(@Nullable JavaType type) {
                JavaType.Primitive primitive = TypeUtils.asPrimitive(type);
                if (primitive != null) {
                    switch (primitive) {
                        case Boolean:
                            return "true";
                        case Byte:
                        case Char:
                        case Int:
                        case Double:
                        case Float:
                        case Long:
                        case Short:
                            return "0";
                        case String:
                        case Null:
                            return "null";
                        case None:
                        case Void:
                        default:
                            return "";
                    }
                }

                return "null";
            }
        });

        return new TreeVisitor<Tree, ExecutionContext>() {
            @Override
            public boolean isAcceptable(SourceFile sourceFile, ExecutionContext ctx) {
                return sourceFile instanceof JavaSourceFile;
            }

            @Override
            public @Nullable Tree visit(@Nullable Tree tree, ExecutionContext ctx) {
                if (tree instanceof JavaSourceFile) {
                    new ReportVisitor().visit(tree, ctx);
                }
                return convert.visit(tree, ctx);
            }
        };
    }

    /**
     * Records every anonymous functional interface implementation, including the ones the recipe cannot
     * rewrite — those are the sites a reader has to convert by hand, so they belong in the inventory.
     */
    private class ReportVisitor extends JavaIsoVisitor<ExecutionContext> {
        @Override
        public J.NewClass visitNewClass(J.NewClass newClass, ExecutionContext ctx) {
            if (newClass.getBody() != null && newClass.getClazz() != null) {
                JavaType.FullyQualified type = TypeUtils.asFullyQualified(newClass.getClazz().getType());
                JavaType.Method sam = samMethod(newClass);
                if (sam != null) {
                    String blocker = conversionBlocker(newClass, getCursor());
                    insertRow(ctx, type.getFullyQualifiedName(), sam.getName(),
                            blocker == null, blocker == null ? "" : blocker);
                } else {
                    String undecidable = undecidableReason(type);
                    if (undecidable != null) {
                        insertRow(ctx,
                                type == null ? newClass.getClazz().printTrimmed(getCursor()) : type.getFullyQualifiedName(),
                                "", false, undecidable);
                    }
                }
            }
            return super.visitNewClass(newClass, ctx);
        }

        private void insertRow(ExecutionContext ctx, String functionalInterface, String method,
                               boolean convertible, String reason) {
            JavaSourceFile sourceFile = getCursor().firstEnclosing(JavaSourceFile.class);
            J.ClassDeclaration enclosing = getCursor().firstEnclosing(J.ClassDeclaration.class);
            report.insertRow(ctx, new AnonymousFunctionalInterfaceImplementations.Row(
                    sourceFile == null ? "" : sourceFile.getSourcePath().toString(),
                    enclosing == null || enclosing.getType() == null ? "" :
                            enclosing.getType().getFullyQualifiedName(),
                    functionalInterface,
                    method,
                    convertible,
                    reason));
        }
    }

    /**
     * The single abstract method of the interface this anonymous class implements, or {@code null} when the
     * class is not an anonymous implementation of a functional interface at all.
     */
    private static JavaType.@Nullable Method samMethod(J.NewClass n) {
        if (n.getBody() == null || n.getClazz() == null) {
            return null;
        }
        JavaType.FullyQualified type = TypeUtils.asFullyQualified(n.getClazz().getType());
        if (type == null || type.getKind() != JavaType.Class.Kind.Interface) {
            return null;
        }
        return functionalInterfaceMethod(type);
    }

    /**
     * Why an anonymous class could neither be confirmed nor ruled out as a functional interface
     * implementation, or {@code null} when it is genuinely out of scope — extending a class, or
     * implementing an interface with several abstract methods. Sites that are merely undecidable are
     * recorded as unconvertible carrying this reason, so that a reader can tell "the recipe is blind
     * here" apart from "there was nothing to convert".
     */
    private static @Nullable String undecidableReason(JavaType.@Nullable FullyQualified type) {
        if (type == null) {
            return "the supertype has no type attribution";
        }
        if (type.getKind() != JavaType.Class.Kind.Interface) {
            return null;
        }
        Map<String, JavaType.Method> abstractMethods = new LinkedHashMap<>();
        collectAbstractMethods(type, abstractMethods, new HashSet<>());
        // Either a genuine marker interface or, far more often, an interface whose methods were not
        // carried into the LST — indistinguishable from here, so say what was actually observed.
        return abstractMethods.isEmpty() ?
                "the interface has no abstract methods recorded in its type attribution" : null;
    }

    /**
     * The abstract method that makes this interface functional, or {@code null} when it is not one.
     * <p>
     * Unlike {@link #getSamCompatible}, which only looks at methods the interface declares itself, this
     * follows JLS 9.8: abstract methods inherited from superinterfaces count, and ones that redeclare a
     * public {@code Object} method do not. That difference is what makes {@code Comparator} — which
     * declares both {@code compare} and {@code equals} — recognisable as a functional interface.
     */
    private static JavaType.@Nullable Method functionalInterfaceMethod(JavaType.FullyQualified type) {
        Map<String, JavaType.Method> abstractMethods = new LinkedHashMap<>();
        collectAbstractMethods(type, abstractMethods, new HashSet<>());
        return abstractMethods.size() == 1 ? abstractMethods.values().iterator().next() : null;
    }

    /**
     * Sources of type information disagree about how they flag a default method: parsed sources mark it
     * both {@link Flag#Abstract} and {@link Flag#Default}, while sources derived from bytecode — where
     * {@code default} is not an access flag — mark it with neither. Both conventions do mark genuinely abstract
     * methods {@link Flag#Abstract}, so require that positive signal rather than inferring abstractness
     * from the absence of {@link Flag#Default}: an interface whose methods are all {@code default} is not
     * functional, and a lambda in its place does not compile.
     */
    private static boolean isConcrete(JavaType.Method method) {
        return !method.hasFlags(Flag.Abstract) || method.hasFlags(Flag.Default) ||
                method.hasFlags(Flag.Static) || method.hasFlags(Flag.Private);
    }

    private static void collectAbstractMethods(JavaType.FullyQualified type,
                                               Map<String, JavaType.Method> abstractMethods,
                                               Set<String> visited) {
        if (!visited.add(type.getFullyQualifiedName())) {
            return;
        }
        for (JavaType.Method method : type.getMethods()) {
            if (isConcrete(method) || overridesObjectMethod(method)) {
                continue;
            }
            // Keyed by name and arity so a subinterface redeclaring an inherited method counts once.
            abstractMethods.putIfAbsent(method.getName() + '/' + method.getParameterTypes().size(), method);
        }
        for (JavaType.FullyQualified superInterface : type.getInterfaces()) {
            collectAbstractMethods(superInterface, abstractMethods, visited);
        }
    }

    // JLS 9.8: an abstract method matching a public method of Object does not count toward SAM-ness.
    private static boolean overridesObjectMethod(JavaType.Method method) {
        int arity = method.getParameterTypes().size();
        return arity == 0 && ("hashCode".equals(method.getName()) || "toString".equals(method.getName())) ||
               arity == 1 && "equals".equals(method.getName());
    }

    /**
     * Why this anonymous class cannot be rewritten to a lambda, or {@code null} when it can. Only call this
     * for a site {@link #samMethod} has already accepted. Checks run in the order the conversion would hit
     * them, so the reason reported is the one that actually stopped it.
     */
    private static @Nullable String conversionBlocker(J.NewClass n, Cursor cursor) {
        J.Block body = n.getBody();
        assert body != null && n.getClazz() != null;

        if (enclosedByEnum(cursor)) {
            return "declared inside an enum";
        }
        if (body.getStatements().size() != 1 || !(body.getStatements().get(0) instanceof J.MethodDeclaration)) {
            return "declares more than the interface method";
        }
        JavaType.Method sam = getSamCompatible(TypeUtils.asFullyQualified(n.getClazz().getType()));
        if (sam == null) {
            // Reporting recognises inherited SAMs and interfaces that also redeclare an `Object` method,
            // but the rewrite only handles a single abstract method declared on the interface itself.
            return "the abstract method is inherited or declared alongside an `Object` method";
        }
        if (usesThis(cursor)) {
            return "references `this`";
        }
        if (shadowsLocalVariable(cursor)) {
            return "shadows a local variable";
        }
        if (usedAsStatement(cursor)) {
            return "used as a statement";
        }
        if (fieldInitializerReferencingUninitializedField(cursor)) {
            return "initializes a field from an uninitialized field";
        }

        JavaType.FullyQualified type = TypeUtils.asFullyQualified(n.getClazz().getType());
        JavaType.FullyQualified anonymousClass = TypeUtils.asFullyQualified(n.getType());
        if (anonymousClass == null || type == null ||
            anonymousClass.getInterfaces().stream()
                    .noneMatch(i -> i.getFullyQualifiedName().equals(type.getFullyQualifiedName()))) {
            return "missing type information";
        }

        J.MethodDeclaration methodDeclaration = (J.MethodDeclaration) body.getStatements().get(0);
        JavaType.Method declaredMethod = methodDeclaration.getMethodType();
        if (declaredMethod == null) {
            return "missing type information";
        }
        // A lambda can only implement the single abstract method; overriding a `default` method is not equivalent.
        if (!sam.getName().equals(declaredMethod.getName()) ||
            sam.getParameterTypes().size() != declaredMethod.getParameterTypes().size()) {
            return "overrides a `default` method rather than the abstract method";
        }
        if (methodDeclaration.getTypeParameters() != null && !methodDeclaration.getTypeParameters().isEmpty()) {
            return "the interface method declares type parameters";
        }
        return null;
    }

    /**
     * Conversion skips enum bodies entirely to avoid `Accessing static field from enum constructor is not
     * allowed`, so anything nested under an enum declaration is reported but never rewritten.
     */
    private static boolean enclosedByEnum(Cursor cursor) {
        for (Cursor c = cursor.getParent(); c != null; c = c.getParent()) {
            Object v = c.getValue();
            if (v instanceof J.ClassDeclaration &&
                ((J.ClassDeclaration) v).getKind() == J.ClassDeclaration.Kind.Type.Enum) {
                return true;
            }
        }
        return false;
    }

    private static boolean usesThis(Cursor cursor) {
        J.NewClass n = cursor.getValue();
        assert n.getBody() != null;
        AtomicBoolean hasThis = new AtomicBoolean(false);
        new JavaVisitor<Integer>() {
            @Override
            public J visitIdentifier(J.Identifier ident, Integer integer) {
                if ("this".equals(ident.getSimpleName())) {
                    hasThis.set(true);
                }
                return super.visitIdentifier(ident, integer);
            }
        }.visit(n.getBody(), 0, cursor);
        return hasThis.get();
    }

    private static List<String> parameterNames(J.MethodDeclaration method) {
        return method.getParameters().stream()
                .filter(J.VariableDeclarations.class::isInstance)
                .map(v -> ((J.VariableDeclarations) v).getVariables().get(0).getSimpleName())
                .collect(toList());
    }

    // This does not recursive descend extended classes for inherited fields.
    private static List<String> classFields(J.ClassDeclaration classDeclaration) {
        return classDeclaration.getBody().getStatements().stream()
                .filter(J.VariableDeclarations.class::isInstance)
                .map(v -> ((J.VariableDeclarations) v).getVariables().get(0).getSimpleName())
                .collect(toList());
    }

    private static boolean usedAsStatement(Cursor cursor) {
        Iterator<Object> path = cursor.getParentOrThrow().getPath();
        for (Object last = cursor.getValue(); path.hasNext(); ) {
            Object next = path.next();
            if (next instanceof J.Block) {
                return true;
            }
            if (next instanceof J && !(next instanceof J.MethodInvocation)) {
                return false;
            }
            if (next instanceof J.MethodInvocation) {
                for (Expression argument : ((J.MethodInvocation) next).getArguments()) {
                    if (argument == last) {
                        return false;
                    }
                }
            }

            if (next instanceof J) {
                last = next;
            }
        }
        return false;
    }

    private static boolean fieldInitializerReferencingUninitializedField(Cursor cursor) {
        J.NewClass n = cursor.getValue();
        assert n.getBody() != null;
        Cursor parent = cursor.dropParentUntil(is -> is instanceof J.VariableDeclarations.NamedVariable || is instanceof SourceFile);
        Object parentValue = parent.getValue();
        if (!(parentValue instanceof J.VariableDeclarations.NamedVariable)) {
            return false;
        }

        J.VariableDeclarations.NamedVariable variable = cursor.firstEnclosing(J.VariableDeclarations.NamedVariable.class);
        if (variable == null || variable.getInitializer() == null) {
            return false;
        }

        parent = cursor.dropParentUntil(is -> is instanceof J.MethodDeclaration || is instanceof J.ClassDeclaration || is instanceof SourceFile);
        parentValue = parent.getValue();
        if (!(parentValue instanceof J.ClassDeclaration) || ((J.ClassDeclaration) parentValue).getType() == null) {
            return false;
        }

        JavaType.FullyQualified owner = ((J.ClassDeclaration) parentValue).getType();
        AtomicBoolean referencesUninitializedFinalField = new AtomicBoolean(false);
        new JavaIsoVisitor<Integer>() {
            @Override
            public J.Identifier visitIdentifier(J.Identifier ident, Integer integer) {
                if (referencesUninitializedFinalField.get()) {
                    return ident;
                }
                if (ident.getFieldType() != null && ident.getFieldType().hasFlags(Flag.Final) &&
                    !ident.getFieldType().hasFlags(Flag.HasInit) &&
                    owner.equals(ident.getFieldType().getOwner())) {
                    referencesUninitializedFinalField.set(true);
                }
                return super.visitIdentifier(ident, integer);
            }
        }.visit(n.getBody(), 0, cursor);
        return referencesUninitializedFinalField.get();
    }

    // if the contents of the cursor value shadow a local variable in its containing name scope
    private static boolean shadowsLocalVariable(Cursor cursor) {
        J.NewClass n = cursor.getValue();
        assert n.getBody() != null;
        AtomicBoolean hasShadow = new AtomicBoolean(false);

        List<String> localVariables = new ArrayList<>();
        List<J.Block> nameScopeBlocks = new ArrayList<>();
        J nameScope = cursor.dropParentUntil(p -> {
            if (p instanceof J.Block) {
                nameScopeBlocks.add((J.Block) p);
            }
            return p instanceof J.MethodDeclaration || p instanceof J.ClassDeclaration;
        }).getValue();
        if (nameScope instanceof J.MethodDeclaration) {
            J.MethodDeclaration m = (J.MethodDeclaration) nameScope;
            localVariables.addAll(parameterNames(m));
            J.ClassDeclaration c = cursor.firstEnclosing(J.ClassDeclaration.class);
            assert c != null;
            localVariables.addAll(classFields(c));
        } else {
            J.ClassDeclaration c = (J.ClassDeclaration) nameScope;
            localVariables.addAll(classFields(c));
        }

        new JavaVisitor<List<String>>() {
            @Override
            public J visitVariable(J.VariableDeclarations.NamedVariable variable, List<String> variables) {
                variables.add(variable.getSimpleName());
                return variable;
            }

            @Override
            public J visitBlock(J.Block block, List<String> strings) {
                return nameScopeBlocks.contains(block) ? super.visitBlock(block, strings) : block;
            }

            @Override
            public J visitNewClass(J.NewClass newClass, List<String> variables) {
                if (newClass == n) {
                    getCursor().putMessageOnFirstEnclosing(JavaSourceFile.class, "stop", true);
                }
                return newClass;
            }

            @Override
            public @Nullable J visit(@Nullable Tree tree, List<String> variables) {
                if (getCursor().getNearestMessage("stop") != null) {
                    return (J) tree;
                }
                return super.visit(tree, variables);
            }
        }.visit(nameScope, localVariables);

        new JavaVisitor<Integer>() {
            @Override
            public J visitVariable(J.VariableDeclarations.NamedVariable variable, Integer integer) {
                if (localVariables.contains(variable.getSimpleName())) {
                    hasShadow.set(true);
                }
                return super.visitVariable(variable, integer);
            }
        }.visit(n.getBody(), 0, cursor);

        return hasShadow.get();
    }

    private static boolean hasGenerics(J.Lambda lambda) {
        AtomicBoolean atomicBoolean = new AtomicBoolean();
        new JavaVisitor<AtomicBoolean>() {
            @Override
            public J visitMethodInvocation(J.MethodInvocation method, AtomicBoolean atomicBoolean) {
                if (method.getMethodType() != null &&
                    method.getMethodType().getParameterTypes().stream()
                            .anyMatch(p -> p instanceof JavaType.Parameterized &&
                                           ((JavaType.Parameterized) p).getTypeParameters().stream().anyMatch(t -> t instanceof JavaType.GenericTypeVariable))
                ) {
                    atomicBoolean.set(true);
                }
                return super.visitMethodInvocation(method, atomicBoolean);
            }
        }.visit(lambda.getBody(), atomicBoolean);
        return atomicBoolean.get();
    }

    // TODO consider moving to TypeUtils
    private static JavaType.@Nullable Method getSamCompatible(@Nullable JavaType type) {
        JavaType.Method sam = null;
        JavaType.FullyQualified fullyQualified = TypeUtils.asFullyQualified(type);
        if (fullyQualified == null) {
            return null;
        }
        for (JavaType.Method method : fullyQualified.getMethods()) {
            if (isConcrete(method)) {
                continue;
            }
            if (sam != null) {
                return null;
            }
            sam = method;
        }
        return sam;
    }
}
