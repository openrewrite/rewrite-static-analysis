/*
 * Copyright 2024 the original author or authors.
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

import org.junit.jupiter.api.Test;
import org.openrewrite.DocumentExample;
import org.openrewrite.test.RecipeSpec;
import org.openrewrite.test.RewriteTest;

import static org.openrewrite.java.Assertions.java;

@SuppressWarnings("UnpredictableBigDecimalConstructorCall")
class BigDecimalDoubleConstructorTest implements RewriteTest {

    @Override
    public void defaults(RecipeSpec spec) {
        spec.recipe(new BigDecimalDoubleConstructorRecipe());
    }

    @DocumentExample
    @Test
    void bigDecimalDoubleConstructor() {
        rewriteRun(
          java(
            """
            import java.math.BigDecimal;
            class Test {
                void test(double d) {
                    BigDecimal bd = new BigDecimal(1.0);
                    BigDecimal bd2 = new BigDecimal(d);
                }
            }
            """,
            """
            import java.math.BigDecimal;
            class Test {
                void test(double d) {
                    BigDecimal bd = BigDecimal.valueOf(1.0);
                    BigDecimal bd2 = BigDecimal.valueOf(d);
                }
            }
            """
          )
        );
    }
}
