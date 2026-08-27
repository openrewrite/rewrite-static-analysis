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

import static org.openrewrite.golang.Assertions.go;
import static org.openrewrite.groovy.Assertions.groovy;
import static org.openrewrite.java.Assertions.java;
import static org.openrewrite.javascript.Assertions.typescript;
import static org.openrewrite.kotlin.Assertions.kotlin;
import static org.openrewrite.python.Assertions.python;

class CollapsibleIfStatementsTest implements RewriteTest {

    @Override
    public void defaults(RecipeSpec spec) {
        spec.recipe(new CollapsibleIfStatements());
    }

    @DocumentExample
    @Test
    void mergeNestedIfs() {
        rewriteRun(
          //language=java
          java(
            """
              class Test {
                  void test(boolean a, boolean b) {
                      if (a) {
                          if (b) {
                              System.out.println();
                          }
                      }
                  }
              }
              """,
            """
              class Test {
                  void test(boolean a, boolean b) {
                      if (a && b) {
                          System.out.println();
                      }
                  }
              }
              """
          )
        );
    }

    @Test
    void mergeWithComparisonOperators() {
        rewriteRun(
          //language=java
          java(
            """
              class Test {
                  void test(int x, int y) {
                      if (x > 0) {
                          if (y < 10) {
                              System.out.println();
                          }
                      }
                  }
              }
              """,
            """
              class Test {
                  void test(int x, int y) {
                      if (x > 0 && y < 10) {
                          System.out.println();
                      }
                  }
              }
              """
          )
        );
    }

    @Test
    void wrapOrConditionInParens() {
        rewriteRun(
          //language=java
          java(
            """
              class Test {
                  void test(boolean a, boolean b, boolean c) {
                      if (a) {
                          if (b || c) {
                              System.out.println();
                          }
                      }
                  }
              }
              """,
            """
              class Test {
                  void test(boolean a, boolean b, boolean c) {
                      if (a && (b || c)) {
                          System.out.println();
                      }
                  }
              }
              """
          )
        );
    }

    @Test
    void wrapOuterOrConditionInParens() {
        rewriteRun(
          //language=java
          java(
            """
              class Test {
                  void test(boolean a, boolean b, boolean c) {
                      if (a || b) {
                          if (c) {
                              System.out.println();
                          }
                      }
                  }
              }
              """,
            """
              class Test {
                  void test(boolean a, boolean b, boolean c) {
                      if ((a || b) && c) {
                          System.out.println();
                      }
                  }
              }
              """
          )
        );
    }

    @Test
    void doNotChangeOuterIfWithElse() {
        rewriteRun(
          //language=java
          java(
            """
              class Test {
                  void test(boolean a, boolean b) {
                      if (a) {
                          if (b) {
                              System.out.println("yes");
                          }
                      } else {
                          System.out.println("no");
                      }
                  }
              }
              """
          )
        );
    }

    @Test
    void doNotChangeInnerIfWithElse() {
        rewriteRun(
          //language=java
          java(
            """
              class Test {
                  void test(boolean a, boolean b) {
                      if (a) {
                          if (b) {
                              System.out.println("yes");
                          } else {
                              System.out.println("no");
                          }
                      }
                  }
              }
              """
          )
        );
    }

    @Test
    void doNotChangeWhenBlockHasMultipleStatements() {
        rewriteRun(
          //language=java
          java(
            """
              class Test {
                  void test(boolean a, boolean b) {
                      if (a) {
                          System.out.println("before");
                          if (b) {
                              System.out.println("inner");
                          }
                      }
                  }
              }
              """
          )
        );
    }

    @Test
    void mergeTripleNestedIfs() {
        rewriteRun(
          //language=java
          java(
            """
              class Test {
                  void test(boolean a, boolean b, boolean c) {
                      if (a) {
                          if (b) {
                              if (c) {
                                  System.out.println();
                              }
                          }
                      }
                  }
              }
              """,
            """
              class Test {
                  void test(boolean a, boolean b, boolean c) {
                      if (a && b && c) {
                          System.out.println();
                      }
                  }
              }
              """
          )
        );
    }

    @Test
    void mergeNestedIfsKotlin() {
        rewriteRun(
          //language=kotlin
          kotlin(
            """
              fun test(a: Boolean, b: Boolean) {
                  if (a) {
                      if (b) {
                          println()
                      }
                  }
              }
              """,
            """
              fun test(a: Boolean, b: Boolean) {
                  if (a && b) {
                      println()
                  }
              }
              """
          )
        );
    }

    @Test
    void mergeNestedIfsGroovy() {
        rewriteRun(
          //language=groovy
          groovy(
            """
              void test(boolean a, boolean b) {
                  if (a) {
                      if (b) {
                          println()
                      }
                  }
              }
              """,
            """
              void test(boolean a, boolean b) {
                  if (a && b) {
                      println()
                  }
              }
              """
          )
        );
    }

    @Test
    void mergeNestedIfsTypeScript() {
        rewriteRun(
          //language=typescript
          typescript(
            """
              function test(a: boolean, b: boolean) {
                  if (a) {
                      if (b) {
                          f();
                      }
                  }
              }
              """,
            """
              function test(a: boolean, b: boolean) {
                  if (a && b) {
                      f();
                  }
              }
              """
          )
        );
    }

    @Test
    void mergeNestedIfsPython() {
        rewriteRun(
          //language=python
          python(
            """
              def test(a, b):
                  if a:
                      if b:
                          print()
              """,
            """
              def test(a, b):
                  if a and b:
                      print()
              """
          )
        );
    }

    @Test
    void mergeNestedIfsGo() {
        rewriteRun(
          //language=go
          go(
            """
              package main

              type User struct {
                  Active        bool
                  EmailVerified bool
              }

              func welcome(user User) {
                  if user.Active {
                      if user.EmailVerified {
                          sendWelcome(user)
                      }
                  }
              }

              func sendWelcome(user User) {}
              """,
            """
              package main

              type User struct {
                  Active        bool
                  EmailVerified bool
              }

              func welcome(user User) {
                  if user.Active && user.EmailVerified {
                      sendWelcome(user)
                  }
              }

              func sendWelcome(user User) {}
              """
          )
        );
    }
}
