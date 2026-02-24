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
import org.openrewrite.java.JavaIsoVisitor;
import org.openrewrite.java.tree.Expression;
import org.openrewrite.java.tree.J;
import org.openrewrite.java.tree.JavaType;
import org.openrewrite.java.tree.TypeUtils;

public class SimplifyForLoopBoundaryComparison extends Recipe {

    @Getter
    final String displayName = "Simplify for loop boundary comparisons";

    @Getter
    final String description = "Replace `<=` with `<` in for loop conditions by removing `+1` or `-1` offsets. " +
            "For example, `i <= n - 1` simplifies to `i < n`.";

    @Override
    public JavaIsoVisitor<ExecutionContext> getVisitor() {
        return new JavaIsoVisitor<ExecutionContext>() {

            @Override
            public J.Binary visitBinary(J.Binary binary, ExecutionContext ctx) {
                binary = super.visitBinary(binary, ctx);

                J.Binary.Type op = binary.getOperator();
                if (op != J.Binary.Type.LessThanOrEqual ||
                    !isIntegralType(binary.getLeft().getType()) ||
                    !isIntegralType(binary.getRight().getType()) ||
                    getCursor().firstEnclosing(J.ForLoop.Control.class) == null) {
                    return binary;
                }

                Expression right = unwrapParentheses(binary.getRight());
                if (right instanceof J.Binary) {
                    J.Binary rightArith = (J.Binary) right;
                    Expression simplified = getSimplifiedOperand(rightArith);
                    if (simplified != null && canSimplify(rightArith.getOperator(), true)) {
                        return binary.withOperator(J.Binary.Type.LessThan)
                                .withRight(simplified.withPrefix(binary.getRight().getPrefix()));
                    }
                }

                Expression left = unwrapParentheses(binary.getLeft());
                if (left instanceof J.Binary) {
                    J.Binary leftArith = (J.Binary) left;
                    Expression simplified = getSimplifiedOperand(leftArith);
                    if (simplified != null && canSimplify(leftArith.getOperator(), false)) {
                        return binary.withOperator(J.Binary.Type.LessThan)
                                .withLeft(simplified.withPrefix(binary.getLeft().getPrefix()));
                    }
                }

                return binary;
            }

            private Expression unwrapParentheses(Expression expr) {
                while (expr instanceof J.Parentheses) {
                    expr = (Expression) ((J.Parentheses<?>) expr).getTree();
                }
                return expr;
            }

            private @Nullable Expression getSimplifiedOperand(J.Binary arithmetic) {
                if (arithmetic.getOperator() == J.Binary.Type.Addition) {
                    if (isLiteralOne(arithmetic.getRight())) {
                        return arithmetic.getLeft();
                    }
                    if (isLiteralOne(arithmetic.getLeft())) {
                        return arithmetic.getRight();
                    }
                } else if (arithmetic.getOperator() == J.Binary.Type.Subtraction) {
                    if (isLiteralOne(arithmetic.getRight())) {
                        return arithmetic.getLeft();
                    }
                }
                return null;
            }

            private boolean isLiteralOne(Expression expr) {
                if (expr instanceof J.Literal) {
                    Object value = ((J.Literal) expr).getValue();
                    if (value instanceof Integer) {
                        return (Integer) value == 1;
                    }
                    if (value instanceof Long) {
                        return (Long) value == 1L;
                    }
                }
                return false;
            }

            private boolean isIntegralType(@Nullable JavaType type) {
                JavaType.Primitive p = TypeUtils.asPrimitive(type);
                return p == JavaType.Primitive.Int || p == JavaType.Primitive.Long ||
                       p == JavaType.Primitive.Short || p == JavaType.Primitive.Byte ||
                       p == JavaType.Primitive.Char;
            }

            private boolean canSimplify(J.Binary.Type arithmeticOp, boolean isOnRight) {
                boolean isSubtraction = arithmeticOp == J.Binary.Type.Subtraction;
                // Right subtract (i <= n - 1) or Left add (i + 1 <= n)
                return isOnRight == isSubtraction;
            }
        };
    }
}
