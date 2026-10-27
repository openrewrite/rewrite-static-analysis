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
import org.openrewrite.test.RecipeSpec;
import org.openrewrite.test.RewriteTest;

import static org.junit.jupiter.api.Assumptions.assumeTrue;
import static org.openrewrite.golang.Assertions.go;
import static org.openrewrite.java.Assertions.java;
import static org.openrewrite.kotlin.Assertions.kotlin;

@SuppressWarnings({"UnnecessaryContinue", "UnnecessaryReturnStatement"})
class NoRedundantJumpStatementsTest implements RewriteTest {

    @Override
    public void defaults(RecipeSpec spec) {
        spec.recipe(new NoRedundantJumpStatements());
    }

    @DocumentExample
    @Test
    void noRedundantJumpStatements() {
        rewriteRun(
          //language=java
          java(
            """
              class Test {
                  boolean condition1;
                  boolean condition2;
                  void test() {
                      while (condition1) {
                          if (condition2) {
                              continue;
                          } else {
                              System.out.println();
                          }
                      }
                      return;
                  }
              }
              """,
            """
              class Test {
                  boolean condition1;
                  boolean condition2;
                  void test() {
                      while (condition1) {
                          if (!condition2) {
                              System.out.println();
                          }
                      }
                  }
              }
              """
          )
        );
    }

    @Test
    void doNotRemoveLabelledContinue() {
        rewriteRun(
          //language=java
          java(
            """
              class Test {
                  void test(int[][] rows, boolean stop) {
                      outer:
                      for (int[] row : rows) {
                          for (int cell : row) {
                              if (stop) {
                                  continue outer;
                              } else {
                                  System.out.println(cell);
                              }
                          }
                      }
                  }
              }
              """
          )
        );
    }

    @Test
    void doNotRemoveLabelledContinueWithoutBraces() {
        rewriteRun(
          //language=java
          java(
            """
              class Test {
                  void test(int[][] rows, boolean stop) {
                      outer:
                      for (int[] row : rows) {
                          for (int cell : row) {
                              if (stop) continue outer;
                              else System.out.println(cell);
                          }
                      }
                  }
              }
              """
          )
        );
    }

    @Test
    void doNotRemoveLabelledContinueInKotlin() {
        rewriteRun(
          //language=kotlin
          kotlin(
            """
              fun test(rows: List<List<Int>>, stop: Boolean) {
                  outer@ for (row in rows) {
                      for (cell in row) {
                          if (stop) {
                              continue@outer
                          } else {
                              println(cell)
                          }
                      }
                  }
              }
              """
          )
        );
    }

    @Test
    void keepCommentsOfRemovedReturn() {
        rewriteRun(
          //language=java
          java(
            """
              class Test {
                  void test() {
                      System.out.println();
                      // nothing left to do
                      return;
                  }
              }
              """,
            """
              class Test {
                  void test() {
                      System.out.println();
                      // nothing left to do
                  }
              }
              """
          )
        );
    }

    @Test
    void doNotRemoveGoReturnWithExpression() {
        assumeTrue(GoEngineTestListener.isAvailable(), "rewrite-go-rpc engine unavailable");
        rewriteRun(
          go(
            """
              package main

              func doWork() {
                  println("work")
              }

              func wrapper() {
                  return doWork()
              }
              """
          )
        );
    }
}
