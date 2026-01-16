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

import lombok.Getter;
import org.openrewrite.ExecutionContext;
import org.openrewrite.Preconditions;
import org.openrewrite.Recipe;
import org.openrewrite.TreeVisitor;
import org.openrewrite.java.JavaTemplate;
import org.openrewrite.java.JavaVisitor;
import org.openrewrite.java.MethodMatcher;
import org.openrewrite.java.search.UsesMethod;
import org.openrewrite.java.tree.Expression;
import org.openrewrite.java.tree.J;
import org.openrewrite.java.tree.JavaCoordinates;

import java.time.Duration;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.Objects;
import java.util.Set;

public class IsEmptyCallOnCollections extends Recipe {
    private static final MethodMatcher COLLECTION_SIZE = new MethodMatcher("java.util.Collection size()", true);

    @Getter
    final String displayName = "Use `Collection#isEmpty()` instead of comparing `size()`";

    @Getter
    final String description = "Also check for _not_ `isEmpty()` when testing for not equal to zero size.";

    @Getter
    final Set<String> tags = new LinkedHashSet<>(Arrays.asList("RSPEC-S1155", "RSPEC-S3981"));

    @Override
    public Duration getEstimatedEffortPerOccurrence() {
        return Duration.ofMinutes(2);
    }

    @Override
    public TreeVisitor<?, ExecutionContext> getVisitor() {
        return Preconditions.check(new UsesMethod<>(COLLECTION_SIZE), new JavaVisitor<ExecutionContext>() {

            @Override
            public J visitBinary(J.Binary binary, ExecutionContext ctx) {
                if (isZero(binary.getLeft()) || isZero(binary.getRight())) {
                    boolean zeroRight = isZero(binary.getRight());
                    if (binary.getOperator() == J.Binary.Type.Equal || binary.getOperator() == J.Binary.Type.NotEqual ||
                        zeroRight && binary.getOperator() == J.Binary.Type.GreaterThan ||
                        !zeroRight && binary.getOperator() == J.Binary.Type.LessThan) {
                        J maybeSizeCall = zeroRight ? binary.getLeft() : binary.getRight();
                        if (maybeSizeCall instanceof J.MethodInvocation) {
                            J.MethodInvocation maybeSizeCallMethod = (J.MethodInvocation) maybeSizeCall;
                            if (COLLECTION_SIZE.matches(maybeSizeCallMethod) && maybeSizeCallMethod.getMethodType() != null) {
                                return newIsEmptyCall(maybeSizeCallMethod, binary.getCoordinates().replace(), binary.getOperator() != J.Binary.Type.Equal)
                                        .withPrefix(binary.getPrefix());
                            }
                        }
                    }
                } else if (isOne(binary.getLeft()) || isOne(binary.getRight())) {
                    boolean oneRight = isOne(binary.getRight());
                    if ((oneRight && binary.getOperator() == J.Binary.Type.LessThan) ||
                        (!oneRight && binary.getOperator() == J.Binary.Type.GreaterThan) ||
                        (oneRight && binary.getOperator() == J.Binary.Type.GreaterThanOrEqual) ||
                        (!oneRight && binary.getOperator() == J.Binary.Type.LessThanOrEqual)) {
                        J maybeSizeCall = oneRight ? binary.getLeft() : binary.getRight();
                        if (maybeSizeCall instanceof J.MethodInvocation) {
                            J.MethodInvocation maybeSizeCallMethod = (J.MethodInvocation) maybeSizeCall;
                            if (COLLECTION_SIZE.matches(maybeSizeCallMethod) && maybeSizeCallMethod.getMethodType() != null) {
                                return newIsEmptyCall(maybeSizeCallMethod, binary.getCoordinates().replace(),
                                        binary.getOperator() == J.Binary.Type.GreaterThanOrEqual || binary.getOperator() == J.Binary.Type.LessThanOrEqual)
                                        .withPrefix(binary.getPrefix());
                            }
                        }
                    }
                }
                return super.visitBinary(binary, ctx);
            }

            private J newIsEmptyCall(J.MethodInvocation method, JavaCoordinates coordinates, boolean negate) {
                JavaTemplate isEmpty = JavaTemplate.builder("#{}#{any(java.util.Collection)}.isEmpty()").build();
                if (method.getSelect() == null) {
                    assert method.getMethodType() != null;
                    J.Identifier this_ = JavaElementFactory.newThis(method.getMethodType().getDeclaringType());
                    J isEmptyCall = isEmpty.apply(getCursor(), coordinates, negate ? "!" : "", this_);
                    if (negate) {
                        return ((J.Unary) isEmptyCall).withExpression(((J.MethodInvocation) ((J.Unary) isEmptyCall).getExpression()).withSelect(null));
                    }
                    return ((J.MethodInvocation) isEmptyCall).withSelect(null);
                }
                return isEmpty.apply(getCursor(), coordinates, negate ? "!" : "", method.getSelect());
            }
        });
    }

    private static boolean isZero(Expression expression) {
        return expression instanceof J.Literal && Objects.equals(0, ((J.Literal) expression).getValue());
    }

    private static boolean isOne(Expression expression) {
        return expression instanceof J.Literal && Objects.equals(1, ((J.Literal) expression).getValue());
    }

}
