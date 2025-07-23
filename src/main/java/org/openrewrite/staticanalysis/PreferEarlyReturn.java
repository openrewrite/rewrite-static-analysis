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
import org.openrewrite.java.JavaVisitor;
import org.openrewrite.java.tree.*;
import org.openrewrite.marker.Markers;

import java.time.Duration;
import java.util.Collections;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.openrewrite.Tree.randomId;

public class PreferEarlyReturn extends Recipe {

    @Override
    public String getDisplayName() {
        return "Prefer early returns";
    }

    @Override
    public String getDescription() {
        return "Refactors methods to use early returns for error/edge cases, reducing nesting and improving readability. " +
               "The recipe heuristically identifies if-else statements where the if block contains the main logic and the " +
               "else block contains a simple return. It then inverts the condition and moves the else block " +
               "to the beginning of the method with an early return, allowing the main logic to be un-indented.";
    }

    @Override
    public Set<String> getTags() {
        return Collections.emptySet();
    }

    @Override
    public Duration getEstimatedEffortPerOccurrence() {
        return Duration.ofMinutes(2);
    }

    @Override
    public TreeVisitor<?, ExecutionContext> getVisitor() {
        return new PreferEarlyReturnVisitor();
    }

    private static class PreferEarlyReturnVisitor extends JavaVisitor<ExecutionContext> {

        @Override
        public J visitIf(J.If ifStatement, ExecutionContext ctx) {
            J.If if_ = (J.If) super.visitIf(ifStatement, ctx);

            if (!isEligibleForEarlyReturn(if_)) {
                return if_;
            }

            J.ControlParentheses<Expression> invertedCondition = if_.getIfCondition().withTree(invertExpression(if_.getIfCondition().getTree()));
            J.If newIf = if_.withIfCondition(invertedCondition)
                    .withThenPart(if_.getElsePart().getBody())
                    .withElsePart(new J.If.Else(
                            randomId(),
                            if_.getElsePart().getPrefix(),
                            Markers.EMPTY,
                            JRightPadded.build(if_.getThenPart())
                    ));

            doAfterVisit(new UnwrapElseAfterReturn().getVisitor());

            return newIf;
        }

        private boolean isEligibleForEarlyReturn(J.If ifStatement) {
            if (ifStatement.getElsePart() == null ||
                !(ifStatement.getThenPart() instanceof J.Block) ||
                !(ifStatement.getElsePart().getBody() instanceof J.Block)) {
                return false;
            }

            J.Block thenBlock = (J.Block) ifStatement.getThenPart();
            J.Block elseBlock = (J.Block) ifStatement.getElsePart().getBody();

            int thenStatements = (thenBlock == null || thenBlock.getStatements() == null) ? 0 : thenBlock.getStatements().size();
            int elseStatements = (elseBlock == null || elseBlock.getStatements() == null) ? 0 : elseBlock.getStatements().size();

            if (thenStatements < 5 || (thenStatements - elseStatements) < 2) {
                // heuristics for determining if the then block is the "main flow" over the else block
                return false;
            }

            return hasReturnOrThrowStatement(elseBlock);
        }

        private boolean hasReturnOrThrowStatement(J.Block block) {
            if (block == null || block.getStatements() == null) {
                return false;
            }

            AtomicBoolean hasReturnOrThrow = new AtomicBoolean(false);
            new JavaVisitor<AtomicBoolean>() {
                @Override
                public J visitReturn(J.Return return_, AtomicBoolean flag) {
                    flag.set(true);
                    return return_;
                }

                @Override
                public J visitThrow(J.Throw thrown, AtomicBoolean flag) {
                    flag.set(true);
                    return thrown;
                }
            }.visit(block, hasReturnOrThrow);

            return hasReturnOrThrow.get();
        }

        private Expression invertExpression(Expression expr) {
            Expression toNegate = expr;
            if (expr instanceof J.Binary) {
                toNegate = new J.Parentheses<>(
                        randomId(),
                        expr.getPrefix(),
                        Markers.EMPTY,
                        JRightPadded.build(expr.withPrefix(Space.EMPTY))
                );
            }

            return new J.Unary(
                    randomId(),
                    toNegate.getPrefix(),
                    Markers.EMPTY,
                    new JLeftPadded<>(Space.EMPTY, J.Unary.Type.Not, Markers.EMPTY),
                    toNegate.withPrefix(Space.EMPTY),
                    JavaType.Primitive.Boolean
            );
        }
    }
}
