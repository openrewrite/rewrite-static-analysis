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

import lombok.Getter;
import org.jspecify.annotations.Nullable;
import org.openrewrite.ExecutionContext;
import org.openrewrite.Recipe;
import org.openrewrite.Repeat;
import org.openrewrite.TreeVisitor;
import org.openrewrite.internal.ListUtils;
import org.openrewrite.java.JavaVisitor;
import org.openrewrite.java.tree.Comment;
import org.openrewrite.java.tree.J;
import org.openrewrite.java.tree.Space;
import org.openrewrite.java.tree.Statement;

import java.time.Duration;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

public class UnwrapElseAfterReturn extends Recipe {

    @Getter
    final String displayName = "Unwrap else block after return or throw statement";

    @Getter
    final String description = "Unwraps the else block when the if block ends with a return or throw statement, " +
            "reducing nesting and improving code readability.";

    @Getter
    final Duration estimatedEffortPerOccurrence = Duration.ofMinutes(1);

    @Override
    public TreeVisitor<?, ExecutionContext> getVisitor() {
        JavaVisitor<ExecutionContext> javaVisitor = new JavaVisitor<ExecutionContext>() {
            @Override
            public J.Block visitBlock(J.Block block, ExecutionContext ctx) {
                J.Block b = visitAndCast(block, ctx, super::visitBlock);
                AtomicReference<@Nullable Space> endWhitespace = new AtomicReference<>(null);
                J.Block alteredBlock = b.withStatements(ListUtils.flatMap(b.getStatements(), statement -> {
                    if (statement instanceof J.If) {
                        J.If ifStatement = (J.If) statement;
                        if (ifStatement.getElsePart() != null && endsWithReturnOrThrow(ifStatement.getThenPart())) {
                            Statement elsePart = ifStatement.getElsePart().getBody();
                            if (elsePart instanceof J.If) {
                                // Else-if chain: find and unwrap the innermost else
                                J.If innermost = findInnermostIfWithElse((J.If) elsePart);
                                if (innermost != null &&
                                        innermost.getElsePart() != null &&
                                        endsWithReturnOrThrow(innermost.getThenPart()) &&
                                        !(innermost.getElsePart().getBody() instanceof J.If)) {
                                    // Unwrap the innermost else
                                    J.If modifiedChain = removeInnermostElse(ifStatement);
                                    Statement innermostElseBody = innermost.getElsePart().getBody();
                                    return flatten(innermost, innermostElseBody, endWhitespace, modifiedChain);
                                }
                            } else {
                                // Plain else block: unwrap directly
                                J.If newIf = ifStatement.withElsePart(null);
                                return flatten(ifStatement, elsePart, endWhitespace, newIf);
                            }
                        }
                    }
                    return statement;
                }));

                Space end = endWhitespace.get();
                if (end != null) {
                    List<Comment> mergedComments = ListUtils.concatAll(end.getComments(), b.getEnd().getComments());
                    alteredBlock = alteredBlock.withEnd(b.getEnd().withComments(mergedComments).withWhitespace(end.getWhitespace()));
                }

                return maybeAutoFormat(b, alteredBlock, ctx);
            }

            private List<Statement> flatten(J.If tailIf, Statement tailElse, AtomicReference<@Nullable Space> endWhitespace, J.If ifWithoutElse) {
                if (tailElse instanceof J.Block) {
                    J.Block elseBlock = (J.Block) tailElse;
                    endWhitespace.set(elseBlock.getEnd());
                    return ListUtils.concat(ifWithoutElse, ListUtils.mapFirst(elseBlock.getStatements(), elseStmt -> {
                        List<Comment> elseComments = elseBlock.getPrefix().getComments();
                        List<Comment> stmtComments = elseStmt.getPrefix().getComments();
                        if (!elseComments.isEmpty() || !stmtComments.isEmpty()) {
                            return elseStmt.withComments(ListUtils.concatAll(elseComments, stmtComments));
                        }
                        String whitespace = tailIf.getElsePart().getPrefix().getWhitespace();
                        return elseStmt.withPrefix(elseStmt.getPrefix().withWhitespace(whitespace));
                    }));
                }
                return Arrays.asList(ifWithoutElse, tailElse.withPrefix(tailIf.getElsePart().getPrefix()));
            }

            private J.@Nullable If findInnermostIfWithElse(J.If ifStatement) {
                if (ifStatement.getElsePart() == null) {
                    return null;
                }
                Statement elseBody = ifStatement.getElsePart().getBody();
                if (elseBody instanceof J.If) {
                    J.If result = findInnermostIfWithElse((J.If) elseBody);
                    return result != null ? result : ifStatement;
                }
                return ifStatement;
            }

            private J.If removeInnermostElse(J.If ifStatement) {
                if (ifStatement.getElsePart() == null) {
                    return ifStatement;
                }
                Statement elseBody = ifStatement.getElsePart().getBody();
                if (elseBody instanceof J.If) {
                    J.If innerIf = (J.If) elseBody;
                    if (innerIf.getElsePart() != null && !(innerIf.getElsePart().getBody() instanceof J.If)) {
                        // This is the innermost if with a non-if else, remove its else
                        return ifStatement.withElsePart(
                                ifStatement.getElsePart().withBody(innerIf.withElsePart(null))
                        );
                    }
                    // Recurse deeper into the chain
                    return ifStatement.withElsePart(
                            ifStatement.getElsePart().withBody(removeInnermostElse(innerIf))
                    );
                }
                // Direct else (not else-if), remove it
                return ifStatement.withElsePart(null);
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
