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
import org.openrewrite.test.RecipeSpec;
import org.openrewrite.test.RewriteTest;

import static org.openrewrite.java.Assertions.java;

@SuppressWarnings("ConstantConditions")
class PreferIncrementOperatorTest implements RewriteTest {

    @Override
    public void defaults(RecipeSpec spec) {
        spec.recipe(new PreferIncrementOperator());
    }

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
    void multipleIncrements() {
        rewriteRun(
          java(
            """
              class Test {
                  void test(int i, int j) {
                      i = i + 1;
                      j = j - 1;
                  }
              }
              """,
            """
              class Test {
                  void test(int i, int j) {
                      i++;
                      j--;
                  }
              }
              """
          )
        );
    }

    @Test
    void doNotChangeNonLiteralOne() {
        rewriteRun(
          java(
            """
              class Test {
                  void test(int i) {
                      i = i + 2;
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
                  void test(int i, int j) {
                      i = j + 1;
                  }
              }
              """
          )
        );
    }

    @Test
    void doNotChangeIfOrderIsReversed() {
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
    void longType() {
        rewriteRun(
          java(
            """
              class Test {
                  void test(long l) {
                      l = l + 1;
                  }
              }
              """,
            """
              class Test {
                  void test(long l) {
                      l++;
                  }
              }
              """
          )
        );
    }

    @ExpectedToFail("Not implemented yet")
    @Test
    void fieldIncrement() {
        rewriteRun(
          java(
            """
              class Test {
                  int count;

                  void test() {
                      this.count = this.count + 1;
                  }
              }
              """,
            """
              class Test {
                  int count;

                  void test() {
                      this.count++;
                  }
              }
              """
          )
        );
    }
}
