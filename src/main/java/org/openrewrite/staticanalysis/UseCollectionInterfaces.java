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
import org.jetbrains.annotations.Contract;
import org.jspecify.annotations.Nullable;
import org.openrewrite.*;
import org.openrewrite.internal.ListUtils;
import org.openrewrite.java.JavaIsoVisitor;
import org.openrewrite.java.tree.*;
import org.openrewrite.marker.Markers;

import java.nio.file.Path;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;

import static java.util.Collections.*;
import static java.util.Objects.requireNonNull;

import static org.openrewrite.Tree.randomId;

public class UseCollectionInterfaces extends ScanningRecipe<UseCollectionInterfaces.Accumulator> {

    @Getter
    final String displayName = "Use `Collection` interfaces";

    @Getter
    final String description = "Use `Deque`, `List`, `Map`, `ConcurrentMap`, `Queue`, " +
            "and `Set` instead of implemented collections. Replaces the return type " +
            "of public method declarations and the variable type public variable " +
            "declarations. Programming to an interface rather than a concrete " +
            "collection type decouples callers from a specific implementation, " +
            "making it easier to swap data structures later without breaking " +
            "dependent code. A declaration keeps its type when any of its uses, in any source file, " +
            "requires the implementation type.";

    @Getter
    final Set<String> tags = singleton("RSPEC-S1319");

    @Getter
    final Duration estimatedEffortPerOccurrence = Duration.ofMinutes(10);

    static final Map<String, String> rspecRulesReplaceTypeMap = new HashMap<>();

    static {
        rspecRulesReplaceTypeMap.put("java.util.ArrayDeque", "java.util.Deque");
        rspecRulesReplaceTypeMap.put("java.util.concurrent.ConcurrentLinkedDeque", "java.util.Deque");
        // List
        rspecRulesReplaceTypeMap.put("java.util.AbstractList", "java.util.List");
        rspecRulesReplaceTypeMap.put("java.util.AbstractSequentialList", "java.util.List");
        rspecRulesReplaceTypeMap.put("java.util.ArrayList", "java.util.List");
        rspecRulesReplaceTypeMap.put("java.util.concurrent.CopyOnWriteArrayList", "java.util.List");
        rspecRulesReplaceTypeMap.put("java.util.LinkedList", "java.util.List");
        rspecRulesReplaceTypeMap.put("java.util.Stack", "java.util.List");
        rspecRulesReplaceTypeMap.put("java.util.Vector", "java.util.List");
        // Map
        rspecRulesReplaceTypeMap.put("java.util.AbstractMap", "java.util.Map");
        rspecRulesReplaceTypeMap.put("java.util.EnumMap", "java.util.Map");
        rspecRulesReplaceTypeMap.put("java.util.HashMap", "java.util.Map");
        rspecRulesReplaceTypeMap.put("java.util.Hashtable", "java.util.Map");
        rspecRulesReplaceTypeMap.put("java.util.IdentityHashMap", "java.util.Map");
        rspecRulesReplaceTypeMap.put("java.util.LinkedHashMap", "java.util.Map");
        rspecRulesReplaceTypeMap.put("java.util.TreeMap", "java.util.Map");
        rspecRulesReplaceTypeMap.put("java.util.WeakHashMap", "java.util.Map");
        // ConcurrentMap
        rspecRulesReplaceTypeMap.put("java.util.concurrent.ConcurrentHashMap", "java.util.concurrent.ConcurrentMap");
        rspecRulesReplaceTypeMap.put("java.util.concurrent.ConcurrentSkipListMap", "java.util.concurrent.ConcurrentMap");
        // Queue
        rspecRulesReplaceTypeMap.put("java.util.AbstractQueue", "java.util.Queue");
        rspecRulesReplaceTypeMap.put("java.util.concurrent.ConcurrentLinkedQueue", "java.util.Queue");
        rspecRulesReplaceTypeMap.put("java.util.PriorityQueue", "java.util.Queue");
        // Set
        rspecRulesReplaceTypeMap.put("java.util.AbstractSet", "java.util.Set");
        rspecRulesReplaceTypeMap.put("java.util.HashSet", "java.util.Set");
        rspecRulesReplaceTypeMap.put("java.util.LinkedHashSet", "java.util.Set");
        rspecRulesReplaceTypeMap.put("java.util.TreeSet", "java.util.Set");
        rspecRulesReplaceTypeMap.put("java.util.concurrent.CopyOnWriteArraySet", "java.util.Set");
    }

    public static class Accumulator {
        final Set<Path> widenableFiles = new HashSet<>();
        final Set<String> candidates = new HashSet<>();
        final Set<String> unsafe = new HashSet<>(singleton(UNKNOWN_DECLARATION));
        // A declaration is only widened when each of its dependencies is widened too
        final Map<String, Set<String>> dependencies = new HashMap<>();

        private @Nullable Set<String> widened;

        Set<String> widened() {
            if (widened == null) {
                widened = computeWidened();
            }
            return widened;
        }

        private Set<String> computeWidened() {
            Set<String> excluded = new HashSet<>(unsafe);
            boolean changed = true;
            while (changed) {
                changed = false;
                for (Map.Entry<String, Set<String>> dependency : dependencies.entrySet()) {
                    if (!excluded.contains(dependency.getKey()) &&
                        dependency.getValue().stream().anyMatch(target -> excluded.contains(target) || !candidates.contains(target))) {
                        excluded.add(dependency.getKey());
                        changed = true;
                    }
                }
            }
            Set<String> widened = new HashSet<>(candidates);
            widened.removeAll(excluded);
            return widened;
        }
    }

    @Override
    public Accumulator getInitialValue(ExecutionContext ctx) {
        return new Accumulator();
    }

    private static boolean isWidenable(Tree tree) {
        // The analysis follows Java's typing rules, so other languages only contribute uses
        return tree instanceof J.CompilationUnit &&
               !new InterfaceIncompatibleMethodDetector().reduce(tree, new AtomicBoolean(false)).get();
    }

    @Override
    public TreeVisitor<?, ExecutionContext> getScanner(Accumulator acc) {
        return new TreeVisitor<Tree, ExecutionContext>() {
            @Override
            public @Nullable Tree visit(@Nullable Tree tree, ExecutionContext ctx) {
                if (tree instanceof JavaSourceFile) {
                    boolean widenable = isWidenable(tree);
                    if (widenable) {
                        acc.widenableFiles.add(((JavaSourceFile) tree).getSourcePath());
                    }
                    new DeclarationUsageAnalyzer(acc, widenable).visit(tree, 0);
                }
                return tree;
            }
        };
    }

    @Override
    public TreeVisitor<?, ExecutionContext> getVisitor(Accumulator acc) {
        Set<String> widenedDeclarations = acc.widened();
        return new JavaIsoVisitor<ExecutionContext>() {
            @Override
            public J visit(@Nullable Tree tree, ExecutionContext ctx) {
                if (tree instanceof JavaSourceFile &&
                    (widenedDeclarations.isEmpty() || !acc.widenableFiles.contains(((JavaSourceFile) tree).getSourcePath()))) {
                    return (J) tree;
                }
                return super.visit(tree, ctx);
            }

            @Override
            public J.MethodInvocation visitMethodInvocation(J.MethodInvocation method, ExecutionContext ctx) {
                J.MethodInvocation mi = super.visitMethodInvocation(method, ctx);
                if ((mi.getSelect() instanceof J.Identifier || mi.getSelect() instanceof J.FieldAccess) && mi.getSelect().getType() != null &&
                        isWidened(mi.getSelect())) {
                    JavaType originalType = mi.getSelect().getType();
                    JavaType.FullyQualified fullyQualified = TypeUtils.asFullyQualified(originalType);
                    if (fullyQualified != null) {
                        String fullyQualifiedName = fullyQualified.getFullyQualifiedName();
                        if (rspecRulesReplaceTypeMap.containsKey(fullyQualifiedName)) {
                            JavaType.FullyQualified newType = TypeUtils.asFullyQualified(JavaType.buildType(rspecRulesReplaceTypeMap.get(fullyQualifiedName)));
                            if (newType != null) {
                                if (originalType instanceof JavaType.Parameterized) {
                                    newType = new JavaType.Parameterized(null, newType, ((JavaType.Parameterized) originalType).getTypeParameters());
                                }
                                return updateMethodInvocation(mi, newType);
                            }
                        }
                    }
                }
                return mi;
            }

            private boolean isWidened(Expression select) {
                J.Identifier name = nameOf(select);
                return name != null && name.getFieldType() != null && widenedDeclarations.contains(variableKey(name.getFieldType()));
            }

            private J.MethodInvocation updateMethodInvocation(J.MethodInvocation mi, JavaType.FullyQualified newType) {
                if (mi.getSelect() != null) {
                    mi = mi.withSelect(mi.getSelect().withType(newType));
                    if (mi.getSelect() instanceof J.FieldAccess) {
                        J.FieldAccess fieldAccess = (J.FieldAccess) mi.getSelect();
                        mi = mi.withSelect(fieldAccess.withName(fieldAccess.getName().withType(newType)));
                    }
                }
                if (mi.getMethodType() != null) {
                    mi = mi.withMethodType(mi.getMethodType().withDeclaringType(newType));
                }
                return mi;
            }

            @Override
            public J.MethodDeclaration visitMethodDeclaration(J.MethodDeclaration method, ExecutionContext ctx) {
                J.MethodDeclaration m = super.visitMethodDeclaration(method, ctx);
                if (isEligible(m) && m.getReturnTypeExpression() != null &&
                        widenedDeclarations.contains(methodKey(method.getMethodType()))) {
                    JavaType.FullyQualified originalType = TypeUtils.asFullyQualified(m.getReturnTypeExpression().getType());
                    if (originalType != null) {

                        JavaType.FullyQualified newType = TypeUtils.asFullyQualified(
                                JavaType.buildType(rspecRulesReplaceTypeMap.get(originalType.getFullyQualifiedName())));
                        if (newType != null) {
                            maybeRemoveImport(originalType);
                            maybeAddImport(newType);

                            TypeTree returnTypeExpression = getTypeTree(m.getReturnTypeExpression(), newType);
                            m = m.withReturnTypeExpression(returnTypeExpression);
                            JavaType.Method methodType = m.getMethodType();
                            if (methodType != null) {
                                methodType = methodType.withReturnType(returnTypeExpression.getType());
                                m = m.withMethodType(methodType).withName(m.getName().withType(methodType));
                            }
                        }
                    }
                }
                return m;
            }

            @Override
            public J.VariableDeclarations visitVariableDeclarations(J.VariableDeclarations multiVariable, ExecutionContext ctx) {
                J.VariableDeclarations mv = super.visitVariableDeclarations(multiVariable, ctx);
                JavaType.FullyQualified originalType = TypeUtils.asFullyQualified(mv.getType());
                if (originalType != null && isEligible(multiVariable) &&
                        widenedDeclarations.contains(variableKey(multiVariable.getVariables().get(0).getVariableType()))) {
                    JavaType.FullyQualified newType = TypeUtils.asFullyQualified(
                            JavaType.buildType(rspecRulesReplaceTypeMap.get(originalType.getFullyQualifiedName())));
                    if (newType != null) {
                        maybeRemoveImport(originalType);
                        maybeAddImport(newType);

                        mv = mv.withTypeExpression(getTypeTree(mv.getTypeExpression(), newType));
                        mv = mv.withVariables(ListUtils.map(mv.getVariables(), var -> {
                            JavaType.FullyQualified varType = TypeUtils.asFullyQualified(var.getType());
                            if (varType != null && !TypeUtils.isOfType(varType, newType)) {
                                return var.withType(newType).withName(var.getName().withType(newType));
                            }
                            return var;
                        }));
                    }
                }
                return mv;
            }

            @Contract("null, _ -> null")
            private @Nullable TypeTree getTypeTree(@Nullable TypeTree inputTypeExpression, JavaType.FullyQualified newType) {
                if (inputTypeExpression == null) {
                    return null;
                }
                if (inputTypeExpression instanceof J.Identifier) {
                    return new J.Identifier(
                            randomId(),
                            inputTypeExpression.getPrefix(),
                            Markers.EMPTY,
                            emptyList(),
                            newType.getClassName(),
                            newType,
                            null
                    );
                }
                if (inputTypeExpression instanceof J.FieldAccess) {
                    // Fully-qualified type name like java.util.HashSet
                    return new J.Identifier(
                            randomId(),
                            inputTypeExpression.getPrefix(),
                            Markers.EMPTY,
                            emptyList(),
                            newType.getClassName(),
                            newType,
                            null
                    );
                }
                if (inputTypeExpression instanceof J.AnnotatedType) {
                    J.AnnotatedType annotatedType = (J.AnnotatedType) inputTypeExpression;
                    TypeTree annotatedTypeExpression = annotatedType.getTypeExpression();
                    if (annotatedTypeExpression instanceof J.Identifier || annotatedTypeExpression instanceof J.FieldAccess) {
                        return annotatedType.withTypeExpression(new J.Identifier(
                                randomId(),
                                annotatedTypeExpression.getPrefix(),
                                Markers.EMPTY,
                                emptyList(),
                                newType.getClassName(),
                                newType,
                                null
                        ));
                    }
                    J.ParameterizedType parameterizedType = (J.ParameterizedType) annotatedTypeExpression;
                    return annotatedType.withTypeExpression(removeFromParameterizedType(newType, parameterizedType));
                }
                J.ParameterizedType parameterizedType = (J.ParameterizedType) inputTypeExpression;
                return removeFromParameterizedType(newType, parameterizedType);
            }

            private TypeTree removeFromParameterizedType(JavaType.FullyQualified newType,
                                                         J.ParameterizedType parameterizedType) {
                J.Identifier returnType = new J.Identifier(
                        randomId(),
                        Space.EMPTY,
                        Markers.EMPTY,
                        emptyList(),
                        newType.getClassName(),
                        newType,
                        null
                );
                JavaType.Parameterized javaType = (JavaType.Parameterized) parameterizedType.getType();
                return parameterizedType.withClazz(returnType)
                        .withType(javaType != null ? javaType.withType(newType) :
                                new JavaType.Parameterized(null, newType, null));
            }
        };
    }

    private static boolean isReplaceable(@Nullable JavaType type) {
        JavaType.FullyQualified fq = TypeUtils.asFullyQualified(type);
        return fq != null && rspecRulesReplaceTypeMap.containsKey(fq.getFullyQualifiedName());
    }

    private static boolean hasEligibleModifiers(List<J.Modifier> modifiers) {
        return modifiers.isEmpty() || J.Modifier.hasModifier(modifiers, J.Modifier.Type.Public) ||
               J.Modifier.hasModifier(modifiers, J.Modifier.Type.Private);
    }

    private static boolean isEligible(J.MethodDeclaration method) {
        return hasEligibleModifiers(method.getModifiers()) && method.getReturnTypeExpression() != null &&
               isReplaceable(method.getReturnTypeExpression().getType());
    }

    private static boolean isEligible(J.VariableDeclarations variables) {
        TypeTree typeExpression = variables.getTypeExpression();
        return hasEligibleModifiers(variables.getModifiers()) && typeExpression != null &&
               !(typeExpression instanceof J.Identifier && "var".equals(((J.Identifier) typeExpression).getSimpleName())) &&
               isReplaceable(variables.getType());
    }

    private static final String UNKNOWN_DECLARATION = "<unknown>";

    // Not keyed by parameter types, which an invocation may see substituted; same-arity overloads share a key
    private static String methodKey(JavaType.@Nullable Method method) {
        if (method == null) {
            return UNKNOWN_DECLARATION;
        }
        return method.getDeclaringType().getFullyQualifiedName() + "." + method.getName() + "/" + method.getParameterTypes().size();
    }

    private static String variableKey(JavaType.@Nullable Variable variable) {
        if (variable == null) {
            return UNKNOWN_DECLARATION;
        }
        JavaType owner = variable.getOwner();
        if (owner instanceof JavaType.Method) {
            return parameterKey((JavaType.Method) owner, variable.getName());
        }
        JavaType.FullyQualified ownerType = TypeUtils.asFullyQualified(owner);
        return (ownerType == null ? "?" : ownerType.getFullyQualifiedName()) + "#" + variable.getName();
    }

    private static String parameterKey(JavaType.Method method, String parameterName) {
        return methodKey(method) + "#" + parameterName;
    }

    // The name in a simple (`list`) or qualified (`this.list`) variable reference
    private static J.@Nullable Identifier nameOf(Expression expression) {
        if (expression instanceof J.FieldAccess) {
            return ((J.FieldAccess) expression).getName();
        }
        return expression instanceof J.Identifier ? (J.Identifier) expression : null;
    }

    private static class DeclarationUsageAnalyzer extends JavaIsoVisitor<Integer> {
        private static final Set<String> OBJECT_METHODS = new HashSet<>(Arrays.asList(
                "equals", "getClass", "hashCode", "notify", "notifyAll", "toString", "wait"));

        private final Accumulator acc;
        private final boolean mayWiden;

        DeclarationUsageAnalyzer(Accumulator acc, boolean mayWiden) {
            this.acc = acc;
            this.mayWiden = mayWiden;
        }

        @Override
        public J.MethodDeclaration visitMethodDeclaration(J.MethodDeclaration method, Integer p) {
            JavaType.Method methodType = method.getMethodType();
            String key = methodKey(methodType);
            JavaType.FullyQualified returnType = method.getReturnTypeExpression() == null ? null :
                    TypeUtils.asFullyQualified(method.getReturnTypeExpression().getType());
            boolean replaceableReturnType = isReplaceable(returnType);
            if (replaceableReturnType) {
                if (mayWiden && isEligible(method)) {
                    acc.candidates.add(key);
                } else {
                    acc.unsafe.add(key);
                }
            }
            if (method.getBody() == null) {
                // Implementations, lambdas and method references must keep an abstract method's parameter types
                markParametersUnsafe(methodType);
            }
            JavaType.Method overridden = TypeUtils.findOverriddenMethod(methodType).orElse(null);
            if (overridden != null) {
                markParametersUnsafe(methodType);
                markParametersUnsafe(overridden);
                if (replaceableReturnType &&
                    !targetAccepts(key, overridden.getReturnType(), methodKey(overridden), interfaceOf(requireNonNull(returnType)))) {
                    acc.unsafe.add(key);
                }
            }
            return super.visitMethodDeclaration(method, p);
        }

        @Override
        public J.VariableDeclarations visitVariableDeclarations(J.VariableDeclarations multiVariable, Integer p) {
            if (isReplaceable(multiVariable.getType())) {
                Set<String> keys = new HashSet<>();
                for (J.VariableDeclarations.NamedVariable variable : multiVariable.getVariables()) {
                    keys.add(variableKey(variable.getVariableType()));
                }
                if (mayWiden && isEligible(multiVariable) && !hasGeneratedAccessor(multiVariable)) {
                    acc.candidates.addAll(keys);
                    // Variables that share a type expression are widened together or not at all
                    for (String key : keys) {
                        acc.dependencies.computeIfAbsent(key, k -> new HashSet<>()).addAll(keys);
                    }
                } else {
                    acc.unsafe.addAll(keys);
                }
            }
            return super.visitVariableDeclarations(multiVariable, p);
        }

        @Override
        public J.Identifier visitIdentifier(J.Identifier identifier, Integer p) {
            JavaType.Variable variable = identifier.getFieldType();
            if (variable != null && isReplaceable(variable.getType())) {
                Cursor parentCursor = getCursor().getParentTreeCursor();
                Object parent = parentCursor.getValue();
                boolean isDeclaredName = parent instanceof J.VariableDeclarations.NamedVariable &&
                                         ((J.VariableDeclarations.NamedVariable) parent).getName() == identifier;
                if (!isDeclaredName) {
                    // In `this.list`, the use is the whole field access
                    boolean isQualifiedName = parent instanceof J.FieldAccess && ((J.FieldAccess) parent).getName() == identifier;
                    checkUse(variableKey(variable), variable.getType(), isQualifiedName ? parentCursor : getCursor());
                }
            }
            return super.visitIdentifier(identifier, p);
        }

        @Override
        public J.MethodInvocation visitMethodInvocation(J.MethodInvocation method, Integer p) {
            JavaType.Method methodType = method.getMethodType();
            if (methodType != null && isReplaceable(methodType.getReturnType())) {
                checkUse(methodKey(methodType), methodType.getReturnType(), getCursor());
            }
            return super.visitMethodInvocation(method, p);
        }

        @Override
        public J.MemberReference visitMemberReference(J.MemberReference memberRef, Integer p) {
            JavaType.Method methodType = memberRef.getMethodType();
            if (methodType != null && isReplaceable(methodType.getReturnType())) {
                // The functional interface may require the concrete return type
                acc.unsafe.add(methodKey(methodType));
            }
            return super.visitMemberReference(memberRef, p);
        }

        // Record components and Lombok getters are read through accessors, whose uses the analysis does not see
        private boolean hasGeneratedAccessor(J.VariableDeclarations multiVariable) {
            Cursor parent = getCursor().getParentTreeCursor();
            if (parent.getValue() instanceof J.ClassDeclaration) {
                return true;
            }
            Object enclosing = parent.getParentTreeCursor().getValue();
            return parent.getValue() instanceof J.Block && enclosing instanceof J.ClassDeclaration &&
                   (hasLombokGetter(multiVariable.getLeadingAnnotations()) ||
                    hasLombokGetter(((J.ClassDeclaration) enclosing).getLeadingAnnotations()));
        }

        private static boolean hasLombokGetter(List<J.Annotation> annotations) {
            for (J.Annotation annotation : annotations) {
                if (TypeUtils.isOfClassType(annotation.getType(), "lombok.Getter") ||
                    TypeUtils.isOfClassType(annotation.getType(), "lombok.Data") ||
                    TypeUtils.isOfClassType(annotation.getType(), "lombok.Value")) {
                    return true;
                }
            }
            return false;
        }

        private void markParametersUnsafe(JavaType.@Nullable Method method) {
            if (method != null) {
                for (String parameterName : method.getParameterNames()) {
                    acc.unsafe.add(parameterKey(method, parameterName));
                }
            }
        }

        private void checkUse(String key, @Nullable JavaType type, Cursor use) {
            JavaType.FullyQualified concrete = TypeUtils.asFullyQualified(type);
            if (concrete == null || !isSafeUse(key, use, interfaceOf(concrete))) {
                acc.unsafe.add(key);
            }
        }

        private boolean isSafeUse(String key, Cursor useCursor, JavaType.FullyQualified iface) {
            Object use = useCursor.getValue();
            Cursor parentCursor = useCursor.getParentTreeCursor();
            Object parent = parentCursor.getValue();
            if (parent instanceof J.Parentheses || parent instanceof J.Ternary) {
                return isSafeUse(key, parentCursor, iface);
            }
            if (parent instanceof J.MethodInvocation) {
                J.MethodInvocation mi = (J.MethodInvocation) parent;
                if (mi.getSelect() == use) {
                    return mi.getMethodType() != null &&
                           declaresMethod(iface, mi.getSimpleName(), mi.getMethodType().getParameterTypes().size());
                }
                return argumentAccepted(key, mi.getMethodType(), mi.getArguments().indexOf(use), iface);
            }
            if (parent instanceof J.NewClass) {
                J.NewClass nc = (J.NewClass) parent;
                return argumentAccepted(key, nc.getConstructorType(), nc.getArguments().indexOf(use), iface);
            }
            if (parent instanceof J.MemberReference) {
                J.MemberReference mr = (J.MemberReference) parent;
                return mr.getContaining() == use && mr.getMethodType() != null &&
                       declaresMethod(iface, mr.getReference().getSimpleName(), mr.getMethodType().getParameterTypes().size());
            }
            if (parent instanceof J.Assignment) {
                J.Assignment assignment = (J.Assignment) parent;
                if (assignment.getVariable() == use) {
                    // Anything assignable to the concrete type is assignable to the interface
                    return true;
                }
                Expression variable = assignment.getVariable();
                J.Identifier name = nameOf(variable);
                return targetAccepts(key, variable.getType(),
                        name == null || name.getFieldType() == null ? null : variableKey(name.getFieldType()), iface);
            }
            if (parent instanceof J.VariableDeclarations.NamedVariable) {
                J.VariableDeclarations.NamedVariable variable = (J.VariableDeclarations.NamedVariable) parent;
                return targetAccepts(key, variable.getType(), variableKey(variable.getVariableType()), iface);
            }
            if (parent instanceof J.Return) {
                Object enclosing = parentCursor.dropParentUntil(t -> t instanceof J.MethodDeclaration ||
                        t instanceof J.Lambda || t instanceof J.ClassDeclaration || t == Cursor.ROOT_VALUE).getValue();
                if (enclosing instanceof J.MethodDeclaration) {
                    J.MethodDeclaration method = (J.MethodDeclaration) enclosing;
                    return method.getReturnTypeExpression() != null &&
                           targetAccepts(key, method.getReturnTypeExpression().getType(), methodKey(method.getMethodType()), iface);
                }
                return false;
            }
            if (parent instanceof J.NewArray) {
                JavaType.Array arrayType = TypeUtils.asArray(((J.NewArray) parent).getType());
                return arrayType != null && accepts(arrayType.getElemType(), iface);
            }
            if (parent instanceof J.ForEachLoop.Control) {
                return ((J.ForEachLoop.Control) parent).getIterable() == use;
            }
            if (parent instanceof J.ControlParentheses) {
                return parentCursor.getParentTreeCursor().getValue() instanceof J.Synchronized;
            }
            // String concatenation and comparisons, instanceof, casts, and invocations whose result is discarded
            return parent instanceof J.Binary || parent instanceof J.InstanceOf || parent instanceof J.TypeCast ||
                   parent instanceof J.Block;
        }

        private boolean argumentAccepted(String key, JavaType.@Nullable Method method, int index, JavaType.FullyQualified iface) {
            if (method == null || index < 0 || method.getParameterTypes().isEmpty()) {
                return false;
            }
            List<JavaType> parameterTypes = method.getParameterTypes();
            int last = parameterTypes.size() - 1;
            if (method.hasFlags(Flag.Varargs) && index >= last && parameterTypes.get(last) instanceof JavaType.Array &&
                accepts(((JavaType.Array) parameterTypes.get(last)).getElemType(), iface)) {
                return true;
            }
            if (index > last) {
                return false;
            }
            List<String> parameterNames = method.getParameterNames();
            String targetKey = index < parameterNames.size() ? parameterKey(method, parameterNames.get(index)) : null;
            return targetAccepts(key, parameterTypes.get(index), targetKey, iface);
        }

        /**
         * Whether a value of the interface type fits a target of the given type. When it does not, but the target
         * is itself a declaration this recipe widens to a type that fits, the use is fine on the condition that the
         * target is widened too.
         */
        private boolean targetAccepts(String key, @Nullable JavaType target, @Nullable String targetKey, JavaType.FullyQualified iface) {
            if (accepts(target, iface)) {
                return true;
            }
            JavaType.FullyQualified targetType = TypeUtils.asFullyQualified(target);
            if (targetKey != null && targetType != null && isReplaceable(targetType) &&
                accepts(interfaceOf(targetType), iface)) {
                acc.dependencies.computeIfAbsent(key, k -> new HashSet<>()).add(targetKey);
                return true;
            }
            return false;
        }

        private static boolean accepts(@Nullable JavaType target, JavaType.FullyQualified iface) {
            JavaType.FullyQualified targetType = TypeUtils.asFullyQualified(target);
            return targetType != null && !(targetType instanceof JavaType.Unknown) &&
                   TypeUtils.isAssignableTo(targetType.getFullyQualifiedName(), iface);
        }

        private static boolean declaresMethod(JavaType.FullyQualified type, String name, int arity) {
            return OBJECT_METHODS.contains(name) || declaresMethod(type, name, arity, new HashSet<>());
        }

        private static boolean declaresMethod(JavaType.@Nullable FullyQualified type, String name, int arity, Set<String> seen) {
            if (type == null || !seen.add(type.getFullyQualifiedName())) {
                return false;
            }
            for (JavaType.Method method : type.getMethods()) {
                if (name.equals(method.getName()) && method.getParameterTypes().size() == arity) {
                    return true;
                }
            }
            for (JavaType.FullyQualified anInterface : type.getInterfaces()) {
                if (declaresMethod(anInterface, name, arity, seen)) {
                    return true;
                }
            }
            return false;
        }

        private static JavaType.FullyQualified interfaceOf(JavaType.FullyQualified concrete) {
            String iface = requireNonNull(rspecRulesReplaceTypeMap.get(concrete.getFullyQualifiedName()));
            JavaType.FullyQualified found = findSupertype(concrete, iface, new HashSet<>());
            return found != null ? found : JavaType.ShallowClass.build(iface);
        }

        private static JavaType.@Nullable FullyQualified findSupertype(JavaType.@Nullable FullyQualified type, String fqn, Set<String> seen) {
            if (type == null || !seen.add(type.getFullyQualifiedName())) {
                return null;
            }
            if (fqn.equals(type.getFullyQualifiedName())) {
                return type;
            }
            JavaType.FullyQualified found = findSupertype(type.getSupertype(), fqn, seen);
            for (int i = 0; found == null && i < type.getInterfaces().size(); i++) {
                found = findSupertype(type.getInterfaces().get(i), fqn, seen);
            }
            return found;
        }
    }

    private static class InterfaceIncompatibleMethodDetector extends JavaIsoVisitor<AtomicBoolean> {
        private static final Map<String, Set<String>> nonInterfaceMethods = new HashMap<>();

        static {
            nonInterfaceMethods.put("java.util.Hashtable", unmodifiableSet(new HashSet<>(Arrays.asList(
                    "contains", "elements", "keys"))));
            nonInterfaceMethods.put("java.util.Vector", unmodifiableSet(new HashSet<>(Arrays.asList(
                    "addElement", "capacity", "copyInto", "elementAt", "elements", "ensureCapacity", "insertElementAt", "removeAllElements", "removeElement", "removeElementAt", "setElementAt", "setSize", "trimToSize"))));
            nonInterfaceMethods.put("java.util.ArrayList", unmodifiableSet(new HashSet<>(Arrays.asList(
                    "ensureCapacity", "trimToSize"))));
            nonInterfaceMethods.put("java.util.LinkedList", unmodifiableSet(new HashSet<>(Arrays.asList(
                    // These have been promoted to Java 21 SequencedCollection interface, but we don't check that here
                    "addFirst", "addLast",  "getFirst", "getLast", "removeFirst", "removeLast",
                    "descendingIterator", "offerFirst", "offerLast", "peekFirst", "peekLast", "pollFirst", "pollLast", "pop", "push", "removeFirstOccurrence", "removeLastOccurrence"))));
            nonInterfaceMethods.put("java.util.Stack", unmodifiableSet(new HashSet<>(Arrays.asList(
                    "empty", "peek", "pop", "push", "search"))));
            nonInterfaceMethods.put("java.util.TreeMap", unmodifiableSet(new HashSet<>(Arrays.asList(
                    "ceilingEntry", "ceilingKey", "descendingKeySet", "descendingMap", "firstEntry", "firstKey", "floorEntry", "floorKey", "headMap", "higherEntry", "higherKey", "lastEntry", "lastKey", "lowerEntry", "lowerKey", "navigableKeySet", "pollFirstEntry", "pollLastEntry", "subMap", "tailMap"))));
            nonInterfaceMethods.put("java.util.TreeSet", unmodifiableSet(new HashSet<>(Arrays.asList(
                    "ceiling", "descendingIterator", "descendingSet", "first", "floor", "headSet", "higher", "last", "lower", "pollFirst", "pollLast", "subSet", "tailSet"))));
        }

        @Override
        public J.MethodInvocation visitMethodInvocation(J.MethodInvocation method, AtomicBoolean foundNonInterfaceMethod) {
            if (foundNonInterfaceMethod.get()) {
                return method;
            }
            if (method.getSelect() != null) {
                JavaType.FullyQualified fqType = TypeUtils.asFullyQualified(method.getSelect().getType());
                if (fqType != null && nonInterfaceMethods.getOrDefault(fqType.getFullyQualifiedName(), emptySet())
                        .contains(method.getSimpleName())) {
                    foundNonInterfaceMethod.set(true);
                    return method;
                }
            }
            return super.visitMethodInvocation(method, foundNonInterfaceMethod);
        }
    }
}
