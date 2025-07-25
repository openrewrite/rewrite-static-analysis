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

import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;
import org.openrewrite.ExecutionContext;
import org.openrewrite.Recipe;
import org.openrewrite.Tree;
import org.openrewrite.TreeVisitor;
import org.openrewrite.java.JavaVisitor;
import org.openrewrite.java.ParenthesizeVisitor;
import org.openrewrite.java.tree.*;
import org.openrewrite.marker.Markers;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;

import static java.util.Objects.requireNonNull;

public class SimplifyBooleanExpressionWithDeMorgan extends Recipe {

    @Override
    public String getDisplayName() {
        return "Simplify boolean expressions using De Morgan's laws";
    }

    @Override
    public String getDescription() {
        return "Applies De Morgan's laws to simplify boolean expressions with negation. " +
                "Transforms `!(a && b)` to `!a || !b` and `!(a || b)` to `!a && !b`.";
    }

    @Override
    public Set<String> getTags() {
        return Collections.singleton("RSPEC-1125");
    }

    @Override
    public Duration getEstimatedEffortPerOccurrence() {
        return Duration.ofMinutes(2);
    }

    @Override
    public TreeVisitor<?, ExecutionContext> getVisitor() {
        return new JavaVisitor<ExecutionContext>() {

            @Override
            public J visitUnary(J.Unary unary, ExecutionContext ctx) {
                if (unary.getOperator() == J.Unary.Type.Not && unary.getExpression() instanceof J.Parentheses) {
                    J.Parentheses<?> parentheses = (J.Parentheses<?>) unary.getExpression();
                    if (parentheses.getTree() instanceof J.Binary) {
                        J.Binary binary = (J.Binary) parentheses.getTree();
                        J.Parentheses<J.Binary> parenthesesBinary = (J.Parentheses<J.Binary>) unary.getExpression();

                        J.Binary.Type newOperator = null;
                        if (binary.getOperator() == J.Binary.Type.And) {
                            newOperator = J.Binary.Type.Or;
                        } else if (binary.getOperator() == J.Binary.Type.Or) {
                            newOperator = J.Binary.Type.And;
                        }
                        Expression left = binary.getLeft();
                        Expression right = binary.getRight();

                        if (newOperator != null) {
                            left = negate(left);
                            left = (Expression) new ParenthesizeVisitor<>().visit(left, ctx);
                            right = negate(right);
                            right = (Expression) new ParenthesizeVisitor<>().visit(right, ctx);
                        }

                        left = (Expression) this.visit(left, ctx);
                        right = (Expression) this.visit(right, ctx);

                        if (newOperator == null) {
                            J.Binary visitedBinary = binary.withLeft(left).withRight(right);
                            return unary.withExpression(parenthesesBinary.withTree(visitedBinary));
                        }
                        Space prefix = unary.getPrefix();
                        List<Comment> comments = new ArrayList<>(prefix.getComments());
                        comments.addAll(parenthesesBinary.getComments());
                        comments.addAll(binary.getComments());
                        prefix = prefix.withComments(comments);
                        getCursor().getParent().putMessage("MIGHT_NEED_PARENTHESES", true);
                        return binary.withLeft(left).withRight(right).withOperator(newOperator).withPrefix(prefix);
                    }
                }
                return requireNonNull(super.visitUnary(unary, ctx));
            }

            @Override
            public @Nullable J postVisit(@NonNull J tree, ExecutionContext executionContext) {
                J ret = super.postVisit(tree, executionContext);
                if (getCursor().pollMessage("MIGHT_NEED_PARENTHESES") != null) {
                    return new ParenthesizeVisitor<>().visit(ret, executionContext);
                };
                return ret;
            }

            private Expression negate(Expression expression) {
                if (expression instanceof J.Unary) {
                    J.Unary unaryExpr = (J.Unary) expression;
                    if (unaryExpr.getOperator() == J.Unary.Type.Not) {
                        return unaryExpr.getExpression().withPrefix(expression.getPrefix());
                    }
                }
                return new J.Unary(
                        Tree.randomId(),
                        expression.getPrefix(),
                        Markers.EMPTY,
                        new JLeftPadded<>(Space.EMPTY, J.Unary.Type.Not, Markers.EMPTY),
                        expression.withPrefix(Space.EMPTY),
                        JavaType.Primitive.Boolean
                );
            }
        };
    }
}
