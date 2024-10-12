/*
 * Copyright 2022 the original author or authors.
 * <p>
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * <p>
 * https://www.apache.org/licenses/LICENSE-2.0
 * <p>
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.openrewrite.staticanalysis;

import org.jetbrains.annotations.NotNull;
import org.jspecify.annotations.Nullable;
import org.openrewrite.ExecutionContext;
import org.openrewrite.Recipe;
import org.openrewrite.TreeVisitor;
import org.openrewrite.internal.ListUtils;
import org.openrewrite.java.JavaIsoVisitor;
import org.openrewrite.java.tree.Expression;
import org.openrewrite.java.tree.J;
import org.openrewrite.java.tree.JavaType;
import org.openrewrite.java.tree.Statement;

import java.time.Duration;
import java.util.Collections;
import java.util.List;
import java.util.Set;

import static java.util.Objects.nonNull;
import static java.util.Objects.requireNonNull;

public class InlineVariable extends Recipe {

    @Override
    public String getDisplayName() {
        return "Inline variable";
    }

    @Override
    public String getDescription() {
        return "Inline variables when they are immediately used to return or throw.";
    }

    @Override
    public Set<String> getTags() {
        return Collections.singleton("RSPEC-S1488");
    }

    @Override
    public Duration getEstimatedEffortPerOccurrence() {
        return Duration.ofMinutes(2);
    }

    @Override
    public TreeVisitor<?, ExecutionContext> getVisitor() {
        return new JavaIsoVisitor<ExecutionContext>() {
            @Override
            public J.Block visitBlock(J.Block block, ExecutionContext ctx) {
                J.Block bl = super.visitBlock(block, ctx);
                List<Statement> statements = bl.getStatements();
                if (statements.size() > 1) {
                    String identReturned = identReturned(statements);
                    if (nonNull(identReturned)) {
                        if (statements.get(statements.size() - 2) instanceof J.VariableDeclarations) {
                            J.VariableDeclarations varDec = (J.VariableDeclarations) statements.get(statements.size() - 2);
                            J.VariableDeclarations.NamedVariable identDefinition = varDec.getVariables().get(0);
                            if (varDec.getLeadingAnnotations().isEmpty() && identDefinition.getSimpleName().equals(identReturned)) {
                                return getBlock(bl, statements, identDefinition, varDec);
                            }
                        }
                    }
                }
                return bl;
            }

            private J.@NotNull Block getBlock(final J.Block bl, final List<Statement> statements, final J.VariableDeclarations.NamedVariable identDefinition, final J.VariableDeclarations varDec) {
                return bl.withStatements(ListUtils.map(statements, (i, statement) -> {
                    if (i == statements.size() - 2) {
                        return null;
                    } else if (i == statements.size() - 1) {
                        if (statement instanceof J.Return) {
                            J.Return return_ = (J.Return) statement;
                            return return_.withExpression(requireNonNull(identDefinition.getInitializer())
                                            .withPrefix(requireNonNull(return_.getExpression()).getPrefix()))
                                    .withPrefix(varDec.getPrefix().withComments(ListUtils.concatAll(varDec.getComments(), return_.getComments())));
                        } else if (statement instanceof J.Throw) {
                            J.Throw thrown = (J.Throw) statement;
                            return thrown.withException(requireNonNull(identDefinition.getInitializer())
                                            .withPrefix(requireNonNull(thrown.getException()).getPrefix()))
                                    .withPrefix(varDec.getPrefix().withComments(ListUtils.concatAll(varDec.getComments(), thrown.getComments())));
                        }
                    }
                    return statement;
                }));
            }

            private @Nullable String identReturned(List<Statement> stats) {
                Statement lastStatement = stats.get(stats.size() - 1);
                if (lastStatement instanceof J.Return) {
                    J.Return return_ = (J.Return) lastStatement;
                    Expression expression = return_.getExpression();
                    if (expression instanceof J.Identifier &&
                        !(expression.getType() instanceof JavaType.Array)) {
                        return ((J.Identifier) expression).getSimpleName();
                    }
                } else if (lastStatement instanceof J.Throw) {
                    J.Throw thr = (J.Throw) lastStatement;
                    if (thr.getException() instanceof J.Identifier) {
                        return ((J.Identifier) thr.getException()).getSimpleName();
                    }
                }
                return null;
            }
        };
    }
}
