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

import lombok.EqualsAndHashCode;
import lombok.Value;
import org.jspecify.annotations.Nullable;
import org.openrewrite.*;
import org.openrewrite.internal.ListUtils;
import org.openrewrite.internal.StringUtils;
import org.openrewrite.java.AnnotationMatcher;
import org.openrewrite.java.JavaIsoVisitor;
import org.openrewrite.java.search.UsesType;
import org.openrewrite.java.tree.*;
import org.openrewrite.marker.Markers;
import org.openrewrite.staticanalysis.java.JavaFileChecker;

import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;

import static java.util.Collections.*;
import static java.util.stream.Collectors.toList;

import static org.openrewrite.java.tree.J.Wildcard.Bound.Extends;
import static org.openrewrite.java.tree.J.Wildcard.Bound.Super;

@EqualsAndHashCode(callSuper = false)
@Value
public class DeclarationSiteTypeVariance extends ScanningRecipe<DeclarationSiteTypeVariance.Accumulator> {
    private static final AnnotationMatcher OVERRIDE = new AnnotationMatcher("@java.lang.Override");

    @Option(displayName = "Variant types",
            description = "A list of well-known classes that have in/out type variance.",
            example = "java.util.function.Function<IN, OUT>")
    List<String> variantTypes;

    @Option(displayName = "Excluded bounds",
            description = "A list of bounds that should not receive explicit variance. Globs supported.",
            example = "java.lang.*",
            required = false)
    @Nullable
    List<String> excludedBounds;

    @Option(displayName = "Exclude final classes",
            description = "If true, do not add `? extends` variance to final classes. " +
                          "`? super` variance will be added regardless of finality.",
            required = false)
    @Nullable
    Boolean excludeFinalClasses;

    String displayName = "Properly use declaration-site type variance";

    String description = "Java requires use-site type variance, so a `Function<IN, OUT>` method parameter should rather be `Function<? super IN, ? extends OUT>`. " +
               "It is easy to miss that `? super` and `? extends` are absent, so this recipe adds them where that improves the situation.";

    @Override
    public Validated<Object> validate() {
        Validated<Object> v = super.validate();
        v = v.and(Validated.required("variantTypes", variantTypes));
        if (v.isValid()) {
            for (String variantType : variantTypes) {
                v = v.and(Validated.test("variantTypes", "Must be a valid variant type", variantType, vt -> {
                    try {
                        VariantTypeSpec.build(vt);
                        return true;
                    } catch (Throwable ignored) {
                        return false;
                    }
                }));
            }
        }
        return v;
    }

    public static class Accumulator {
        /**
         * {@code type#name#arity} of every method that another declaration overrides, hides or implements
         * through inheritance in the source set.
         */
        final Set<String> relatedMethods = new HashSet<>();

        /**
         * Functional interfaces of the source set that a lambda or method reference implements.
         */
        final Set<String> lambdaTargets = new HashSet<>();

        /**
         * {@code type#name} of every method that a subtype overloads, so a call on the subtype could pick the
         * other method once this one accepts more.
         */
        final Set<String> overloadedInSubtypes = new HashSet<>();
    }

    @Override
    public Accumulator getInitialValue(ExecutionContext ctx) {
        return new Accumulator();
    }

    /**
     * Widening a method that a declaration elsewhere overrides or implements, or that a lambda implements, leaves
     * that declaration with the old signature, which no longer overrides it (a name clash). Collect those methods.
     */
    @Override
    public TreeVisitor<?, ExecutionContext> getScanner(Accumulator acc) {
        return new JavaIsoVisitor<ExecutionContext>() {
            // by identity, since two modules may declare different types with the same name
            private final Set<JavaType.FullyQualified> scannedTypes = newSetFromMap(new IdentityHashMap<>());

            @Override
            public J.CompilationUnit visitCompilationUnit(J.CompilationUnit cu, ExecutionContext ctx) {
                scannedTypes.clear();
                return super.visitCompilationUnit(cu, ctx);
            }

            @Override
            public J.ClassDeclaration visitClassDeclaration(J.ClassDeclaration classDecl, ExecutionContext ctx) {
                addRelatedMethods(classDecl.getType(), scannedTypes, acc);
                return super.visitClassDeclaration(classDecl, ctx);
            }

            @Override
            public J.MethodDeclaration visitMethodDeclaration(J.MethodDeclaration method, ExecutionContext ctx) {
                // anonymous classes are only reachable through the declaring type of their methods
                if (method.getMethodType() != null) {
                    addRelatedMethods(method.getMethodType().getDeclaringType(), scannedTypes, acc);
                }
                return super.visitMethodDeclaration(method, ctx);
            }

            @Override
            public J.Lambda visitLambda(J.Lambda lambda, ExecutionContext ctx) {
                addLambdaTarget(lambda.getType());
                return super.visitLambda(lambda, ctx);
            }

            @Override
            public J.MemberReference visitMemberReference(J.MemberReference memberRef, ExecutionContext ctx) {
                addLambdaTarget(memberRef.getType());
                return super.visitMemberReference(memberRef, ctx);
            }

            private void addLambdaTarget(@Nullable JavaType type) {
                JavaType.FullyQualified fq = TypeUtils.asFullyQualified(type);
                // the functional method may be inherited from a superinterface
                if (fq != null && acc.lambdaTargets.add(fq.getFullyQualifiedName())) {
                    for (JavaType.FullyQualified anInterface : fq.getInterfaces()) {
                        addLambdaTarget(anInterface);
                    }
                }
            }
        };
    }

    /**
     * Every name and arity that more than one type in the hierarchy of {@code type} declares relates those
     * declarations by overriding, hiding or implementation through inheritance, so none of them can change alone.
     */
    private static void addRelatedMethods(JavaType.@Nullable FullyQualified type, Set<JavaType.FullyQualified> scannedTypes,
                                          Accumulator acc) {
        if (type == null || !scannedTypes.add(type)) {
            return;
        }
        Map<String, Set<String>> declaringTypes = new HashMap<>();
        Map<String, List<JavaType.Method>> byName = new HashMap<>();
        for (JavaType.FullyQualified t : hierarchy(type)) {
            for (JavaType.Method method : t.getMethods()) {
                if (!method.isConstructor() && !method.hasFlags(Flag.Private)) {
                    declaringTypes.computeIfAbsent(method.getName() + '#' + method.getParameterTypes().size(),
                            k -> new HashSet<>()).add(t.getFullyQualifiedName());
                    byName.computeIfAbsent(method.getName(), k -> new ArrayList<>()).add(method);
                }
            }
        }
        for (JavaType.Method declared : type.getMethods()) {
            if (declared.isConstructor() || declared.hasFlags(Flag.Private)) {
                continue;
            }
            for (JavaType.Method inherited : byName.getOrDefault(declared.getName(), emptyList())) {
                if (inherited.getDeclaringType() != null &&
                    !inherited.getDeclaringType().getFullyQualifiedName().equals(type.getFullyQualifiedName()) &&
                    !inherited.getParameterTypes().equals(declared.getParameterTypes())) {
                    acc.overloadedInSubtypes.add(inherited.getDeclaringType().getFullyQualifiedName() + '#' + declared.getName());
                }
            }
        }
        for (Map.Entry<String, Set<String>> entry : declaringTypes.entrySet()) {
            if (entry.getValue().size() > 1) {
                for (String declaringType : entry.getValue()) {
                    acc.relatedMethods.add(declaringType + '#' + entry.getKey());
                }
            }
        }
    }

    private static List<JavaType.FullyQualified> hierarchy(JavaType.FullyQualified type) {
        List<JavaType.FullyQualified> types = new ArrayList<>();
        addHierarchy(type, types, new HashSet<>());
        return types;
    }

    private static void addHierarchy(JavaType.@Nullable FullyQualified type, List<JavaType.FullyQualified> types,
                                     Set<String> seen) {
        if (type != null && seen.add(type.getFullyQualifiedName())) {
            types.add(type);
            addHierarchy(type.getSupertype(), types, seen);
            for (JavaType.FullyQualified anInterface : type.getInterfaces()) {
                addHierarchy(anInterface, types, seen);
            }
        }
    }

    @Override
    public TreeVisitor<?, ExecutionContext> getVisitor(Accumulator acc) {
        List<VariantTypeSpec> variantTypeSpecs = variantTypes.stream().map(VariantTypeSpec::build).collect(toList());
        @SuppressWarnings("unchecked")
        TreeVisitor<?, ExecutionContext>[] usesVariantType = variantTypeSpecs.stream()
                .map(spec -> new UsesType<>(spec.getFullyQualifiedName(), false))
                .toArray(TreeVisitor[]::new);
        return Preconditions.check(Preconditions.and(new JavaFileChecker<>(), Preconditions.or(usesVariantType)),
                new JavaIsoVisitor<ExecutionContext>() {
                    private Map<UUID, Candidate> widened = emptyMap();

                    @Override
                    public J.CompilationUnit visitCompilationUnit(J.CompilationUnit cu, ExecutionContext ctx) {
                        widened = new Analysis(variantTypeSpecs, acc).widenedParameters(cu);
                        if (widened.isEmpty()) {
                            return cu;
                        }
                        return super.visitCompilationUnit(cu, ctx);
                    }

                    @Override
                    public J.MethodDeclaration visitMethodDeclaration(J.MethodDeclaration method, ExecutionContext ctx) {
                        J.MethodDeclaration m = super.visitMethodDeclaration(method, ctx);
                        return m.withParameters(ListUtils.map(m.getParameters(), param -> {
                            if (param instanceof J.VariableDeclarations) {
                                J.VariableDeclarations varParam = (J.VariableDeclarations) param;
                                Candidate candidate = widened.get(varParam.getVariables().get(0).getId());
                                if (candidate != null) {
                                    return varParam.withTypeExpression(candidate.widenedType);
                                }
                            }
                            return param;
                        }));
                    }
                });
    }

    @Value
    @EqualsAndHashCode(onlyExplicitlyIncluded = true)
    private static class Candidate {
        J.MethodDeclaration method;
        int index;
        JavaType.Variable variable;

        @EqualsAndHashCode.Include
        UUID id;

        String fullyQualifiedName;
        J.ParameterizedType widenedType;
        Map<Integer, VariantTypeSpec.Variance> changed;

        boolean widensOut() {
            return changed.containsValue(VariantTypeSpec.Variance.OUT);
        }

        boolean widensIn() {
            return changed.containsValue(VariantTypeSpec.Variance.IN);
        }
    }

    private enum Capture {
        BARE,
        REINFERABLE,
        FIXED
    }

    /**
     * A parameter is widened when every use in its method body still compiles with the wildcard type. Parameters
     * passed on to each other within the compilation unit are widened together.
     */
    private class Analysis {
        private static final int MAX_DEPTH = 10;

        private final List<VariantTypeSpec> specs;
        private final Accumulator acc;
        private Set<Candidate> current = emptySet();
        private List<Candidate> all = emptyList();

        Analysis(List<VariantTypeSpec> specs, Accumulator acc) {
            this.specs = specs;
            this.acc = acc;
        }

        Map<UUID, Candidate> widenedParameters(J.CompilationUnit cu) {
            List<Candidate> candidates = new JavaIsoVisitor<List<Candidate>>() {
                // an unresolved supertype may declare a method that one of these overrides
                @Override
                public J.ClassDeclaration visitClassDeclaration(J.ClassDeclaration classDecl, List<Candidate> list) {
                    return ListUtils.concat(classDecl.getImplements(), classDecl.getExtends()).stream().anyMatch(this::isUnresolved) ?
                            classDecl : super.visitClassDeclaration(classDecl, list);
                }

                @Override
                public J.NewClass visitNewClass(J.NewClass newClass, List<Candidate> list) {
                    return newClass.getBody() != null && isUnresolved(newClass.getClazz()) ?
                            newClass : super.visitNewClass(newClass, list);
                }

                private boolean isUnresolved(@Nullable TypeTree typeTree) {
                    return typeTree != null && TypeUtils.asFullyQualified(typeTree.getType()) == null;
                }

                @Override
                public J.MethodDeclaration visitMethodDeclaration(J.MethodDeclaration method, List<Candidate> list) {
                    collectCandidates(method, list);
                    return super.visitMethodDeclaration(method, list);
                }
            }.reduce(cu, new ArrayList<>());
            if (candidates.isEmpty()) {
                return emptyMap();
            }

            all = candidates;
            current = new LinkedHashSet<>(candidates);
            boolean removed;
            do {
                // dropping a candidate can only make others unsafe, so this converges on the greatest fixpoint
                removed = current.removeIf(candidate -> !isSafe(candidate));
            } while (removed);

            Map<UUID, Candidate> result = new HashMap<>();
            for (Candidate candidate : current) {
                result.put(candidate.id, candidate);
            }
            return result;
        }

        private void collectCandidates(J.MethodDeclaration m, List<Candidate> list) {
            JavaType.Method methodType = m.getMethodType();
            if (methodType == null || methodType.getDeclaringType() == null) {
                return;
            }
            // `isOverride()` misses an unresolved supertype, so trust `@Override` too; a private method overrides nothing
            // https://github.com/openrewrite/rewrite-static-analysis/issues/277
            if ((methodType.isOverride() && !methodType.hasFlags(Flag.Private)) ||
                m.getLeadingAnnotations().stream().anyMatch(OVERRIDE::matches)) {
                return;
            }
            JavaType.FullyQualified declaringType = methodType.getDeclaringType();
            String nameKey = declaringType.getFullyQualifiedName() + '#' + methodType.getName();
            if (!methodType.hasFlags(Flag.Private) &&
                (acc.relatedMethods.contains(nameKey + '#' + methodType.getParameterTypes().size()) ||
                 acc.overloadedInSubtypes.contains(nameKey))) {
                return;
            }
            // frameworks such as dependency injection read the generic signature of annotated methods
            for (J.Annotation annotation : m.getLeadingAnnotations()) {
                JavaType.FullyQualified annotationType = TypeUtils.asFullyQualified(annotation.getType());
                if (annotationType == null ||
                    !(annotationType.getFullyQualifiedName().startsWith("java.lang.") || isNullness(annotation))) {
                    return;
                }
            }
            // a lambda or method reference implementing it keeps the old function type
            if (m.getBody() == null && !methodType.hasFlags(Flag.Default) && !methodType.hasFlags(Flag.Static) &&
                acc.lambdaTargets.contains(declaringType.getFullyQualifiedName())) {
                return;
            }
            List<JavaType.Method> overloads = null;
            List<Statement> parameters = m.getParameters();
            for (int i = 0; i < parameters.size(); i++) {
                if (!(parameters.get(i) instanceof J.VariableDeclarations)) {
                    continue;
                }
                J.VariableDeclarations varParam = (J.VariableDeclarations) parameters.get(i);
                if (varParam.getVariables().size() != 1 || varParam.getVarargs() != null ||
                    !(varParam.getTypeExpression() instanceof J.ParameterizedType)) {
                    continue;
                }
                if (hasFrameworkAnnotation(varParam)) {
                    continue;
                }
                J.VariableDeclarations.NamedVariable variable = varParam.getVariables().get(0);
                if (variable.getVariableType() == null) {
                    continue;
                }
                J.ParameterizedType pt = (J.ParameterizedType) varParam.getTypeExpression();
                VariantTypeSpec spec = specs.stream().filter(s -> s.hasType(pt)).findFirst().orElse(null);
                if (spec == null) {
                    continue;
                }
                if (overloads == null) {
                    overloads = sameArityOverloads(declaringType, methodType);
                    if (overloads == null) {
                        return;
                    }
                }
                if (!distinctOverloads(methodType, overloads, i)) {
                    continue;
                }
                J.ParameterizedType widenedType = useDeclarationSiteVariance(pt, spec);
                Map<Integer, VariantTypeSpec.Variance> changed = new HashMap<>();
                List<Expression> before = pt.getTypeParameters() == null ? emptyList() : pt.getTypeParameters();
                List<Expression> after = widenedType.getTypeParameters() == null ? emptyList() : widenedType.getTypeParameters();
                for (int p = 0; p < before.size(); p++) {
                    // the type arguments left alone keep their identity
                    if (before.get(p) != after.get(p)) {
                        changed.put(p, spec.getVariances().get(p));
                    }
                }
                if (!changed.isEmpty()) {
                    list.add(new Candidate(m, i, variable.getVariableType(), variable.getId(),
                            spec.getFullyQualifiedName(), widenedType, changed));
                }
            }
        }

        /**
         * Null when a varargs overload exists, since a call of another arity may then resolve to either.
         */
        private @Nullable List<JavaType.Method> sameArityOverloads(JavaType.FullyQualified declaringType, JavaType.Method methodType) {
            List<JavaType.Method> overloads = new ArrayList<>();
            for (JavaType.Method other : methodsNamed(declaringType, methodType.getName())) {
                if (!other.getParameterTypes().equals(methodType.getParameterTypes())) {
                    if (other.hasFlags(Flag.Varargs) || methodType.hasFlags(Flag.Varargs)) {
                        return null;
                    }
                    if (other.getParameterTypes().size() == methodType.getParameterTypes().size()) {
                        overloads.add(other);
                    }
                }
            }
            return overloads;
        }

        /**
         * A qualifier such as {@code @Named} makes the parameter's generic type part of a lookup key; nullness
         * annotations do not.
         */
        private boolean hasFrameworkAnnotation(J.VariableDeclarations parameter) {
            for (J.Annotation annotation : parameter.getLeadingAnnotations()) {
                if (!isNullness(annotation) && !"SuppressWarnings".equals(annotation.getSimpleName())) {
                    return true;
                }
            }
            return false;
        }

        private boolean isNullness(J.Annotation annotation) {
            return annotation.getSimpleName().toLowerCase(Locale.ROOT).contains("null");
        }

        private List<JavaType.Method> methodsNamed(JavaType.FullyQualified type, String name) {
            List<JavaType.Method> methods = new ArrayList<>();
            for (JavaType.FullyQualified t : hierarchy(type)) {
                for (JavaType.Method method : t.getMethods()) {
                    // constructors and private methods of a supertype are not inherited
                    if (method.getName().equals(name) &&
                        (t == type || (!method.isConstructor() && !method.hasFlags(Flag.Private)))) {
                        methods.add(method);
                    }
                }
            }
            return methods;
        }

        /**
         * A caller may resolve to another overload, or find the call ambiguous, once the parameter at
         * {@code index} accepts more types. That cannot happen when every other overload of the same arity has a
         * parameter that no argument but {@code null} fits together with this method's.
         */
        private boolean distinctOverloads(JavaType.Method method, List<JavaType.Method> overloads, int index) {
            for (JavaType.Method other : overloads) {
                boolean distinct = false;
                for (int j = 0; j < method.getParameterTypes().size() && !distinct; j++) {
                    JavaType mine = method.getParameterTypes().get(j);
                    JavaType theirs = other.getParameterTypes().get(j);
                    distinct = unrelated(mine, theirs) || (j == index && unrelatedErasures(mine, theirs));
                }
                if (!distinct) {
                    return false;
                }
            }
            return true;
        }

        /**
         * Widening only changes type arguments, so a call that picked between two unrelated generic types, such as
         * {@code Supplier} and {@code BiFunction}, still does. The same erasure would clash, and a subtype could
         * make the call ambiguous.
         */
        private boolean unrelatedErasures(JavaType a, JavaType b) {
            JavaType.FullyQualified fa = TypeUtils.asFullyQualified(a);
            JavaType.FullyQualified fb = TypeUtils.asFullyQualified(b);
            return fa != null && fb != null &&
                   !TypeUtils.isAssignableTo(fa.getFullyQualifiedName(), fb) &&
                   !TypeUtils.isAssignableTo(fb.getFullyQualifiedName(), fa);
        }

        /**
         * Whether no value but {@code null} can be assigned to both types.
         */
        private boolean unrelated(JavaType a, JavaType b) {
            if (a instanceof JavaType.Array && b instanceof JavaType.Array) {
                return unrelated(((JavaType.Array) a).getElemType(), ((JavaType.Array) b).getElemType());
            }
            if (a instanceof JavaType.Array || b instanceof JavaType.Array) {
                JavaType other = a instanceof JavaType.Array ? b : a;
                return !(other instanceof JavaType.GenericTypeVariable) &&
                       !TypeUtils.isObject(other) &&
                       !TypeUtils.isOfClassType(other, "java.lang.Cloneable") &&
                       !TypeUtils.isOfClassType(other, "java.io.Serializable");
            }
            JavaType.FullyQualified fa = TypeUtils.asFullyQualified(a);
            JavaType.FullyQualified fb = TypeUtils.asFullyQualified(b);
            if (fa == null || fb == null ||
                TypeUtils.isAssignableTo(fa.getFullyQualifiedName(), fb) ||
                TypeUtils.isAssignableTo(fb.getFullyQualifiedName(), fa)) {
                return false;
            }
            boolean aClass = fa.getKind() != JavaType.FullyQualified.Kind.Interface &&
                             fa.getKind() != JavaType.FullyQualified.Kind.Annotation;
            boolean bClass = fb.getKind() != JavaType.FullyQualified.Kind.Interface &&
                             fb.getKind() != JavaType.FullyQualified.Kind.Annotation;
            return (aClass && bClass) ||
                   (aClass && fa.hasFlags(Flag.Final)) ||
                   (bClass && fb.hasFlags(Flag.Final));
        }

        private boolean isSafe(Candidate candidate) {
            return candidate.method.getBody() == null || areUsesSafe(candidate.method, candidate.variable, candidate, 0);
        }

        private boolean areUsesSafe(J.MethodDeclaration method, JavaType.Variable variable, Candidate candidate, int depth) {
            return new JavaIsoVisitor<AtomicBoolean>() {
                @Override
                public @Nullable J visit(@Nullable Tree tree, AtomicBoolean s) {
                    return s.get() ? super.visit(tree, s) : (J) tree;
                }

                @Override
                public J.Identifier visitIdentifier(J.Identifier identifier, AtomicBoolean s) {
                    if (variable.equals(identifier.getFieldType())) {
                        Object parent = getCursor().getParentTreeCursor().getValue();
                        boolean declaration = parent instanceof J.VariableDeclarations.NamedVariable &&
                                              ((J.VariableDeclarations.NamedVariable) parent).getName() == identifier;
                        if (!declaration && !isSafeUse(getCursor(), candidate, depth)) {
                            s.set(false);
                        }
                    }
                    return identifier;
                }
            }.reduce(method.getBody(), new AtomicBoolean(true), new Cursor(new Cursor(null, Cursor.ROOT_VALUE), method)).get();
        }

        private boolean isSafeUse(Cursor cursor, Candidate candidate, int depth) {
            Cursor exprCursor = outsideParentheses(cursor);
            Object expr = exprCursor.getValue();
            Cursor parentCursor = exprCursor.getParentTreeCursor();
            Object parent = parentCursor.getValue();
            if (parent instanceof J.Block) {
                // an expression statement, such as `Objects.requireNonNull(param)`, discards its value
                return true;
            } else if (parent instanceof J.MethodInvocation) {
                J.MethodInvocation mi = (J.MethodInvocation) parent;
                if (mi.getSelect() == expr) {
                    return isSafeCall(mi, parentCursor, candidate, depth);
                }
                int idx = indexOf(mi.getArguments(), expr);
                return idx >= 0 && isSafeArgument(mi.getMethodType(), idx, parentCursor, false, candidate, depth);
            } else if (parent instanceof J.NewClass) {
                J.NewClass nc = (J.NewClass) parent;
                int idx = indexOf(nc.getArguments(), expr);
                return idx >= 0 && isSafeArgument(nc.getConstructorType(), idx, parentCursor, isDiamond(nc), candidate, depth);
            } else if (parent instanceof J.Assignment) {
                J.Assignment assignment = (J.Assignment) parent;
                if (assignment.getVariable() == expr) {
                    // assigning to the parameter: whatever it accepted before still fits the wider type
                    return true;
                }
                Expression target = assignment.getVariable().unwrap();
                if (target instanceof J.Identifier &&
                    candidate.variable.equals(((J.Identifier) target).getFieldType())) {
                    return true;
                }
                return assignment.getAssignment() == expr && accepts(assignment.getVariable().getType(), candidate);
            } else if (parent instanceof J.VariableDeclarations.NamedVariable) {
                J.VariableDeclarations.NamedVariable variable = (J.VariableDeclarations.NamedVariable) parent;
                J.VariableDeclarations declarations = parentCursor.firstEnclosing(J.VariableDeclarations.class);
                if (declarations != null && isVar(declarations)) {
                    // `var` takes the widened type, so the local's own uses decide
                    J.MethodDeclaration method = parentCursor.firstEnclosing(J.MethodDeclaration.class);
                    return variable.getVariableType() != null && method != null && depth < MAX_DEPTH &&
                           areUsesSafe(method, variable.getVariableType(), candidate, depth + 1);
                }
                return accepts(variable.getType(), candidate);
            } else if (parent instanceof J.Return) {
                JavaType.Method enclosing = enclosingMethodOfReturn(parentCursor);
                return enclosing != null && accepts(enclosing.getReturnType(), candidate);
            } else if (parent instanceof J.Binary) {
                J.Binary.Type operator = ((J.Binary) parent).getOperator();
                return operator == J.Binary.Type.Equal || operator == J.Binary.Type.NotEqual;
            } else if (parent instanceof J.MemberReference) {
                J.MemberReference ref = (J.MemberReference) parent;
                // `param::apply` is typed by where it goes; its function type may then carry the capture
                return ref.getContaining() == expr &&
                       (isObjectMethod(ref.getMethodType()) ||
                        isFunctionalMethod(ref.getMethodType(), candidate) &&
                        isCaptureSafe(parentCursor, Capture.REINFERABLE, depth));
            }
            return false;
        }

        private boolean isSafeCall(J.MethodInvocation mi, Cursor miCursor, Candidate candidate, int depth) {
            JavaType.Method method = mi.getMethodType();
            if (isObjectMethod(method)) {
                return true;
            }
            if (!isFunctionalMethod(method, candidate)) {
                return false;
            }
            // the parameter types become the captured `? super` wildcards, which are not functional interfaces,
            // so a lambda or method reference argument no longer has a target type
            if (candidate.widensIn()) {
                for (Expression argument : mi.getArguments()) {
                    if (isPolyFunctionalExpression(argument)) {
                        return false;
                    }
                }
            }
            // the result becomes the captured `? extends` wildcard
            if (candidate.widensOut() && method != null && !(method.getReturnType() instanceof JavaType.Primitive)) {
                return isCaptureSafe(miCursor, Capture.BARE, depth);
            }
            return true;
        }

        private boolean isPolyFunctionalExpression(Expression expression) {
            Expression e = expression.unwrap();
            if (e instanceof J.Ternary) {
                return isPolyFunctionalExpression(((J.Ternary) e).getTruePart()) ||
                       isPolyFunctionalExpression(((J.Ternary) e).getFalsePart());
            }
            return e instanceof J.Lambda || e instanceof J.MemberReference;
        }

        /**
         * Whether an expression whose type now mentions a captured wildcard is used where that does not matter.
         * <ul>
         * <li>{@link Capture#BARE}: its type is the capture itself, like the result of {@code supplier.get()}, which
         * converts to the wildcard's bound anywhere except where inference would take it as a type argument.</li>
         * <li>{@link Capture#REINFERABLE}: a generic call or poly expression, like {@code optional.map(function)} or
         * {@code function::apply}, whose type a target type re-infers.</li>
         * <li>{@link Capture#FIXED}: its type has the capture as a type argument for good, like the receiver
         * {@code optional.map(function)} in {@code optional.map(function).filter(p)}.</li>
         * </ul>
         */
        private boolean isCaptureSafe(Cursor cursor, Capture capture, int depth) {
            if (depth >= MAX_DEPTH) {
                return false;
            }
            Cursor exprCursor = outsideParentheses(cursor);
            Object expr = exprCursor.getValue();
            Cursor parentCursor = exprCursor.getParentTreeCursor();
            Object parent = parentCursor.getValue();
            if (parent instanceof J.Block) {
                return true;
            } else if (parent instanceof J.MethodInvocation && ((J.MethodInvocation) parent).getSelect() == expr) {
                // members of a capture are those of its bound
                return capture == Capture.BARE || isCaptureSafeReceiver((J.MethodInvocation) parent, parentCursor, depth);
            } else if (capture == Capture.FIXED) {
                return false;
            } else if (parent instanceof J.Assignment) {
                return ((J.Assignment) parent).getAssignment() == expr;
            } else if (parent instanceof J.VariableDeclarations.NamedVariable) {
                // `var` takes the upward projection, which is the bound of a bare capture
                J.VariableDeclarations declarations = parentCursor.firstEnclosing(J.VariableDeclarations.class);
                return declarations != null && (capture == Capture.BARE || !isVar(declarations));
            } else if (parent instanceof J.Return) {
                Cursor function = enclosingFunction(parentCursor);
                if (function.getValue() instanceof J.Lambda) {
                    // safe where the lambda's own target type is not inferred
                    return isCaptureSafe(function, Capture.REINFERABLE, depth + 1);
                }
                return enclosingMethodOfReturn(parentCursor) != null;
            } else if (parent instanceof J.Lambda) {
                return ((J.Lambda) parent).getBody() == expr && isCaptureSafe(parentCursor, Capture.REINFERABLE, depth + 1);
            } else if (parent instanceof J.MethodInvocation) {
                J.MethodInvocation mi = (J.MethodInvocation) parent;
                int idx = indexOf(mi.getArguments(), expr);
                return idx >= 0 && (!mayInferFromArgument(mi.getMethodType(), idx, false, null) ||
                                    isCaptureSafe(parentCursor, Capture.REINFERABLE, depth + 1));
            } else if (parent instanceof J.NewClass) {
                J.NewClass nc = (J.NewClass) parent;
                int idx = indexOf(nc.getArguments(), expr);
                return idx >= 0 && (!mayInferFromArgument(nc.getConstructorType(), idx, isDiamond(nc), null) ||
                                    isCaptureSafe(parentCursor, Capture.REINFERABLE, depth + 1));
            } else if (capture != Capture.BARE) {
                return false;
            } else if (parent instanceof J.Ternary) {
                J.Ternary ternary = (J.Ternary) parent;
                return ternary.getCondition() != expr && isCaptureSafe(parentCursor, Capture.BARE, depth + 1);
            } else if (parent instanceof J.ControlParentheses) {
                // a condition, but not a switch selector, whose case labels are looked up in the capture
                Object statement = parentCursor.getParentTreeCursor().getValue();
                return statement instanceof J.If || statement instanceof J.WhileLoop || statement instanceof J.DoWhileLoop;
            } else if (parent instanceof J.ForEachLoop.Control) {
                return ((J.ForEachLoop.Control) parent).getIterable() == expr;
            }
            return parent instanceof J.Binary || parent instanceof J.Unary || parent instanceof J.InstanceOf ||
                   parent instanceof J.TypeCast || parent instanceof J.FieldAccess || parent instanceof J.Throw;
        }

        /**
         * A call on a receiver whose type has the capture as a type argument, like {@code Stream<CAP>}. Its
         * arguments must still fit once the receiver's type parameters are the capture, and its result is then:
         * the capture itself for a bare type parameter, like {@code orElse(T)}; fixed when built from the
         * receiver's type parameters, like {@code filter} or {@code toList()}; re-inferred when built only from
         * the method's own type variables, like {@code collect(Collector)}; and unaffected otherwise.
         */
        private boolean isCaptureSafeReceiver(J.MethodInvocation mi, Cursor miCursor, int depth) {
            if (mi.getMethodType() == null) {
                return false;
            }
            JavaType.Method generic = genericDeclaration(mi.getMethodType());
            if (generic == null || generic.getDeclaringType() == null) {
                return false;
            }
            Set<String> classParameters = new HashSet<>();
            for (JavaType typeParameter : generic.getDeclaringType().getTypeParameters()) {
                if (!(typeParameter instanceof JavaType.GenericTypeVariable)) {
                    return false;
                }
                classParameters.add(((JavaType.GenericTypeVariable) typeParameter).getName());
            }
            if (!disjoint(classParameters, generic.getDeclaredFormalTypeNames())) {
                return false;
            }
            // an argument fits a `? super T` parameter; a `T` parameter only takes `null`
            List<JavaType> parameterTypes = generic.getParameterTypes();
            for (int i = 0; i < mi.getArguments().size() && !parameterTypes.isEmpty(); i++) {
                JavaType declared = parameterTypes.get(Math.min(i, parameterTypes.size() - 1));
                Expression argument = mi.getArguments().get(i);
                if (argument instanceof J.Empty) {
                    continue;
                }
                for (String name : classParameters) {
                    if (mentionsOutsideSuper(declared, name) &&
                        !(J.Literal.isLiteralValue(argument, null) &&
                          declared instanceof JavaType.GenericTypeVariable)) {
                        return false;
                    }
                }
            }
            JavaType returnType = generic.getReturnType();
            if (returnType instanceof JavaType.GenericTypeVariable &&
                classParameters.contains(((JavaType.GenericTypeVariable) returnType).getName())) {
                // a supertype's parameter may be bound to a type built from the capture, like `S` of `BaseStream`
                JavaType.FullyQualified receiver = mi.getSelect() == null ? null : TypeUtils.asFullyQualified(mi.getSelect().getType());
                boolean ownParameter = receiver != null &&
                                       receiver.getFullyQualifiedName().equals(generic.getDeclaringType().getFullyQualifiedName());
                return isCaptureSafe(miCursor, ownParameter ? Capture.BARE : Capture.FIXED, depth + 1);
            }
            for (String name : classParameters) {
                if (mentions(returnType, name)) {
                    return isCaptureSafe(miCursor, Capture.FIXED, depth + 1);
                }
            }
            for (String formal : generic.getDeclaredFormalTypeNames()) {
                if (mentions(returnType, formal)) {
                    return isCaptureSafe(miCursor, Capture.REINFERABLE, depth + 1);
                }
            }
            return true;
        }

        private Cursor outsideParentheses(Cursor cursor) {
            Cursor exprCursor = cursor;
            while (exprCursor.getParentTreeCursor().getValue() instanceof J.Parentheses) {
                exprCursor = exprCursor.getParentTreeCursor();
            }
            return exprCursor;
        }

        private Cursor enclosingFunction(Cursor returnCursor) {
            return returnCursor.dropParentUntil(t -> t instanceof J.Lambda ||
                                                     t instanceof J.MethodDeclaration ||
                                                     t instanceof J.ClassDeclaration ||
                                                     t == Cursor.ROOT_VALUE);
        }

        private JavaType.@Nullable Method enclosingMethodOfReturn(Cursor returnCursor) {
            Object function = enclosingFunction(returnCursor).getValue();
            return function instanceof J.MethodDeclaration ? ((J.MethodDeclaration) function).getMethodType() : null;
        }

        private boolean isDiamond(J.NewClass nc) {
            if (nc.getClazz() instanceof J.ParameterizedType) {
                List<Expression> typeParameters = ((J.ParameterizedType) nc.getClazz()).getTypeParameters();
                return typeParameters == null || typeParameters.isEmpty() ||
                       (typeParameters.size() == 1 && typeParameters.get(0) instanceof J.Empty);
            }
            return false;
        }

        private boolean isObjectMethod(JavaType.@Nullable Method method) {
            return method != null && TypeUtils.isObject(method.getDeclaringType());
        }

        /**
         * Default methods such as {@code andThen} or {@code negate} return a type built from the captured wildcard,
         * so only the abstract method is safe.
         */
        private boolean isFunctionalMethod(JavaType.@Nullable Method method, Candidate candidate) {
            return method != null && method.getDeclaringType() != null &&
                   method.getDeclaringType().getFullyQualifiedName().equals(candidate.fullyQualifiedName) &&
                   !method.hasFlags(Flag.Default) && !method.hasFlags(Flag.Static);
        }

        private boolean isVar(J.VariableDeclarations declarations) {
            TypeTree typeExpression = declarations.getTypeExpression();
            return typeExpression == null ||
                   (typeExpression instanceof J.Identifier && "var".equals(((J.Identifier) typeExpression).getSimpleName()));
        }

        private boolean isSafeArgument(JavaType.@Nullable Method method, int idx, Cursor invocation, boolean diamond,
                                       Candidate candidate, int depth) {
            if (method == null || method.getParameterTypes().isEmpty()) {
                return false;
            }
            List<JavaType> parameterTypes = method.getParameterTypes();
            int paramIdx = Math.min(idx, parameterTypes.size() - 1);
            JavaType target = parameterTypes.get(paramIdx);
            if (method.hasFlags(Flag.Varargs) && paramIdx == parameterTypes.size() - 1 && target instanceof JavaType.Array) {
                target = ((JavaType.Array) target).getElemType();
            }

            boolean fits = accepts(target, candidate);
            if (!fits) {
                // passed on to a parameter of this compilation unit that is widened as well
                J.MethodDeclaration callee = null;
                for (Candidate other : all) {
                    JavaType.Method otherType = other.method.getMethodType();
                    if (otherType != null && other.method != callee && TypeUtils.isOfTypeIgnoringGenerics(otherType, method)) {
                        if (callee != null) {
                            callee = null;
                            break;
                        }
                        callee = other.method;
                    }
                }
                for (Candidate other : current) {
                    if (other.method == callee && other.index == paramIdx &&
                        other.fullyQualifiedName.equals(candidate.fullyQualifiedName) &&
                        other.changed.keySet().containsAll(candidate.changed.keySet())) {
                        fits = true;
                        break;
                    }
                }
            }
            if (fits) {
                // a type variable inferred from the wildcard becomes its capture, which may show in the result
                return !mayInferFromArgument(method, paramIdx, diamond, candidate.changed.keySet()) ||
                       isCaptureSafe(invocation, Capture.REINFERABLE, depth + 1);
            }
            return isGenericPassThrough(method, paramIdx, invocation, candidate, depth);
        }

        /**
         * Whether the declared type of the parameter at {@code paramIdx} mentions a type variable that the call
         * infers and that also appears in its result. With {@code positions}, only the type arguments at those
         * positions of the declared parameter count, since the others keep their type.
         */
        private boolean mayInferFromArgument(JavaType.@Nullable Method method, int paramIdx, boolean diamond,
                                             @Nullable Set<Integer> positions) {
            if (method == null) {
                return true;
            }
            if (method.isConstructor() && !diamond) {
                return false;
            }
            JavaType.Method generic = genericDeclaration(method);
            if (generic == null) {
                return true;
            }
            if (generic.getParameterTypes().isEmpty()) {
                return false;
            }
            JavaType declaredParam = generic.getParameterTypes().get(Math.min(paramIdx, generic.getParameterTypes().size() - 1));
            List<JavaType> examined = new ArrayList<>();
            if (positions != null && declaredParam instanceof JavaType.Parameterized) {
                List<JavaType> typeArguments = ((JavaType.Parameterized) declaredParam).getTypeParameters();
                for (Integer position : positions) {
                    if (position < typeArguments.size()) {
                        examined.add(typeArguments.get(position));
                    }
                }
            } else {
                examined.add(declaredParam);
            }
            Set<String> inferred = new HashSet<>(generic.getDeclaredFormalTypeNames());
            if (method.isConstructor()) {
                // a diamond infers the class's type parameters
                JavaType.FullyQualified declaring = generic.getDeclaringType();
                if (declaring == null) {
                    return true;
                }
                for (JavaType typeParameter : declaring.getTypeParameters()) {
                    if (typeParameter instanceof JavaType.GenericTypeVariable) {
                        inferred.add(((JavaType.GenericTypeVariable) typeParameter).getName());
                    }
                }
            }
            for (String name : inferred) {
                if (examined.stream().anyMatch(type -> mentions(type, name)) &&
                    (method.isConstructor() || mentions(generic.getReturnType(), name))) {
                    return true;
                }
            }
            return false;
        }

        /**
         * The declaration of an invoked method, with its type variables, since the invocation's type has them
         * substituted. Null when it cannot be found unambiguously.
         */
        private JavaType.@Nullable Method genericDeclaration(JavaType.Method method) {
            if (method.getDeclaringType() == null) {
                return null;
            }
            JavaType.Method generic = null;
            for (JavaType.Method declared : method.getDeclaringType().getMethods()) {
                if (TypeUtils.isOfTypeIgnoringGenerics(declared, method)) {
                    if (generic != null) {
                        return null;
                    }
                    generic = declared;
                }
            }
            return generic;
        }

        /**
         * A generic method parameter declared as a bare type variable, like {@code Objects.requireNonNull(T)},
         * takes the widened type as well. It is safe when the type variable appears nowhere else in the signature,
         * or only as the return type and the result is itself used safely.
         */
        private boolean isGenericPassThrough(JavaType.Method method, int paramIdx, Cursor invocation,
                                             Candidate candidate, int depth) {
            if (depth >= MAX_DEPTH) {
                return false;
            }
            Object call = invocation.getValue();
            if (call instanceof J.MethodInvocation && ((J.MethodInvocation) call).getTypeParameters() != null) {
                // a type witness fixes the type variable to the old type
                return false;
            }
            JavaType.Method generic = genericDeclaration(method);
            if (generic == null || paramIdx >= generic.getParameterTypes().size()) {
                return false;
            }
            JavaType declaredParam = generic.getParameterTypes().get(paramIdx);
            if (!(declaredParam instanceof JavaType.GenericTypeVariable)) {
                return false;
            }
            JavaType.GenericTypeVariable typeVariable = (JavaType.GenericTypeVariable) declaredParam;
            String name = typeVariable.getName();
            if (!generic.getDeclaredFormalTypeNames().contains(name)) {
                return false;
            }
            for (JavaType bound : typeVariable.getBounds()) {
                if (!TypeUtils.isObject(bound)) {
                    return false;
                }
            }
            for (int i = 0; i < generic.getParameterTypes().size(); i++) {
                if (i != paramIdx && mentions(generic.getParameterTypes().get(i), name)) {
                    return false;
                }
            }
            JavaType returnType = generic.getReturnType();
            if (returnType instanceof JavaType.GenericTypeVariable &&
                name.equals(((JavaType.GenericTypeVariable) returnType).getName())) {
                return isSafeUse(invocation, candidate, depth + 1);
            }
            return !mentions(returnType, name);
        }

        private boolean mentions(@Nullable JavaType type, String name) {
            return mentions(type, name, false, 0);
        }

        /**
         * Whether {@code name} occurs in {@code type} other than as (part of) the bound of a {@code ? super} wildcard.
         */
        private boolean mentionsOutsideSuper(@Nullable JavaType type, String name) {
            return mentions(type, name, true, 0);
        }

        private boolean mentions(@Nullable JavaType type, String name, boolean outsideSuper, int depth) {
            if (type == null) {
                return false;
            }
            if (depth > 5) {
                return true;
            }
            if (type instanceof JavaType.GenericTypeVariable) {
                JavaType.GenericTypeVariable g = (JavaType.GenericTypeVariable) type;
                if (name.equals(g.getName())) {
                    return true;
                }
                // a named variable counts through its bounds, like `U extends T`
                if (outsideSuper && "?".equals(g.getName()) && g.getVariance() == JavaType.GenericTypeVariable.Variance.CONTRAVARIANT) {
                    return false;
                }
                for (JavaType bound : g.getBounds()) {
                    if (mentions(bound, name, outsideSuper, depth + 1)) {
                        return true;
                    }
                }
                return false;
            } else if (type instanceof JavaType.Parameterized) {
                for (JavaType typeParameter : ((JavaType.Parameterized) type).getTypeParameters()) {
                    if (mentions(typeParameter, name, outsideSuper, depth + 1)) {
                        return true;
                    }
                }
                return false;
            } else if (type instanceof JavaType.Array) {
                return mentions(((JavaType.Array) type).getElemType(), name, outsideSuper, depth + 1);
            }
            return false;
        }

        /**
         * {@code Object}, the raw type, or the same type with {@code ?} or a matching bounded wildcard at every
         * widened position.
         */
        private boolean accepts(@Nullable JavaType target, Candidate candidate) {
            if (target instanceof JavaType.Class) {
                String fqn = ((JavaType.Class) target).getFullyQualifiedName();
                return TypeUtils.isObject(target) || fqn.equals(candidate.fullyQualifiedName);
            }
            if (!(target instanceof JavaType.Parameterized) ||
                !((JavaType.Parameterized) target).getFullyQualifiedName().equals(candidate.fullyQualifiedName)) {
                return false;
            }
            List<JavaType> typeParameters = ((JavaType.Parameterized) target).getTypeParameters();
            for (Map.Entry<Integer, VariantTypeSpec.Variance> entry : candidate.changed.entrySet()) {
                if (entry.getKey() >= typeParameters.size() ||
                    !(typeParameters.get(entry.getKey()) instanceof JavaType.GenericTypeVariable)) {
                    return false;
                }
                JavaType.GenericTypeVariable arg = (JavaType.GenericTypeVariable) typeParameters.get(entry.getKey());
                if (!"?".equals(arg.getName())) {
                    return false;
                }
                boolean unbounded = arg.getVariance() == JavaType.GenericTypeVariable.Variance.INVARIANT;
                boolean matches = entry.getValue() == VariantTypeSpec.Variance.OUT ?
                        arg.getVariance() == JavaType.GenericTypeVariable.Variance.COVARIANT :
                        arg.getVariance() == JavaType.GenericTypeVariable.Variance.CONTRAVARIANT;
                if (!unbounded && !matches) {
                    return false;
                }
            }
            return true;
        }

        private int indexOf(List<Expression> arguments, Object expr) {
            for (int i = 0; i < arguments.size(); i++) {
                if (arguments.get(i) == expr) {
                    return i;
                }
            }
            return -1;
        }
    }

    private J.ParameterizedType useDeclarationSiteVariance(J.ParameterizedType pt, VariantTypeSpec spec) {
        return pt.withTypeParameters(ListUtils.map(pt.getTypeParameters(), (i, tp) -> {
            VariantTypeSpec.Variance variance = spec.getVariances().get(i);
            if (tp instanceof J.Wildcard ||
                !(tp instanceof NameTree) ||
                variance == VariantTypeSpec.Variance.INVARIANT) {
                return tp;
            }

            JavaType.FullyQualified fq = TypeUtils.asFullyQualified(tp.getType());
            if (fq != null) {
                if (excludedBounds != null) {
                    for (String excludedBound : excludedBounds) {
                        if (StringUtils.matchesGlob(fq.getFullyQualifiedName(), excludedBound)) {
                            return tp;
                        }
                    }
                }
                if (Boolean.TRUE.equals(excludeFinalClasses) && fq.getFlags().contains(Flag.Final) &&
                    variance == VariantTypeSpec.Variance.OUT) {
                    return tp;
                }
            }

            return new J.Wildcard(
                    Tree.randomId(),
                    tp.getPrefix(),
                    Markers.EMPTY,
                    JLeftPadded.build(variance == VariantTypeSpec.Variance.OUT ? Extends : Super)
                            .withBefore(Space.format(" ")),
                    tp.withPrefix(Space.format(" "))
            );
        }));
    }


    @Value
    private static class VariantTypeSpec {
        String fullyQualifiedName;
        List<Variance> variances;

        enum Variance {
            IN,
            OUT,
            INVARIANT
        }

        public boolean hasType(J.ParameterizedType pt) {
            return TypeUtils.isOfClassType(pt.getType(), fullyQualifiedName);
        }

        public static VariantTypeSpec build(String pattern) {
            String fqn = pattern.substring(0, pattern.indexOf('<'));
            String variancesStr = pattern.substring(pattern.indexOf('<') + 1, pattern.lastIndexOf('>'));
            return new VariantTypeSpec(fqn, Arrays.stream(variancesStr.split(","))
                    .map(String::trim)
                    .map(Variance::valueOf)
                    .collect(toList()));
        }
    }
}
