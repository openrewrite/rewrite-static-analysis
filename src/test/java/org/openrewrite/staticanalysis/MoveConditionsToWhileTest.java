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
import org.openrewrite.test.RewriteTest;

import static org.openrewrite.java.Assertions.java;

@SuppressWarnings("ConstantConditions")
class MoveConditionsToWhileTest implements RewriteTest {

    @DocumentExample
    @Test
    void basicTransformation() {
        rewriteRun(
          spec -> spec.recipe(new MoveConditionsToWhile()),
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
                      while (!(counter >= 5)) {
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
    void withComplexCondition() {
        rewriteRun(
          spec -> spec.recipe(new MoveConditionsToWhile()),
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
          spec -> spec.recipe(new MoveConditionsToWhile()),
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
          spec -> spec.recipe(new MoveConditionsToWhile()),
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
          spec -> spec.recipe(new MoveConditionsToWhile()),
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
          spec -> spec.recipe(new MoveConditionsToWhile()),
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
          spec -> spec.recipe(new MoveConditionsToWhile()),
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
          spec -> spec.recipe(new MoveConditionsToWhile()),
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
          spec -> spec.recipe(new MoveConditionsToWhile()),
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
    void preserveComments() {
        rewriteRun(
          spec -> spec.recipe(new MoveConditionsToWhile()),
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
                      while (!(counter >= 5)) {
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
}
