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

import static java.util.Objects.requireNonNull;

public class InlineVariable extends Recipe {

    @Override
    public String getDisplayName() {
        return "Inline variable";
    }

    @Override
    public String getDescription() {
        return "Inline variables when they are immediately used to return or throw. " +
                "Supports both variable declarations and assignments to local variables.";
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
                    if (identReturned != null) {
                        Statement secondLastStatement = statements.get(statements.size() - 2);
                        if (secondLastStatement instanceof J.VariableDeclarations) {
                            J.VariableDeclarations varDec = (J.VariableDeclarations) secondLastStatement;
                            // Only inline if there's exactly one variable declared
                            if (varDec.getVariables().size() == 1) {
                                J.VariableDeclarations.NamedVariable identDefinition = varDec.getVariables().get(0);
                                if (varDec.getLeadingAnnotations().isEmpty() && identDefinition.getSimpleName().equals(identReturned)) {
                                    bl = inlineExpression(statements, bl, identDefinition.getInitializer(), varDec.getPrefix(), varDec.getComments());
                                }
                            }
                        } else if (secondLastStatement instanceof J.Assignment) {
                            J.Assignment assignment = (J.Assignment) secondLastStatement;
                            if (assignment.getVariable() instanceof J.Identifier) {
                                J.Identifier assignedVar = (J.Identifier) assignment.getVariable();
                                // Only inline local variable assignments, not fields
                                if (assignedVar.getFieldType() != null &&
                                        assignedVar.getFieldType().getOwner() instanceof JavaType.Method &&
                                        assignedVar.getSimpleName().equals(identReturned)) {
                                    bl = inlineExpression(statements, bl, assignment.getAssignment(), assignment.getPrefix(), assignment.getComments());
                                }
                            }
                        }
                    }
                }
                return bl;
            }

            private J.Block inlineExpression(List<Statement> statements, J.Block bl, @Nullable Expression expression,
                                             org.openrewrite.java.tree.Space prefix, List<org.openrewrite.java.tree.Comment> comments) {
                if (expression == null) {
                    return bl;
                }

                return bl.withStatements(ListUtils.map(statements, (i, statement) -> {
                    if (i == statements.size() - 2) {
                        return null;
                    } else if (i == statements.size() - 1) {
                        if (statement instanceof J.Return) {
                            J.Return return_ = (J.Return) statement;
                            return return_.withExpression(expression
                                            .withPrefix(requireNonNull(return_.getExpression()).getPrefix()))
                                    .withPrefix(prefix.withComments(ListUtils.concatAll(comments, return_.getComments())));
                        } else if (statement instanceof J.Throw) {
                            J.Throw thrown = (J.Throw) statement;
                            return thrown.withException(expression
                                            .withPrefix(requireNonNull(thrown.getException()).getPrefix()))
                                    .withPrefix(prefix.withComments(ListUtils.concatAll(comments, thrown.getComments())));
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
