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

import lombok.EqualsAndHashCode;
import lombok.Value;
import org.openrewrite.ExecutionContext;
import org.openrewrite.Recipe;
import org.openrewrite.TreeVisitor;
import org.openrewrite.java.JavaIsoVisitor;
import org.openrewrite.java.search.SemanticallyEqual;
import org.openrewrite.java.tree.Expression;
import org.openrewrite.java.tree.J;
import org.openrewrite.java.tree.Statement;

import java.util.ArrayList;
import java.util.List;

@Value
@EqualsAndHashCode(callSuper = false)
public class InlineReturnValue extends Recipe {
    @Override
    public String getDisplayName() {
        return "Inline return values";
    }

    @Override
    public String getDescription() {
        return "Inline variables that are immediately returned. " +
                "When the second to last statement in a block assigns to a local variable, " +
                "and the last statement returns that local variable, the assignment can be inlined into the return statement.";
    }

    @Override
    public TreeVisitor<?, ExecutionContext> getVisitor() {
        return new JavaIsoVisitor<ExecutionContext>() {
            @Override
            public J.Block visitBlock(J.Block block, ExecutionContext ctx) {
                J.Block b = super.visitBlock(block, ctx);

                List<Statement> statements = b.getStatements();
                if (statements.size() < 2) {
                    return b;
                }

                int lastIndex = statements.size() - 1;
                int secondLastIndex = lastIndex - 1;

                Statement lastStatement = statements.get(lastIndex);
                Statement secondLastStatement = statements.get(secondLastIndex);

                // Check if last statement is a return statement with an identifier
                if (!(lastStatement instanceof J.Return)) {
                    return b;
                }

                J.Return returnStatement = (J.Return) lastStatement;
                if (returnStatement.getExpression() == null || !(returnStatement.getExpression() instanceof J.Identifier)) {
                    return b;
                }

                J.Identifier returnedIdentifier = (J.Identifier) returnStatement.getExpression();
                if (secondLastStatement instanceof J.Assignment) {
                    return handleAssignment((J.Assignment) secondLastStatement, returnedIdentifier, returnStatement, statements, secondLastIndex, b);
                }
                if (secondLastStatement instanceof J.VariableDeclarations) {
                    return handleVariableDeclarations((J.VariableDeclarations) secondLastStatement, returnedIdentifier, returnStatement, statements, secondLastIndex, b);
                }
                return b;
            }

            private J.Block handleVariableDeclarations(J.VariableDeclarations secondLastStatement, J.Identifier returnedIdentifier, J.Return returnStatement, List<Statement> statements, int secondLastIndex, J.Block b) {
                if (secondLastStatement.getVariables().size() != 1) {
                    return b;
                }

                J.VariableDeclarations.NamedVariable variable = secondLastStatement.getVariables().get(0);
                Expression initializer = variable.getInitializer();
                if (initializer == null) {
                    return b;
                }

                // Check if the returned identifier matches the declared variable
                if (!SemanticallyEqual.areEqual(variable.getName(), returnedIdentifier)) {
                    return b;
                }

                return blockWithInlinedReturn(returnStatement, initializer, statements, secondLastIndex, b);
            }

            private J.Block handleAssignment(J.Assignment secondLastStatement, J.Identifier returnedIdentifier, J.Return returnStatement, List<Statement> statements, int secondLastIndex, J.Block b) {
                // Check if the left-hand side matches the returned identifier
                if (!SemanticallyEqual.areEqual(secondLastStatement.getVariable(), returnedIdentifier)) {
                    return b;
                }

                return blockWithInlinedReturn(returnStatement, secondLastStatement.getAssignment(), statements, secondLastIndex, b);
            }

            private J.Block blockWithInlinedReturn(J.Return returnStatement, Expression initializer, List<Statement> statements, int secondLastIndex, J.Block b) {
                // Create new return statement with the initializer expression
                J.Return newReturn = returnStatement.withExpression(initializer);

                // Build the new statement list
                List<Statement> newStatements = new ArrayList<>(statements.subList(0, secondLastIndex));
                newStatements.add(newReturn);

                return b.withStatements(newStatements);
            }
        };
    }
}
