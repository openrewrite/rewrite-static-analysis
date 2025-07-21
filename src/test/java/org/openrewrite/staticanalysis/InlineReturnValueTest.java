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

@SuppressWarnings("UnnecessaryLocalVariable")
class InlineReturnValueTest implements RewriteTest {
    @Override
    public void defaults(RecipeSpec spec) {
        spec.recipe(new InlineReturnValue());
    }

    @DocumentExample
    @Test
    void inlineSimpleReturnValue() {
        rewriteRun(
          //language=java
          java(
            """
              class Test {
                  String getString() {
                      String result = "Hello World";
                      return result;
                  }
              }
              """,
            """
              class Test {
                  String getString() {
                      return "Hello World";
                  }
              }
              """
          )
        );
    }

    @Test
    void inlineMethodCallReturnValue() {
        rewriteRun(
          //language=java
          java(
            """
              class Test {
                  String process(String input) {
                      String processed = input.toUpperCase();
                      return processed;
                  }
              }
              """,
            """
              class Test {
                  String process(String input) {
                      return input.toUpperCase();
                  }
              }
              """
          )
        );
    }

    @Test
    void inlineComplexExpression() {
        rewriteRun(
          //language=java
          java(
            """
              class Test {
                  int calculate(int a, int b) {
                      int sum = a + b * 2;
                      return sum;
                  }
              }
              """,
            """
              class Test {
                  int calculate(int a, int b) {
                      return a + b * 2;
                  }
              }
              """
          )
        );
    }

    @Test
    void doNotInlineWhenVariableUsedElsewhere() {
        rewriteRun(
          //language=java
          java(
            """
              class Test {
                  String process(String input) {
                      String result = input.trim();
                      System.out.println(result);
                      return result;
                  }
              }
              """
          )
        );
    }

    @Test
    void doNotInlineWhenNotLastStatement() {
        rewriteRun(
          //language=java
          java(
            """
              class Test {
                  String getString() {
                      String result = "Hello";
                      System.out.println("Debug");
                      return result;
                  }
              }
              """
          )
        );
    }

    @Test
    void doNotInlineWhenMultipleVariables() {
        rewriteRun(
          //language=java
          java(
            """
              class Test {
                  String getString() {
                      String a = "Hello", b = "World";
                      return a;
                  }
              }
              """
          )
        );
    }

    @Test
    void inlineWhenReturningSecondVariable() {
        rewriteRun(
          //language=java
          java(
            """
              class Test {
                  String getString() {
                      String a = "Hello";
                      String b = "World";
                      return b;
                  }
              }
              """,
            """
              class Test {
                  String getString() {
                      String a = "Hello";
                      return "World";
                  }
              }
              """
          )
        );
    }

    @Test
    void inlineAssignment() {
        rewriteRun(
          //language=java
          java(
            """
              class Test {
                  String getString() {
                      String result;
                      result = "Hello";
                      return result;
                  }
              }
              """,
            """
                class Test {
                    String getString() {
                        String result;
                        return "Hello";
                    }
                }
                """
          )
        );
    }

    @Test
    void inlineInNestedBlock() {
        rewriteRun(
          //language=java
          java(
            """
              class Test {
                  String getString(boolean flag) {
                      if (flag) {
                          String result = "Yes";
                          return result;
                      } else {
                          String result = "No";
                          return result;
                      }
                  }
              }
              """,
            """
              class Test {
                  String getString(boolean flag) {
                      if (flag) {
                          return "Yes";
                      } else {
                          return "No";
                      }
                  }
              }
              """
          )
        );
    }

    @Test
    void doNotInlineWhenReturnNotIdentifier() {
        rewriteRun(
          //language=java
          java(
            """
              class Test {
                  String getString() {
                      String result = "Hello";
                      return result.toUpperCase();
                  }
              }
              """
          )
        );
    }
}
