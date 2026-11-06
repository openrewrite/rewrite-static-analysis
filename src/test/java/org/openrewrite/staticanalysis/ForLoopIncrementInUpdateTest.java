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

class ForLoopIncrementInUpdateTest implements RewriteTest {
    @Override
    public void defaults(RecipeSpec spec) {
        spec.recipe(new ForLoopIncrementInUpdate());
    }

    @DocumentExample
    @SuppressWarnings("StatementWithEmptyBody")
    @Test
    void moveIncrementToUpdate() {
        rewriteRun(
          //language=java
          java(
            """
              class Test {
                  void test() {
                      int h, j;
                      for (int i = 0; i < 10; h++, j++) {
                          i++;
                      }
                  }
              }
              """,
            """
              class Test {
                  void test() {
                      int h, j;
                      for (int i = 0; i < 10; h++, i++, j++) {
                      }
                  }
              }
              """
          )
        );
    }

    @Issue("https://github.com/openrewrite/rewrite-static-analysis/issues/1061")
    @Test
    void doNotMoveIncrementSkippedByContinue() {
        rewriteRun(
          //language=java
          java(
            """
              class Test {
                  int count(String s) {
                      int n = 0;
                      for (int i = 0; i < s.length(); i++) {
                          if (s.charAt(i) != '#') {
                              continue;
                          }
                          n++;
                          i++;
                      }
                      return n;
                  }
              }
              """
          )
        );
    }

    @Test
    void doNotMoveIncrementSkippedByLabeledContinue() {
        rewriteRun(
          //language=java
          java(
            """
              class Test {
                  void test(int[] a) {
                      outer:
                      for (int i = 0; i < 10; ) {
                          for (int x : a) {
                              if (x == i) {
                                  continue outer;
                              }
                          }
                          i++;
                      }
                  }
              }
              """
          )
        );
    }

    @Issue("https://github.com/openrewrite/rewrite-static-analysis/issues/1166")
    @Test
    void doNotMoveIncrementOfVariableIncrementedMoreThanOnce() {
        rewriteRun(
          //language=java
          java(
            """
              import java.util.List;

              class Test {
                  void test(List<Object> ps, String[] strings) {
                      for (int k = 0, pos = 1; k < strings.length; k++) {
                          ps.add(pos);
                          pos++;
                          ps.add(strings[k]);
                          pos++;
                      }
                  }
              }
              """
          )
        );
    }

    @Test
    void doNotMoveIncrementOfVariableAssignedInBody() {
        rewriteRun(
          //language=java
          java(
            """
              class Test {
                  void test(int[] a) {
                      for (int i = 0; i < a.length; ) {
                          if (a[i] < 0) {
                              i += 2;
                          }
                          i++;
                      }
                  }
              }
              """
          )
        );
    }

    @Test
    void moveIncrementWhenOnlyOtherVariablesAreModified() {
        rewriteRun(
          //language=java
          java(
            """
              class Test {
                  int test(int[] a) {
                      int sum = 0;
                      for (int i = 0; i < a.length; ) {
                          sum += a[i];
                          i++;
                      }
                      return sum;
                  }
              }
              """,
            """
              class Test {
                  int test(int[] a) {
                      int sum = 0;
                      for (int i = 0; i < a.length; i++) {
                          sum += a[i];
                      }
                      return sum;
                  }
              }
              """
          )
        );
    }

    @Test
    void moveIncrementWhenContinueTargetsNestedLoop() {
        rewriteRun(
          //language=java
          java(
            """
              class Test {
                  void test(int[] a) {
                      for (int i = 0; i < 10; ) {
                          for (int x : a) {
                              if (x == i) {
                                  continue;
                              }
                          }
                          i++;
                      }
                  }
              }
              """,
            """
              class Test {
                  void test(int[] a) {
                      for (int i = 0; i < 10; i++) {
                          for (int x : a) {
                              if (x == i) {
                                  continue;
                              }
                          }
                      }
                  }
              }
              """
          )
        );
    }
}
