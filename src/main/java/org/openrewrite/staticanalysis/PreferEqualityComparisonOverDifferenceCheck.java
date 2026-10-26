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

import lombok.Getter;
import org.openrewrite.ExecutionContext;
import org.openrewrite.Recipe;
import org.openrewrite.java.JavaIsoVisitor;
import org.openrewrite.java.tree.Expression;
import org.openrewrite.java.tree.J;

public class PreferEqualityComparisonOverDifferenceCheck extends Recipe {

    @Getter
    final String displayName = "Prefer direct comparison of numbers";

    @Getter
    final String description = "Replace `a - b == 0` with `a == b`, `a - b != 0` with `a != b`, `a - b < 0` with `a < b`, " +
            "and similar transformations for all comparison operators to improve readability and avoid overflow issues.";

    @Override
    public JavaIsoVisitor<ExecutionContext> getVisitor() {
        return new JavaIsoVisitor<ExecutionContext>() {

            @Override
            public J.Binary visitBinary(J.Binary binary, ExecutionContext ctx) {
                binary = super.visitBinary(binary, ctx);

                J.Binary.Type operator = binary.getOperator();
                if (operator != J.Binary.Type.Equal &&
                        operator != J.Binary.Type.NotEqual &&
                        operator != J.Binary.Type.LessThan &&
                        operator != J.Binary.Type.LessThanOrEqual &&
                        operator != J.Binary.Type.GreaterThan &&
                        operator != J.Binary.Type.GreaterThanOrEqual) {
                    return binary;
                }

                Expression left = binary.getLeft().unwrap();
                Expression right = binary.getRight().unwrap();
                if (left instanceof J.Binary && ((J.Binary) left).getOperator() == J.Binary.Type.Subtraction &&
                        right instanceof J.Literal && ((J.Literal) right).getValue() instanceof Number &&
                        ((Number) ((J.Literal) right).getValue()).doubleValue() == 0.0) {
                    J.Binary subtraction = (J.Binary) left;
                    return maybeAutoFormat(binary, binary.withLeft(subtraction.getLeft()).withRight(subtraction.getRight()), ctx);
                }
                return binary;
            }
        };
    }
}
