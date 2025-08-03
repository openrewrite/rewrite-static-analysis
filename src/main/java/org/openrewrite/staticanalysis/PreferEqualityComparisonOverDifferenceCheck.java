package org.openrewrite.staticanalysis;

import org.openrewrite.ExecutionContext;
import org.openrewrite.Recipe;
import org.openrewrite.java.JavaIsoVisitor;
import org.openrewrite.java.tree.Expression;
import org.openrewrite.java.tree.J;

public class PreferEqualityComparisonOverDifferenceCheck extends Recipe {

    @Override
    public String getDisplayName() {
        return "Prefer direct equality comparison";
    }

    @Override
    public String getDescription() {
        return "Replace `a - b == 0` with `a == b` for improved readability.";
    }

    @Override
    public JavaIsoVisitor<ExecutionContext> getVisitor() {
        return new JavaIsoVisitor<ExecutionContext>() {

            @Override
            public J.Binary visitBinary(J.Binary binary, ExecutionContext ctx) {
                binary = super.visitBinary(binary, ctx);

                if (binary.getOperator() == J.Binary.Type.Equal) {
                    Expression left = binary.getLeft();
                    Expression right = binary.getRight();

                    Expression unwrappedLeft = unwrapParentheses(left);
                    Expression unwrappedRight = unwrapParentheses(right);

                    // Case 1: (a - b) == 0
                    if (isSubtraction(unwrappedLeft) && isZero(unwrappedRight)) {
                        J.Binary subtraction = (J.Binary) unwrappedLeft;
                        return transformToEquality(binary, subtraction, ctx);
                    }

                    // Case 2: 0 == (a - b)
                    if (isZero(unwrappedLeft) && isSubtraction(unwrappedRight)) {
                        J.Binary subtraction = (J.Binary) unwrappedRight;
                        return transformToEquality(binary, subtraction, ctx);
                    }
                }

                return binary;
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
