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

import org.openrewrite.ExecutionContext;
import org.openrewrite.Preconditions;
import org.openrewrite.Repeat;
import org.openrewrite.ScanningRecipe;
import org.openrewrite.TreeVisitor;
import org.openrewrite.java.JavaIsoVisitor;
import org.openrewrite.java.NoMissingTypes;
import org.openrewrite.java.tree.J;
import org.openrewrite.java.tree.Statement;

import java.time.Duration;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

public class RemoveUnusedParams extends ScanningRecipe<RemoveUnusedParams.Accumulator> {
    static class Accumulator {
        /**
         * Signatures of all methods that override or implement a supertype method.
         * Each entry is a string of the form
         * <code>"fully.qualified.ClassName#methodName(paramType1,paramType2,...)"</code>.
         * Parameters of these methods are considered part of the public API
         * and will not be removed even if they appear unused.
         */
        private final Set<String> overrideSignatures = new HashSet<>();

        void add(final String signature) {
            overrideSignatures.add(signature);
        }

        boolean contains(final String signature) {
            return overrideSignatures.contains(signature);
        }
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
    public Duration getEstimatedEffortPerOccurrence() {
        return Duration.ofMinutes(5);
    }

    @Override
    public Accumulator getInitialValue(final ExecutionContext ctx) {
        return new Accumulator();
    }

    @Override
    public TreeVisitor<?, ExecutionContext> getScanner(final Accumulator acc) {
        return new JavaIsoVisitor<ExecutionContext>() {
            @Override
            public J.MethodDeclaration visitMethodDeclaration(final J.MethodDeclaration method, final ExecutionContext ctx) {
                return collectOverrideSignature(super.visitMethodDeclaration(method, ctx), acc);
            }
        };
    }

    private J.MethodDeclaration collectOverrideSignature(final J.MethodDeclaration m, final Accumulator acc) {
        if (m.getMethodType() != null && m.getMethodType().isOverride()) {
            acc.add(buildSignature(m));
        }
        return m;
    }

    @Override
    public TreeVisitor<?, ExecutionContext> getVisitor(final Accumulator acc) {
        return Preconditions.check(new NoMissingTypes(),
                Repeat.repeatUntilStable(new JavaIsoVisitor<ExecutionContext>() {

                    @Override
                    public J.MethodDeclaration visitMethodDeclaration(final J.MethodDeclaration method, final ExecutionContext ctx) {
                        J.MethodDeclaration m = super.visitMethodDeclaration(method, ctx);
                        if (shouldPruneParameters(m, acc)) {
                            List<Statement> prunedParams = filterUnusedParameters(m, collectUsedParameters(m));
                            return prunedParams.equals(m.getParameters()) ? m : m.withParameters(prunedParams);
                        }
                        return m;
                    }
                })
        );
    }

    private boolean shouldPruneParameters(final J.MethodDeclaration m, final Accumulator acc) {
        return m.getBody() != null
                && m.getMethodType() != null
                && !m.hasModifier(J.Modifier.Type.Native)
                && m.getLeadingAnnotations().isEmpty()
                && !acc.contains(buildSignature(m));
    }

    private String buildSignature(final J.MethodDeclaration m) {
        return m.getSimpleName() + "#"
                + m.getMethodType().getParameterTypes().stream()
                .map(Object::toString)
                .collect(Collectors.joining(","));
    }

    private Set<String> collectUsedParameters(final J.MethodDeclaration m) {
        final Set<String> used = new HashSet<>();
        final Deque<Set<String>> shadowStack = new ArrayDeque<>();

        new JavaIsoVisitor<Set<String>>() {
            @Override
            public J.Block visitBlock(final J.Block block, final Set<String> u) {
                shadowStack.push(new HashSet<>());
                try {
                    return super.visitBlock(block, u);
                } finally {
                    shadowStack.pop();
                }
            }

            @Override
            public J.VariableDeclarations visitVariableDeclarations(final J.VariableDeclarations decl, final Set<String> u) {
                decl.getVariables().forEach(v -> shadowStack.peek().add(v.getSimpleName()));
                return super.visitVariableDeclarations(decl, u);
            }

            @Override
            public J.Identifier visitIdentifier(final J.Identifier id, final Set<String> u) {
                if (isVisibleParameter(id, m, shadowStack)) {
                    u.add(id.getSimpleName());
                }
                return id;
            }
        }.visit(m.getBody(), used);

        return used;
    }

    private boolean isVisibleParameter(final J.Identifier id, final J.MethodDeclaration m, final Deque<Set<String>> shadowStack) {
        return !isShadowed(id.getSimpleName(), shadowStack) && isDeclaredAsParameter(id.getSimpleName(), m);
    }

    private boolean isShadowed(final String name, final Deque<Set<String>> shadowStack) {
        for (Set<String> scope : shadowStack) {
            if (scope.contains(name)) {
                return true;
            }
        }
        return false;
    }

    private boolean isDeclaredAsParameter(final String name, final J.MethodDeclaration m) {
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

    private List<Statement> filterUnusedParameters(final J.MethodDeclaration method, final Set<String> usedParams) {
        final List<Statement> result = new ArrayList<>(method.getParameters().size());
        for (Statement param : method.getParameters()) {
            if (param instanceof J.VariableDeclarations) {
                processVariableDeclaration((J.VariableDeclarations) param, usedParams, result);
            } else {
                result.add(param);
            }
        }
        return result;
    }

    private void processVariableDeclaration(final J.VariableDeclarations decl, final Set<String> usedParams, final List<Statement> result) {
        final List<J.VariableDeclarations.NamedVariable> kept = keepUsedVariables(decl, usedParams);

        if (!kept.isEmpty()) {
            result.add(decl.withVariables(kept));
        } else if (!decl.getLeadingAnnotations().isEmpty()) {
            result.add(decl);
        }
    }

    private List<J.VariableDeclarations.NamedVariable> keepUsedVariables(final J.VariableDeclarations decl, final Set<String> usedParams) {
        List<J.VariableDeclarations.NamedVariable> kept = new ArrayList<>(decl.getVariables().size());
        for (J.VariableDeclarations.NamedVariable v : decl.getVariables()) {
            if (usedParams.contains(v.getSimpleName())) {
                kept.add(v);
            }
        }
        return kept;
    }

}
