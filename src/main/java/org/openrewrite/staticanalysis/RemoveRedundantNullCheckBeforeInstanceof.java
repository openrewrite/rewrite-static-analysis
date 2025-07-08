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
import org.openrewrite.java.search.SemanticallyEqual;
import org.openrewrite.java.tree.Expression;
import org.openrewrite.java.tree.J;

import java.time.Duration;
import java.util.Collections;
import java.util.Set;

@EqualsAndHashCode(callSuper = false)
@Value
public class RemoveRedundantNullCheckBeforeInstanceof extends Recipe {

    @Override
    public String getDisplayName() {
        return "Remove redundant null checks before instanceof";
    }

    @Override
    public String getDescription() {
        return "Removes redundant null checks before instanceof operations since instanceof returns false for null.";
    }

    @Override
    public Set<String> getTags() {
        return Collections.singleton("RSPEC-S1697");
    }

    @Override
    public Duration getEstimatedEffortPerOccurrence() {
        return Duration.ofMinutes(1);
    }

    @Override
    public TreeVisitor<?, ExecutionContext> getVisitor() {
        return new JavaVisitor<ExecutionContext>() {
            @Override
            public J visitBinary(J.Binary binary, ExecutionContext ctx) {
                J.Binary bi = (J.Binary) super.visitBinary(binary, ctx);

                if (bi.getOperator() == J.Binary.Type.And) {
                    // Try to simplify the expression by removing redundant null checks
                    Expression simplified = simplifyExpression(bi);
                    if (simplified != bi) {
                        return simplified;
                    }
                }

                return bi;
            }

            private Expression simplifyExpression(J.Binary binary) {
                if (binary.getOperator() != J.Binary.Type.And) {
                    return binary;
                }

                Expression left = binary.getLeft();
                Expression right = binary.getRight();

                // Recursively simplify left side if it's also an AND expression
                if (left instanceof J.Binary && ((J.Binary) left).getOperator() == J.Binary.Type.And) {
                    left = simplifyExpression((J.Binary) left);
                }

                // Recursively simplify right side if it's also an AND expression
                if (right instanceof J.Binary && ((J.Binary) right).getOperator() == J.Binary.Type.And) {
                    right = simplifyExpression((J.Binary) right);
                }

                // Check if we have a pattern: (expr != null) && (expr instanceof Type)
                if (left instanceof J.Binary && right instanceof J.InstanceOf) {
                    J.Binary nullCheck = (J.Binary) left;
                    J.InstanceOf instanceOf = (J.InstanceOf) right;

                    if (isRedundantNullCheck(nullCheck, instanceOf)) {
                        // Return just the instanceof check
                        return instanceOf.withPrefix(binary.getPrefix());
                    }
                }

                // After recursive simplification, check if we have:
                // (... && expr != null) && (expr instanceof Type)
                if (left instanceof J.Binary && ((J.Binary) left).getOperator() == J.Binary.Type.And &&
                        right instanceof J.InstanceOf) {
                    J.Binary leftBinary = (J.Binary) left;
                    J.InstanceOf instanceOf = (J.InstanceOf) right;

                    // Check if the rightmost part of left is a null check for the same expression
                    Expression rightmostOfLeft = getRightmost(leftBinary);
                    if (rightmostOfLeft instanceof J.Binary) {
                        J.Binary nullCheck = (J.Binary) rightmostOfLeft;
                        if (isRedundantNullCheck(nullCheck, instanceOf)) {
                            // Remove the null check from the left side
                            Expression newLeft = removeRightmost(leftBinary);
                            if (newLeft == null) {
                                // If removing rightmost leaves nothing, just return the instanceof
                                return instanceOf.withPrefix(binary.getPrefix());
                            } else {
                                // Otherwise, return newLeft && instanceof
                                return binary.withLeft(newLeft).withRight(instanceOf);
                            }
                        }
                    }
                }

                // If no simplification was made, but children were simplified, return updated binary
                if (left != binary.getLeft() || right != binary.getRight()) {
                    return binary.withLeft(left).withRight(right);
                }

                return binary;
            }

            private Expression getRightmost(J.Binary binary) {
                if (binary.getOperator() == J.Binary.Type.And && binary.getRight() instanceof J.Binary &&
                        ((J.Binary) binary.getRight()).getOperator() == J.Binary.Type.And) {
                    return getRightmost((J.Binary) binary.getRight());
                }
                return binary.getRight();
            }

            private Expression removeRightmost(J.Binary binary) {
                if (binary.getOperator() == J.Binary.Type.And && binary.getRight() instanceof J.Binary &&
                        ((J.Binary) binary.getRight()).getOperator() == J.Binary.Type.And) {
                    Expression newRight = removeRightmost((J.Binary) binary.getRight());
                    if (newRight == null) {
                        return binary.getLeft();
                    }
                    return binary.withRight(newRight);
                }
                // We're at the rightmost, so removing it means returning just the left
                return binary.getLeft();
            }

            private boolean isRedundantNullCheck(J.Binary nullCheck, J.InstanceOf instanceOf) {
                if (nullCheck.getOperator() == J.Binary.Type.NotEqual) {
                    if (J.Literal.isLiteralValue(nullCheck.getLeft(), null)) {
                        return SemanticallyEqual.areEqual(nullCheck.getRight(), instanceOf.getExpression());
                    }
                    if (J.Literal.isLiteralValue(nullCheck.getRight(), null)) {
                        return SemanticallyEqual.areEqual(nullCheck.getLeft(), instanceOf.getExpression());
                    }
                }
                return false;
            }
        };
    }
}
