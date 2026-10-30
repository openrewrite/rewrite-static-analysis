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

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.openrewrite.DocumentExample;
import org.openrewrite.test.RecipeSpec;
import org.openrewrite.test.RewriteTest;

import static org.openrewrite.java.Assertions.java;

class FindJumpOutOfFinallyTest implements RewriteTest {

    @Override
    public void defaults(RecipeSpec spec) {
        spec.recipe(new FindJumpOutOfFinally());
    }

    @DocumentExample
    @Test
    void returnInFinally() {
        rewriteRun(
          //language=java
          java(
            """
              class A {
                  void doSomethingWhichThrowsException() {
                      try {
                          throw new RuntimeException();
                      } finally {
                          return;
                      }
                  }
              }
              """,
            """
              class A {
                  void doSomethingWhichThrowsException() {
                      try {
                          throw new RuntimeException();
                      } finally {
                          /*~~(This `return` leaves the enclosing `finally` block, discarding any exception the `try` or `catch` was propagating.)~~>*/return;
                      }
                  }
              }
              """
          )
        );
    }

    @Test
    void breakOutOfALoopThatEnclosesTheTry() {
        rewriteRun(
          //language=java
          java(
            """
              class A {
                  void m() {
                      for (int i = 0; i < 10; i++) {
                          try {
                              throw new RuntimeException();
                          } finally {
                              break;
                          }
                      }
                  }
              }
              """,
            """
              class A {
                  void m() {
                      for (int i = 0; i < 10; i++) {
                          try {
                              throw new RuntimeException();
                          } finally {
                              /*~~(This `break` leaves the enclosing `finally` block, discarding any exception the `try` or `catch` was propagating.)~~>*/break;
                          }
                      }
                  }
              }
              """
          )
        );
    }

    @Test
    void continueOutOfALoopThatEnclosesTheTry() {
        rewriteRun(
          //language=java
          java(
            """
              class A {
                  void m() {
                      while (true) {
                          try {
                              throw new RuntimeException();
                          } finally {
                              continue;
                          }
                      }
                  }
              }
              """,
            """
              class A {
                  void m() {
                      while (true) {
                          try {
                              throw new RuntimeException();
                          } finally {
                              /*~~(This `continue` leaves the enclosing `finally` block, discarding any exception the `try` or `catch` was propagating.)~~>*/continue;
                          }
                      }
                  }
              }
              """
          )
        );
    }

    @Test
    void labelledBreakToALabelOutsideTheFinally() {
        rewriteRun(
          //language=java
          java(
            """
              class A {
                  void m() {
                      outer:
                      for (int i = 0; i < 10; i++) {
                          try {
                              throw new RuntimeException();
                          } finally {
                              for (int j = 0; j < 10; j++) {
                                  break outer;
                              }
                          }
                      }
                  }
              }
              """,
            """
              class A {
                  void m() {
                      outer:
                      for (int i = 0; i < 10; i++) {
                          try {
                              throw new RuntimeException();
                          } finally {
                              for (int j = 0; j < 10; j++) {
                                  /*~~(This `break` leaves the enclosing `finally` block, discarding any exception the `try` or `catch` was propagating.)~~>*/break outer;
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
    void returnInsideALoopDeclaredInFinally() {
        rewriteRun(
          //language=java
          java(
            """
              class A {
                  int m() {
                      try {
                          throw new RuntimeException();
                      } finally {
                          for (int i = 0; i < 10; i++) {
                              return 1;
                          }
                      }
                  }
              }
              """,
            """
              class A {
                  int m() {
                      try {
                          throw new RuntimeException();
                      } finally {
                          for (int i = 0; i < 10; i++) {
                              /*~~(This `return` leaves the enclosing `finally` block, discarding any exception the `try` or `catch` was propagating.)~~>*/return 1;
                          }
                      }
                  }
              }
              """
          )
        );
    }

    @Test
    void continueInsideASwitchInsideAFinally() {
        rewriteRun(
          //language=java
          java(
            """
              class A {
                  void m(int k) {
                      while (true) {
                          try {
                              work();
                          } finally {
                              switch (k) {
                                  case 1:
                                      continue;
                                  default:
                                      break;
                              }
                          }
                      }
                  }

                  void work() {
                  }
              }
              """,
            """
              class A {
                  void m(int k) {
                      while (true) {
                          try {
                              work();
                          } finally {
                              switch (k) {
                                  case 1:
                                      /*~~(This `continue` leaves the enclosing `finally` block, discarding any exception the `try` or `catch` was propagating.)~~>*/continue;
                                  default:
                                      break;
                              }
                          }
                      }
                  }

                  void work() {
                  }
              }
              """
          )
        );
    }

    @Nested
    class NoChange {

        @Test
        void breakInsideALoopDeclaredInFinallyIsContained() {
            rewriteRun(
              //language=java
              java(
                """
                  class A {
                      void m(int q) {
                          try {
                              throw new RuntimeException();
                          } finally {
                              for (int i = 0; i < 10; i++) {
                                  if (q == i) {
                                      break;
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
        void labelledBreakToALabelInsideTheFinally() {
            rewriteRun(
              //language=java
              java(
                """
                  class A {
                      void m() {
                          try {
                              throw new RuntimeException();
                          } finally {
                              inner:
                              for (int j = 0; j < 10; j++) {
                                  break inner;
                              }
                          }
                      }
                  }
                  """
              )
            );
        }

        @Test
        void breakInsideASwitchDeclaredInFinallyIsContained() {
            rewriteRun(
              //language=java
              java(
                """
                  class A {
                      void m(int q) {
                          try {
                              throw new RuntimeException();
                          } finally {
                              switch (q) {
                                  case 1:
                                      break;
                                  default:
                                      break;
                              }
                          }
                      }
                  }
                  """
              )
            );
        }

        @Test
        void returnInALambdaDeclaredInFinally() {
            rewriteRun(
              //language=java
              java(
                """
                  import java.util.function.Supplier;

                  class A {
                      void m() {
                          try {
                              throw new RuntimeException();
                          } finally {
                              Supplier<String> s = () -> {
                                  return "ok";
                              };
                          }
                      }
                  }
                  """
              )
            );
        }

        @Test
        void returnInAnAnonymousClassMethodDeclaredInFinally() {
            rewriteRun(
              //language=java
              java(
                """
                  import java.util.function.Supplier;

                  class A {
                      void m() {
                          try {
                              System.out.println("work");
                          } finally {
                              Supplier<String> s = new Supplier<String>() {
                                  @Override
                                  public String get() {
                                      return "ok";
                                  }
                              };
                          }
                      }
                  }
                  """
              )
            );
        }

        @Test
        void returnInTheTryOrCatchBlock() {
            rewriteRun(
              //language=java
              java(
                """
                  class A {
                      int m() {
                          try {
                              return 1;
                          } catch (RuntimeException e) {
                              return 2;
                          } finally {
                              System.out.println("clean up");
                          }
                      }
                  }
                  """
              )
            );
        }

        @Test
        void throwIsLeftToTheOtherRecipe() {
            rewriteRun(
              //language=java
              java(
                """
                  class A {
                      void m() {
                          try {
                              System.out.println("work");
                          } finally {
                              throw new RuntimeException();
                          }
                      }
                  }
                  """
              )
            );
        }
    }
}
