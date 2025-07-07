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

import org.junit.jupiter.api.Test;
import org.openrewrite.DocumentExample;
import org.openrewrite.Issue;
import org.openrewrite.test.RecipeSpec;
import org.openrewrite.test.RewriteTest;

import static org.openrewrite.java.Assertions.java;

class SimplifyDurationCreationUnitsTest implements RewriteTest {

    @Override
    public void defaults(RecipeSpec spec) {
        spec.recipe(new SimplifyDurationCreationUnits());
    }

    @DocumentExample
    @Test
    void simplifyLiteralMillisToSeconds() {
        rewriteRun(
          //language=java
          java(
              """
              import java.time.Duration;

              public class Test {
                  static Duration duration = Duration.ofMillis(5000);
              }
              """,
              """
              import java.time.Duration;

              public class Test {
                  static Duration duration = Duration.ofSeconds(5);
              }
              """
          )
        );
    }

    @Test
    void simplifyLiteralMillisToMinutes() {
        rewriteRun(
          //language=java
          java(
              """
              import java.time.Duration;

              public class Test {
                  static Duration duration = Duration.ofMillis(300000);
              }
              """,
              """
              import java.time.Duration;

              public class Test {
                  static Duration duration = Duration.ofMinutes(5);
              }
              """
          )
        );
    }

    @Test
    void simplifyLiteralMillisToHours() {
        rewriteRun(
          //language=java
          java(
              """
              import java.time.Duration;

              public class Test {
                  static Duration duration = Duration.ofMillis(18000000);
              }
              """,
              """
              import java.time.Duration;

              public class Test {
                  static Duration duration = Duration.ofHours(5);
              }
              """
          )
        );
    }

    @Test
    void simplifyLiteralMillisToDays() {
        rewriteRun(
          //language=java
          java(
              """
              import java.time.Duration;

              public class Test {
                  static Duration duration = Duration.ofMillis(432000000);
              }
              """,
              """
              import java.time.Duration;

              public class Test {
                  static Duration duration = Duration.ofDays(5);
              }
              """
          )
        );
    }

    @Test
    void simplifyLiteralMillisProduct() {
        rewriteRun(
          //language=java
          java(
              """
              import java.time.Duration;

              public class Test {
                  static Duration duration = Duration.ofMillis(5 * 1000);
              }
              """,
              """
              import java.time.Duration;

              public class Test {
                  static Duration duration = Duration.ofSeconds(5);
              }
              """
          )
        );
    }

    @Test
    void simplifyLiteralMillisProductWithWeirdFactors() {
        rewriteRun(
          //language=java
          java(
              """
              import java.time.Duration;

              public class Test {
                  static Duration duration = Duration.ofMillis(5 * 5000);
              }
              """,
              """
              import java.time.Duration;

              public class Test {
                  static Duration duration = Duration.ofSeconds(25);
              }
              """
          )
        );
    }

    @Test
    void simplifyLiteralSeconds() {
        rewriteRun(
          //language=java
          java(
              """
              import java.time.Duration;

              public class Test {
                  static Duration duration = Duration.ofSeconds(300);
              }
              """,
              """
              import java.time.Duration;

              public class Test {
                  static Duration duration = Duration.ofMinutes(5);
              }
              """
          )
        );
    }

    @Test
    void simplifyLiteralMinutes() {
        rewriteRun(
          //language=java
          java(
              """
              import java.time.Duration;

              public class Test {
                  static Duration duration = Duration.ofMinutes(120);
              }
              """,
              """
              import java.time.Duration;

              public class Test {
                  static Duration duration = Duration.ofHours(2);
              }
              """
          )
        );
    }

    @Test
    void simplifyLiteralHours() {
        rewriteRun(
          //language=java
          java(
              """
              import java.time.Duration;

              public class Test {
                  static Duration duration = Duration.ofHours(48);
              }
              """,
              """
              import java.time.Duration;

              public class Test {
                  static Duration duration = Duration.ofDays(2);
              }
              """
          )
        );
    }

    @Test
    void doesNotChangeLiteralDays() {
        rewriteRun(
          //language=java
          java(
              """
              import java.time.Duration;

              public class Test {
                  static Duration duration = Duration.ofDays(14);
              }
              """
          )
        );
    }

    @Test
    void doNotChangeSubSecondMillis() {
        rewriteRun(
          //language=java
          java(
              """
              import java.time.Duration;

              public class Test {
                  static Duration duration = Duration.ofMillis(5500);
              }
              """
          )
        );
    }

    /**
     * This is not a necessary constraint but should be considered carefully
     * before removing: when an operation other than multiplication is present
     * in one of these method invocations, what does it mean to the user?
     *
     * <p>
     * It's possible that simplifying units could obfuscate meaning.
     * It may be better to distribute the multiplicative factor change, rather
     * than simplify the expression; e.g. preferring
     *
     * <p>
     *     `Duration.ofMillis(1000 + 1000)`  --> `Duration.ofSeconds(1 + 1)`
     *
     * <p>
     * rather than
     *
     * <p>
     *     `Duration.ofMillis(1000 + 1000)`  --> `Duration.ofSeconds(2)`
     */
    @Test
    void doesNotChangeNonMultiplicationArithmetic() {
        rewriteRun(
          //language=java
          java(
            """
            import java.time.Duration;

            public class Test {
                static Duration durationPlus = Duration.ofMillis(1000 + 1000);
                static Duration durationMinus = Duration.ofMillis(2000 - 1000);
                static Duration durationDivide = Duration.ofMillis(5000 / 5);
            }
            """
          )
        );
    }

    /**
     * This is not a necessary constraint; the recipe could simplify when there's a
     * constant multiplicative factor present (e.g. `Duration.ofSeconds(seconds)` here).
     * <p>
     * This test just documents the current behavior.
     */
    @SuppressWarnings("IntegerMultiplicationImplicitCastToLong")
    @Test
    void doesNotChangeNonConstantUnitCount() {
        rewriteRun(
          //language=java
          java(
              """
              import java.time.Duration;

              public class Test {
                  int seconds = 30;
                  static Duration duration = Duration.ofMillis(1000 * seconds);
              }
              """
          )
        );
    }

    @Issue("https://github.com/moderneinc/support-public/issues/30")
    @Test
    void doesNotChangeZeroConstant() {
        rewriteRun(
          //language=java
          java(
              """
              import java.time.Duration;

              public class Test {
                  static Duration duration1 = Duration.ofMillis(0);
                  static Duration duration2 = Duration.ofSeconds(0);
                  static Duration duration3 = Duration.ofDays(0);
              }
              """
          )
        );
    }
}
