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
        // The trailing `i++` runs only on iterations that reach it; in the update clause it would run on every one.
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

    @Issue("https://github.com/openrewrite/rewrite-static-analysis/issues/1061")
    @Test
    void doNotMoveIncrementSkippedByLabeledContinue() {
        rewriteRun(
          //language=java
          java(
            """
              class Test {
                  void test(int[][] grid) {
                      outer:
                      for (int i = 0; i < grid.length; i++) {
                          for (int j = 0; j < grid[i].length; j++) {
                              if (grid[i][j] == 0) {
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

    @Issue("https://github.com/openrewrite/rewrite-static-analysis/issues/1061")
    @Test
    void moveIncrementWhenContinueBelongsToNestedLoop() {
        // The unlabeled continue targets the inner loop, so the outer increment is still reached every iteration.
        rewriteRun(
          //language=java
          java(
            """
              class Test {
                  void test(int[][] grid) {
                      for (int i = 0; i < grid.length; i++) {
                          for (int j = 0; j < grid[i].length; j++) {
                              if (grid[i][j] == 0) {
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
                  void test(int[][] grid) {
                      for (int i = 0; i < grid.length; i++, i++) {
                          for (int j = 0; j < grid[i].length; j++) {
                              if (grid[i][j] == 0) {
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
