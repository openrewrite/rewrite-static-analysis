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
import org.openrewrite.Repeat;
import org.openrewrite.TreeVisitor;
import org.openrewrite.internal.ListUtils;
import org.openrewrite.java.JavaVisitor;
import org.openrewrite.java.tree.Comment;
import org.openrewrite.java.tree.J;
import org.openrewrite.java.tree.Statement;

import java.time.Duration;
import java.util.Arrays;
import java.util.List;

public class UnwrapElseAfterReturn extends Recipe {

    @Override
    public String getDisplayName() {
        return "Unwrap else block after return or throw statement";
    }

    @Override
    public String getDescription() {
        return "Unwraps the else block when the if block ends with a return or throw statement, " +
                "reducing nesting and improving code readability.";
    }

    @Override
    public Duration getEstimatedEffortPerOccurrence() {
        return Duration.ofMinutes(1);
    }

    @Override
    public TreeVisitor<?, ExecutionContext> getVisitor() {
        JavaVisitor<ExecutionContext> javaVisitor = new JavaVisitor<ExecutionContext>() {
            @Override
            public J.Block visitBlock(J.Block block, ExecutionContext ctx) {
                J.Block b = visitAndCast(block, ctx, super::visitBlock);
                J.Block after = b.withStatements(ListUtils.flatMap(b.getStatements(), statement -> {
                    if (statement instanceof J.If) {
                        J.If ifStatement = (J.If) statement;
                        if (ifStatement.getElsePart() != null && endsWithReturnOrThrow(ifStatement.getThenPart())) {
                            J.If newIf = ifStatement.withElsePart(null);
                            Statement elsePart = ifStatement.getElsePart().getBody();
                            if (elsePart instanceof J.Block) {
                                J.Block elseBlock = (J.Block) elsePart;
                                return ListUtils.concat(newIf, ListUtils.mapFirst(elseBlock.getStatements(), elseStmt -> {
                                    // Combine comments from the else block itself and the first statement
                                    List<Comment> elseComments = elseBlock.getPrefix().getComments();
                                    List<Comment> stmtComments = elseStmt.getPrefix().getComments();
                                    if (!elseComments.isEmpty() || !stmtComments.isEmpty()) {
                                        return elseStmt.withComments(ListUtils.concatAll(elseComments, stmtComments));
                                    }
                                    String whitespace = ifStatement.getElsePart().getPrefix().getWhitespace();
                                    return elseStmt.withPrefix(elseStmt.getPrefix().withWhitespace(whitespace));
                                }));
                            }
                            return Arrays.asList(newIf, elsePart.<Statement>withPrefix(ifStatement.getElsePart().getPrefix()));
                        }
                    }
                    return statement;
                }));
                return maybeAutoFormat(b, after, ctx);
            }

            private boolean endsWithReturnOrThrow(Statement statement) {
                if (statement instanceof J.Return || statement instanceof J.Throw) {
                    return true;
                }
                if (statement instanceof J.Block) {
                    J.Block block = (J.Block) statement;
                    if (!block.getStatements().isEmpty()) {
                        Statement lastStatement = block.getStatements().get(block.getStatements().size() - 1);
                        return lastStatement instanceof J.Return || lastStatement instanceof J.Throw;
                    }
                }
                return false;
            }
        };
        return Repeat.repeatUntilStable(javaVisitor);
    }
}
