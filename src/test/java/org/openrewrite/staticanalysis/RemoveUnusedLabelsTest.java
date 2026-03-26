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

import static org.openrewrite.java.Assertions.java;

@SuppressWarnings({"UnusedLabel", "unused"})
class RemoveUnusedLabelsTest implements RewriteTest {

    @Override
    public void defaults(RecipeSpec spec) {
        spec.recipe(new RemoveUnusedLabels());
    }

    @DocumentExample
    @Test
    void unusedLabelOnForLoop() {
        rewriteRun(
          //language=java
          java(
            """
              class A {
                  void foo() {
                      label: for (int i = 0; i < 10; i++) {
                          System.out.println(i);
                      }
                  }
              }
              """,
            """
              class A {
                  void foo() {
                      for (int i = 0; i < 10; i++) {
                          System.out.println(i);
                      }
                  }
              }
              """
          )
        );
    }

    @Test
    void unusedLabelOnWhileLoopWithUnlabeledBreak() {
        rewriteRun(
          //language=java
          java(
            """
              class A {
                  void foo() {
                      loop: while (true) {
                          break;
                      }
                  }
              }
              """,
            """
              class A {
                  void foo() {
                      while (true) {
                          break;
                      }
                  }
              }
              """
          )
        );
    }

    @Test
    void doNotChangeUsedLabelWithBreak() {
        rewriteRun(
          //language=java
          java(
            """
              class A {
                  void foo() {
                      outer: for (int i = 0; i < 10; i++) {
                          for (int j = 0; j < 10; j++) {
                              if (j == 5) break outer;
                          }
                      }
                  }
              }
              """
          )
        );
    }

    @Test
    void doNotChangeUsedLabelWithContinue() {
        rewriteRun(
          //language=java
          java(
            """
              class A {
                  void foo() {
                      outer: for (int i = 0; i < 10; i++) {
                          for (int j = 0; j < 10; j++) {
                              if (j == 5) continue outer;
                          }
                      }
                  }
              }
              """
          )
        );
    }

    @Test
    void removeUnusedInnerLabelKeepUsedOuterLabel() {
        rewriteRun(
          //language=java
          java(
            """
              class A {
                  void foo() {
                      outer: for (int i = 0; i < 10; i++) {
                          inner: for (int j = 0; j < 10; j++) {
                              if (j == 5) break outer;
                          }
                      }
                  }
              }
              """,
            """
              class A {
                  void foo() {
                      outer: for (int i = 0; i < 10; i++) {
                          for (int j = 0; j < 10; j++) {
                              if (j == 5) break outer;
                          }
                      }
                  }
              }
              """
          )
        );
    }

    @Test
    void unusedLabelOnBlock() {
        rewriteRun(
          //language=java
          java(
            """
              class A {
                  void foo() {
                      block: {
                          System.out.println("hello");
                      }
                  }
              }
              """,
            """
              class A {
                  void foo() {
                      {
                          System.out.println("hello");
                      }
                  }
              }
              """
          )
        );
    }
}
