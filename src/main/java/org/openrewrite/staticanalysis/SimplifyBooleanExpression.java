/*
 * Copyright 2020 the original author or authors.
 * <p>
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * <p>
 * https://www.apache.org/licenses/LICENSE-2.0
 * <p>
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.openrewrite.staticanalysis;

import org.openrewrite.*;
import org.openrewrite.internal.lang.Nullable;
import org.openrewrite.java.JavaIsoVisitor;
import org.openrewrite.java.JavaVisitor;
import org.openrewrite.java.MethodMatcher;
import org.openrewrite.java.UnwrapParentheses;
import org.openrewrite.java.format.AutoFormatVisitor;
import org.openrewrite.java.tree.*;

import java.time.Duration;
import java.util.Collections;
import java.util.Set;

import static java.util.Objects.requireNonNull;

public class SimplifyBooleanExpression extends Recipe {

    @Override
    public String getDisplayName() {
        return "Simplify boolean expression";
    }

    @Override
    public String getDescription() {
        return "Checks for over-complicated boolean expressions. Finds code like `if (b == true)`, `b || true`, `!false`, etc.";
    }

    @Override
    public Set<String> getTags() {
        return Collections.singleton("RSPEC-1125");
    }

    @Override
    public Duration getEstimatedEffortPerOccurrence() {
        return Duration.ofMinutes(5);
    }

    @Override
    public TreeVisitor<?, ExecutionContext> getVisitor() {
        return new JavaVisitor<ExecutionContext>() {
            private static final String MAYBE_AUTO_FORMAT_ME = "MAYBE_AUTO_FORMAT_ME";

            @Override
            public J visit(@Nullable Tree tree, ExecutionContext ctx) {
                if (tree instanceof JavaSourceFile) {
                    JavaSourceFile cu = (JavaSourceFile) requireNonNull(super.visit(tree, ctx));
                    if (tree != cu) {
                        // recursive simplification
                        cu = (JavaSourceFile) getVisitor().visitNonNull(cu, ctx);
                    }
                    return cu;
                }
                return super.visit(tree, ctx);
            }

            @Override
            public J visitBinary(J.Binary binary, ExecutionContext ctx) {
                J j = super.visitBinary(binary, ctx);
                J.Binary asBinary = (J.Binary) j;

                if (asBinary.getOperator() == J.Binary.Type.And) {
                    if (isLiteralFalse(asBinary.getLeft())) {
                        maybeUnwrapParentheses();
                        j = asBinary.getLeft();
                    } else if (isLiteralFalse(asBinary.getRight())) {
                        maybeUnwrapParentheses();
                        j = asBinary.getRight().withPrefix(asBinary.getRight().getPrefix().withWhitespace(""));
                    } else if (isLiteralTrue(asBinary.getLeft())) {
                        maybeUnwrapParentheses();
                        j = asBinary.getRight();
                    } else if (isLiteralTrue(asBinary.getRight())) {
                        maybeUnwrapParentheses();
                        j = asBinary.getLeft().withPrefix(asBinary.getLeft().getPrefix().withWhitespace(""));
                    } else if (removeAllSpace(asBinary.getLeft()).printTrimmed(getCursor())
                            .equals(removeAllSpace(asBinary.getRight()).printTrimmed(getCursor()))) {
                        maybeUnwrapParentheses();
                        j = asBinary.getLeft();
                    }
                } else if (asBinary.getOperator() == J.Binary.Type.Or) {
                    if (isLiteralTrue(asBinary.getLeft())) {
                        maybeUnwrapParentheses();
                        j = asBinary.getLeft();
                    } else if (isLiteralTrue(asBinary.getRight())) {
                        maybeUnwrapParentheses();
                        j = asBinary.getRight().withPrefix(asBinary.getRight().getPrefix().withWhitespace(""));
                    } else if (isLiteralFalse(asBinary.getLeft())) {
                        maybeUnwrapParentheses();
                        j = asBinary.getRight();
                    } else if (isLiteralFalse(asBinary.getRight())) {
                        maybeUnwrapParentheses();
                        j = asBinary.getLeft().withPrefix(asBinary.getLeft().getPrefix().withWhitespace(""));
                    } else if (removeAllSpace(asBinary.getLeft()).printTrimmed(getCursor())
                            .equals(removeAllSpace(asBinary.getRight()).printTrimmed(getCursor()))) {
                        maybeUnwrapParentheses();
                        j = asBinary.getLeft();
                    }
                } else if (asBinary.getOperator() == J.Binary.Type.Equal) {
                    if (isLiteralTrue(asBinary.getLeft())) {
                        if (isNotKotlinNullableType(asBinary.getRight())) {
                            maybeUnwrapParentheses();
                            j = asBinary.getRight().withPrefix(asBinary.getRight().getPrefix().withWhitespace(""));
                        }
                    } else if (isLiteralTrue(asBinary.getRight())) {
                        if (isNotKotlinNullableType(asBinary.getLeft())) {
                            maybeUnwrapParentheses();
                            j = asBinary.getLeft().withPrefix(asBinary.getLeft().getPrefix().withWhitespace(" "));
                        }
                    } else {
                        j = maybeReplaceCompareWithNull(asBinary, true);
                    }
                } else if (asBinary.getOperator() == J.Binary.Type.NotEqual) {
                    if (isLiteralFalse(asBinary.getLeft())) {
                        if (isNotKotlinNullableType(asBinary.getRight())) {
                            maybeUnwrapParentheses();
                            j = asBinary.getRight().withPrefix(asBinary.getRight().getPrefix().withWhitespace(""));
                        }
                    } else if (isLiteralFalse(asBinary.getRight())) {
                        if (isNotKotlinNullableType(asBinary.getLeft())) {
                            maybeUnwrapParentheses();
                            j = asBinary.getLeft().withPrefix(asBinary.getLeft().getPrefix().withWhitespace(" "));
                        }
                    } else {
                        j = maybeReplaceCompareWithNull(asBinary, false);
                    }
                }
                if (asBinary != j) {
                    getCursor().getParentTreeCursor().putMessage(MAYBE_AUTO_FORMAT_ME, "");
                }
                return j;
            }

            @Override
            public J postVisit(J tree, ExecutionContext ctx) {
                J j = super.postVisit(tree, ctx);
                if (getCursor().pollMessage(MAYBE_AUTO_FORMAT_ME) != null) {
                    j = new AutoFormatVisitor<>().visit(j, ctx, getCursor().getParentOrThrow());
                }
                return j;
            }

            @Override
            public J visitUnary(J.Unary unary, ExecutionContext ctx) {
                J j = super.visitUnary(unary, ctx);
                J.Unary asUnary = (J.Unary) j;

                if (asUnary.getOperator() == J.Unary.Type.Not) {
                    if (isLiteralTrue(asUnary.getExpression())) {
                        maybeUnwrapParentheses();
                        j = ((J.Literal) asUnary.getExpression()).withValue(false).withValueSource("false");
                    } else if (isLiteralFalse(asUnary.getExpression())) {
                        maybeUnwrapParentheses();
                        j = ((J.Literal) asUnary.getExpression()).withValue(true).withValueSource("true");
                    } else if (asUnary.getExpression() instanceof J.Unary && ((J.Unary) asUnary.getExpression()).getOperator() == J.Unary.Type.Not) {
                        maybeUnwrapParentheses();
                        j = ((J.Unary) asUnary.getExpression()).getExpression();
                    }
                }
                if (asUnary != j) {
                    getCursor().getParentTreeCursor().putMessage(MAYBE_AUTO_FORMAT_ME, "");
                }
                return j;
            }

            private final MethodMatcher isEmpty = new MethodMatcher("java.lang.String isEmpty()");

            @Override
            public J visitMethodInvocation(J.MethodInvocation method, ExecutionContext executionContext) {
                J j = super.visitMethodInvocation(method, executionContext);
                J.MethodInvocation asMethod = (J.MethodInvocation) j;
                if (isEmpty.matches(asMethod) && J.Literal.isLiteralValue(asMethod.getSelect(), "")) {
                    maybeUnwrapParentheses();
                    return booleanLiteral(method, true);
                }
                return j;
            }

            /**
             * Specifically for removing immediately-enclosing parentheses on Identifiers and Literals.
             * This queues a potential unwrap operation for the next visit. After unwrapping something, it's possible
             * there are more Simplifications this recipe can identify and perform, which is why visitCompilationUnit
             * checks for any changes to the entire Compilation Unit, and if so, queues up another SimplifyBooleanExpression
             * recipe call. This convergence loop eventually reconciles any remaining Boolean Expression Simplifications
             * the recipe can perform.
             */
            private void maybeUnwrapParentheses() {
                Cursor c = getCursor().getParentOrThrow().getParentTreeCursor();
                if (c.getValue() instanceof J.Parentheses) {
                    doAfterVisit(new UnwrapParentheses<>(c.getValue()));
                }
            }

            private boolean isLiteralTrue(@Nullable Expression expression) {
                return expression instanceof J.Literal && ((J.Literal) expression).getValue() == Boolean.valueOf(true);
            }

            private boolean isLiteralFalse(@Nullable Expression expression) {
                return expression instanceof J.Literal && ((J.Literal) expression).getValue() == Boolean.valueOf(false);
            }

            private boolean isNullLiteral(Expression expression) {
                return expression instanceof J.Literal && ((J.Literal) expression).getType() == JavaType.Primitive.Null;
            }

            private boolean isNonNullLiteral(Expression expression) {
                return expression instanceof J.Literal && ((J.Literal) expression).getType() != JavaType.Primitive.Null;
            }

            private J maybeReplaceCompareWithNull(J.Binary asBinary, boolean valueIfEqual) {
                Expression left = asBinary.getLeft();
                Expression right = asBinary.getRight();

                boolean leftIsNull = isNullLiteral(left);
                boolean rightIsNull = isNullLiteral(right);
                if (leftIsNull && rightIsNull) {
                    maybeUnwrapParentheses();
                    return booleanLiteral(asBinary, valueIfEqual);
                }
                boolean leftIsNonNullLiteral = isNonNullLiteral(left);
                boolean rightIsNonNullLiteral = isNonNullLiteral(right);
                if ((leftIsNull && rightIsNonNullLiteral) || (rightIsNull && leftIsNonNullLiteral)) {
                    maybeUnwrapParentheses();
                    return booleanLiteral(asBinary, !valueIfEqual);
                }

                return asBinary;
            }

            private J.Literal booleanLiteral(J j, boolean value) {
                return new J.Literal(Tree.randomId(),
                        j.getPrefix(),
                        j.getMarkers(),
                        value,
                        String.valueOf(value),
                        Collections.emptyList(),
                        JavaType.Primitive.Boolean);
            }

            private J removeAllSpace(J j) {
                //noinspection ConstantConditions
                return new JavaIsoVisitor<Integer>() {
                    @Override
                    public Space visitSpace(Space space, Space.Location loc, Integer integer) {
                        return Space.EMPTY;
                    }
                }.visit(j, 0);
            }

            // Comparing Kotlin nullable type `?` with tree/false can not be simplified,
            // e.g. `X?.fun() == true` is not equivalent to `X?.fun()`
            private boolean isNotKotlinNullableType(@Nullable J j) {
                if (j == null) {
                    return true;
                }

                if (j instanceof J.MethodInvocation) {
                    J.MethodInvocation m = (J.MethodInvocation) j;
                    return m.getSelect() != null && !m.getSelect().getMarkers().findFirst(org.openrewrite.kotlin.marker.IsNullSafe.class).isPresent();
                }

                return true;
            }
        };
    }

    @Override
    public boolean causesAnotherCycle() {
        return true;
    }
}
