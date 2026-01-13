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
import org.openrewrite.Preconditions;
import org.openrewrite.Recipe;
import org.openrewrite.TreeVisitor;
import org.openrewrite.java.JavaVisitor;
import org.openrewrite.java.search.SemanticallyEqual;
import org.openrewrite.java.tree.Expression;
import org.openrewrite.java.tree.J;
import org.openrewrite.staticanalysis.kotlin.KotlinFileChecker;

import java.time.Duration;
import java.util.Set;

import static java.util.Collections.singleton;

@EqualsAndHashCode(callSuper = false)
@Value
public class RemoveRedundantNullCheckBeforeInstanceof extends Recipe {

    String displayName = "Remove redundant null checks before instanceof";

    String description = "Removes redundant null checks before instanceof operations since instanceof returns false for null.";

    @Override
    public Set<String> getTags() {
        return singleton("RSPEC-S1697");
    }

    @Override
    public Duration getEstimatedEffortPerOccurrence() {
        return Duration.ofMinutes(1);
    }

    @Override
    public TreeVisitor<?, ExecutionContext> getVisitor() {
        return Preconditions.check(Preconditions.not(new KotlinFileChecker<>()), new JavaVisitor<ExecutionContext>() {
            @Override
            public J visitBinary(J.Binary binary, ExecutionContext ctx) {
                J.Binary bi = (J.Binary) super.visitBinary(binary, ctx);
                if (bi.getOperator() != J.Binary.Type.And) {
                    return bi;
                }

                Expression left = bi.getLeft();
                Expression right = bi.getRight();

                // Check if we have a pattern: (expr != null) && (expr instanceof Type)
                if (left instanceof J.Binary && right instanceof J.InstanceOf) {
                    J.Binary nullCheck = (J.Binary) left;
                    J.InstanceOf instanceOf = (J.InstanceOf) right;

                    if (isRedundantNullCheck(nullCheck, instanceOf)) {
                        // Return just the instanceof check
                        return instanceOf.withPrefix(bi.getPrefix());
                    }
                }

                // Check if we have chained patterns like:
                // (... && expr != null) && (expr instanceof Type)
                if (left instanceof J.Binary && ((J.Binary) left).getOperator() == J.Binary.Type.And &&
                        right instanceof J.InstanceOf) {
                    J.Binary leftBinary = (J.Binary) left;
                    J.InstanceOf instanceOf = (J.InstanceOf) right;

                    // Check if the rightmost part of left is a null check for the same expression
                    Expression rightmostOfLeft = leftBinary.getRight();
                    if (rightmostOfLeft instanceof J.Binary &&
                            isRedundantNullCheck((J.Binary) rightmostOfLeft, instanceOf)) {
                        // Remove the null check from the left side
                        return bi.withLeft(leftBinary.getLeft()).withRight(instanceOf);
                    }
                }

                return bi;
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
        });
    }
}
