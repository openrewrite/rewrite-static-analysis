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
import org.openrewrite.test.RecipeSpec;
import org.openrewrite.test.RewriteTest;

import static org.openrewrite.java.Assertions.java;

class PreferEqualityComparisonOverDifferenceCheckBoxedTest implements RewriteTest {
    @Override
    public void defaults(RecipeSpec spec) {
        spec.recipe(new PreferEqualityComparisonOverDifferenceCheck());
    }

    @Test
    void retainBoxedIntegerValueEquality() {
        rewriteRun(
          //language=java
          java(
            """
              @SuppressWarnings("removal")
              class A {
                  boolean equal(Integer a, Integer b) {
                      return a - b == 0;
                  }

                  boolean distinctInstancesOutsideCache() {
                      return equal(new Integer(1000), new Integer(1000));
                  }

                  boolean nullOperand() {
                      return equal(null, Integer.valueOf(1000));
                  }
              }
              """
          )
        );
    }

    @Test
    void retainBoxedIntegerValueInequality() {
        rewriteRun(
          //language=java
          java(
            """
              @SuppressWarnings("removal")
              class A {
                  boolean different(Integer a, Integer b) {
                      return a - b != 0;
                  }

                  boolean distinctInstancesOutsideCache() {
                      return different(new Integer(1000), new Integer(1000));
                  }

                  boolean nullOperand() {
                      return different(null, Integer.valueOf(1000));
                  }
              }
              """
          )
        );
    }

    @Test
    void retainOtherBoxedNumericEqualityAndInequality() {
        rewriteRun(
          //language=java
          java(
            """
              @SuppressWarnings("removal")
              class A {
                  boolean equal(Long a, Long b) {
                      return a - b == 0L;
                  }

                  boolean different(Long a, Long b) {
                      return a - b != 0L;
                  }

                  boolean equal(Float a, Float b) {
                      return a - b == 0.0f;
                  }

                  boolean different(Float a, Float b) {
                      return a - b != 0.0f;
                  }

                  boolean equal(Double a, Double b) {
                      return a - b == 0.0;
                  }

                  boolean different(Double a, Double b) {
                      return a - b != 0.0;
                  }

                  boolean[] distinctInstances() {
                      Long l1 = new Long(1000L);
                      Long l2 = new Long(1000L);
                      Float f1 = new Float(1000.0f);
                      Float f2 = new Float(1000.0f);
                      Double d1 = new Double(1000.0);
                      Double d2 = new Double(1000.0);
                      return new boolean[] {
                          equal(l1, l2), different(l1, l2),
                          equal(f1, f2), different(f1, f2),
                          equal(d1, d2), different(d1, d2)
                      };
                  }
              }
              """
          )
        );
    }

    @Test
    void retainMixedBoxedNumericEqualityAndInequality() {
        rewriteRun(
          //language=java
          java(
            """
              class A {
                  boolean equal(Integer a, Long b) {
                      return a - b == 0L;
                  }

                  boolean different(Long a, Integer b) {
                      return a - b != 0L;
                  }
              }
              """
          )
        );
    }

    @Test
    void convertPrimitiveIntegralEqualityAndInequality() {
        rewriteRun(
          //language=java
          java(
            """
              class A {
                  boolean equalInts(int a, int b) {
                      return a - b == 0;
                  }

                  boolean differentInts(int a, int b) {
                      return a - b != 0;
                  }

                  boolean equalLongs(long a, long b) {
                      return a - b == 0L;
                  }

                  boolean differentLongs(long a, long b) {
                      return a - b != 0L;
                  }
              }
              """,
            """
              class A {
                  boolean equalInts(int a, int b) {
                      return a == b;
                  }

                  boolean differentInts(int a, int b) {
                      return a != b;
                  }

                  boolean equalLongs(long a, long b) {
                      return a == b;
                  }

                  boolean differentLongs(long a, long b) {
                      return a != b;
                  }
              }
              """
          )
        );
    }

    @Test
    void convertSingleBoxedIntegralEqualityAndInequality() {
        rewriteRun(
          //language=java
          java(
            """
              class A {
                  boolean equalInts(Integer a, int b) {
                      return a - b == 0;
                  }

                  boolean differentInts(int a, Integer b) {
                      return a - b != 0;
                  }

                  boolean equalLongs(Long a, long b) {
                      return a - b == 0L;
                  }

                  boolean differentLongs(long a, Long b) {
                      return a - b != 0L;
                  }
              }
              """,
            """
              class A {
                  boolean equalInts(Integer a, int b) {
                      return a == b;
                  }

                  boolean differentInts(int a, Integer b) {
                      return a != b;
                  }

                  boolean equalLongs(Long a, long b) {
                      return a == b;
                  }

                  boolean differentLongs(long a, Long b) {
                      return a != b;
                  }
              }
              """
          )
        );
    }

    @Test
    void keepExistingBoxedRelationalConversionPolicy() {
        // The recipe intentionally avoids subtraction overflow for ordering comparisons.
        rewriteRun(
          //language=java
          java(
            """
              class A {
                  boolean below(Integer a, Integer b) {
                      return a - b < 0;
                  }

                  boolean atMost(Long a, Long b) {
                      return a - b <= 0L;
                  }

                  boolean above(Integer a, Integer b) {
                      return a - b > 0;
                  }

                  boolean atLeast(Long a, Long b) {
                      return a - b >= 0L;
                  }
              }
              """,
            """
              class A {
                  boolean below(Integer a, Integer b) {
                      return a < b;
                  }

                  boolean atMost(Long a, Long b) {
                      return a <= b;
                  }

                  boolean above(Integer a, Integer b) {
                      return a > b;
                  }

                  boolean atLeast(Long a, Long b) {
                      return a >= b;
                  }
              }
              """
          )
        );
    }
}
