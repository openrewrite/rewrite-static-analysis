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
import org.openrewrite.Tree;
import org.openrewrite.TreeVisitor;
import org.openrewrite.internal.ListUtils;
import org.openrewrite.java.JavaVisitor;
import org.openrewrite.java.tree.Comment;
import org.openrewrite.java.tree.J;
import org.openrewrite.java.tree.JavaType;
import org.openrewrite.java.tree.Space;
import org.openrewrite.java.tree.Statement;
import org.openrewrite.marker.Markers;

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
                        if (ifStatement.getElsePart() != null) {
                            // Case 1: If block already ends with return/throw
                            if (endsWithReturnOrThrow(ifStatement.getThenPart())) {
                                return unwrapElseBlock(ifStatement);
                            }
                            // Case 2: Void method with one big if-else (no return/throw)
                            else if (isVoidMethodWithSingleIfElse(block, ifStatement)) {
                                return unwrapElseBlockWithReturn(ifStatement);
                            }
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

            private boolean isVoidMethodWithSingleIfElse(J.Block methodBlock, J.If ifStatement) {
                // Check if the method block contains only this if statement
                if (methodBlock.getStatements().size() != 1) {
                    return false;
                }
                
                // Must be in a void method
                if (!isInVoidMethod()) {
                    return false;
                }
                
                // The if statement must not already end with return/throw
                if (endsWithReturnOrThrow(ifStatement.getThenPart())) {
                    return false;
                }
                
                // Both if and else parts should be blocks (not single statements)
                if (!(ifStatement.getThenPart() instanceof J.Block) || 
                    !(ifStatement.getElsePart().getBody() instanceof J.Block)) {
                    return false;
                }
                
                return true;
            }

            private boolean isInVoidMethod() {
                // Walk up the cursor to find the enclosing method declaration
                try {
                    return getCursor().getPathAsStream()
                        .filter(J.MethodDeclaration.class::isInstance)
                        .map(J.MethodDeclaration.class::cast)
                        .findFirst()
                        .map(method -> {
                            if (method.getReturnTypeExpression() == null) {
                                return true; // Constructor
                            }
                            if (method.getReturnTypeExpression() instanceof J.Primitive) {
                                J.Primitive primitive = (J.Primitive) method.getReturnTypeExpression();
                                return primitive.getType() == JavaType.Primitive.Void;
                            }
                            return false;
                        })
                        .orElse(false);
                } catch (Exception e) {
                    // If there's any issue with cursor traversal, be conservative
                    return false;
                }
            }

            private List<Statement> unwrapElseBlock(J.If ifStatement) {
                // Original logic for existing case
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

            private List<Statement> unwrapElseBlockWithReturn(J.If ifStatement) {
                // Add return statement to the if block
                J.Block ifBlock = (J.Block) ifStatement.getThenPart();
                J.Return returnStmt = new J.Return(
                    Tree.randomId(),
                    Space.SINGLE_SPACE,
                    Markers.EMPTY,
                    null
                );
                
                // Add return to the end of the if block
                J.Block newIfBlock = ifBlock.withStatements(
                    ListUtils.concat(ifBlock.getStatements(), returnStmt)
                );
                
                J.If newIf = ifStatement.withThenPart(newIfBlock).withElsePart(null);
                
                // Unwrap else block statements
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
        };
        return Repeat.repeatUntilStable(javaVisitor);
    }
}
