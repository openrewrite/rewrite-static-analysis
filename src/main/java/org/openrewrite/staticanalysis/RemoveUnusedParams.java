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
                if (mt != null && mt.isOverride()) {
                    while (mt != null) {
                        String signature = MethodMatcher.methodPattern(mt);
                        acc.overrideSignatures.add(signature);
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
                List<Statement> prunedParams = filterUnusedParameters(m);
                return prunedParams == m.getParameters() ? m : m.withParameters(prunedParams);
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
            String signature = MethodMatcher.methodPattern(m.getMethodType());
            return !acc.overrideSignatures.contains(signature);
        }

        private Set<String> collectUsedParameters(J.MethodDeclaration m) {
            Deque<Set<String>> shadowStack = new ArrayDeque<>();
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
                    decl.getVariables().forEach(v -> shadowStack.peek().add(v.getSimpleName()));
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

        private boolean isVisibleParameter(J.Identifier id, J.MethodDeclaration m, Deque<Set<String>> shadowStack) {
            return !isShadowed(id.getSimpleName(), shadowStack) && isDeclaredAsParameter(id.getSimpleName(), m);
        }

        private boolean isShadowed(String name, Deque<Set<String>> shadowStack) {
            for (Set<String> scope : shadowStack) {
                if (scope.contains(name)) {
                    return true;
                }
            }
            return false;
        }

        private boolean isDeclaredAsParameter(String name, J.MethodDeclaration m) {
            for (Statement p : m.getParameters()) {
                if (p instanceof J.VariableDeclarations) {
                    for (J.VariableDeclarations.NamedVariable v : ((J.VariableDeclarations) p).getVariables()) {
                        if (v.getSimpleName().equals(name)) {
                            return true;
                        }
                    }
                }
            }
            return false;
        }

        private List<Statement> filterUnusedParameters(J.MethodDeclaration m) {
            return ListUtils.map(
                    m.getParameters(),
                    p -> {
                        if (!(p instanceof J.VariableDeclarations)) {
                            return p;
                        }
                        return processVariableDeclaration((J.VariableDeclarations) p, collectUsedParameters(m));
                    }
            );
        }

        private Statement processVariableDeclaration(J.VariableDeclarations decl, Set<String> usedParams) {
            // exactly the same code you had in the lambda:
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
    }
}
