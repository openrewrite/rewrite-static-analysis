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

import org.openrewrite.ExecutionContext;
import org.openrewrite.Recipe;
import org.openrewrite.TreeVisitor;
import org.openrewrite.java.JavaIsoVisitor;
import org.openrewrite.java.tree.Expression;
import org.openrewrite.java.tree.J.*;
import org.openrewrite.java.tree.J.VariableDeclarations.NamedVariable;
import org.openrewrite.java.tree.JavaType;
import org.openrewrite.java.tree.Statement;

import javax.annotation.Nullable;
import java.time.Duration;
import java.util.List;
import java.util.Set;

import static java.time.Duration.ofMinutes;
import static java.util.Collections.singleton;
import static java.util.Objects.requireNonNull;
import static org.apache.commons.lang3.ObjectUtils.defaultIfNull;
import static org.apache.commons.lang3.ObjectUtils.isEmpty;
import static org.openrewrite.internal.ListUtils.concatAll;
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
            public Block visitBlock(Block block, ExecutionContext ctx) {
                Block superBlock = super.visitBlock(block, ctx);
                List<Statement> statements = superBlock.getStatements();
                if (statements.size() > 1) {
                    return requireNonNull(defaultIfNull(getBlock(statements, superBlock), superBlock));
                }
                return superBlock;
            }
        };
    }

    private static @Nullable Block getBlock(List<Statement> statements, Block bl) {
        return statements.get(statements.size() - 2) instanceof VariableDeclarations ?
                getVariableDeclarationsBlock(statements, bl,
                        (VariableDeclarations) statements.get(statements.size() - 2)) : null;
    }

    private static @Nullable Block getVariableDeclarationsBlock(List<Statement> statements,
                                                                Block bl,
                                                                VariableDeclarations varDec) {
        return getVariableDeclarationsBlock(statements, bl, varDec, varDec.getVariables().get(0));
    }

    private static @Nullable Block getVariableDeclarationsBlock(List<Statement> statements, Block bl,
                                                                VariableDeclarations varDec,
                                                                NamedVariable identDefinition) {
        return isEmpty(varDec.getLeadingAnnotations())
                && identDefinition.getSimpleName().equals(identReturned(statements.get(statements.size() - 1)))
                ? replaceStatements(bl, statements, identDefinition, varDec)
                : null;
    }

    private static Block replaceStatements(Block bl, List<Statement> statements,
                                           NamedVariable identDefinition, VariableDeclarations varDec) {
        return bl.withStatements(map(statements, (i, statement) -> i == statements.size() - 2 ? null :
                i == statements.size() - 1 ? statement instanceof Return
                        ? updateReturnStatement((Return) statement, identDefinition, varDec)
                        : statement instanceof Throw
                        ? updateThrowStatement((Throw) statement, identDefinition, varDec)
                        : statement : statement));
    }

    private static Return updateReturnStatement(Return returnStmt, NamedVariable identDefinition,
                                                VariableDeclarations varDec) {
        return returnStmt.withExpression(requireNonNull(identDefinition.getInitializer())
                        .withPrefix(requireNonNull(returnStmt.getExpression()).getPrefix()))
                .withPrefix(varDec.getPrefix().withComments(concatAll(varDec.getComments(), returnStmt.getComments())));
    }

    private static Throw updateThrowStatement(Throw throwStmt, NamedVariable identDefinition,
                                              VariableDeclarations varDec) {
        return throwStmt.withException(requireNonNull(identDefinition.getInitializer())
                        .withPrefix(requireNonNull(throwStmt.getException()).getPrefix()))
                .withPrefix(varDec.getPrefix().withComments(concatAll(varDec.getComments(), throwStmt.getComments())));
    }

    private static @Nullable String identReturned(Statement lastStatement) {
        return (lastStatement instanceof Return)
                ? extractIdentifierFromReturn(((Return) lastStatement).getExpression())
                : (lastStatement instanceof Throw)
                ? extractIdentifierFromThrow((Throw) lastStatement)
                : null;
    }

    private static @Nullable String extractIdentifierFromThrow(Throw lastStatement) {
        return (lastStatement.getException() instanceof Identifier)
                ? ((Identifier) lastStatement.getException()).getSimpleName()
                : null;
    }

    private static @Nullable String extractIdentifierFromReturn(@Nullable Expression expression) {
        return (expression instanceof Identifier && !(expression.getType() instanceof JavaType.Array))
                ? ((Identifier) expression).getSimpleName()
                : null;
    }
}
