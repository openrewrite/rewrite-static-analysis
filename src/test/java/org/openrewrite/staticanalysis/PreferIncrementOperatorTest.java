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
import org.junitpioneer.jupiter.ExpectedToFail;
import org.openrewrite.DocumentExample;
import org.openrewrite.test.RecipeSpec;
import org.openrewrite.test.RewriteTest;

import static org.openrewrite.java.Assertions.java;

@SuppressWarnings({"ConstantConditions", "UnusedAssignment"})
class PreferIncrementOperatorTest implements RewriteTest {

    @Override
    public void defaults(RecipeSpec spec) {
        spec.recipe(new PreferIncrementOperator());
    }

    @DocumentExample
    @Test
    void incrementByOne() {
        rewriteRun(
          java(
            """
              class Test {
                  void test(int i) {
                      i = i + 1;
                  }
              }
              """,
            """
              class Test {
                  void test(int i) {
                      i++;
                  }
              }
              """
          )
        );
    }

    @Test
    void decrementByOne() {
        rewriteRun(
          java(
            """
              class Test {
                  void test(int i) {
                      // decrement i
                      i = i - 1;
                  }
              }
              """,
            """
              class Test {
                  void test(int i) {
                      // decrement i
                      i--;
                  }
              }
              """
          )
        );
    }

    @Test
    void incrementInLoop() {
        rewriteRun(
          java(
            """
              class Test {
                  void test() {
                      for (int i = 0; i < 10; ) {
                          i = i + 1;
                      }
                  }
              }
              """,
            """
              class Test {
                  void test() {
                      for (int i = 0; i < 10; ) {
                          i++;
                      }
                  }
              }
              """
          )
        );
    }

    @Test
    void incrementByTwo() {
        rewriteRun(
          java(
            """
              class Test {
                  void test(int i) {
                      i = i + 2;
                  }
              }
              """,
            """
              class Test {
                  void test(int i) {
                      i += 2;
                  }
              }
              """
          )
        );
    }

    @Test
    void doNotChangeDifferentVariable() {
        rewriteRun(
          java(
            """
              class Test {
                  int i, j = 0;
                  Test() {
                      i = j + 1;
                  }
              }
              """
          )
        );
    }

    @Test
    void doNotChangeIfOrderIsReversed() {
        // No strong feelings here, just documenting the current behavior of not applying the change here.
        rewriteRun(
          java(
            """
              class Test {
                  void test(int i) {
                      i = 1 + i;
                  }
              }
              """
          )
        );
    }

    @Test
    void compoundAssignmentForVariousValues() {
        rewriteRun(
          java(
            """
              class Test {
                  void test(int i, int j, long l, long n) {
                      i = i + 1;
                      j = j + 5;
                      i = i + 100;
                      l = l + 2L;
                      n = n - 2;
                  }
              }
              """,
            """
              class Test {
                  void test(int i, int j, long l, long n) {
                      i++;
                      j += 5;
                      i += 100;
                      l += 2L;
                      n -= 2;
                  }
              }
              """
          )
        );
    }

    @Test
    void doNotChangeAssignmentInIfCondition() {
        // No strong feelings here, just documenting the current behavior of not applying the change here.
        rewriteRun(
          java(
            """
              class Test {
                  void test(int i) {
                      if ((i = i + 1) > 10) {
                          System.out.println(i);
                      }
                  }
              }
              """
          )
        );
    }

    @Test
    void compoundAssignmentWithNonLiterals() {
        rewriteRun(
          java(
            """
              class Test {
                  int field = 4;
                  int[] arr = new int[10];
                  Test other;

                  void test(int i, int j, int k, int size) {
                      i = i + j;
                      k = k - size;
                      i = i + "alef".length();
                      j = j - (k * 2);
                      field = field + 4;
                      this.field = this.field + 3;
                      this.field = field + 6; // This is not changed as the logic to detect "this.field" is equivalent to "field" in this case is not implemented.
                      arr/*comment*/[0] = arr/*other comment*/[0] + 1;
                      other.field = other.field + 2;
                  }
              }
              """,
            """
              class Test {
                  int field = 4;
                  int[] arr = new int[10];
                  Test other;

                  void test(int i, int j, int k, int size) {
                      i += j;
                      k -= size;
                      i += "alef".length();
                      j -= (k * 2);
                      field += 4;
                      this.field += 3;
                      this.field = field + 6; // This is not changed as the logic to detect "this.field" is equivalent to "field" in this case is not implemented.
                      arr/*comment*/[0]++;
                      other.field += 2;
                  }
              }
              """
          )
        );
    }
}
