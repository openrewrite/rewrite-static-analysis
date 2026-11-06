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
import org.jspecify.annotations.Nullable;
import org.openrewrite.Cursor;
import org.openrewrite.ExecutionContext;
import org.openrewrite.Preconditions;
import org.openrewrite.Recipe;
import org.openrewrite.TreeVisitor;
import org.openrewrite.internal.ListUtils;
import org.openrewrite.java.JavaIsoVisitor;
import org.openrewrite.java.JavaTemplate;
import org.openrewrite.java.MethodMatcher;
import org.openrewrite.java.search.UsesMethod;
import org.openrewrite.java.tree.Expression;
import org.openrewrite.java.tree.J;
import org.openrewrite.java.tree.JavaType;
import org.openrewrite.java.tree.TypeUtils;

import java.time.Duration;
import java.util.Set;

import static java.util.Collections.singleton;

public class BigDecimalRoundingConstantsToEnums extends Recipe {
    private static final MethodMatcher BIG_DECIMAL_DIVIDE = new MethodMatcher("java.math.BigDecimal divide(java.math.BigDecimal, int)");
    private static final MethodMatcher BIG_DECIMAL_DIVIDE_WITH_SCALE = new MethodMatcher("java.math.BigDecimal divide(java.math.BigDecimal, int, int)");
    private static final MethodMatcher BIG_DECIMAL_SET_SCALE = new MethodMatcher("java.math.BigDecimal setScale(int, int)");

    @Getter
    final String displayName = "`BigDecimal` rounding constants to `RoundingMode` enums";

    @Getter
    final String description = "Convert `BigDecimal` rounding constants to the equivalent " +
            "`RoundingMode` enum. The integer-based rounding constants on `BigDecimal` " +
            "are deprecated and lack type safety; the `RoundingMode` enum makes the " +
            "rounding behavior self-documenting and prevents invalid values.";

    @Getter
    final Set<String> tags = singleton("RSPEC-S1874");

    @Getter
    final Duration estimatedEffortPerOccurrence = Duration.ofMinutes(15);

    @Override
    public TreeVisitor<?, ExecutionContext> getVisitor() {
        return Preconditions.check(Preconditions.or(
                new UsesMethod<>(BIG_DECIMAL_DIVIDE),
                new UsesMethod<>(BIG_DECIMAL_DIVIDE_WITH_SCALE),
                new UsesMethod<>(BIG_DECIMAL_SET_SCALE)
        ), new JavaIsoVisitor<ExecutionContext>() {
            @Override
            public J.MethodInvocation visitMethodInvocation(J.MethodInvocation method, ExecutionContext ctx) {
                J.MethodInvocation m = super.visitMethodInvocation(method, ctx);
                if (BIG_DECIMAL_DIVIDE.matches(m) || BIG_DECIMAL_SET_SCALE.matches(m) ||
                    BIG_DECIMAL_DIVIDE_WITH_SCALE.matches(m)) {
                    Expression rounding = m.getArguments().get(m.getArguments().size() - 1);
                    String roundingModeEnum = getTemplateText(rounding);
                    if (roundingModeEnum != null) {
                        // Replacing arguments reparses the receiver as Java, which can contain Groovy or Kotlin syntax.
                        Expression replacement = JavaTemplate.builder(roundingModeEnum)
                                .imports("java.math.RoundingMode")
                                .build()
                                .apply(new Cursor(updateCursor(m), rounding), rounding.getCoordinates().replace());
                        m = m.withArguments(ListUtils.mapLast(m.getArguments(), arg -> replacement.withPrefix(arg.getPrefix())));
                        JavaType.Method methodType = m.getMethodType();
                        if (methodType != null) {
                            methodType = methodType.withParameterTypes(ListUtils.mapLast(methodType.getParameterTypes(), type -> replacement.getType()));
                            m = m.withMethodType(methodType).withName(m.getName().withType(methodType));
                        }
                        maybeAddImport("java.math.RoundingMode");
                    }
                }
                return m;
            }

            private @Nullable String getTemplateText(J elem) {
                String roundingName = null;
                if (elem instanceof J.FieldAccess && ((J.FieldAccess) elem).getTarget().getType() instanceof JavaType.FullyQualified) {
                    J.FieldAccess fa = (J.FieldAccess) elem;
                    if (fa.getTarget().getType() != null && TypeUtils.isOfClassType(fa.getTarget().getType(), "java.math.BigDecimal")) {
                        roundingName = fa.getSimpleName();
                    }
                } else if (elem instanceof J.Literal) {
                    roundingName = ((J.Literal) elem).getValueSource();
                }
                if (roundingName != null) {
                    switch (roundingName) {
                        case "ROUND_UP":
                        case "0":
                            return "RoundingMode.UP";
                        case "ROUND_DOWN":
                        case "1":
                            return "RoundingMode.DOWN";
                        case "ROUND_CEILING":
                        case "2":
                            return "RoundingMode.CEILING";
                        case "ROUND_FLOOR":
                        case "3":
                            return "RoundingMode.FLOOR";
                        case "ROUND_HALF_UP":
                        case "4":
                            return "RoundingMode.HALF_UP";
                        case "ROUND_HALF_DOWN":
                        case "5":
                            return "RoundingMode.HALF_DOWN";
                        case "ROUND_HALF_EVEN":
                        case "6":
                            return "RoundingMode.HALF_EVEN";
                        case "ROUND_UNNECESSARY":
                        case "7":
                            return "RoundingMode.UNNECESSARY";
                    }
                }
                return null;
            }
        });
    }
}
