/*
 * Copyright 2026 the original author or authors.
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
import org.openrewrite.java.JavaTemplate;
import org.openrewrite.java.JavaVisitor;
import org.openrewrite.java.tree.Expression;
import org.openrewrite.java.tree.J;
import org.openrewrite.java.tree.JavaType;
import org.openrewrite.java.tree.TypeUtils;

public class SimplifyForLoopBoundaryComparison extends Recipe {

    @Getter
    final String displayName = "Simplify for loop boundary comparisons";

    @Getter
    final String description = "Replace `<=` with `<` in for loop conditions by adjusting the comparison operands. " +
            "For example, `i <= n - 1` simplifies to `i < n`, and `i <= n` becomes `i < n + 1`.";

    @Override
    public JavaVisitor<ExecutionContext> getVisitor() {
        return new JavaVisitor<ExecutionContext>() {
            @Override
            public J visitBinary(J.Binary binary, ExecutionContext ctx) {
                binary = (J.Binary) super.visitBinary(binary, ctx);

                if (binary.getOperator() != J.Binary.Type.LessThanOrEqual ||
                    !isIntegralType(binary.getLeft().getType()) ||
                    !isIntegralType(binary.getRight().getType()) ||
                    getCursor().firstEnclosing(J.ForLoop.Control.class) == null) {
                    return binary;
                }

                // Try adjusting right-side arithmetic directly (no parentheses)
                if (binary.getRight() instanceof J.Binary) {
                    J.Binary result = tryAdjustRight(binary, (J.Binary) binary.getRight());
                    if (result != null) {
                        return result;
                    }
                }

                // Try adjusting left-side addition directly (no parentheses)
                if (binary.getLeft() instanceof J.Binary) {
                    J.Binary result = tryAdjustLeft(binary, (J.Binary) binary.getLeft());
                    if (result != null) {
                        return result;
                    }
                }

                // Handle parenthesized subtraction on right: i <= (n - 1) → i < n
                Expression unwrappedRight = unwrapParentheses(binary.getRight());
                if (unwrappedRight != binary.getRight() && unwrappedRight instanceof J.Binary) {
                    J.Binary rightArith = (J.Binary) unwrappedRight;
                    if (rightArith.getOperator() == J.Binary.Type.Subtraction && isIntLiteral(rightArith.getRight(), 1)) {
                        return binary.withOperator(J.Binary.Type.LessThan)
                                .withRight(rightArith.getLeft().withPrefix(binary.getRight().getPrefix()));
                    }
                }

                // Handle parenthesized addition on left: (i + 1) <= n → i < n
                Expression unwrappedLeft = unwrapParentheses(binary.getLeft());
                if (unwrappedLeft != binary.getLeft() && unwrappedLeft instanceof J.Binary) {
                    J.Binary leftArith = (J.Binary) unwrappedLeft;
                    if (leftArith.getOperator() == J.Binary.Type.Addition) {
                        if (isIntLiteral(leftArith.getRight(), 1)) {
                            return binary.withOperator(J.Binary.Type.LessThan)
                                    .withLeft(leftArith.getLeft().withPrefix(binary.getLeft().getPrefix()));
                        }
                        if (isIntLiteral(leftArith.getLeft(), 1)) {
                            return binary.withOperator(J.Binary.Type.LessThan)
                                    .withLeft(leftArith.getRight().withPrefix(binary.getLeft().getPrefix()));
                        }
                    }
                }

                // General fallback: A <= B → A < B + 1
                return JavaTemplate.builder("#{any()} < #{any()} + 1").build()
                        .apply(getCursor(), binary.getCoordinates().replace(),
                                binary.getLeft(), binary.getRight());
            }

            private J.@Nullable Binary tryAdjustRight(J.Binary binary, J.Binary rightArith) {
                if (rightArith.getOperator() == J.Binary.Type.Subtraction) {
                    Integer val = getIntLiteralValue(rightArith.getRight());
                    if (val != null && val >= 1) {
                        if (val == 1) {
                            return binary.withOperator(J.Binary.Type.LessThan)
                                    .withRight(rightArith.getLeft().withPrefix(binary.getRight().getPrefix()));
                        }
                        return binary.withOperator(J.Binary.Type.LessThan)
                                .withRight(rightArith.withRight(
                                        withNewValue((J.Literal) rightArith.getRight(), val - 1)));
                    }
                }
                if (rightArith.getOperator() == J.Binary.Type.Addition) {
                    Integer val = getIntLiteralValue(rightArith.getRight());
                    if (val != null) {
                        return binary.withOperator(J.Binary.Type.LessThan)
                                .withRight(rightArith.withRight(
                                        withNewValue((J.Literal) rightArith.getRight(), val + 1)));
                    }
                    val = getIntLiteralValue(rightArith.getLeft());
                    if (val != null) {
                        return binary.withOperator(J.Binary.Type.LessThan)
                                .withRight(rightArith.withLeft(
                                        withNewValue((J.Literal) rightArith.getLeft(), val + 1)));
                    }
                }
                return null;
            }

            private J.@Nullable Binary tryAdjustLeft(J.Binary binary, J.Binary leftArith) {
                if (leftArith.getOperator() != J.Binary.Type.Addition) {
                    return null;
                }
                Integer val = getIntLiteralValue(leftArith.getRight());
                if (val != null && val >= 1) {
                    if (val == 1) {
                        return binary.withOperator(J.Binary.Type.LessThan)
                                .withLeft(leftArith.getLeft().withPrefix(binary.getLeft().getPrefix()));
                    }
                    return binary.withOperator(J.Binary.Type.LessThan)
                            .withLeft(leftArith.withRight(
                                    withNewValue((J.Literal) leftArith.getRight(), val - 1)));
                }
                val = getIntLiteralValue(leftArith.getLeft());
                if (val != null && val >= 1) {
                    if (val == 1) {
                        return binary.withOperator(J.Binary.Type.LessThan)
                                .withLeft(leftArith.getRight().withPrefix(binary.getLeft().getPrefix()));
                    }
                    return binary.withOperator(J.Binary.Type.LessThan)
                            .withLeft(leftArith.withLeft(
                                    withNewValue((J.Literal) leftArith.getLeft(), val - 1)));
                }
                return null;
            }

            private Expression unwrapParentheses(Expression expr) {
                while (expr instanceof J.Parentheses) {
                    expr = (Expression) ((J.Parentheses<?>) expr).getTree();
                }
                return expr;
            }

            private @Nullable Integer getIntLiteralValue(Expression expr) {
                if (expr instanceof J.Literal) {
                    Object value = ((J.Literal) expr).getValue();
                    if (value instanceof Integer) {
                        return (Integer) value;
                    }
                    if (value instanceof Long) {
                        return ((Long) value).intValue();
                    }
                }
                return null;
            }

            private boolean isIntLiteral(Expression expr, int expected) {
                Integer val = getIntLiteralValue(expr);
                return val != null && val == expected;
            }

            private J.Literal withNewValue(J.Literal literal, int newValue) {
                if (literal.getValue() instanceof Long) {
                    return literal.withValue((long) newValue).withValueSource(newValue + "L");
                }
                return literal.withValue(newValue).withValueSource(String.valueOf(newValue));
            }

            private boolean isIntegralType(@Nullable JavaType type) {
                JavaType.Primitive p = TypeUtils.asPrimitive(type);
                return p == JavaType.Primitive.Int || p == JavaType.Primitive.Long ||
                       p == JavaType.Primitive.Short || p == JavaType.Primitive.Byte ||
                       p == JavaType.Primitive.Char;
            }
        };
    }
}
