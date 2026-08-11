/*
 * Copyright 2024 the original author or authors.
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

import lombok.EqualsAndHashCode;
import lombok.Value;
import org.openrewrite.ExecutionContext;
import org.openrewrite.Recipe;
import org.openrewrite.TreeVisitor;
import org.openrewrite.java.JavaVisitor;
import org.openrewrite.java.MethodMatcher;
import org.openrewrite.java.search.SemanticallyEqual;
import org.openrewrite.java.tree.Expression;
import org.openrewrite.java.tree.J;

import java.time.Duration;

import static org.openrewrite.staticanalysis.SideEffects.mayHaveSideEffects;

@EqualsAndHashCode(callSuper = false)
@Value
public class RemoveRedundantNullCheckBeforeLiteralEquals extends Recipe {

    private static final MethodMatcher EQUALS_MATCHER = new MethodMatcher("java.lang.String equals(java.lang.Object)");

    String displayName = "Remove redundant null checks before literal equals";

    String description = "Removes redundant null checks before `equals()` comparisons when the receiver is a literal string, " +
               "since literals can never be null and `equals()` returns false for null arguments.";

    Duration estimatedEffortPerOccurrence = Duration.ofMinutes(1);

    @Override
    public TreeVisitor<?, ExecutionContext> getVisitor() {
        return new JavaVisitor<ExecutionContext>() {
            @Override
            public J visitBinary(J.Binary binary, ExecutionContext ctx) {
                J.Binary bi = (J.Binary) super.visitBinary(binary, ctx);
                if (bi.getOperator() != J.Binary.Type.And) {
                    return bi;
                }

                Expression left = bi.getLeft();
                Expression right = bi.getRight();

                // Check if we have a pattern: (expr != null) && ("literal".equals(expr))
                if (left instanceof J.Binary && right instanceof J.MethodInvocation) {
                    J.Binary nullCheck = (J.Binary) left;
                    J.MethodInvocation equalsCall = (J.MethodInvocation) right;

                    if (isRedundantNullCheck(nullCheck, equalsCall)) {
                        // Return just the equals call
                        return equalsCall.withPrefix(bi.getPrefix());
                    }
                }

                // Check if we have chained patterns like:
                // (... && expr != null) && ("literal".equals(expr))
                if (left instanceof J.Binary && ((J.Binary) left).getOperator() == J.Binary.Type.And &&
                        right instanceof J.MethodInvocation) {
                    J.Binary leftBinary = (J.Binary) left;
                    J.MethodInvocation equalsCall = (J.MethodInvocation) right;

                    // Check if the rightmost part of left is a null check for the same expression
                    Expression rightmostOfLeft = leftBinary.getRight();
                    if (rightmostOfLeft instanceof J.Binary &&
                            isRedundantNullCheck((J.Binary) rightmostOfLeft, equalsCall)) {
                        // Remove the null check from the left side
                        return bi.withLeft(leftBinary.getLeft()).withRight(equalsCall);
                    }
                }

                return bi;
            }

            private boolean isRedundantNullCheck(J.Binary nullCheck, J.MethodInvocation equalsCall) {
                if (nullCheck.getOperator() != J.Binary.Type.NotEqual || !EQUALS_MATCHER.matches(equalsCall)) {
                    return false;
                }

                Expression receiver = equalsCall.getSelect();
                if (!(receiver instanceof J.Literal) || !(((J.Literal) receiver).getValue() instanceof String)) {
                    return false;
                }

                if (equalsCall.getArguments().size() != 1) {
                    return false;
                }
                Expression equalsArg = equalsCall.getArguments().get(0);

                Expression nullChecked = J.Literal.isLiteralValue(nullCheck.getLeft(), null) ? nullCheck.getRight() :
                        J.Literal.isLiteralValue(nullCheck.getRight(), null) ? nullCheck.getLeft() : null;

                // The rewrite evaluates the expression once where it was evaluated twice, so it is only equivalent
                // when evaluating it is free of side effects; `SemanticallyEqual` proves the two occurrences mean the
                // same thing, not that evaluating them twice is the same as evaluating them once.
                return nullChecked != null &&
                        SemanticallyEqual.areEqual(nullChecked, equalsArg) &&
                        !mayHaveSideEffects(equalsArg);
            }
        };
    }
}
