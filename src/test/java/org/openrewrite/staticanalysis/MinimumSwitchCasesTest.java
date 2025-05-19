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

@SuppressWarnings({"SwitchStatementWithTooFewBranches", "EnhancedSwitchMigration"})
class MinimumSwitchCasesTest implements RewriteTest {

    @Override
    public void defaults(RecipeSpec spec) {
        spec.recipe(new MinimumSwitchCases());
    }

    @DocumentExample
    @Test
    void caseWithReturnInsteadOfBreak() {
        rewriteRun(
          //language=java
          java(
            """
              class Test {
                  int variable;
                  int test() {
                      switch (variable) {
                        case 0:
                            return 0;
                        default:
                            doSomethingElse();
                      }
                      return 1;
                  }
                  void doSomething() {}
                  void doSomethingElse() {}
              }
              """,
            """
              class Test {
                  int variable;
                  int test() {
                      if (variable == 0) {
                          return 0;
                      } else {
                          doSomethingElse();
                      }
                      return 1;
                  }
                  void doSomething() {}
                  void doSomethingElse() {}
              }
              """
          )
        );
    }

    @Issue("https://github.com/openrewrite/rewrite/issues/800")
    @Test
    void primitiveAndDefault() {
        rewriteRun(
          //language=java
          java(
            """
              class Test {
                  int variable;
                  void test() {
                      switch (variable) {
                        case 0:
                            doSomething();
                            break;
                        default:
                            doSomethingElse();
                            break;
                      }
                  }
                  void doSomething() {}
                  void doSomethingElse() {}
              }
              """,
            """
              class Test {
                  int variable;
                  void test() {
                      if (variable == 0) {
                          doSomething();
                      } else {
                          doSomethingElse();
                      }
                  }
                  void doSomething() {}
                  void doSomethingElse() {}
              }
              """
          )
        );
    }

    @Test
    void caseWithFallthrough() {
        rewriteRun(
          //language=java
          java(
            """
              class Test {
                  int variable;
                  void test() {
                      switch (variable) {
                        case 0:
                            doSomething();
                        default:
                            doSomethingElse();
                      }
                  }
                  void doSomething() {}
                  void doSomethingElse() {}
              }
              """
          )
        );
    }

    @Test
    void caseWithFallthroughInDefault() {
        rewriteRun(
          //language=java
          java(
            """
              class Test {
                  int variable;
                  void test() {
                      switch (variable) {
                        case 0:
                            doSomething();
                            break;
                        default:
                            doSomethingElse();
                      }
                  }
                  void doSomething() {}
                  void doSomethingElse() {}
              }
              """,
            """
              class Test {
                  int variable;
                  void test() {
                      if (variable == 0) {
                          doSomething();
                      } else {
                          doSomethingElse();
                      }
                  }
                  void doSomething() {}
                  void doSomethingElse() {}
              }
              """
          )
        );
    }

    @Test
    void multipleExpressions() {
        rewriteRun(
          //language=java
          java(
            """
              class Test {
                  int variable;
                  void test() {
                      switch (variable) {
                        case 0:
                        case 1:
                            doSomething();
                            break;
                      }
                  }
                  void doSomething() {}
                  void doSomethingElse() {}
              }
              """
          )
        );
    }

    @Issue("https://github.com/openrewrite/rewrite/issues/800")
    @Test
    void twoPrimitives() {
        rewriteRun(
          //language=java
          java(
            """
              class Test {
                  int variable;
                  void test() {
                      switch (variable) {
                        case 0:
                            doSomething();
                            break;
                        case 1:
                            doSomethingElse();
                            break;
                      }
                  }
                  void doSomething() {}
                  void doSomethingElse() {}
              }
              """,
            """
              class Test {
                  int variable;
                  void test() {
                      if (variable == 0) {
                          doSomething();
                      } else if (variable == 1) {
                          doSomethingElse();
                      }
                  }
                  void doSomething() {}
                  void doSomethingElse() {}
              }
              """
          )
        );
    }

    @Issue("https://github.com/openrewrite/rewrite/issues/800")
    @Test
    void stringAndDefault() {
        rewriteRun(
          //language=java
          java(
            """
              class Test {
                  String name;
                  void test() {
                      switch (name) {
                        case "jonathan":
                            doSomething();
                            break;
                        default:
                            doSomethingElse();
                            break;
                      }
                  }
                  void doSomething() {}
                  void doSomethingElse() {}
              }
              """,
            """
              class Test {
                  String name;
                  void test() {
                      if ("jonathan".equals(name)) {
                          doSomething();
                      } else {
                          doSomethingElse();
                      }
                  }
                  void doSomething() {}
                  void doSomethingElse() {}
              }
              """
          )
        );
    }

    @Issue("https://github.com/openrewrite/rewrite/issues/800")
    @Test
    void twoStrings() {
        rewriteRun(
          //language=java
          java(
            """
              class Test {
                  String name;
                  void test() {
                      switch (name) {
                        case "jonathan":
                            doSomething();
                            break;
                        case "jon":
                            doSomethingElse();
                            break;
                      }
                  }
                  void doSomething() {}
                  void doSomethingElse() {}
              }
              """,
            """
              class Test {
                  String name;
                  void test() {
                      if ("jonathan".equals(name)) {
                          doSomething();
                      } else if ("jon".equals(name)) {
                          doSomethingElse();
                      }
                  }
                  void doSomething() {}
                  void doSomethingElse() {}
              }
              """
          )
        );
    }

    @Issue("https://github.com/openrewrite/rewrite/issues/800")
    @Test
    void onePrimitive() {
        rewriteRun(
          //language=java
          java(
            """
              class Test {
                  int variable;
                  void test() {
                      switch (variable) {
                        case 0:
                            doSomething();
                            break;
                      }
                  }
                  void doSomething() {}
                  void doSomethingElse() {}
              }
              """,
            """
              class Test {
                  int variable;
                  void test() {
                      if (variable == 0) {
                          doSomething();
                      }
                  }
                  void doSomething() {}
                  void doSomethingElse() {}
              }
              """
          )
        );
    }

    @Issue("https://github.com/openrewrite/rewrite/issues/800")
    @Test
    void oneString() {
        rewriteRun(
          //language=java
          java(
            """
              class Test {
                  String name;
                  void test() {
                      switch (name) {
                        case "jonathan":
                            doSomething();
                            break;
                      }
                  }
                  void doSomething() {}
                  void doSomethingElse() {}
              }
              """,
            """
              class Test {
                  String name;
                  void test() {
                      if ("jonathan".equals(name)) {
                          doSomething();
                      }
                  }
                  void doSomething() {}
                  void doSomethingElse() {}
              }
              """
          )
        );
    }

    @SuppressWarnings("StatementWithEmptyBody")
    @Issue("https://github.com/openrewrite/rewrite/issues/800")
    @Test
    void noCases() {
        rewriteRun(
          //language=java
          java(
            """
              class Test {
                  int variable;
                  void test() {
                      switch (variable) {
                      }
                  }
              }
              """
          )
        );
    }

    @Issue("https://github.com/openrewrite/rewrite/issues/1212")
    @Test
    void importsOnEnum() {
        //noinspection EnhancedSwitchMigration
        rewriteRun(
          //language=java
          java(
            """
              import java.time.DayOfWeek;

              class Test {
                  DayOfWeek day;

                  void test() {
                      switch(day) {
                          case MONDAY:
                              someMethod();
                              break;
                      }
                      switch(day) {
                          case MONDAY:
                              someMethod();
                              break;
                          default:
                              someMethod();
                              break;
                      }
                      switch (day) {
                          case MONDAY:
                              someMethod();
                              break;
                          case TUESDAY:
                              someMethod();
                              break;
                      }
                  }

                  void someMethod() {
                  }
              }
              """,
            """
              import java.time.DayOfWeek;

              class Test {
                  DayOfWeek day;

                  void test() {
                      if (day == DayOfWeek.MONDAY) {
                          someMethod();
                      }
                      if (day == DayOfWeek.MONDAY) {
                          someMethod();
                      } else {
                          someMethod();
                      }
                      if (day == DayOfWeek.MONDAY) {
                          someMethod();
                      } else if (day == DayOfWeek.TUESDAY) {
                          someMethod();
                      }
                  }

                  void someMethod() {
                  }
              }
              """
          )
        );
    }

    @Test
    void importsOnEnumImplied() {
        //noinspection EnhancedSwitchMigration
        rewriteRun(
          //language=java
          java(
            """
              import java.time.LocalDate;

              class Test {
                  void test(LocalDate date) {
                      switch(date.getDayOfWeek()) {
                          case MONDAY:
                              someMethod();
                              break;
                          default:
                              someMethod();
                              break;
                      }
                  }

                  void someMethod() {
                  }
              }
              """,
            """
              import java.time.DayOfWeek;
              import java.time.LocalDate;

              class Test {
                  void test(LocalDate date) {
                      if (date.getDayOfWeek() == DayOfWeek.MONDAY) {
                          someMethod();
                      } else {
                          someMethod();
                      }
                  }

                  void someMethod() {
                  }
              }
              """
          )
        );
    }

    @Issue("https://github.com/openrewrite/rewrite/issues/1701")
    @Test
    void removeBreaksFromCaseBody() {
        rewriteRun(
          //language=java
          java(
            """
              class Test {
                  String name;
                  void test() {
                      switch (name) {
                        case "jonathan": {
                            doSomething();
                            break;
                        }
                      }
                  }
                  void doSomething() {}
                  void doSomethingElse() {}
              }
              """,
            """
              class Test {
                  String name;
                  void test() {
                      if ("jonathan".equals(name)) {
                          doSomething();
                      }
                  }
                  void doSomething() {}
                  void doSomethingElse() {}
              }
              """
          )
        );
    }

    @Issue("https://github.com/openrewrite/rewrite/issues/2258")
    @Test
    void defaultOnly() {
        rewriteRun(
          //language=java
          java(
            """
              enum Test {
                  A, B, C;

                  @Override
                  public String toString() {
                      String s;
                      switch (this) {
                          default:
                              s = this.name();
                              break;
                      }
                      switch(this) {
                          default:
                              return s;
                      }
                  }
              }
              """,
            """
              enum Test {
                  A, B, C;

                  @Override
                  public String toString() {
                      String s;
                      s = this.name();
                      return s;
                  }
              }
              """
          )
        );
    }

    @Test
    @Issue("https://github.com/openrewrite/rewrite/issues/3076")
    void switchExpressions() {
        rewriteRun(
          //language=java
          java(
            """
              class Test {
                  int variable;
                  void test() {
                      switch (variable) {
                        case 0 -> doSomething();
                        default -> doSomethingElse();
                      }
                  }
                  void doSomething() {}
                  void doSomethingElse() {}
              }
              """,
            """
              class Test {
                  int variable;
                  void test() {
                      if (variable == 0) {
                          doSomething();
                      } else {
                          doSomethingElse();
                      }
                  }
                  void doSomething() {}
                  void doSomethingElse() {}
              }
              """
          )
        );
    }

    @Test
    void multipleCaseConstants() {
        rewriteRun(
          //language=java
          java(
            """
              class Test {
                  int variable;
                  void test() {
                      switch (variable) {
                        case 0, 1: doSomething(); break;
                        default: doSomethingElse(); break;
                      }
                  }
                  void doSomething() {}
                  void doSomethingElse() {}
              }
              """
          )
        );
    }

    @Test
    void nestedEnum() {
        rewriteRun(
          //language=java
          java(
            """
              class Test {
                  int test(java.io.ObjectInputFilter filter) {
                      switch (filter.checkInput(null)) {
                        case ALLOWED: return 0;
                        default: return 1;
                      }
                  }
              }
              """,
            """
              import java.io.ObjectInputFilter;

              class Test {
                  int test(ObjectInputFilter filter) {
                      if (filter.checkInput(null) == ObjectInputFilter.Status.ALLOWED) {
                          return 0;
                      } else {
                          return 1;
                      }
                  }
              }
              """
          )
        );
    }

    @SuppressWarnings({"ConstantValue", "DataFlowIssue"})
    @Test
    void nestedSwitches() {
        rewriteRun(
          //language=java
          java(
            """
              class Test {
                  int test(E e) {
                      switch (e) {
                        case A:
                            switch (e) {
                                case A:
                                    return 0;
                                default:
                                    return 1;
                            }
                        case B:
                        default:
                            return 1;
                      }
                  }
                  enum E {
                      A, B
                  }
              }
              """,
            """
              class Test {
                  int test(E e) {
                      switch (e) {
                        case A:
                            if (e == Test.E.A) {
                                return 0;
                            } else {
                                return 1;
                            }
                        case B:
                        default:
                            return 1;
                      }
                  }
                  enum E {
                      A, B
                  }
              }
              """
          )
        );
    }

    @Test
    @Issue("https://github.com/openrewrite/rewrite/issues/3076")
    void multipleSwitchExpressions() {
        rewriteRun(
          //language=java
          java(
            """
              class Test {
                  int variable;
                  void test() {
                      switch (variable) {
                        case 0, 1 -> doSomething();
                        default -> doSomethingElse();
                      }
                  }
                  void doSomething() {}
                  void doSomethingElse() {}
              }
              """
          )
        );
    }
}
