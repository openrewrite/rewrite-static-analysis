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
import org.openrewrite.DocumentExample;
import org.openrewrite.test.RecipeSpec;
import org.openrewrite.test.RewriteTest;

import static org.openrewrite.java.Assertions.java;

class FindThrowInFinallyTest implements RewriteTest {

    @Override
    public void defaults(RecipeSpec spec) {
        spec.recipe(new FindThrowInFinally());
    }

    @DocumentExample
    @Test
    void throwInFinally() {
        rewriteRun(
          //language=java
          java(
            """
              class A {
                  void m() {
                      try {
                          throw new IllegalArgumentException();
                      } finally {
                          throw new RuntimeException();
                      }
                  }
              }
              """,
            """
              class A {
                  void m() {
                      try {
                          throw new IllegalArgumentException();
                      } finally {
                          /*~~(A `throw` in a `finally` block replaces any exception the `try` or `catch` was propagating, losing its message and stack trace.)~~>*/throw new RuntimeException();
                      }
                  }
              }
              """
          )
        );
    }

    @Test
    void throwNestedInAnIfInsideFinally() {
        rewriteRun(
          //language=java
          java(
            """
              class A {
                  void m(boolean fail) {
                      try {
                          System.out.println("work");
                      } finally {
                          if (fail) {
                              throw new RuntimeException();
                          }
                      }
                  }
              }
              """,
            """
              class A {
                  void m(boolean fail) {
                      try {
                          System.out.println("work");
                      } finally {
                          if (fail) {
                              /*~~(A `throw` in a `finally` block replaces any exception the `try` or `catch` was propagating, losing its message and stack trace.)~~>*/throw new RuntimeException();
                          }
                      }
                  }
              }
              """
          )
        );
    }

    @Test
    void throwInTheTryOrCatchBlock() {
        rewriteRun(
          //language=java
          java(
            """
              class A {
                  void m() {
                      try {
                          throw new IllegalArgumentException();
                      } catch (IllegalArgumentException e) {
                          throw new RuntimeException(e);
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
    void throwInALambdaDeclaredInFinally() {
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
                          Supplier<String> s = () -> {
                              throw new RuntimeException();
                          };
                      }
                  }
              }
              """
          )
        );
    }

    @Test
    void throwInAnAnonymousClassDeclaredInFinally() {
        rewriteRun(
          //language=java
          java(
            """
              class A {
                  void m() {
                      try {
                          System.out.println("work");
                      } finally {
                          Runnable r = new Runnable() {
                              @Override
                              public void run() {
                                  throw new RuntimeException();
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
    void throwInANestedTryWithinFinallyStillLeavesIt() {
        rewriteRun(
          //language=java
          java(
            """
              class A {
                  void m() {
                      try {
                          System.out.println("work");
                      } finally {
                          try {
                              throw new RuntimeException();
                          } finally {
                              System.out.println("inner clean up");
                          }
                      }
                  }
              }
              """,
            """
              class A {
                  void m() {
                      try {
                          System.out.println("work");
                      } finally {
                          try {
                              /*~~(A `throw` in a `finally` block replaces any exception the `try` or `catch` was propagating, losing its message and stack trace.)~~>*/throw new RuntimeException();
                          } finally {
                              System.out.println("inner clean up");
                          }
                      }
                  }
              }
              """
          )
        );
    }

    @Test
    void noFinallyAtAll() {
        rewriteRun(
          //language=java
          java(
            """
              class A {
                  void m() {
                      throw new RuntimeException();
                  }
              }
              """
          )
        );
    }

    @Test
    void doNotChangeThrowInAnAnonymousClassInitializerInFinally() {
        rewriteRun(
          //language=java
          java(
            """
              class A {
                  void m() {
                      try {
                          System.out.println("work");
                      } finally {
                          Runnable r = new Runnable() {
                              {
                                  if (hashCode() == 0) {
                                      throw new RuntimeException();
                                  }
                              }

                              @Override
                              public void run() {
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
    void throwInTheConstructorArgumentsOfAnAnonymousClass() {
        rewriteRun(
          //language=java
          java(
            """
              import java.util.ArrayList;

              class A {
                  void m(int k) {
                      try {
                          work();
                      } finally {
                          Object o = new ArrayList<String>(switch (k) {
                              case 1 -> throw new RuntimeException();
                              default -> 0;
                          }) {
                          };
                      }
                  }

                  void work() {
                  }
              }
              """,
            """
              import java.util.ArrayList;

              class A {
                  void m(int k) {
                      try {
                          work();
                      } finally {
                          Object o = new ArrayList<String>(switch (k) {
                              case 1 -> /*~~(A `throw` in a `finally` block replaces any exception the `try` or `catch` was propagating, losing its message and stack trace.)~~>*/throw new RuntimeException();
                              default -> 0;
                          }) {
                          };
                      }
                  }

                  void work() {
                  }
              }
              """
          )
        );
    }
}
