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

class NoValueOfOnStringTypeTest implements RewriteTest {

    @Override
    public void defaults(RecipeSpec spec) {
        spec.recipe(new NoValueOfOnStringType());
    }

    @Test
    @DocumentExample
    @SuppressWarnings({
    "UnnecessaryCallToStringValueOf",
    "UnusedAssignment",
    "StringConcatenationMissingWhitespace",
    })
    void valueOfOnLiterals() {
        rewriteRun(
          //language=java
          java(
            """
              class Test {
                  static void method(char[] data) {
                      String str = String.valueOf("changeMe");
                      str = String.valueOf(0);
                      str = "changeMe" + String.valueOf(0);
                      str = String.valueOf(data);
                      str = "changeMe" + String.valueOf(data);
                      str = String.valueOf(data, 0, 0);
                      str = "doNotChangeMe" + String.valueOf(data, 0, 0);
                  }
              }
              """,
              """
              class Test {
                  static void method(char[] data) {
                      String str = "changeMe";
                      str = String.valueOf(0);
                      str = "changeMe" + 0;
                      str = String.valueOf(data);
                      str = "changeMe" + String.valueOf(data);
                      str = String.valueOf(data, 0, 0);
                      str = "doNotChangeMe" + String.valueOf(data, 0, 0);
                  }
              }
              """
          )
        );
    }

    @Test
    void doNotChangeOnObject() {
        rewriteRun(
          //language=java
          java(
            """
              class Test {
                  static String method(Object obj) {
                      return String.valueOf(obj);
                  }
              }
              """
          )
        );
    }

    @Test
    void isMethodInvocationSelect() {
        rewriteRun(
          //language=java
          java(
            """
              class Test {
                  String trimPropertyName(String propertyName) {
                      return String.valueOf(propertyName).trim();
                  }
              }
              """
          )
        );
    }

    @Test
    @SuppressWarnings("UnnecessaryCallToStringValueOf")
    void valueOfOnNonStringPrimitiveWithinBinaryConcatenation() {
        rewriteRun(
          //language=java
          java(
            """
              class Test {
                  static void count(int i) {
                      System.out.println("Count: " + String.valueOf(i));
                  }
              }
              """,
              """
              class Test {
                  static void count(int i) {
                      System.out.println("Count: " + i);
                  }
              }
              """
          )
        );
    }

    @Test
    void valueOfOnNonStringPrimitiveWithinBinaryNotAString() {
        rewriteRun(
          //language=java
          java(
            """
              class Test {
                  static void count(int i) {
                      String fred = String.valueOf(i) + i;
                  }
              }
              """
          )
        );
    }

    @Test
    @Issue("https://github.com/openrewrite/rewrite-static-analysis/issues/456")
    void valueOfOnNonStringPrimitiveWithBinaryArgument() {
        rewriteRun(
          //language=java
          java(
            """
              class Test {
                  static void method(int i) {
                      String s = String.valueOf(41 + 1);
                 }
              }
              """
          )
        );
    }

    @Test
    @Issue("https://github.com/openrewrite/rewrite/issues/1200")
    void valueOfIsMethodInvocationPartOfBinary() {
        rewriteRun(
          //language=java
          java(
            """
              class Test {
                  static String method(Long id) {
                      return "example" + Test.method(String.valueOf(id));
                  }

                  static String method(String str) {
                      return str;
                  }
              }
              """
          )
        );
    }

    @Test
    @SuppressWarnings({"UnnecessaryCallToStringValueOf", "StringConcatenationMissingWhitespace"})
    void valueOfOnStandaloneNonStringPrimitive() {
        rewriteRun(
          //language=java
          java(
            """
              class Test {
                  static void method(int i) {
                      String str = String.valueOf(i) + "example";
                  }
              }
              """,
            """
              class Test {
                  static void method(int i) {
                      String str = i + "example";
                  }
              }
              """
          )
        );
    }

    @Test
    void concatenationResultingInNonString() {
        rewriteRun(
          //language=java
          java(
            """
              class Test {
                  static void method(int i) {
                      String str = i + String.valueOf(i);
                  }
              }
              """
          )
        );
    }

    @Test
    @Issue("https://github.com/openrewrite/rewrite/issues/1200")
    @SuppressWarnings({"IndexOfReplaceableByContains", "StatementWithEmptyBody"})
    void valueOfOnIntWithinBinaryComparison() {
        rewriteRun(
          //language=java
          java(
            """
              class Test {
                  static void method(String str, int i) {
                      if (str.indexOf(String.valueOf(i)) >= 0) {
                          // do nothing
                      }
                  }
              }
              """
          )
        );
    }

    @Test
    @SuppressWarnings("UnnecessaryCallToStringValueOf")
    void valueOfOnMethodInvocation() {
        rewriteRun(
          //language=java
          java(
            """
              class Test {
                  static void method1() {
                      String a = String.valueOf(method2());
                  }

                  static String method2() {
                      return "";
                  }
              }
              """,
            """
              class Test {
                  static void method1() {
                      String a = method2();
                  }

                  static String method2() {
                      return "";
                  }
              }
              """
          )
        );
    }

    @Test
    @Issue("https://github.com/openrewrite/rewrite-static-analysis/issues/441")
    void concatenationExpressionNeedsParentheses() {
        rewriteRun(
          //language=java
          java(
            """
              class Test {
                  static void method(int i) {
                      String parens = "prefix" + String.valueOf(i - 1);
                  }
              }
              """,
            """
              class Test {
                  static void method(int i) {
                      String parens = "prefix" + (i - 1);
                  }
              }
              """
          )
        );
    }
}
