/*
 * Copyright 2022 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * https://www.apache.org/licenses/LICENSE-2.0
 *
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

/**
 * Inlines variables that are immediately used in return or throw statements.
 *
 * <p>This transformation simplifies code by removing unnecessary variable
 * declarations, improving readability and potentially reducing memory usage.</p>
 *
 * <p>For more information, see the rule details at:
 * <a href="https://sonarsource.github.io/rspec/#/rspec/S1488/java">SonarSource RSPEC-S1488</a>.</p>
 */
public class InlineVariable extends Recipe {

    @Override
    public String getDisplayName() {
        return "Inline variable";
    }

    @Override
    public String getDescription() {
        return "Inlines variables when they are immediately used in return or throw statements.";
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
                if (statements.size() > 1) { // Only consider blocks with more than one statement
                    return requireNonNull(defaultIfNull(getBlock(statements, superBlock), superBlock));
                }
                return superBlock;
            }
        };
    }

    /**
     * Checks the last two statements in the block to see if they consist of variable declarations
     * followed by a return or throw statement. If so, it returns the modified block with inlined
     * variables.
     *
     * @param statements The list of statements in the block.
     * @param bl         The original block.
     * @return The modified block with inlined variables, or null if conditions aren't met.
     */
    private static @Nullable Block getBlock(List<Statement> statements, Block bl) {
        return statements.get(statements.size() - 2) instanceof VariableDeclarations ?
                getVariableDeclarationsBlock(statements, bl,
                        (VariableDeclarations) statements.get(statements.size() - 2)) : null;
    }

    /**
     * Examines variable declarations to determine if they can be inlined.
     *
     * @param statements The list of statements in the block.
     * @param bl         The original block.
     * @param varDec     The variable declarations to be examined.
     * @return The modified block with inlined variables, or null if conditions aren't met.
     */
    private static @Nullable Block getVariableDeclarationsBlock(List<Statement> statements,
                                                                Block bl,
                                                                VariableDeclarations varDec) {
        return getVariableDeclarationsBlock(statements, bl, varDec, varDec.getVariables().get(0));
    }

    /**
     * Determines if the variable declaration is eligible for inlining based on its usage.
     *
     * @param statements      The list of statements in the block.
     * @param bl              The original block.
     * @param varDec          The variable declarations to be examined.
     * @param identDefinition The identifier of the variable.
     * @return The modified block with inlined variables, or null if conditions aren't met.
     */
    private static @Nullable Block getVariableDeclarationsBlock(List<Statement> statements, Block bl,
                                                                VariableDeclarations varDec,
                                                                NamedVariable identDefinition) {
        return isEmpty(varDec.getLeadingAnnotations())
                && identDefinition.getSimpleName().equals(identReturned(statements.get(statements.size() - 1)))
                ? replaceStatements(bl, statements, identDefinition, varDec)
                : null;
    }

    /**
     * Replaces the variable declaration and its usage with the actual value.
     *
     * @param bl              The original block.
     * @param statements      The list of statements in the block.
     * @param identDefinition The identifier of the variable.
     * @param varDec          The variable declarations being replaced.
     * @return The modified block with the inlined variables.
     */
    private static Block replaceStatements(Block bl, List<Statement> statements,
                                           NamedVariable identDefinition, VariableDeclarations varDec) {
        return bl.withStatements(map(statements, (i, statement) -> i == statements.size() - 2 ? null :
                i == statements.size() - 1 ? statement instanceof Return
                        ? updateReturnStatement((Return) statement, identDefinition, varDec)
                        : statement instanceof Throw
                        ? updateThrowStatement((Throw) statement, identDefinition, varDec)
                        : statement : statement));
    }

    /**
     * Updates a return statement to use the actual expression instead of the variable.
     *
     * @param returnStmt      The original return statement.
     * @param identDefinition The identifier of the variable.
     * @param varDec          The variable declarations being replaced.
     * @return The updated return statement.
     */
    private static Return updateReturnStatement(Return returnStmt, NamedVariable identDefinition,
                                                VariableDeclarations varDec) {
        return returnStmt.withExpression(requireNonNull(identDefinition.getInitializer())
                        .withPrefix(requireNonNull(returnStmt.getExpression()).getPrefix()))
                .withPrefix(varDec.getPrefix().withComments(concatAll(varDec.getComments(), returnStmt.getComments())));
    }

    /**
     * Updates a throw statement to use the actual exception instead of the variable.
     *
     * @param throwStmt       The original throw statement.
     * @param identDefinition The identifier of the variable.
     * @param varDec          The variable declarations being replaced.
     * @return The updated throw statement.
     */
    private static Throw updateThrowStatement(Throw throwStmt, NamedVariable identDefinition,
                                              VariableDeclarations varDec) {
        return throwStmt.withException(requireNonNull(identDefinition.getInitializer())
                        .withPrefix(requireNonNull(throwStmt.getException()).getPrefix()))
                .withPrefix(varDec.getPrefix().withComments(concatAll(varDec.getComments(), throwStmt.getComments())));
    }

    /**
     * Extracts the identifier returned in the last statement if applicable.
     *
     * @param lastStatement The last statement in the block.
     * @return The identifier as a string, or null if not applicable.
     */
    private static @Nullable String identReturned(Statement lastStatement) {
        return (lastStatement instanceof Return)
                ? extractIdentifierFromReturn(((Return) lastStatement).getExpression())
                : (lastStatement instanceof Throw)
                ? extractIdentifierFromThrow((Throw) lastStatement)
                : null;
    }

    /**
     * Extracts the identifier from a throw statement.
     *
     * @param lastStatement The throw statement to extract from.
     * @return The identifier as a string, or null if not applicable.
     */
    private static @Nullable String extractIdentifierFromThrow(Throw lastStatement) {
        return (lastStatement.getException() instanceof Identifier)
                ? ((Identifier) lastStatement.getException()).getSimpleName()
                : null;
    }

    /**
     * Extracts the identifier from a return expression.
     *
     * @param expression The expression to extract from.
     * @return The identifier as a string, or null if not applicable.
     */
    private static @Nullable String extractIdentifierFromReturn(@Nullable Expression expression) {
        return (expression instanceof Identifier && !(expression.getType() instanceof JavaType.Array))
                ? ((Identifier) expression).getSimpleName()
                : null;
    }
}
