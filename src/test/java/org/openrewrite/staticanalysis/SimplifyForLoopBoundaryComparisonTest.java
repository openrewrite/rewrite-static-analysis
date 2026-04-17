/*
 * Copyright 2026 the original author or authors.
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

import org.junit.jupiter.api.Test;
import org.openrewrite.DocumentExample;
import org.openrewrite.Issue;
import org.openrewrite.test.RecipeSpec;
import org.openrewrite.test.RewriteTest;

import static org.openrewrite.java.Assertions.java;

@SuppressWarnings({"ConstantConditions", "ConditionCoveredByFurtherCondition"})
class SimplifyForLoopBoundaryComparisonTest implements RewriteTest {

    @Override
    public void defaults(RecipeSpec spec) {
        spec.recipe(new SimplifyForLoopBoundaryComparison());
    }

    @DocumentExample
    @Issue("https://github.com/openrewrite/rewrite-static-analysis/issues/607")
    @Test
    void lessThanOrEqualWithSubtractionOnRight() {
        rewriteRun(
          //language=java
          java(
            """
            class Test {
                void test(int n) {
                    for (int i = 0; i <= n - 1; i++) {
                    }
                }
            }
            """,
            """
            class Test {
                void test(int n) {
                    for (int i = 0; i < n; i++) {
                    }
                }
            }
            """
          )
        );
    }

    @Test
    void lessThanOrEqualWithSubtractionOfTwo() {
        rewriteRun(
          //language=java
          java(
            """
            class Test {
                void test(int n) {
                    for (int i = 0; i <= n - 2; i++) {
                    }
                }
            }
            """,
            """
            class Test {
                void test(int n) {
                    for (int i = 0; i < n - 1; i++) {
                    }
                }
            }
            """
          )
        );
    }

    @Test
    void lessThanOrEqualWithAdditionOnRight() {
        rewriteRun(
          //language=java
          java(
            """
            class Test {
                void test(int n) {
                    for (int i = 0; i <= n + 1; i++) {
                    }
                }
            }
            """,
            """
            class Test {
                void test(int n) {
                    for (int i = 0; i < n + 2; i++) {
                    }
                }
            }
            """
          )
        );
    }

    @Test
    void additionOnLeft() {
        rewriteRun(
          //language=java
          java(
            """
            class Test {
                void test(int n) {
                    for (int i = 0; i + 1 <= n; i++) {
                    }
                }
            }
            """,
            """
            class Test {
                void test(int n) {
                    for (int i = 0; i < n; i++) {
                    }
                }
            }
            """
          )
        );
    }

    @Test
    void additionOnLeftWithTwo() {
        rewriteRun(
          //language=java
          java(
            """
            class Test {
                void test(int n) {
                    for (int i = 0; i + 3 <= n; i++) {
                    }
                }
            }
            """,
            """
            class Test {
                void test(int n) {
                    for (int i = 0; i + 2 < n; i++) {
                    }
                }
            }
            """
          )
        );
    }

    @Test
    void subtractionOnLeft() {
        rewriteRun(
          //language=java
          java(
            """
            class Test {
                void test(int n) {
                    for (int i = 0; i - 1 <= n; i++) {
                    }
                }
            }
            """,
            """
            class Test {
                void test(int n) {
                    for (int i = 0; i - 1 < n + 1; i++) {
                    }
                }
            }
            """
          )
        );
    }

    @Test
    void noArithmetic() {
        rewriteRun(
          //language=java
          java(
            """
            class Test {
                void test(int n) {
                    for (int i = 0; i <= n; i++) {
                    }
                }
            }
            """,
            """
            class Test {
                void test(int n) {
                    for (int i = 0; i < n + 1; i++) {
                    }
                }
            }
            """
          )
        );
    }

    @Test
    void parenthesizedSubtraction() {
        rewriteRun(
          //language=java
          java(
            """
            class Test {
                void test(int n) {
                    for (int i = 0; i <= (n - 1); i++) {
                    }
                }
            }
            """,
            """
            class Test {
                void test(int n) {
                    for (int i = 0; i < n; i++) {
                    }
                }
            }
            """
          )
        );
    }

    @Test
    void longType() {
        rewriteRun(
          //language=java
          java(
            """
            class Test {
                void test(long n) {
                    for (long i = 0; i <= n - 1L; i++) {
                    }
                }
            }
            """,
            """
            class Test {
                void test(long n) {
                    for (long i = 0; i < n; i++) {
                    }
                }
            }
            """
          )
        );
    }

    @Test
    void arrayLengthOperand() {
        rewriteRun(
          //language=java
          java(
            """
            class Test {
                void test(int[] arr) {
                    for (int i = 0; i <= arr.length - 1; i++) {
                    }
                }
            }
            """,
            """
            class Test {
                void test(int[] arr) {
                    for (int i = 0; i < arr.length; i++) {
                    }
                }
            }
            """
          )
        );
    }

    @Test
    void additionWithOneOnLeftOfSum() {
        rewriteRun(
          //language=java
          java(
            """
            class Test {
                void test(int n) {
                    for (int i = 0; 1 + i <= n; i++) {
                    }
                }
            }
            """,
            """
            class Test {
                void test(int n) {
                    for (int i = 0; i < n; i++) {
                    }
                }
            }
            """
          )
        );
    }

    @Test
    void doNotChangeOutsideForLoop() {
        rewriteRun(
          //language=java
          java(
            """
            class Test {
                boolean test(int a, int b) {
                    return a <= b - 1;
                }
            }
            """
          )
        );
    }

    @Test
    void doNotChangeGreaterThanOrEqual() {
        rewriteRun(
          //language=java
          java(
            """
            class Test {
                void test(int n) {
                    for (int i = n; i >= 0 + 1; i--) {
                    }
                }
            }
            """
          )
        );
    }

    @Test
    void doNotChangeDoubleType() {
        rewriteRun(
          //language=java
          java(
            """
            class Test {
                void test(double n) {
                    for (int i = 0; i <= n - 1; i++) {
                    }
                }
            }
            """
          )
        );
    }

    @Test
    void doNotChangeLessThan() {
        rewriteRun(
          //language=java
          java(
            """
            class Test {
                void test(int n) {
                    for (int i = 0; i < n - 1; i++) {
                    }
                }
            }
            """
          )
        );
    }
}
