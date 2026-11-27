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
import lombok.RequiredArgsConstructor;
import org.jspecify.annotations.Nullable;
import org.openrewrite.Cursor;
import org.openrewrite.ExecutionContext;
import org.openrewrite.Preconditions;
import org.openrewrite.ScanningRecipe;
import org.openrewrite.Tree;
import org.openrewrite.TreeVisitor;
import org.openrewrite.internal.ListUtils;
import org.openrewrite.java.JavaIsoVisitor;
import org.openrewrite.java.marker.JavaSourceSet;
import org.openrewrite.java.tree.Expression;
import org.openrewrite.java.tree.Flag;
import org.openrewrite.java.tree.J;
import org.openrewrite.java.tree.JContainer;
import org.openrewrite.java.tree.JLeftPadded;
import org.openrewrite.java.tree.JRightPadded;
import org.openrewrite.java.tree.JavaType;
import org.openrewrite.java.tree.MethodCall;
import org.openrewrite.java.tree.NameTree;
import org.openrewrite.java.tree.Space;
import org.openrewrite.java.tree.TypeTree;
import org.openrewrite.java.tree.TypeUtils;
import org.openrewrite.marker.Markers;
import org.openrewrite.staticanalysis.javascript.JavascriptFileChecker;

import javax.lang.model.SourceVersion;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import static java.util.Collections.emptyList;
import static java.util.Collections.emptySet;
import static java.util.Collections.newSetFromMap;
import static java.util.Collections.singleton;
import static java.util.Objects.requireNonNull;

public class ExplicitLambdaArgumentTypes extends ScanningRecipe<ExplicitLambdaArgumentTypes.SourceTypes> {
    @Getter
    final String displayName = "Use explicit types on lambda arguments";

    @Getter
    final String description = "Adds explicit types on lambda arguments, which are otherwise optional. This can make the code clearer and easier to read. " +
            "This does not add explicit types on arguments when the lambda has one or two parameters and does not have a block body, as things are considered more readable in those cases. " +
            "For example, `(a, b) -> a.length() - b.length()` will not have explicit types added.";

    @Getter
    final Set<String> tags = singleton("RSPEC-S2211");

    // A class declared in the sources hides a name as much as one on the classpath.
    public static class SourceTypes {
        // package -> simple names of its top-level classes; class -> simple names of its member classes
        final Map<String, Set<String>> declaredIn = new HashMap<>();

        // the same for each source set's classpath, filled only for the containers asked about
        final Map<UUID, Map<String, Set<String>>> classpathDeclaredIn = new ConcurrentHashMap<>();

        private Set<String> classpathDeclaredIn(JavaSourceSet sourceSet, String container) {
            return classpathDeclaredIn.computeIfAbsent(sourceSet.getId(), id -> new ConcurrentHashMap<>())
                    .computeIfAbsent(container, c -> {
                        Set<String> names = new HashSet<>();
                        for (JavaType.FullyQualified type : sourceSet.getClasspath()) {
                            String memberName = memberName(type.getFullyQualifiedName(), c);
                            if (memberName != null) {
                                names.add(memberName);
                            }
                        }
                        return names.isEmpty() ? emptySet() : names;
                    });
        }

        // the simple name of a type directly in the container, comparing '$' as '.'
        private static @Nullable String memberName(String fullyQualifiedName, String container) {
            int start = container.isEmpty() ? 0 : container.length() + 1;
            if (fullyQualifiedName.length() <= start) {
                return null;
            }
            for (int i = 0; i < start; i++) {
                char ch = fullyQualifiedName.charAt(i);
                char expected = i < container.length() ? container.charAt(i) : '.';
                if ((ch == '$' ? '.' : ch) != expected) {
                    return null;
                }
            }
            for (int i = start; i < fullyQualifiedName.length(); i++) {
                char ch = fullyQualifiedName.charAt(i);
                if (ch == '.' || ch == '$') {
                    return null;
                }
            }
            return fullyQualifiedName.substring(start);
        }

        boolean declares(Collection<String> containers, String simpleName, List<JavaSourceSet> sourceSets) {
            for (String container : containers) {
                Set<String> names = declaredIn.get(container);
                if (names != null && names.contains(simpleName)) {
                    return true;
                }
                for (JavaSourceSet sourceSet : sourceSets) {
                    if (classpathDeclaredIn(sourceSet, container).contains(simpleName)) {
                        return true;
                    }
                }
            }
            return false;
        }
    }

    @Override
    public SourceTypes getInitialValue(ExecutionContext ctx) {
        return new SourceTypes();
    }

    @Override
    public TreeVisitor<?, ExecutionContext> getScanner(SourceTypes acc) {
        return Preconditions.check(Preconditions.not(new JavascriptFileChecker<>()), new JavaIsoVisitor<ExecutionContext>() {
            @Override
            public J.ClassDeclaration visitClassDeclaration(J.ClassDeclaration classDecl, ExecutionContext ctx) {
                JavaType.FullyQualified type = classDecl.getType();
                if (type != null && !(type instanceof JavaType.Unknown)) {
                    String container = type.getOwningClass() == null ? type.getPackageName() : dotted(type.getOwningClass());
                    acc.declaredIn.computeIfAbsent(container, k -> new HashSet<>()).add(classDecl.getSimpleName());
                }
                return super.visitClassDeclaration(classDecl, ctx);
            }
        });
    }

    @Override
    public TreeVisitor<?, ExecutionContext> getVisitor(SourceTypes sourceTypes) {
        return Preconditions.check(Preconditions.not(new JavascriptFileChecker<>()), new JavaIsoVisitor<ExecutionContext>() {
            private @Nullable NameScope scope;

            @Override
            public J.Lambda visitLambda(J.Lambda lambda, ExecutionContext ctx) {
                J.Lambda l = super.visitLambda(lambda, ctx);
                if (l.getParameters().getParameters().size() <= 2 && !(l.getBody() instanceof J.Block)) {
                    return l;
                }
                // only Java: Groovy closures and Kotlin lambdas declare parameter types with another syntax
                J.CompilationUnit cu = getCursor().firstEnclosing(J.CompilationUnit.class);
                if (cu == null) {
                    return l;
                }
                if (scope == null || scope.cu != cu) {
                    scope = new NameScope(cu, sourceTypes);
                }
                TypeTreeBuilder builder = new TypeTreeBuilder(scope, typeVariablesInScope(), supertypesOfEnclosingClasses());

                // Java does not allow mixing implicitly and explicitly typed lambda parameters
                List<J> newParameters = new ArrayList<>(l.getParameters().getParameters().size());
                boolean changed = false;
                for (J parameter : l.getParameters().getParameters()) {
                    if (parameter instanceof J.Empty) {
                        newParameters.add(parameter);
                        continue;
                    }
                    if (!(parameter instanceof J.VariableDeclarations)) {
                        return l;
                    }
                    J.VariableDeclarations multiVariable = (J.VariableDeclarations) parameter;
                    if (multiVariable.getTypeExpression() != null) {
                        newParameters.add(multiVariable);
                        continue;
                    }
                    TypeTree typeExpression = buildParameterType(multiVariable, builder);
                    if (typeExpression == null) {
                        return l;
                    }
                    newParameters.add(multiVariable
                            .withTypeExpression(typeExpression)
                            .withVariables(ListUtils.mapFirst(multiVariable.getVariables(),
                                    variable -> variable.withPrefix(variable.getPrefix().withWhitespace(" ")))));
                    changed = true;
                }
                if (!changed) {
                    return l;
                }
                builder.commit();
                return l.withParameters(l.getParameters()
                        .withParameters(newParameters)
                        .withParenthesized(true));
            }

            private @Nullable TypeTree buildParameterType(J.VariableDeclarations multiVariable, TypeTreeBuilder builder) {
                JavaType type = multiVariable.getVariables().get(0).getType();
                // a captured "? extends Foo" can be written as Foo only where the target takes the capture under
                // "? super", as in forEach(Consumer<? super T>)
                JavaType.GenericTypeVariable capture = asWildcard(type);
                if (capture != null) {
                    if (capture.getVariance() != JavaType.GenericTypeVariable.Variance.COVARIANT || capture.getBounds().size() != 1 ||
                            !isOnlyContravariantInTarget(capture)) {
                        return null;
                    }
                    type = capture.getBounds().get(0);
                    // an F-bounded capture, as of EnumSet<?>, shows itself in its bound as Enum<?>, which a declared
                    // "?" cannot be told apart from
                    if (hasUnboundedWildcard(type)) {
                        return null;
                    }
                } else if (hasWildcard(type) && mayCarryCaptureOutsideSuper()) {
                    // a nested wildcard may be a capture, which a declared wildcard matches only under "? super"
                    return null;
                }
                return builder.build(type, Space.EMPTY);
            }

            private boolean isOnlyContravariantInTarget(JavaType.GenericTypeVariable capture) {
                JavaType.Parameterized functionalInterface = TypeUtils.asParameterized(targetOfMethodArgument());
                if (functionalInterface == null) {
                    return false;
                }
                boolean underSuper = false;
                for (JavaType typeArgument : functionalInterface.getTypeParameters()) {
                    if (TypeUtils.isOfType(typeArgument, capture)) {
                        return false;
                    }
                    JavaType.GenericTypeVariable wildcard = asWildcard(typeArgument);
                    if (wildcard != null) {
                        for (JavaType bound : wildcard.getBounds()) {
                            if (TypeUtils.isOfType(bound, capture)) {
                                if (wildcard.getVariance() != JavaType.GenericTypeVariable.Variance.CONTRAVARIANT) {
                                    return false;
                                }
                                underSuper = true;
                            }
                        }
                    }
                }
                return underSuper;
            }

            // a capture comes from inferring a method's type arguments; a declared target type has none
            private boolean mayCarryCaptureOutsideSuper() {
                Object parent = getCursor().getParentTreeCursor().getValue();
                if (!(parent instanceof MethodCall)) {
                    return !(parent instanceof J.VariableDeclarations.NamedVariable || parent instanceof J.Return ||
                            parent instanceof J.Assignment || parent instanceof J.TypeCast);
                }
                JavaType target = targetOfMethodArgument();
                if (target == null) {
                    return true;
                }
                JavaType.Parameterized functionalInterface = TypeUtils.asParameterized(target);
                if (functionalInterface == null) {
                    return false;
                }
                for (JavaType typeArgument : functionalInterface.getTypeParameters()) {
                    JavaType.GenericTypeVariable wildcard = asWildcard(typeArgument);
                    if (wildcard == null) {
                        if (hasWildcard(typeArgument)) {
                            return true;
                        }
                    } else if (wildcard.getVariance() == JavaType.GenericTypeVariable.Variance.COVARIANT) {
                        for (JavaType bound : wildcard.getBounds()) {
                            if (hasWildcard(bound)) {
                                return true;
                            }
                        }
                    }
                }
                return false;
            }

            private @Nullable JavaType targetOfMethodArgument() {
                J.Lambda lambda = getCursor().getValue();
                Object parent = getCursor().getParentTreeCursor().getValue();
                if (!(parent instanceof MethodCall)) {
                    return null;
                }
                List<Expression> arguments = ((MethodCall) parent).getArguments();
                JavaType.Method methodType = ((MethodCall) parent).getMethodType();
                if (methodType == null || methodType.getParameterTypes().isEmpty()) {
                    return null;
                }
                int index = arguments.indexOf(lambda);
                if (index < 0) {
                    return null;
                }
                List<JavaType> parameterTypes = methodType.getParameterTypes();
                JavaType target = parameterTypes.get(Math.min(index, parameterTypes.size() - 1));
                JavaType.Array varargs = TypeUtils.asArray(target);
                if (index >= parameterTypes.size() - 1 && methodType.hasFlags(Flag.Varargs) && varargs != null) {
                    target = varargs.getElemType();
                }
                return target;
            }

            private Set<String> typeVariablesInScope() {
                Set<String> names = new HashSet<>();
                Set<String> hidden = new HashSet<>();
                // the type variables of the classes outside a static method or static class are not in scope there
                boolean staticContext = false;
                for (Iterator<Object> it = getCursor().getPath(); it.hasNext() && !staticContext; ) {
                    Object o = it.next();
                    List<J.TypeParameter> typeParameters = null;
                    if (o instanceof J.ClassDeclaration) {
                        J.ClassDeclaration classDecl = (J.ClassDeclaration) o;
                        typeParameters = classDecl.getTypeParameters();
                        JavaType.FullyQualified owner = classDecl.getType() == null ? null : classDecl.getType().getOwningClass();
                        staticContext = classDecl.hasModifier(J.Modifier.Type.Static) ||
                                classDecl.getKind() != J.ClassDeclaration.Kind.Type.Class ||
                                classDecl.getType() != null && owner == null ||
                                owner != null && owner.getKind() == JavaType.FullyQualified.Kind.Interface;
                    } else if (o instanceof J.MethodDeclaration) {
                        J.MethodDeclaration method = (J.MethodDeclaration) o;
                        typeParameters = method.getTypeParameters();
                        staticContext = method.hasModifier(J.Modifier.Type.Static);
                    }
                    if (typeParameters != null) {
                        for (J.TypeParameter typeParameter : typeParameters) {
                            if (typeParameter.getName() instanceof J.Identifier) {
                                String name = ((J.Identifier) typeParameter.getName()).getSimpleName();
                                if (!names.add(name)) {
                                    hidden.add(name);
                                }
                            }
                        }
                    }
                }
                // an inner declaration hides an outer one of the same name
                names.removeAll(hidden);
                return names;
            }

            private Set<String> supertypesOfEnclosingClasses() {
                Set<String> supertypes = new HashSet<>();
                for (Iterator<Object> it = getCursor().getPath(); it.hasNext(); ) {
                    Object o = it.next();
                    if (o instanceof J.ClassDeclaration) {
                        JavaType.FullyQualified type = ((J.ClassDeclaration) o).getType();
                        if (type != null) {
                            addSupertypesOf(type, supertypes);
                        }
                    } else if (o instanceof J.NewClass && ((J.NewClass) o).getBody() != null) {
                        J.NewClass newClass = (J.NewClass) o;
                        addSupertypes(TypeUtils.asFullyQualified(newClass.getType()), supertypes);
                        if (newClass.getClazz() != null) {
                            addSupertypes(TypeUtils.asFullyQualified(newClass.getClazz().getType()), supertypes);
                        }
                    }
                }
                supertypes.remove("java.lang.Object");
                return supertypes;
            }

            private void addSupertypes(JavaType.@Nullable FullyQualified type, Set<String> supertypes) {
                if (type != null && !(type instanceof JavaType.Unknown) && supertypes.add(dotted(type))) {
                    addSupertypesOf(type, supertypes);
                }
            }

            private void addSupertypesOf(JavaType.FullyQualified type, Set<String> supertypes) {
                addSupertypes(type.getSupertype(), supertypes);
                for (JavaType.FullyQualified i : type.getInterfaces()) {
                    addSupertypes(i, supertypes);
                }
            }

            // registers names and imports only in commit(), once every parameter has a type
            @RequiredArgsConstructor
            class TypeTreeBuilder {
                private final NameScope scope;
                private final Set<String> typeVariablesInScope;

                // fully qualified names of the supertypes of the classes around the lambda
                private final Set<String> inheritedFrom;

                // simple names this lambda writes, reserved for the rest of the file on commit
                private final Map<String, String> names = new LinkedHashMap<>();
                private final Set<String> imports = new LinkedHashSet<>();
                private final Set<JavaType> recursionGuard = newSetFromMap(new IdentityHashMap<>());

                void commit() {
                    names.forEach(scope::reserve);
                    for (String imp : imports) {
                        maybeAddImport(imp);
                    }
                }

                @Nullable
                TypeTree build(@Nullable JavaType type, Space space) {
                    if (type == null || type instanceof JavaType.Unknown) {
                        return null;
                    }
                    if (!recursionGuard.add(type)) {
                        return null;
                    }
                    try {
                        return build0(type, space);
                    } finally {
                        recursionGuard.remove(type);
                    }
                }

                private @Nullable TypeTree build0(JavaType type, Space space) {
                    if (type instanceof JavaType.Primitive) {
                        JavaType.Primitive primitive = (JavaType.Primitive) type;
                        if (primitive == JavaType.Primitive.None || primitive == JavaType.Primitive.Null) {
                            return null;
                        }
                        return new J.Primitive(Tree.randomId(), space, Markers.EMPTY, primitive);
                    }
                    if (type instanceof JavaType.FullyQualified) {
                        JavaType.FullyQualified fq = (JavaType.FullyQualified) type;
                        NameTree name = typeName(fq, space);
                        if (name == null) {
                            return null;
                        }
                        // a class that is not parameterized is used raw, even when it declares type variables
                        if (!(fq instanceof JavaType.Parameterized) || fq.getTypeParameters().isEmpty()) {
                            return (TypeTree) name;
                        }
                        JContainer<Expression> typeParameters = buildTypeParameters(fq.getTypeParameters());
                        if (typeParameters == null) {
                            return null;
                        }
                        return new J.ParameterizedType(
                                Tree.randomId(),
                                space,
                                Markers.EMPTY,
                                name.withPrefix(Space.EMPTY),
                                typeParameters,
                                fq
                        );
                    }
                    if (type instanceof JavaType.Array) {
                        JavaType elemType = ((JavaType.Array) type).getElemType();
                        if (asWildcard(elemType) != null) {
                            // only a captured wildcard can be an array's element type, and it cannot be written
                            return null;
                        }
                        TypeTree elem = build(elemType, space);
                        if (elem == null) {
                            return null;
                        }
                        return new J.ArrayType(
                                Tree.randomId(),
                                Space.EMPTY,
                                Markers.EMPTY,
                                elem,
                                null,
                                new JLeftPadded<>(Space.EMPTY, Space.EMPTY, Markers.EMPTY),
                                type
                        );
                    }
                    if (type instanceof JavaType.GenericTypeVariable) {
                        JavaType.GenericTypeVariable genericType = (JavaType.GenericTypeVariable) type;
                        String name = genericType.getName();
                        if (!"?".equals(name)) {
                            // only a type variable declared around the lambda can be named there
                            if (!typeVariablesInScope.contains(name) || scope.isTypeNameDeclared(name) ||
                                    scope.isInheritedMemberType(name, inheritedFrom)) {
                                return null;
                            }
                            return new J.Identifier(Tree.randomId(), space, Markers.EMPTY, emptyList(), name, type, null);
                        }
                        if (genericType.getVariance() == JavaType.GenericTypeVariable.Variance.INVARIANT) {
                            return new J.Wildcard(Tree.randomId(), space, Markers.EMPTY, null, null);
                        }
                        if (genericType.getBounds().size() != 1 || asWildcard(genericType.getBounds().get(0)) != null) {
                            // only a captured wildcard can be the bound of a wildcard, and it cannot be written
                            return null;
                        }
                        TypeTree boundedType = build(genericType.getBounds().get(0), Space.format(" "));
                        if (boundedType == null) {
                            return null;
                        }
                        J.Wildcard.Bound bound = genericType.getVariance() == JavaType.GenericTypeVariable.Variance.COVARIANT ?
                                J.Wildcard.Bound.Extends : J.Wildcard.Bound.Super;
                        return new J.Wildcard(Tree.randomId(), space, Markers.EMPTY,
                                new JLeftPadded<>(Space.format(" "), bound, Markers.EMPTY), boundedType);
                    }
                    return null;
                }

                private @Nullable JContainer<Expression> buildTypeParameters(List<JavaType> typeParameters) {
                    List<JRightPadded<Expression>> typeExpressions = new ArrayList<>();
                    for (JavaType type : typeParameters) {
                        TypeTree typeParameterExpression = build(type, typeExpressions.isEmpty() ? Space.EMPTY : Space.SINGLE_SPACE);
                        if (!(typeParameterExpression instanceof Expression)) {
                            return null;
                        }
                        typeExpressions.add(new JRightPadded<>((Expression) typeParameterExpression, Space.EMPTY, Markers.EMPTY));
                    }
                    return JContainer.build(Space.EMPTY, typeExpressions, Markers.EMPTY);
                }

                // A nested class is written Outer.Inner, importing at most Outer, so that it hides no other Inner in the
                // file and AddImport does not shorten the file's existing Outer.Inner references.
                private @Nullable NameTree typeName(JavaType.FullyQualified fq, Space space) {
                    JavaType.FullyQualified raw = fq instanceof JavaType.Parameterized ? ((JavaType.Parameterized) fq).getType() : fq;
                    if (raw instanceof JavaType.Unknown) {
                        return null;
                    }
                    String packageName = raw.getPackageName();
                    String[] segments = raw.getClassName().split("\\.", -1);
                    for (String segment : segments) {
                        if (!SourceVersion.isIdentifier(segment)) {
                            // anonymous and local classes cannot be named
                            return null;
                        }
                    }

                    // the owning classes, outermost first, aligned with the segments
                    JavaType.FullyQualified[] owners = new JavaType.FullyQualified[segments.length];
                    JavaType.FullyQualified t = raw;
                    for (int i = segments.length - 1; i >= 0 && t != null; i--) {
                        owners[i] = t;
                        // which instance of the generic outer class an inner class belongs to is not in the type
                        if (!scope.isAccessible(t, getCursor(), inheritedFrom) || isInnerClassOfGenericClass(t)) {
                            return null;
                        }
                        t = t.getOwningClass();
                    }
                    if (owners[0] == null) {
                        return null;
                    }

                    if (segments.length > 1) {
                        String simpleName = segments[segments.length - 1];
                        String qualifiedName = dotted(raw);
                        if ((isMemberOfEnclosingClass(raw) ||
                                scope.isExplicitlyImported(raw) && !scope.isInheritedMemberType(simpleName, inheritedFrom)) &&
                                !scope.hasConflict(simpleName, qualifiedName, names)) {
                            names.put(simpleName, qualifiedName);
                            return new J.Identifier(Tree.randomId(), space, Markers.EMPTY, emptyList(), simpleName, raw, null);
                        }
                    }

                    String topLevelName = qualify(packageName, segments[0]);
                    Resolution resolution = scope.isInheritedMemberType(segments[0], inheritedFrom) ?
                            Resolution.QUALIFIED : scope.resolve(segments[0], topLevelName, packageName, names);
                    if (resolution == Resolution.QUALIFIED) {
                        if (packageName.isEmpty()) {
                            return null;
                        }
                        String[] packageSegments = packageName.split("\\.");
                        if (scope.isTypeNameDeclared(packageSegments[0])) {
                            return null;
                        }
                        return buildName(packageSegments, segments, owners, space);
                    }
                    if (resolution == Resolution.IMPORT) {
                        imports.add(topLevelName);
                    }
                    names.put(segments[0], topLevelName);
                    return buildName(new String[0], segments, owners, space);
                }

                // Outer.Inner would be a raw type when Inner belongs to an instance of a generic Outer
                private boolean isInnerClassOfGenericClass(JavaType.FullyQualified type) {
                    JavaType.FullyQualified owner = type.getOwningClass();
                    if (owner == null || type.getFlags().contains(Flag.Static) ||
                            type.getKind() != JavaType.FullyQualified.Kind.Class) {
                        return false;
                    }
                    for (JavaType.FullyQualified o = owner; o != null; o = o.getOwningClass()) {
                        if (!o.getTypeParameters().isEmpty()) {
                            return true;
                        }
                        if (o.getFlags().contains(Flag.Static) || o.getKind() != JavaType.FullyQualified.Kind.Class) {
                            break;
                        }
                    }
                    return false;
                }

                private boolean isMemberOfEnclosingClass(JavaType.FullyQualified type) {
                    JavaType.FullyQualified owner = type.getOwningClass();
                    if (owner == null) {
                        return false;
                    }
                    // only the nearest class: a class nested deeper may inherit another member type of the same name
                    for (Iterator<Object> it = getCursor().getPath(); it.hasNext(); ) {
                        Object o = it.next();
                        if (o instanceof J.ClassDeclaration) {
                            J.ClassDeclaration classDecl = (J.ClassDeclaration) o;
                            return TypeUtils.isOfClassType(classDecl.getType(), owner.getFullyQualifiedName());
                        }
                        if (o instanceof J.NewClass && ((J.NewClass) o).getBody() != null) {
                            return false;
                        }
                    }
                    return false;
                }

                private NameTree buildName(String[] packageSegments, String[] segments,
                                           JavaType.@Nullable FullyQualified[] owners, Space space) {
                    Expression name = null;
                    for (String packageSegment : packageSegments) {
                        name = appendName(name, packageSegment, null, space);
                    }
                    for (int i = 0; i < segments.length; i++) {
                        name = appendName(name, segments[i], owners[i], space);
                    }
                    return (NameTree) requireNonNull(name);
                }

                private Expression appendName(@Nullable Expression qualifier, String segment,
                                              JavaType.@Nullable FullyQualified type, Space space) {
                    if (qualifier == null) {
                        return new J.Identifier(Tree.randomId(), space, Markers.EMPTY, emptyList(), segment, type, null);
                    }
                    return new J.FieldAccess(Tree.randomId(), Space.EMPTY, Markers.EMPTY, qualifier,
                            new JLeftPadded<>(Space.EMPTY,
                                    new J.Identifier(Tree.randomId(), Space.EMPTY, Markers.EMPTY, emptyList(), segment, type, null),
                                    Markers.EMPTY),
                            type);
                }
            }
        });
    }

    private static boolean hasUnboundedWildcard(JavaType type) {
        return hasUnboundedWildcard(type, newSetFromMap(new IdentityHashMap<>()));
    }

    private static boolean hasUnboundedWildcard(JavaType type, Set<JavaType> seen) {
        JavaType.Parameterized parameterized = TypeUtils.asParameterized(type);
        if (parameterized == null || !seen.add(parameterized)) {
            return false;
        }
        for (JavaType typeArgument : parameterized.getTypeParameters()) {
            JavaType.GenericTypeVariable wildcard = asWildcard(typeArgument);
            if (wildcard != null && wildcard.getVariance() == JavaType.GenericTypeVariable.Variance.INVARIANT ||
                    hasUnboundedWildcard(typeArgument, seen)) {
                return true;
            }
        }
        return false;
    }

    private static boolean hasWildcard(JavaType type) {
        return hasWildcard(type, newSetFromMap(new IdentityHashMap<>()));
    }

    private static boolean hasWildcard(JavaType type, Set<JavaType> seen) {
        if (asWildcard(type) != null) {
            return true;
        }
        JavaType.Array array = TypeUtils.asArray(type);
        if (array != null) {
            return hasWildcard(array.getElemType(), seen);
        }
        JavaType.Parameterized parameterized = TypeUtils.asParameterized(type);
        if (parameterized == null || !seen.add(parameterized)) {
            return false;
        }
        for (JavaType typeArgument : parameterized.getTypeParameters()) {
            if (hasWildcard(typeArgument, seen)) {
                return true;
            }
        }
        return false;
    }

    private static JavaType.@Nullable GenericTypeVariable asWildcard(@Nullable JavaType type) {
        JavaType.GenericTypeVariable generic = TypeUtils.asGeneric(type);
        return generic != null && "?".equals(generic.getName()) ? generic : null;
    }

    private static String dotted(JavaType.FullyQualified type) {
        return TypeUtils.toFullyQualifiedName(type.getFullyQualifiedName());
    }

    private static String qualify(String packageName, String name) {
        return packageName.isEmpty() ? name : packageName + "." + name;
    }

    private static String simpleName(String className) {
        return className.substring(className.lastIndexOf('.') + 1);
    }

    private enum Resolution {
        SIMPLE,
        IMPORT,
        QUALIFIED
    }

    private static class NameScope {
        private final J.CompilationUnit cu;
        private final List<JavaSourceSet> sourceSets;
        private final SourceTypes sourceTypes;
        private final String packageName;
        private final Map<String, Set<String>> singleTypeImports = new HashMap<>();
        private final Set<String> onDemandPackages = new HashSet<>();
        // simple name -> classes declared in this compilation unit, at any depth
        private final Map<String, Set<String>> declaredTypes = new HashMap<>();
        private final Set<String> declaredTopLevelTypes = new HashSet<>();
        private final Set<String> typeParameterNames = new HashSet<>();
        private final Map<String, Set<String>> typesInUse = new HashMap<>();
        // simple names already given to classes in this compilation unit's lambdas
        private final Map<String, String> reserved = new HashMap<>();

        NameScope(J.CompilationUnit cu, SourceTypes sourceTypes) {
            this.cu = cu;
            this.sourceSets = cu.getMarkers().findAll(JavaSourceSet.class);
            this.sourceTypes = sourceTypes;
            this.packageName = cu.getPackageDeclaration() == null ? "" : cu.getPackageDeclaration().getPackageName();
            for (J.Import imp : cu.getImports()) {
                String qualified = imp.getQualid().printTrimmed().replaceAll("\\s", "");
                String name = imp.getQualid().getSimpleName();
                if ("*".equals(name)) {
                    // a package, or a class whose member classes are imported (also by a static import)
                    onDemandPackages.add(qualified.substring(0, qualified.length() - 2));
                } else {
                    // a static import can bring in a member class by its simple name too
                    singleTypeImports.computeIfAbsent(name, k -> new HashSet<>()).add(qualified);
                }
            }
            new JavaIsoVisitor<Integer>() {
                @Override
                public J.ClassDeclaration visitClassDeclaration(J.ClassDeclaration classDecl, Integer p) {
                    String fqn = classDecl.getType() == null ?
                            classDecl.getSimpleName() : dotted(classDecl.getType());
                    declaredTypes.computeIfAbsent(classDecl.getSimpleName(), k -> new HashSet<>()).add(fqn);
                    if (getCursor().getParentTreeCursor().getValue() instanceof J.CompilationUnit) {
                        declaredTopLevelTypes.add(fqn);
                    }
                    return super.visitClassDeclaration(classDecl, p);
                }

                @Override
                public J.TypeParameter visitTypeParameter(J.TypeParameter typeParam, Integer p) {
                    if (typeParam.getName() instanceof J.Identifier) {
                        typeParameterNames.add(((J.Identifier) typeParam.getName()).getSimpleName());
                    }
                    return super.visitTypeParameter(typeParam, p);
                }
            }.visit(cu, 0);
            for (JavaType type : cu.getTypesInUse().getTypesInUse()) {
                if (type instanceof JavaType.FullyQualified && !(type instanceof JavaType.Unknown)) {
                    JavaType.FullyQualified fq = (JavaType.FullyQualified) type;
                    typesInUse.computeIfAbsent(simpleName(fq.getClassName()), k -> new HashSet<>()).add(dotted(fq));
                }
            }
        }

        boolean isTypeNameDeclared(String simpleName) {
            return declaredTypes.containsKey(simpleName);
        }

        boolean isExplicitlyImported(JavaType.FullyQualified type) {
            return singleTypeImports.getOrDefault(simpleName(type.getClassName()), emptySet()).contains(dotted(type));
        }

        void reserve(String simpleName, String fullyQualifiedName) {
            reserved.put(simpleName, fullyQualifiedName);
        }

        boolean hasConflict(String simpleName, String fullyQualifiedName, Map<String, String> pending) {
            // a single-type import or a top-level class of this file fixes what the simple name means, whatever
            // other classes of that name the file refers to by their qualified names
            Set<String> imported = singleTypeImports.get(simpleName);
            boolean bound = imported != null && imported.size() == 1 && imported.contains(fullyQualifiedName) ||
                    declaredTopLevelTypes.contains(fullyQualifiedName);
            return hasOther(imported, fullyQualifiedName) ||
                    hasOther(declaredTypes.get(simpleName), fullyQualifiedName) ||
                    !bound && hasOther(typesInUse.get(simpleName), fullyQualifiedName) ||
                    typeParameterNames.contains(simpleName) ||
                    isOther(reserved.get(simpleName), fullyQualifiedName) ||
                    isOther(pending.get(simpleName), fullyQualifiedName);
        }

        Resolution resolve(String simpleName, String topLevelName, String typePackage, Map<String, String> pending) {
            if (hasConflict(simpleName, topLevelName, pending)) {
                return Resolution.QUALIFIED;
            }
            if (declaredTopLevelTypes.contains(topLevelName) ||
                    singleTypeImports.containsKey(simpleName) ||
                    typePackage.equals(packageName)) {
                return Resolution.SIMPLE;
            }
            if ("java.lang".equals(typePackage) || onDemandPackages.contains(typePackage)) {
                // resolved through an on-demand import; another on-demand import or this package could claim the name
                return isClaimedElsewhere(simpleName, typePackage) ? Resolution.QUALIFIED : Resolution.SIMPLE;
            }
            return Resolution.IMPORT;
        }

        // a member class inherited from a supertype hides an imported class of the same name
        boolean isInheritedMemberType(String simpleName, Set<String> inheritedFrom) {
            return sourceTypes.declares(inheritedFrom, simpleName, sourceSets);
        }

        private boolean isClaimedElsewhere(String simpleName, String typePackage) {
            Set<String> candidates = new HashSet<>(onDemandPackages);
            candidates.add("java.lang");
            candidates.add(packageName);
            candidates.remove(typePackage);
            return sourceTypes.declares(candidates, simpleName, sourceSets);
        }

        boolean isAccessible(JavaType.FullyQualified type, Cursor cursor, Set<String> inheritedFrom) {
            Set<Flag> flags = type.getFlags();
            if (flags.contains(Flag.Public)) {
                return true;
            }
            if (flags.contains(Flag.Private)) {
                // only within the top-level class that declares it
                J.ClassDeclaration outermost = null;
                for (Iterator<Object> it = cursor.getPath(); it.hasNext(); ) {
                    Object o = it.next();
                    if (o instanceof J.ClassDeclaration) {
                        outermost = (J.ClassDeclaration) o;
                    }
                }
                return outermost != null && TypeUtils.isOfClassType(outermost.getType(), topLevelOf(type));
            }
            // package-private or protected: the same package can always name it
            if (type.getPackageName().equals(packageName)) {
                return true;
            }
            // a protected member class, from a subclass of the class that declares it
            return flags.contains(Flag.Protected) && type.getOwningClass() != null &&
                    inheritedFrom.contains(dotted(type.getOwningClass()));
        }

        private static String topLevelOf(JavaType.FullyQualified type) {
            JavaType.FullyQualified t = type;
            while (t.getOwningClass() != null) {
                t = t.getOwningClass();
            }
            return dotted(t);
        }

        private static boolean hasOther(@Nullable Set<String> fqns, String fullyQualifiedName) {
            return fqns != null && fqns.stream().anyMatch(fqn -> !fqn.equals(fullyQualifiedName));
        }

        private static boolean isOther(@Nullable String fqn, String fullyQualifiedName) {
            return fqn != null && !fqn.equals(fullyQualifiedName);
        }
    }
}
