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
import org.openrewrite.Issue;
import org.openrewrite.test.RecipeSpec;
import org.openrewrite.test.RewriteTest;

import static org.openrewrite.java.Assertions.java;

@SuppressWarnings("ConstantConditions")
class ReplaceIfElseWithTernaryTest implements RewriteTest {

    @Override
    public void defaults(RecipeSpec spec) {
        spec.recipe(new ReplaceIfElseWithTernary());
    }

    @DocumentExample
    @Issue("https://github.com/openrewrite/rewrite-static-analysis/issues/683")
    @Test
    void simpleIfElseWithAssignment() {
        rewriteRun(
          java(
            """
              class Test {
                  void method(int age) {
                      String category;
                      if (age >= 18) {
                          category = "Adult";
                      } else {
                          category = "Minor";
                      }
                  }
              }
              """,
            """
              class Test {
                  void method(int age) {
                      String category;
                      category = age >= 18 ? "Adult" : "Minor";
                  }
              }
              """
          )
        );
    }

    @Test
    void ifElseWithoutBraces() {
        rewriteRun(
          java(
            """
              class Test {
                  void method(boolean condition, String s1, String s2) {
                      String result;
                      if (condition)
                          result = s1;
                      else
                          result = s2;
                  }
              }
              """,
            """
              class Test {
                  void method(boolean condition, String s1, String s2) {
                      String result;
                      result = condition ? s1 : s2;
                  }
              }
              """
          )
        );
    }

    @Test
    void ifElseWithFieldAssignment() {
        rewriteRun(
          java(
            """
              class Test {
                  String field;

                  void method(boolean flag) {
                      if (flag) {
                          this.field = "true";
                      } else {
                          this.field = "false";
                      }
                  }
              }
              """,
            """
              class Test {
                  String field;

                  void method(boolean flag) {
                      this.field = flag ? "true" : "false";
                  }
              }
              """
          )
        );
    }

    @Test
    void ifElseWithNumericAssignment() {
        rewriteRun(
          java(
            """
              class Test {
                  void method(boolean premium) {
                      double discount;
                      if (premium) {
                          discount = 0.2;
                      } else {
                          discount = 0.05;
                      }
                  }
              }
              """,
            """
              class Test {
                  void method(boolean premium) {
                      double discount;
                      discount = premium ? 0.2 : 0.05;
                  }
              }
              """
          )
        );
    }

    @Test
    void ifElseWithComplexCondition() {
        rewriteRun(
          java(
            """
              class Test {
                  void method(int x, int y) {
                      String result;
                      if (x > 10 && y < 5) {
                          result = "A";
                      } else {
                          result = "B";
                      }
                  }
              }
              """,
            """
              class Test {
                  void method(int x, int y) {
                      String result;
                      result = x > 10 && y < 5 ? "A" : "B";
                  }
              }
              """
          )
        );
    }

    @Test
    void doNotChangeIfNoElse() {
        rewriteRun(
          java(
            """
              class Test {
                  void method(boolean condition) {
                      String result = "default";
                      if (condition) {
                          result = "changed";
                      }
                  }
              }
              """
          )
        );
    }

    @Test
    void doNotChangeIfMultipleStatements() {
        rewriteRun(
          java(
            """
              class Test {
                  void method(boolean condition) {
                      String result;
                      if (condition) {
                          System.out.println("True branch");
                          result = "true";
                      } else {
                          result = "false";
                      }
                  }
              }
              """
          )
        );
    }

    @Test
    void doNotChangeIfDifferentVariables() {
        rewriteRun(
          java(
            """
              class Test {
                  void method(boolean condition) {
                      String result1;
                      String result2;
                      if (condition) {
                          result1 = "true";
                      } else {
                          result2 = "false";
                      }
                  }
              }
              """
          )
        );
    }

    @Test
    void doNotChangeIfNotAssignment() {
        rewriteRun(
          java(
            """
              class Test {
                  void method(boolean condition) {
                      if (condition) {
                          System.out.println("true");
                      } else {
                          System.out.println("false");
                      }
                  }
              }
              """
          )
        );
    }

    @Test
    void convertNestedIfElseButAvoidNestedTernary() {
        rewriteRun(
          java(
            """
              class Test {
                  void method(boolean c1, boolean c2) {
                      String result;
                      if (c1) {
                          if (c2) {
                              result = "A";
                          } else {
                              result = "B";
                          }
                      } else {
                          result = "C";
                      }
                  }
              }
              """,
            """
              class Test {
                  void method(boolean c1, boolean c2) {
                      String result;
                      if (c1) {
                          result = c2 ? "A" : "B";
                      } else {
                          result = "C";
                      }
                  }
              }
              """
          )
        );
    }
}
