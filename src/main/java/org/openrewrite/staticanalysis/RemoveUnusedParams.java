/*
 * Copyright 2025 the original author or authors.
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

import lombok.RequiredArgsConstructor;
import org.openrewrite.*;
import org.openrewrite.internal.ListUtils;
import org.openrewrite.java.JavaIsoVisitor;
import org.openrewrite.java.MethodMatcher;
import org.openrewrite.java.NoMissingTypes;
import org.openrewrite.java.search.SemanticallyEqual;
import org.openrewrite.java.tree.Expression;
import org.openrewrite.java.tree.J;
import org.openrewrite.java.tree.JavaType;
import org.openrewrite.java.tree.Statement;

import java.util.*;

public class RemoveUnusedParams extends ScanningRecipe<RemoveUnusedParams.Accumulator> {
    public static class Accumulator {
        /**
         * Signatures of all methods that override or implement a supertype method.
         * Each entry is a string of the form
         * <code>"fully.qualified.ClassName#methodName(paramType1,paramType2,...)"</code>.
         * Parameters of these methods are considered part of the public API
         * and will not be removed even if they appear unused.
         */
        private final Set<String> overrideSignatures = new HashSet<>();

        private final Map<String,Set<String>> originalSignatures = new HashMap<>();
    }

    @Override
    public String getDisplayName() {
        return "Remove obsolete constructor and method parameters";
    }

    @Override
    public String getDescription() {
        return "Removes obsolete method parameters from signature, not used in body.";
    }

    @Override
    public Accumulator getInitialValue(ExecutionContext ctx) {
        return new Accumulator();
    }

    @Override
    public TreeVisitor<?, ExecutionContext> getScanner(Accumulator acc) {
        return new JavaIsoVisitor<ExecutionContext>() {
            @Override
            public J.MethodDeclaration visitMethodDeclaration(J.MethodDeclaration method, ExecutionContext ctx) {
                J.MethodDeclaration m = super.visitMethodDeclaration(method, ctx);
                JavaType.Method mt = m.getMethodType();
                String className = mt.getDeclaringType().toString();
                acc.originalSignatures.computeIfAbsent(className, k -> new HashSet<>())
                        .add(MethodMatcher.methodPattern(mt));
                if (mt != null && mt.isOverride()) {
                    while (mt != null) {
                        acc.overrideSignatures.add(MethodMatcher.methodPattern(mt));
                        mt = mt.getOverride();
                    }
                }
                return m;
            }
        };
    }

    @Override
    public TreeVisitor<?, ExecutionContext> getVisitor(Accumulator acc) {
        return Preconditions.check(
                new NoMissingTypes(),
                Repeat.repeatUntilStable(new RemoveUnusedParametersVisitor(acc)));
    }

    @RequiredArgsConstructor
    private static class RemoveUnusedParametersVisitor extends JavaIsoVisitor<ExecutionContext> {
        private final Accumulator acc;

        @Override
        public J.MethodDeclaration visitMethodDeclaration(J.MethodDeclaration method, ExecutionContext ctx) {
            J.MethodDeclaration m = super.visitMethodDeclaration(method, ctx);
            if (shouldPruneParameters(m)) {
                List<Statement> prunedParams = filterUnusedParameters(m, collectUsedParameters(m));
                return prunedParams == m.getParameters() ? m : applyPrunedSignature(m, prunedParams);
            }
            return m;
        }

        private boolean shouldPruneParameters(J.MethodDeclaration m) {
            if (m.getBody() == null ||
                    m.getMethodType() == null ||
                    m.hasModifier(J.Modifier.Type.Native) ||
                    !m.getLeadingAnnotations().isEmpty()) {
                return false;
            }
            return !acc.overrideSignatures.contains(MethodMatcher.methodPattern(m.getMethodType()));
        }

        private Set<String> collectUsedParameters(J.MethodDeclaration m) {
            Deque<Set<J.Identifier>> shadowStack = new ArrayDeque<>();
            return new JavaIsoVisitor<Set<String>>() {
                @Override
                public J.Block visitBlock(J.Block block, Set<String> u) {
                    shadowStack.push(new HashSet<>());
                    try {
                        return super.visitBlock(block, u);
                    } finally {
                        shadowStack.pop();
                    }
                }

                @Override
                public J.VariableDeclarations visitVariableDeclarations(J.VariableDeclarations decl, Set<String> u) {
                    decl.getVariables().forEach(v -> shadowStack.peek().add(v.getName()));
                    return super.visitVariableDeclarations(decl, u);
                }

                @Override
                public J.Identifier visitIdentifier(J.Identifier id, Set<String> u) {
                    if (isVisibleParameter(id, m, shadowStack)) {
                        u.add(id.getSimpleName());
                    }
                    return id;
                }
            }.reduce(m.getBody(), new HashSet<>());
        }

        private boolean isVisibleParameter(J.Identifier id, J.MethodDeclaration m, Deque<Set<J.Identifier>> shadowStack) {
            return !isShadowed(id, shadowStack) && isDeclaredAsParameter(id, m);
        }

        private boolean isShadowed(J.Identifier id, Deque<Set<J.Identifier>> shadowStack) {
            for (Set<J.Identifier> scope : shadowStack) {
                for (J.Identifier local : scope) {
                    if (SemanticallyEqual.areEqual(id, local)) {
                        return true;
                    }
                }
            }
            return false;
        }

        private boolean isDeclaredAsParameter(J.Identifier id, J.MethodDeclaration m) {
            for (Statement p : m.getParameters()) {
                if (p instanceof J.VariableDeclarations) {
                    for (J.VariableDeclarations.NamedVariable v : ((J.VariableDeclarations) p).getVariables()) {
                        if (v.getSimpleName().equals(id.getSimpleName())) {
                            return true;
                        }
                    }
                }
            }
            return false;
        }

        private List<Statement> filterUnusedParameters(J.MethodDeclaration m, Set<String> usedParams) {
            return ListUtils.map(
                    m.getParameters(),
                    p -> {
                        if (!(p instanceof J.VariableDeclarations)) {
                            return p;
                        }
                        return processVariableDeclaration((J.VariableDeclarations) p, usedParams);
                    }
            );
        }

        private Statement processVariableDeclaration(J.VariableDeclarations decl, Set<String> usedParams) {
            List<J.VariableDeclarations.NamedVariable> kept = keepUsedVariables(decl, usedParams);
            if (!kept.isEmpty()) {
                return decl.withVariables(kept);
            } else if (!decl.getLeadingAnnotations().isEmpty()) {
                return decl;
            }
            return null;
        }

        private List<J.VariableDeclarations.NamedVariable> keepUsedVariables(J.VariableDeclarations decl, Set<String> usedParams) {
            List<J.VariableDeclarations.NamedVariable> kept = new ArrayList<>(decl.getVariables().size());
            for (J.VariableDeclarations.NamedVariable v : decl.getVariables()) {
                if (usedParams.contains(v.getSimpleName())) {
                    kept.add(v);
                }
            }
            return kept;
        }

        private J.MethodDeclaration applyPrunedSignature(J.MethodDeclaration original,
                                                         List<Statement> pruned) {
            // Identify exactly which parameter positions were removed
            List<Statement> originalParams = original.getParameters();
            Set<Integer> removedIndexes = new HashSet<>();
            for (int i = 0; i < originalParams.size(); i++) {
                if (!pruned.contains(originalParams.get(i))) {
                    removedIndexes.add(i);
                }
            }

            // Build the pruned method declaration
            JavaType.Method originalType = original.getMethodType();
            List<JavaType> prunedParamTypes = collectParameterTypes(pruned);
            J.MethodDeclaration candidate = original
                    .withParameters(pruned)
                    .withMethodType(originalType.withParameterTypes(prunedParamTypes));

            // Do override/original‐signature/superclass conflict checks
            String fullSignature = MethodMatcher.methodPattern(candidate);
            int split = fullSignature.indexOf(' ');
            String qualifier    = fullSignature.substring(0, split);
            String signatureTail = fullSignature.substring(split + 1);

            if (acc.overrideSignatures.contains(fullSignature) ||
                    acc.originalSignatures
                    .contains(fullSignature) ||
                    conflictsWithSuperClassMethods(original, candidate, signatureTail) != null) {
                return original;
            }

            // Schedule a one‐off visitor to prune matching call‐site arguments
            String oldSignature = MethodMatcher.methodPattern(originalType);
            doAfterVisit(new JavaIsoVisitor<ExecutionContext>() {
                private final MethodMatcher matcher = new MethodMatcher(oldSignature);

                @Override
                public J.MethodInvocation visitMethodInvocation(J.MethodInvocation invocation,
                                                                ExecutionContext ctx) {
                    J.MethodInvocation m = super.visitMethodInvocation(invocation, ctx);
                    if (matcher.matches(m) && m.getArguments().size() != prunedParamTypes.size()) {
                        // Trim the argument list
                        List<Expression> keptArgs = new ArrayList<>();
                        for (int i = 0; i < m.getArguments().size(); i++) {
                            if (!removedIndexes.contains(i)) {
                                keptArgs.add(m.getArguments().get(i));
                            }
                        }
                        // Trim the MethodType parameter list
                        JavaType.Method mt = m.getMethodType();
                        List<JavaType> keptTypes = new ArrayList<>();
                        for (int i = 0; i < mt.getParameterTypes().size(); i++) {
                            if (!removedIndexes.contains(i)) {
                                keptTypes.add(mt.getParameterTypes().get(i));
                            }
                        }
                        JavaType.Method updatedType = mt.withParameterTypes(keptTypes);
                        // Update the name identifier to carry the same type instance
                        J.Identifier newName = m.getName().withType(updatedType);
                        return m.withArguments(keptArgs)
                                .withMethodType(updatedType)
                                .withName(newName);
                    }
                    return m;
                }
            });

            return candidate;
        }

        private J.MethodDeclaration conflictsWithSuperClassMethods(J.MethodDeclaration original,
                                                                   J.MethodDeclaration candidate, String tail) {
            JavaType.Method mt = candidate.getMethodType();
            if (mt != null && mt.getDeclaringType() instanceof JavaType.Class) {
                JavaType.Class cls       = (JavaType.Class) mt.getDeclaringType();
                JavaType.Class superCls  = (JavaType.Class) cls.getSupertype();
                if (superCls != null) {
                    String superKey = superCls.getFullyQualifiedName() + " " + tail;
                    Set<String> superSigs = acc.originalSignatures
                            .getOrDefault(superCls.getFullyQualifiedName(),
                                    Collections.emptySet());
                    if (superSigs.contains(superKey)) {
                        return original;
                    }
                }
            }
            return null;
        }

        private static List<JavaType> collectParameterTypes(List<Statement> prunedParams) {
            List<JavaType> newParamTypes = new ArrayList<>();
            for (Statement stmt : prunedParams) {
                if (stmt instanceof J.VariableDeclarations) {
                    J.VariableDeclarations decl = (J.VariableDeclarations) stmt;
                    for (J.VariableDeclarations.NamedVariable v : decl.getVariables()) {
                        JavaType t = v.getType();
                        if (t != null) {
                            newParamTypes.add(t);
                        }
                    }
                }
            }
            return newParamTypes;
        }
    }
}
