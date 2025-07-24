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

import org.jetbrains.annotations.NotNull;
import org.openrewrite.ExecutionContext;
import org.openrewrite.Recipe;
import org.openrewrite.Tree;
import org.openrewrite.TreeVisitor;
import org.openrewrite.java.JavaVisitor;
import org.openrewrite.java.tree.Expression;
import org.openrewrite.java.tree.J;
import org.openrewrite.java.tree.JLeftPadded;
import org.openrewrite.java.tree.JavaType;
import org.openrewrite.java.tree.Space;
import org.openrewrite.marker.Markers;

import java.time.Duration;
import java.util.Collections;
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
                        Expression leftAltered = requireNonNull((Expression) visit(binary.getLeft(), ctx));
                        Expression rightAltered = requireNonNull((Expression) visit(binary.getRight(), ctx));

                        if (newOperator != null) {
                            Expression leftNegated = negate(leftAltered, binary.getLeft().getPrefix());
                            Expression rightNegated = negate(rightAltered, binary.getRight().getPrefix());

                            return new J.Binary(
                                    binary.getId(),
                                    unary.getPrefix(),
                                    unary.getMarkers(),
                                    leftNegated,
                                    binary.getPadding().getOperator().withElement(newOperator),
                                    rightNegated,
                                    JavaType.Primitive.Boolean
                            );
                        } else {
                            J.Binary visitedBinary = binary.withLeft(leftAltered).withRight(rightAltered);
                            return unary.withExpression(parenthesesBinary.withTree(visitedBinary));
                        }
                    }
                }
                return requireNonNull(super.visitUnary(unary, ctx));
            }

            private Expression negate(Expression expression, Space prefix) {
                if (expression instanceof J.Unary) {
                    J.Unary unaryExpr = (J.Unary) expression;
                    if (unaryExpr.getOperator() == J.Unary.Type.Not) {
                        return unaryExpr.getExpression().withPrefix(prefix);
                    }
                }
                return new J.Unary(
                        Tree.randomId(),
                        prefix,
                        Markers.EMPTY,
                        new JLeftPadded<>(Space.EMPTY, J.Unary.Type.Not, Markers.EMPTY),
                        expression.withPrefix(Space.EMPTY),
                        JavaType.Primitive.Boolean
                );
            }

            /**
             * Check if all operators in a chained binary expression are the same.
             * Handles nesting on both left and right sides.
             */
            private boolean allOperatorsAreSame(J.Binary binary, J.Binary.Type expectedOperator) {
                // Check current operator
                if (binary.getOperator() != expectedOperator) {
                    return false;
                }

                // Recursively check left side if it's a binary expression
                if (binary.getLeft() instanceof J.Binary) {
                    J.Binary leftBinary = (J.Binary) binary.getLeft();
                    if (!allOperatorsAreSame(leftBinary, expectedOperator)) {
                        return false;
                    }
                }

                // Recursively check right side if it's a binary expression
                if (binary.getRight() instanceof J.Binary) {
                    J.Binary rightBinary = (J.Binary) binary.getRight();
                    if (!allOperatorsAreSame(rightBinary, expectedOperator)) {
                        return false;
                    }
                }

                return true;
            }
        };
    }
}
