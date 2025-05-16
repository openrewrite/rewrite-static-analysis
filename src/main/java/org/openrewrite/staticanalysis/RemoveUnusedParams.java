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
        Set<String> overrideSignatures = new HashSet<>();
    }

    @Override
    public String getDisplayName() {
        return "Remove unused method parameters";
    }

    @Override
    public String getDescription() {
        return "Removes parameters from methods that are declared but never used in the method body.";
    }

    @Override
    public Duration getEstimatedEffortPerOccurrence() {
        return Duration.ofMinutes(5);
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
                for (J.Annotation ann : m.getLeadingAnnotations()) {
                    if ("Override".equals(ann.getSimpleName())) {
                        String key = m.getSimpleName() + "#" + m.getParameters().size();
                        acc.overrideSignatures.add(key);
                        break;
                    }
                }
                return m;
            }
        };
    }

    @Override
    public TreeVisitor<?, ExecutionContext> getVisitor(Accumulator acc) {
        return Preconditions.check(new NoMissingTypes(),
                Repeat.repeatUntilStable(new JavaIsoVisitor<ExecutionContext>() {

                    private boolean skipAnyExplicitOverride(J.MethodDeclaration m) {
                        return m.getMethodType() != null && m.getMethodType().isOverride();
                    }

                    private boolean skipIfOverriddenElsewhere(String signature) {
                        return acc.overrideSignatures.contains(signature);
                    }

                    @Override
                    public J.MethodDeclaration visitMethodDeclaration(J.MethodDeclaration method, ExecutionContext ctx) {
                        J.MethodDeclaration m = super.visitMethodDeclaration(method, ctx);

                        if (skipAnyExplicitOverride(m)) {
                            return m;
                        }

                        String signature = m.getSimpleName() + "#" + m.getParameters().size();
                        if (skipIfOverriddenElsewhere(signature)) {
                            return m;
                        }

                        if (m.getBody() == null ||
                                m.hasModifier(J.Modifier.Type.Native) ||
                                !m.getLeadingAnnotations().isEmpty()) {
                            return m;
                        }

                        Set<String> params = m.getParameters().stream()
                                .filter(p -> p instanceof J.VariableDeclarations)
                                .flatMap(p -> ((J.VariableDeclarations) p).getVariables().stream())
                                .map(J.VariableDeclarations.NamedVariable::getSimpleName)
                                .collect(Collectors.toSet());

                        Set<String> used = new HashSet<>();
                        new JavaIsoVisitor<Set<String>>() {
                            Deque<Set<String>> shadowed = new ArrayDeque<>();

                            @Override
                            public J.Block visitBlock(J.Block block, Set<String> u) {
                                shadowed.push(new HashSet<>());
                                J.Block b = super.visitBlock(block, u);
                                shadowed.pop();
                                return b;
                            }

                            @Override
                            public J.VariableDeclarations visitVariableDeclarations(J.VariableDeclarations vars, Set<String> u) {
                                vars.getVariables().forEach(v -> shadowed.peek().add(v.getSimpleName()));
                                return super.visitVariableDeclarations(vars, u);
                            }

                            @Override
                            public J.Identifier visitIdentifier(J.Identifier ident, Set<String> u) {
                                for (Set<String> scope : shadowed) {
                                    if (scope.contains(ident.getSimpleName())) {
                                        return ident;
                                    }
                                }
                                if (params.contains(ident.getSimpleName())) {
                                    u.add(ident.getSimpleName());
                                }
                                return ident;
                            }
                        }.visit(m.getBody(), used);

                        List<Statement> newParams = new ArrayList<>();
                        for (Statement p : m.getParameters()) {
                            if (!(p instanceof J.VariableDeclarations)) {
                                newParams.add(p);
                                continue;
                            }
                            J.VariableDeclarations vd = (J.VariableDeclarations) p;
                            if (!vd.getLeadingAnnotations().isEmpty()) {
                                newParams.add(vd);
                                continue;
                            }
                            List<J.VariableDeclarations.NamedVariable> keep = vd.getVariables().stream()
                                    .filter(v -> used.contains(v.getSimpleName()))
                                    .collect(Collectors.toList());
                            if (!keep.isEmpty()) {
                                newParams.add(vd.withVariables(keep));
                            }
                        }

                        return newParams.equals(m.getParameters()) ?
                                m :
                                m.withParameters(newParams);
                    }
                })
        );
    }
}
