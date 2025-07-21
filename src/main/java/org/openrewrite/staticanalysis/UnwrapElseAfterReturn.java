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
import org.openrewrite.Recipe;
import org.openrewrite.TreeVisitor;
import org.openrewrite.internal.ListUtils;
import org.openrewrite.java.JavaVisitor;
import org.openrewrite.java.tree.Comment;
import org.openrewrite.java.tree.J;
import org.openrewrite.java.tree.Space;
import org.openrewrite.java.tree.Statement;

import java.time.Duration;
import java.util.List;

public class UnwrapElseAfterReturn extends Recipe {

    @Override
    public String getDisplayName() {
        return "Unwrap else block after return";
    }

    @Override
    public String getDescription() {
        return "Unwraps the else block when the if block ends with a return statement, reducing nesting and improving code readability.";
    }

    @Override
    public Duration getEstimatedEffortPerOccurrence() {
        return Duration.ofMinutes(1);
    }

    @Override
    public TreeVisitor<?, ExecutionContext> getVisitor() {
        return new JavaVisitor<ExecutionContext>() {
            @Override
            public J.Block visitBlock(J.Block block, ExecutionContext ctx) {
                J.Block b = visitAndCast(block, ctx, super::visitBlock);

                List<Statement> statements = b.getStatements();
                List<Statement> newStatements = statements;

                for (int i = 0; i < statements.size(); i++) {
                    Statement statement = statements.get(i);
                    if (statement instanceof J.If) {
                        J.If ifStatement = (J.If) statement;

                        if (ifStatement.getElsePart() != null && endsWithReturn(ifStatement.getThenPart())) {
                            J.If newIf = ifStatement.withElsePart(null);
                            newStatements = ListUtils.map(newStatements, stmt -> stmt == ifStatement ? newIf : stmt);

                            Statement elsePart = ifStatement.getElsePart().getBody();

                            if (elsePart instanceof J.Block) {
                                J.Block elseBlock = (J.Block) elsePart;
                                for (int j = 0; j < elseBlock.getStatements().size(); j++) {
                                    Statement elseStmt = elseBlock.getStatements().get(j);
                                    if (j == 0) {
                                        // Combine comments from the else block itself and the first statement
                                        List<Comment> elseComments = elseBlock.getPrefix().getComments();
                                        List<Comment> stmtComments = elseStmt.getPrefix().getComments();
                                        String whitespace = ifStatement.getElsePart().getPrefix().getWhitespace();

                                        if (!elseComments.isEmpty() || !stmtComments.isEmpty()) {
                                            elseStmt = elseStmt.withPrefix(
                                                Space.build(whitespace, ListUtils.concatAll(elseComments, stmtComments))
                                            );
                                        } else {
                                            elseStmt = elseStmt.withPrefix(elseStmt.getPrefix().withWhitespace(whitespace));
                                        }
                                    }
                                    newStatements = ListUtils.insert(newStatements, elseStmt, i + 1 + j);
                                }
                                // Update index to account for inserted statements
                                i += elseBlock.getStatements().size();
                            } else {
                                Statement elseStmt = elsePart.withPrefix(ifStatement.getElsePart().getPrefix());
                                newStatements = ListUtils.insert(newStatements, elseStmt, i + 1);
                                // Update index to account for inserted statement
                                i += 1;
                            }
                        }
                    }
                }

                if (newStatements != statements) {
                    return maybeAutoFormat(b, b.withStatements(newStatements), ctx);
                }

                return b;
            }

            private boolean endsWithReturn(Statement statement) {
                if (statement instanceof J.Return) {
                    return true;
                }
                if (statement instanceof J.Block) {
                    J.Block block = (J.Block) statement;
                    if (!block.getStatements().isEmpty()) {
                        Statement lastStatement = block.getStatements().get(block.getStatements().size() - 1);
                        return lastStatement instanceof J.Return;
                    }
                }
                return false;
            }
        };
    }
}
