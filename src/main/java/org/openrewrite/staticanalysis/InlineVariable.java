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
import org.openrewrite.java.tree.J.Identifier;
import org.openrewrite.java.tree.J.Return;
import org.openrewrite.java.tree.J.Throw;
import org.openrewrite.java.tree.J.VariableDeclarations;
import org.openrewrite.java.tree.J.VariableDeclarations.NamedVariable;
import org.openrewrite.java.tree.JavaType;
import org.openrewrite.java.tree.Statement;

import java.time.Duration;
import java.util.List;
import java.util.Set;

import static java.time.Duration.ofMinutes;
import static java.util.Collections.singleton;
import static java.util.Objects.nonNull;
import static java.util.Objects.requireNonNull;
import static org.openrewrite.internal.ListUtils.map;

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
        return singleton("RSPEC-S1488");
    }

    @Override
    public Duration getEstimatedEffortPerOccurrence() {
        return ofMinutes(2);
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
                    if (nonNull(identReturned) && statements.get(statements.size() - 2) instanceof VariableDeclarations) {
                        VariableDeclarations varDec = (VariableDeclarations) statements.get(statements.size() - 2);
                        NamedVariable identDefinition = varDec.getVariables().get(0);
                        if (varDec.getLeadingAnnotations().isEmpty() && identDefinition.getSimpleName().equals(identReturned)) {
                            return getBlock(bl, statements, identDefinition, varDec);
                        }
                    }
                }
                return bl;
            }
        };
    }

    private J.@NotNull Block getBlock(final J.Block bl, final List<Statement> statements,
                                      final NamedVariable identDefinition, final VariableDeclarations varDec) {
        return bl.withStatements(map(statements, (i, statement) -> {
            if (i == statements.size() - 2) {
                return null;
            } else if (i == statements.size() - 1) {
                if (statement instanceof Return) {
                    Return return_ = (Return) statement;
                    return return_.withExpression(requireNonNull(identDefinition.getInitializer())
                                    .withPrefix(requireNonNull(return_.getExpression()).getPrefix()))
                            .withPrefix(varDec.getPrefix().withComments(ListUtils.concatAll(varDec.getComments(),
                                    return_.getComments())));
                } else if (statement instanceof Throw) {
                    Throw thrown = (Throw) statement;
                    return thrown.withException(requireNonNull(identDefinition.getInitializer())
                                    .withPrefix(requireNonNull(thrown.getException()).getPrefix()))
                            .withPrefix(varDec.getPrefix().withComments(ListUtils.concatAll(varDec.getComments(),
                                    thrown.getComments())));
                }
            }
            return statement;
        }));
    }

    private @Nullable String identReturned(List<Statement> stats) {
        Statement lastStatement = stats.get(stats.size() - 1);
        if (lastStatement instanceof Return) {
            return Return((Return) lastStatement);
        } else if (lastStatement instanceof Throw) {
            return Throw((Throw) lastStatement);
        }
        return null;
    }

    private @org.jetbrains.annotations.Nullable String Throw(final Throw lastStatement) {
        if (lastStatement.getException() instanceof Identifier) {
            return ((Identifier) lastStatement.getException()).getSimpleName();
        }
        return null;
    }

    private @org.jetbrains.annotations.Nullable String Return(final Return lastStatement) {
        Expression expression = lastStatement.getExpression();
        if (expression instanceof Identifier &&
                !(expression.getType() instanceof JavaType.Array)) {
            return ((Identifier) expression).getSimpleName();
        }
        return null;
    }
}
