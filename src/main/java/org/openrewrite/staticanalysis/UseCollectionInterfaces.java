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

import org.jspecify.annotations.Nullable;
import org.openrewrite.*;
import org.openrewrite.groovy.tree.G;
import org.openrewrite.internal.ListUtils;
import org.openrewrite.java.JavaIsoVisitor;
import org.openrewrite.java.tree.*;
import org.openrewrite.marker.Markers;

import java.time.Duration;
import java.util.*;

import static java.util.Collections.*;
import static java.util.Objects.requireNonNull;
import static org.openrewrite.Tree.randomId;

public class UseCollectionInterfaces extends Recipe {

    @Override
    public String getDisplayName() {
        return "Use `Collection` interfaces";
    }

    @Override
    public String getDescription() {
        return "Use `Deque`, `List`, `Map`, `ConcurrentMap`, `Queue`, and `Set` instead of implemented collections. " +
               "Replaces the return type of public method declarations and the variable type public variable declarations.";
    }

    @Override
    public Set<String> getTags() {
        return singleton("RSPEC-S1319");
    }

    @Override
    public Duration getEstimatedEffortPerOccurrence() {
        return Duration.ofMinutes(10);
    }

    public static final Map<String, String> rspecRulesReplaceTypeMap = new HashMap<>();
    public static final Map<String, Set<String>> nonInterfaceMethods = new HashMap<>();

    static {
        rspecRulesReplaceTypeMap.put("java.util.ArrayDeque", "java.util.Deque");
        rspecRulesReplaceTypeMap.put("java.util.concurrent.ConcurrentLinkedDeque", "java.util.Deque");
        // List
        rspecRulesReplaceTypeMap.put("java.util.AbstractList", "java.util.List");
        rspecRulesReplaceTypeMap.put("java.util.AbstractSequentialList", "java.util.List");
        rspecRulesReplaceTypeMap.put("java.util.ArrayList", "java.util.List");
        rspecRulesReplaceTypeMap.put("java.util.concurrent.CopyOnWriteArrayList", "java.util.List");
        // Map
        rspecRulesReplaceTypeMap.put("java.util.AbstractMap", "java.util.Map");
        rspecRulesReplaceTypeMap.put("java.util.EnumMap", "java.util.Map");
        rspecRulesReplaceTypeMap.put("java.util.HashMap", "java.util.Map");
        rspecRulesReplaceTypeMap.put("java.util.Hashtable", "java.util.Map");
        rspecRulesReplaceTypeMap.put("java.util.IdentityHashMap", "java.util.Map");
        rspecRulesReplaceTypeMap.put("java.util.LinkedHashMap", "java.util.Map");
        rspecRulesReplaceTypeMap.put("java.util.WeakHashMap", "java.util.Map");
        // ConcurrentMap
        rspecRulesReplaceTypeMap.put("java.util.concurrent.ConcurrentHashMap", "java.util.concurrent.ConcurrentMap");
        rspecRulesReplaceTypeMap.put("java.util.concurrent.ConcurrentSkipListMap", "java.util.concurrent.ConcurrentMap");
        // Queue
        rspecRulesReplaceTypeMap.put("java.util.AbstractQueue", "java.util.Queue");
        rspecRulesReplaceTypeMap.put("java.util.concurrent.ConcurrentLinkedQueue", "java.util.Queue");
        // Set
        rspecRulesReplaceTypeMap.put("java.util.AbstractSet", "java.util.Set");
        rspecRulesReplaceTypeMap.put("java.util.HashSet", "java.util.Set");
        rspecRulesReplaceTypeMap.put("java.util.LinkedHashSet", "java.util.Set");
        rspecRulesReplaceTypeMap.put("java.util.concurrent.CopyOnWriteArraySet", "java.util.Set");

        nonInterfaceMethods.put("java.util.Hashtable", unmodifiableSet(new HashSet<>(Arrays.asList("keys", "elements", "contains"))));
    }

    @Override
    public TreeVisitor<?, ExecutionContext> getVisitor() {
        return new JavaIsoVisitor<ExecutionContext>() {
            private boolean usesNonInterfaceMethods = false;

            @Override
            public boolean isAcceptable(SourceFile sourceFile, ExecutionContext ctx) {
                // TODO: proper Groovy support requires some extra work
                return super.isAcceptable(sourceFile, ctx) && !(sourceFile instanceof G.CompilationUnit);
            }

            @Override
            public J visit(@Nullable Tree tree, ExecutionContext ctx) {
                if (tree instanceof JavaSourceFile) {
                    JavaSourceFile cu = (JavaSourceFile) requireNonNull(tree);

                    InterfaceIncompatibleMethodDetector checker = new InterfaceIncompatibleMethodDetector();
                    checker.visit(cu, ctx);
                    usesNonInterfaceMethods = checker.foundNonInterfaceMethod;
                    if (usesNonInterfaceMethods) {
                        return cu;
                    }
                    for (JavaType type : cu.getTypesInUse().getTypesInUse()) {
                        JavaType.FullyQualified fq = TypeUtils.asFullyQualified(type);
                        if (fq != null && rspecRulesReplaceTypeMap.containsKey(fq.getFullyQualifiedName())) {
                            return super.visit(cu, ctx);
                        }
                    }
                }
                return super.visit(tree, ctx);
            }

            @Override
            public J.MethodDeclaration visitMethodDeclaration(J.MethodDeclaration method, ExecutionContext ctx) {
                if (usesNonInterfaceMethods) {
                    return method;
                }
                J.MethodDeclaration m = super.visitMethodDeclaration(method, ctx);
                if ((m.hasModifier(J.Modifier.Type.Public) || m.hasModifier(J.Modifier.Type.Private) || m.getModifiers().isEmpty()) &&
                    m.getReturnTypeExpression() != null) {
                    JavaType.FullyQualified originalType = TypeUtils.asFullyQualified(m.getReturnTypeExpression().getType());
                    if (originalType != null && rspecRulesReplaceTypeMap.containsKey(originalType.getFullyQualifiedName())) {

                        JavaType.FullyQualified newType = TypeUtils.asFullyQualified(
                                JavaType.buildType(rspecRulesReplaceTypeMap.get(originalType.getFullyQualifiedName())));
                        if (newType != null) {
                            maybeRemoveImport(originalType);
                            maybeAddImport(newType);

                            TypeTree typeExpression;
                            if (m.getReturnTypeExpression() instanceof J.Identifier) {
                                typeExpression = new J.Identifier(
                                        randomId(),
                                        m.getReturnTypeExpression().getPrefix(),
                                        Markers.EMPTY,
                                        emptyList(),
                                        newType.getClassName(),
                                        newType,
                                        null
                                );
                            } else if (m.getReturnTypeExpression() instanceof J.AnnotatedType) {
                                J.AnnotatedType annotatedType = (J.AnnotatedType) m.getReturnTypeExpression();
                                J.ParameterizedType parameterizedType = (J.ParameterizedType) annotatedType.getTypeExpression();
                                typeExpression = annotatedType.withTypeExpression(removeFromParameterizedType(newType, parameterizedType));
                            } else {
                                J.ParameterizedType parameterizedType = (J.ParameterizedType) m.getReturnTypeExpression();
                                typeExpression = removeFromParameterizedType(newType, parameterizedType);
                            }
                            m = m.withReturnTypeExpression(typeExpression);
                        }
                    }
                }
                return m;
            }

            @Override
            public J.MethodInvocation visitMethodInvocation(J.MethodInvocation method, ExecutionContext ctx) {
                if (usesNonInterfaceMethods) {
                    return method;
                }
                J.MethodInvocation mi = super.visitMethodInvocation(method, ctx);
                if ((mi.getSelect() instanceof J.Identifier || mi.getSelect() instanceof J.FieldAccess) && mi.getSelect().getType() != null) {
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

            @Override
            public J.VariableDeclarations visitVariableDeclarations(J.VariableDeclarations multiVariable, ExecutionContext ctx) {
                if (usesNonInterfaceMethods) {
                    return multiVariable;
                }
                J.VariableDeclarations mv = super.visitVariableDeclarations(multiVariable, ctx);
                JavaType.FullyQualified originalType = TypeUtils.asFullyQualified(mv.getType());
                if ((mv.hasModifier(J.Modifier.Type.Public) || mv.hasModifier(J.Modifier.Type.Private) || mv.getModifiers().isEmpty()) &&
                    originalType != null && rspecRulesReplaceTypeMap.containsKey(originalType.getFullyQualifiedName())) {
                    if (mv.getTypeExpression() instanceof J.Identifier && "var".equals(((J.Identifier) mv.getTypeExpression()).getSimpleName())) {
                        return mv;
                    }

                    JavaType.FullyQualified newType = TypeUtils.asFullyQualified(
                            JavaType.buildType(rspecRulesReplaceTypeMap.get(originalType.getFullyQualifiedName())));
                    if (newType != null) {
                        maybeRemoveImport(originalType);
                        maybeAddImport(newType);

                        TypeTree typeExpression;
                        if (mv.getTypeExpression() == null) {
                            typeExpression = null;
                        } else if (mv.getTypeExpression() instanceof J.Identifier) {
                            typeExpression = new J.Identifier(
                                    randomId(),
                                    mv.getTypeExpression().getPrefix(),
                                    Markers.EMPTY,
                                    emptyList(),
                                    newType.getClassName(),
                                    newType,
                                    null
                            );
                        } else if (mv.getTypeExpression() instanceof J.AnnotatedType) {
                            J.AnnotatedType annotatedType = (J.AnnotatedType) mv.getTypeExpression();
                            J.ParameterizedType parameterizedType = (J.ParameterizedType) annotatedType.getTypeExpression();
                            typeExpression = annotatedType.withTypeExpression(removeFromParameterizedType(newType, parameterizedType));
                        } else {
                            J.ParameterizedType parameterizedType = (J.ParameterizedType) mv.getTypeExpression();
                            typeExpression = removeFromParameterizedType(newType, parameterizedType);
                        }

                        mv = mv.withTypeExpression(typeExpression);
                        mv = mv.withVariables(ListUtils.map(mv.getVariables(), var -> {
                            JavaType.FullyQualified varType = TypeUtils.asFullyQualified(var.getType());
                            if (varType != null && !varType.equals(newType)) {
                                return var.withType(newType).withName(var.getName().withType(newType));
                            }
                            return var;
                        }));
                    }
                }
                return mv;
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

    private static class InterfaceIncompatibleMethodDetector extends JavaIsoVisitor<ExecutionContext> {
        boolean foundNonInterfaceMethod  = false;

        @Override
        public J.MethodInvocation visitMethodInvocation(J.MethodInvocation method, ExecutionContext ctx) {
            if (foundNonInterfaceMethod) {
                return method;
            }
            if (method.getSelect() != null) {
                JavaType selectType = method.getSelect().getType();
                JavaType.FullyQualified fqType = TypeUtils.asFullyQualified(selectType);

                if (fqType != null) {
                    String className = fqType.getFullyQualifiedName();
                    Set<String> concreteMethods = nonInterfaceMethods.get(className);

                    if (concreteMethods != null && concreteMethods.contains(method.getSimpleName())) {
                        foundNonInterfaceMethod = true;
                        return method;
                    }
                }
            }
            return super.visitMethodInvocation(method, ctx);
        }
    }
}
