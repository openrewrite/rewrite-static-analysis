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
import org.junit.jupiter.api.Test;
import org.openrewrite.DocumentExample;
import org.openrewrite.java.JavaParser;
import org.openrewrite.test.RecipeSpec;
import org.openrewrite.test.RewriteTest;
import org.openrewrite.test.TypeValidation;

import static org.openrewrite.java.Assertions.java;

public class ReplaceMagicNumbersWithConstantsTest implements RewriteTest {
    @Override
    public void defaults(RecipeSpec spec) {
        spec.recipe(new ReplaceMagicNumbersWithConstants());
    }

    @DocumentExample
    @Test
    void assignMagicNumbersToConstantsTest() {
        rewriteRun(
          spec -> spec
            .typeValidationOptions(TypeValidation.none()),
          java(
"""
public class OrderProcessor {
    private static final double myVariable = 99.99;
    public double calculateShippingCost(double orderTotal) {
        int localVar = 5;
        orderTotal = localVar + 10;
        if (orderTotal < 51.0) {
            return 7.99;
        } else {
            return 0.0;
        }
    }
}
""",
            """
public class OrderProcessor {
    private static final int INT_10 = 10;
    private static final double DOUBLE_51_0 = 51.0;
    private static final double DOUBLE_7_99 = 7.99;
    private static final double myVariable = 99.99;
    public double calculateShippingCost(double orderTotal) {
        int localVar = 5;
        orderTotal = localVar + INT_10;
        if (orderTotal < DOUBLE_51_0) {
            return DOUBLE_7_99;
        } else {
            return 0.0;
        }
    }
}
          """
          )
        );
    }

    @DocumentExample
    @Test
    void assignMagicNumbersToConstantsBasicTest() {
        rewriteRun(
          spec -> spec
            .typeValidationOptions(TypeValidation.none()),
          java(
            """
            public class OrderProcessor {
                public double calculateShippingCost(double orderTotal) {
                    if (orderTotal < 51.0) {
                        return 7.99;
                    }
                }
            }
            """,
            """
public class OrderProcessor {
    private static final double DOUBLE_51_0 = 51.0;
    private static final double DOUBLE_7_99 = 7.99;
    public double calculateShippingCost(double orderTotal) {
        if (orderTotal < DOUBLE_51_0) {
            return DOUBLE_7_99;
        }
    }
}
          """
          )
        );
    }
    @DocumentExample
    @Test
    void assignMagicNumbersToConstantsM1_0_1_AreIgnoredTest() {
        rewriteRun(
          spec -> spec
            .typeValidationOptions(TypeValidation.none()),
          java(
            """
            public class OrderProcessor {
                private static final double myVariable = 99.99;
                public double calculateShippingCost(double orderTotal) {
                    int localVar0 = 0;
                    orderTotal = localVar0 - 1;
                    orderTotal = localVar0 + 0;
                    orderTotal = localVar0 + 1;
                    if (orderTotal < 51.0) {
                        return 7.99;
                    } else {
                        return 0.0;
                    }
                }
            }
            """,
            """
public class OrderProcessor {
    private static final double DOUBLE_51_0 = 51.0;
    private static final double DOUBLE_7_99 = 7.99;
    private static final double myVariable = 99.99;
    public double calculateShippingCost(double orderTotal) {
        int localVar0 = 0;
        orderTotal = localVar0 - 1;
        orderTotal = localVar0 + 0;
        orderTotal = localVar0 + 1;
        if (orderTotal < DOUBLE_51_0) {
            return DOUBLE_7_99;
        } else {
            return 0.0;
        }
    }
}
          """
          )
        );
    }
}
