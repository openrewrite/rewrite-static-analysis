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
import static org.openrewrite.javascript.Assertions.typescript;
import static org.openrewrite.python.Assertions.python;

@SuppressWarnings("ConstantConditions")
class MoveConditionsToWhileTest implements RewriteTest {

    @Override
    public void defaults(RecipeSpec spec) {
        spec.recipe(new MoveConditionsToWhile());
    }

    @DocumentExample
    @Test
    void basicTransformation() {
        rewriteRun(
          java(
            """
              class Test {
                  void foo(int counter) {
                      while (true) {
                          if (counter >= 5) {
                              break;
                          }
                          System.out.println("Counter: " + counter);
                          counter++;
                      }
                  }
              }
              """,
            """
              class Test {
                  void foo(int counter) {
                      while (counter < 5) {
                          System.out.println("Counter: " + counter);
                          counter++;
                      }
                  }
              }
              """
          )
        );
    }

    @Test
    void keepUnrelatedBooleanExpressions() {
        rewriteRun(
          java(
            """
              class Test {
                  boolean unrelated(boolean b) {
                      return !!b;
                  }

                  void foo(int counter) {
                      while (true) {
                          if (counter >= 5) {
                              break;
                          }
                          counter++;
                      }
                  }
              }
              """,
            """
              class Test {
                  boolean unrelated(boolean b) {
                      return !!b;
                  }

                  void foo(int counter) {
                      while (counter < 5) {
                          counter++;
                      }
                  }
              }
              """
          )
        );
    }

    @Test
    void withComplexCondition() {
        rewriteRun(
          java(
            """
              class Test {
                  void foo(int a, int b, String s) {
                      while (true) {
                          if (a > 10 && b < 5 || s == null) {
                              break;
                          }
                          System.out.println("Processing");
                          a--;
                      }
                  }
              }
              """,
            """
              class Test {
                  void foo(int a, int b, String s) {
                      while (!(a > 10 && b < 5 || s == null)) {
                          System.out.println("Processing");
                          a--;
                      }
                  }
              }
              """
          )
        );
    }

    @Test
    void withBlockInIf() {
        rewriteRun(
          java(
            """
              import java.util.concurrent.atomic.AtomicBoolean;
              class Test {
                  void foo(AtomicBoolean done) {
                      while (true) {
                          if (done.get()) {
                              break;
                          }
                          System.out.println("Working");
                      }
                  }
              }
              """,
            """
              import java.util.concurrent.atomic.AtomicBoolean;
              class Test {
                  void foo(AtomicBoolean done) {
                      while (!(done.get())) {
                          System.out.println("Working");
                      }
                  }
              }
              """
          )
        );
    }

    @Test
    void doNotChangeWhenElsePresent() {
        rewriteRun(
          java(
            """
              class Test {
                  void foo(int counter) {
                      while (true) {
                          if (counter >= 5) {
                              break;
                          } else {
                              System.out.println("Not done");
                          }
                          counter++;
                      }
                  }
              }
              """
          )
        );
    }

    @Test
    void doNotChangeWhenNotFirstStatement() {
        rewriteRun(
          java(
            """
              class Test {
                  void foo(int counter) {
                      while (true) {
                          System.out.println("Start");
                          if (counter >= 5) {
                              break;
                          }
                          counter++;
                      }
                  }
              }
              """
          )
        );
    }

    @Test
    void doNotChangeWhenMultipleStatementsInIf() {
        rewriteRun(
          java(
            """
              class Test {
                  void foo(int counter) {
                      while (true) {
                          if (counter >= 5) {
                              System.out.println("Breaking");
                              break;
                          }
                          counter++;
                      }
                  }
              }
              """
          )
        );
    }

    @Test
    void doNotChangeWhenNotWhileTrue() {
        rewriteRun(
          java(
            """
              class Test {
                  void foo(int counter, boolean condition) {
                      while (condition) {
                          if (counter >= 5) {
                              break;
                          }
                          counter++;
                      }
                  }
              }
              """
          )
        );
    }

    @Test
    void doNotChangeWhenIfHasNoBreak() {
        rewriteRun(
          java(
            """
              class Test {
                  void foo(int counter) {
                      while (true) {
                          if (counter >= 5) {
                              return;
                          }
                          counter++;
                      }
                  }
              }
              """
          )
        );
    }

    @Test
    void emptyBodyAfterTransformation() {
        rewriteRun(
          java(
            """
              class Test {
                  void foo(boolean done) {
                      while (true) {
                          if (done) {
                              break;
                          }
                      }
                  }
              }
              """,
            """
              class Test {
                  void foo(boolean done) {
                      while (!(done)) {
                      }
                  }
              }
              """
          )
        );
    }

    @Test
    void doNotChangeWhenBreakHasLabel() {
        rewriteRun(
          java(
            """
              class Test {
                  void foo(int counter) {
                      outer: while (true) {
                          if (counter >= 5) {
                              break outer;
                          }
                          counter++;
                      }
                  }
              }
              """
          )
        );
    }

    @Test
    void preserveComments() {
        rewriteRun(
          java(
            """
              class Test {
                  void foo(int counter) {
                      // Main loop
                      while (true) {
                          // Check if we should exit
                          if (counter >= 5) {
                              break;
                          }
                          // Process item
                          System.out.println("Counter: " + counter);
                          counter++;
                      }
                  }
              }
              """,
            """
              class Test {
                  void foo(int counter) {
                      // Main loop
                      while (counter < 5) {
                          // Process item
                          System.out.println("Counter: " + counter);
                          counter++;
                      }
                  }
              }
              """
          )
        );
    }

    @Test
    void moveConditionToTypeScriptWhile() {
        rewriteRun(
          //language=typescript
          typescript(
            """
              function f(x: boolean) {
                  while (true) {
                      if (x) {
                          break;
                      }
                      console.log(x);
                  }
              }
              """,
            """
              function f(x: boolean) {
                  while (!(x)) {
                      console.log(x);
                  }
              }
              """
          )
        );
    }

    @Test
    void moveConditionToPythonWhile() {
        rewriteRun(
          //language=python
          python(
            """
              def f(x: int):
                  while True:
                      if 0 < x < 10:
                          break
                      x += 1
              """,
            """
              def f(x: int):
                  while not (0 < x < 10):
                      x += 1
              """
          )
        );
    }

    @Test
    void doNotChangePythonWhileElse() {
        rewriteRun(
          //language=python
          python(
            """
              def f(x: bool):
                  while True:
                      if x:
                          break
                      print(x)
                  else:
                      print("never")
              """
          )
        );
    }
}
