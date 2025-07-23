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
import org.openrewrite.Tree;
import org.openrewrite.TreeVisitor;
import org.openrewrite.internal.ListUtils;
import org.openrewrite.java.JavaVisitor;
import org.openrewrite.java.tree.Expression;
import org.openrewrite.java.tree.J;
import org.openrewrite.java.tree.JLeftPadded;
import org.openrewrite.java.tree.JRightPadded;
import org.openrewrite.java.tree.JavaType;
import org.openrewrite.java.tree.Space;
import org.openrewrite.java.tree.Statement;
import org.openrewrite.marker.Markers;

import java.time.Duration;
import java.util.Collections;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import static org.openrewrite.Tree.randomId;

public class PreferEarlyReturn extends Recipe {

    @Override
    public String getDisplayName() {
        return "Prefer early returns";
    }

    @Override
    public String getDescription() {
        return "Refactors methods to use early returns for error/edge cases, reducing nesting and improving readability. " +
               "The recipe identifies if-else statements where the if block contains the main logic (≥5 statements) and the " +
               "else block contains a simple return (≤2 statements). It then inverts the condition and moves the else block " +
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
            
            J.ControlParentheses invertedCondition = invertCondition(if_.getIfCondition());
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
            if (ifStatement.getElsePart() == null) {
                return false;
            }
            
            if (!(ifStatement.getThenPart() instanceof J.Block)) {
                return false;
            }
            
            if (!(ifStatement.getElsePart().getBody() instanceof J.Block)) {
                return false;
            }
            
            J.Block thenBlock = (J.Block) ifStatement.getThenPart();
            J.Block elseBlock = (J.Block) ifStatement.getElsePart().getBody();
            
            int thenStatements = countStatements(thenBlock);
            int elseStatements = countStatements(elseBlock);
            
            if (thenStatements < 5 || elseStatements > 2) {
                return false;
            }
            
            return hasReturnOrThrowStatement(elseBlock);
        }
        
        private int countStatements(J.Block block) {
            if (block == null || block.getStatements() == null) {
                return 0;
            }
            
            return block.getStatements().size();
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
        
        private J.ControlParentheses invertCondition(J.ControlParentheses condition) {
            if (condition == null || !(condition.getTree() instanceof Expression)) {
                return condition;
            }
            
            Expression expr = (Expression) condition.getTree();
            Expression inverted = invertExpression(expr);
            
            return condition.withTree(inverted);
        }
        
        private Expression invertExpression(Expression expr) {
            if (expr instanceof J.Binary) {
                J.Binary binary = (J.Binary) expr;
                
                if (binary.getOperator() == J.Binary.Type.And) {
                    Expression leftInverted = invertExpression(binary.getLeft());
                    Expression rightInverted = invertExpression(binary.getRight());
                    return binary.withOperator(J.Binary.Type.Or)
                            .withLeft(leftInverted)
                            .withRight(rightInverted.withPrefix(Space.SINGLE_SPACE));
                } else if (binary.getOperator() == J.Binary.Type.Or) {
                    Expression leftInverted = invertExpression(binary.getLeft());
                    Expression rightInverted = invertExpression(binary.getRight());
                    return binary.withOperator(J.Binary.Type.And)
                            .withLeft(leftInverted)
                            .withRight(rightInverted.withPrefix(Space.SINGLE_SPACE));
                } else if (binary.getOperator() == J.Binary.Type.Equal) {
                    return binary.withOperator(J.Binary.Type.NotEqual);
                } else if (binary.getOperator() == J.Binary.Type.NotEqual) {
                    return binary.withOperator(J.Binary.Type.Equal);
                } else if (binary.getOperator() == J.Binary.Type.LessThan) {
                    return binary.withOperator(J.Binary.Type.GreaterThanOrEqual);
                } else if (binary.getOperator() == J.Binary.Type.LessThanOrEqual) {
                    return binary.withOperator(J.Binary.Type.GreaterThan);
                } else if (binary.getOperator() == J.Binary.Type.GreaterThan) {
                    return binary.withOperator(J.Binary.Type.LessThanOrEqual);
                } else if (binary.getOperator() == J.Binary.Type.GreaterThanOrEqual) {
                    return binary.withOperator(J.Binary.Type.LessThan);
                }
            } else if (expr instanceof J.Unary) {
                J.Unary unary = (J.Unary) expr;
                if (unary.getOperator() == J.Unary.Type.Not) {
                    return unary.getExpression();
                }
            } else if (expr instanceof J.Parentheses) {
                @SuppressWarnings("unchecked")
                J.Parentheses<Expression> parens = (J.Parentheses<Expression>) expr;
                if (parens.getTree() instanceof Expression) {
                    Expression innerInverted = invertExpression(parens.getTree());
                    return parens.withTree(innerInverted);
                }
            }
            
            return new J.Unary(
                    randomId(),
                    expr.getPrefix(),
                    Markers.EMPTY,
                    new JLeftPadded<>(Space.EMPTY, J.Unary.Type.Not, Markers.EMPTY),
                    expr.withPrefix(Space.EMPTY),
                    JavaType.Primitive.Boolean
            );
        }
    }
}