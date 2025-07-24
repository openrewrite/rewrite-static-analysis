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

import org.junit.jupiter.api.Test;
import org.openrewrite.DocumentExample;
import org.openrewrite.test.RecipeSpec;
import org.openrewrite.test.RewriteTest;

import static org.openrewrite.java.Assertions.java;

class SimplifyBooleanExpressionWithDeMorganTest implements RewriteTest {
    @Override
    public void defaults(RecipeSpec spec) {
        spec.recipe(new SimplifyBooleanExpressionWithDeMorgan());
    }

    @DocumentExample
    @Test
    void transformNegatedAndToOr() {
        rewriteRun(
          //language=java
          java(
            """
            class Test {
                void test(boolean a, boolean b) {
                    if (!(a && !b)) {
                        System.out.println("Not both");
                    }
                }
            }
            """,
            """
            class Test {
                void test(boolean a, boolean b) {
                    if (!a || b) {
                        System.out.println("Not both");
                    }
                }
            }
            """
          )
        );
    }

    @Test
    void transformNegatedOrToAnd() {
        rewriteRun(
          //language=java
          java(
            """
            class Test {
                void test(boolean a, boolean b) {
                    if (!(!a || /*Bee Bee Bee*/ b)) {
                        System.out.println("Neither");
                    }
                }
            }
            """,
            """
            class Test {
                void test(boolean a, boolean b) {
                    if (a && /*Bee Bee Bee*/ !b) {
                        System.out.println("Neither");
                    }
                }
            }
            """
          )
        );
    }

    @Test
    void transformMethodCallExpressions() {
        rewriteRun(
          //language=java
          java(
            """
            class Test {
                void test(String s1, String s2) {
                    if (!(s1.isEmpty() && s2.isEmpty())) {
                        System.out.println("At least one not empty");
                    }
                }
            }
            """,
            """
            class Test {
                void test(String s1, String s2) {
                    if (!s1.isEmpty() || !s2.isEmpty()) {
                        System.out.println("At least one not empty");
                    }
                }
            }
            """
          )
        );
    }

    @Test
    void noChangeWhenNoParentheses() {
        rewriteRun(
          //language=java
          java(
            """
            class Test {
                void test(boolean a, boolean b) {
                    if (!a && b) {
                        System.out.println("Already simplified");
                    }
                }
            }
            """
          )
        );
    }

    @Test
    void noChangeWhenNotNegatedBinaryExpression() {
        rewriteRun(
          //language=java
          java(
            """
            class Test {
                void test(boolean a, boolean b) {
                    if ((a && b)) {
                        System.out.println("No negation");
                    }
                }
            }
            """
          )
        );
    }

    @Test
    void noChangeWhenNegatingComparison() {
        rewriteRun(
          //language=java
          java(
            """
            class Test {
                void test(int x, int y) {
                    if (!(x > y)) {
                        System.out.println("Not greater");
                    }
                }
            }
            """
          )
        );
    }

    @Test
    void triple() {
        rewriteRun(
          //language=java
          java(
            """
            class Test {
                void test(boolean a, boolean b, boolean c) {
                    if (!(a || !b || c)) {
                        System.out.println("None are true");
                    }
                }
            }
            """,
            """
            class Test {
                void test(boolean a, boolean b, boolean c) {
                    if (!a && b && !c) {
                        System.out.println("None are true");
                    }
                }
            }
            """
          )
        );
    }

    @Test
    void quadruple() {
        rewriteRun(
          //language=java
          java(
            """
            class Test {
                static void test(boolean a, boolean b, boolean c, boolean d) {
                    return !(a && !b && c && !d);
                }
            }
            """,
            """
            class Test {
                static void test(boolean a, boolean b, boolean c, boolean d) {
                    return !a || b || !c || d;
                }
            }
            """
          )
        );
    }

    @Test
    void nested() {
        rewriteRun(
          //language=java
          java(
            """
            class Test {
                void test(boolean w, boolean x, boolean y, boolean z) {
                    boolean result = !((w && x) && !(z || y));
                }
            }
            """,
            """
            class Test {
                void test(boolean w, boolean x, boolean y, boolean z) {
                    boolean result = (!w || !x) || (z || y);
                }
            }
            """
          )
        );
    }

    @Test
    void noChangeWhenMixedOperators() {
        rewriteRun(
          //language=java
          java(
            """
            class Test {
                void test(boolean a, boolean b, boolean c) {
                    if (!(a && !b || c)) {
                        System.out.println("Mixed operators");
                    }
                }
            }
            """
          )
        );
    }

    @Test
    void deMorganWithinNonBoolean() {
        rewriteRun(
          //language=java
          java(
            """
            class Test {
                void test(boolean a, boolean b, boolean c, boolean d) {
                    if ((!(a && !b)) == (!(!c || !d))) {
                        System.out.println("Complex boolean comparison");
                    }
                }
            }
            """,
            """
            class Test {
                void test(boolean a, boolean b, boolean c, boolean d) {
                    if ((!a || b) == (c && d)) {
                        System.out.println("Complex boolean comparison");
                    }
                }
            }
            """
          )
        );
    }
}
