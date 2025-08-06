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
import org.openrewrite.java.JavaIsoVisitor;
import org.openrewrite.java.tree.Expression;
import org.openrewrite.java.tree.J;

public class PreferEqualityComparisonOverDifferenceCheck extends Recipe {

    @Override
    public String getDisplayName() {
        return "Prefer direct comparison of numbers";
    }

    @Override
    public String getDescription() {
        return "Replace `a - b == 0` with `a == b`, `a - b != 0` with `a != b`, `a - b < 0` with `a < b`, " +
                "and similar transformations for all comparison operators to improve readability and avoid overflow issues.";
    }

    @Override
    public JavaIsoVisitor<ExecutionContext> getVisitor() {
        return new JavaIsoVisitor<ExecutionContext>() {

            @Override
            public J.Binary visitBinary(J.Binary binary, ExecutionContext ctx) {
                binary = super.visitBinary(binary, ctx);

                J.Binary.Type operator = binary.getOperator();
                if (isComparisonOperator(operator)) {
                    Expression left = binary.getLeft();
                    Expression right = binary.getRight();

                    Expression unwrappedLeft = unwrapParentheses(left);
                    Expression unwrappedRight = unwrapParentheses(right);

                    if (isSubtraction(unwrappedLeft) && isZero(unwrappedRight)) {
                        J.Binary subtraction = (J.Binary) unwrappedLeft;
                        return transformToEquality(binary, subtraction, ctx);
                    }
                }
                return binary;
            }

            private boolean isComparisonOperator(J.Binary.Type operator) {
                return operator == J.Binary.Type.Equal ||
                        operator == J.Binary.Type.NotEqual ||
                        operator == J.Binary.Type.LessThan ||
                        operator == J.Binary.Type.LessThanOrEqual ||
                        operator == J.Binary.Type.GreaterThan ||
                        operator == J.Binary.Type.GreaterThanOrEqual;
            }

            private Expression unwrapParentheses(Expression expr) {
                while (expr instanceof J.Parentheses) {
                    J.Parentheses<?> parentheses = (J.Parentheses<?>) expr;
                    expr = (Expression) parentheses.getTree();
                }
                return expr;
            }

            private boolean isSubtraction(Expression expr) {
                return expr instanceof J.Binary &&
                        ((J.Binary) expr).getOperator() == J.Binary.Type.Subtraction;
            }

            private boolean isZero(Expression expr) {
                if (expr instanceof J.Literal) {
                    Object value = ((J.Literal) expr).getValue();
                    if (value instanceof Number) {
                        return ((Number) value).doubleValue() == 0.0;
                    }
                }
                return false;
            }

            private J.Binary transformToEquality(J.Binary original, J.Binary subtraction, ExecutionContext ctx) {
                return maybeAutoFormat(original,
                        original.withLeft(subtraction.getLeft())
                                .withRight(subtraction.getRight()),
                        ctx);
            }
        };
    }
}
