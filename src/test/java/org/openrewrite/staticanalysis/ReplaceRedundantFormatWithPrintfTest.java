/*
 * Copyright 2022 the original author or authors.
 * <p>
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * <p>
 * https://www.apache.org/licenses/LICENSE-2.0
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

class ReplaceRedundantFormatWithPrintfTest implements RewriteTest {

    @Override
    public void defaults(RecipeSpec spec) {
        spec.recipe(new ReplaceRedundantFormatWithPrintf());
    }

    @Test
    void doesNotModifyNonLiteralFormatStringForPrintln() {
        rewriteRun(
          //language=java
          java(
            """
              class Test {
                  void test(String arg) {
                      String formatString = "hello %s%n";
                      System.out.println(String.format(formatString, arg));
                  }
              }
              """
          )
        );
    }

    @DocumentExample
    @Test
    void transformsNonLiteralFormatStringForPrint() {
        rewriteRun(
          //language=java
          java(
            """
              class Test {
                  void test(String arg) {
                      String formatString = "hello %s%n";
                      System.out.print(String.format(formatString, arg));
                  }
              }
              """,
            """
              class Test {
                  void test(String arg) {
                      String formatString = "hello %s%n";
                      System.out.printf(formatString, arg);
                  }
              }
              """
          )
        );
    }

    @Test
    void modifiesCorrectArgumentGivenLocale() {
        rewriteRun(
          //language=java
          java(
            """
              import java.util.Locale;
              
              class Test {
                  void test(String arg) {
                      System.out.println(String.format(Locale.ENGLISH, "hello %s", arg));
                  }
              }
              """,
            """
              import java.util.Locale;

              class Test {
                  void test(String arg) {
                      System.out.printf(Locale.ENGLISH, "hello %s%n", arg);
                  }
              }
              """
          )
        );
    }

    @Test
    void transformsWhenTargetIsArbitraryPrintStreamSubclass() {
        rewriteRun(
          //language=java
          java(
            """              
              class Test {
                  static class PrintStreamSubclass extends java.io.PrintStream {}
             
                  void test(PrintStreamSubclass stream, String arg) {
                      stream.println(String.format("hello %s", arg));
                  }
              }
              """,
            """
              class Test {
                  static class PrintStreamSubclass extends java.io.PrintStream {}
                  
                  void test(PrintStreamSubclass stream, String arg) {
                      stream.printf("hello %s%n", arg);
                  }
              }
              """
          )
        );
    }

    @Test
    void transformsPrint() {
        rewriteRun(
          //language=java
          java(
            """
              class Test {
                  void test(String arg) {
                      System.out.print(String.format("hello %s", arg));
                  }
              }
              """,
            """
              class Test {
                  void test(String arg) {
                      System.out.printf("hello %s", arg);
                  }
              }
              """
          )
        );
    }

    @Test
    void transformsPrintWithTextBlockFormatString() {
        rewriteRun(
          //language=java
          java(
            """
              class Test {
                  void test(String arg) {
                      System.out.print(String.format(\"\"\"
                      hello %s\"\"\", arg));
                  }
              }
              """,
            """
              class Test {
                  void test(String arg) {
                      System.out.printf(\"\"\"
                      hello %s\"\"\", arg);
                  }
              }
              """
          )
        );
    }

    @Test
    void appendsNewlineForPrintln() {
        rewriteRun(
          //language=java
          java(
            """
              class Test {
                  void test(String arg) {
                      System.out.println(String.format("hello %s", arg));
                  }
              }
              """,
            """
              class Test {
                  void test(String arg) {
                      System.out.printf("hello %s%n", arg);
                  }
              }
              """
          )
        );
    }

    @Test
    void appendsNewlineForPrintlnWithTextBlockFormatString() {
        rewriteRun(
          //language=java
          java(
            """
              class Test {
                  void test(String arg) {
                      System.out.println(String.format(\"\"\"
                      hello %s\"\"\", arg));
                  }
              }
              """,
            """
              class Test {
                  void test(String arg) {
                      System.out.printf(\"\"\"
                      hello %s%n\"\"\", arg);
                  }
              }
              """
          )
        );
    }

    /**
     * Tests that the code generation used for JavaTemplate behaves correctly given a parameterized type.
     */
    @Test
    void doesNotFailWhenArgHasParameterizedType() {
        rewriteRun(
          //language=java
          java(
            """
              class Test {
                  void test() {
                      java.util.List<Integer> ints = new java.util.ArrayList<>();
                      System.out.print(String.format("hello %s", ints));
                  }
              }
              """,
            """
              class Test {
                  void test() {
                      java.util.List<Integer> ints = new java.util.ArrayList<>();
                      System.out.printf("hello %s", ints);
                  }
              }
              """
          )
        );
    }

    /**
     * Tests that the code generation used for JavaTemplate behaves correctly given a primitive type.
     */
    @Test
    void doesNotFailWhenArgHasPrimitiveType() {
        rewriteRun(
          //language=java
          java(
            """
              class Test {
                  void test() {
                      int value = 42;
                      System.out.print(String.format("hello %i", value));
                  }
              }
              """,
            """
              class Test {
                  void test() {
                      int value = 42;
                      System.out.printf("hello %i", value);
                  }
              }
              """
          )
        );
    }

    /**
     * Tests that the code generation used for JavaTemplate behaves correctly given a template parameter.
     */
    @Test
    void doesNotFailWhenArgHasTemplateParameter() {
        rewriteRun(
          //language=java
          java(
            """
              class Test<T> {
                  void test(T arg) {
                      System.out.print(String.format("hello %s", arg));
                  }
              }
              """,
            """
              class Test<T> {
                  void test(T arg) {
                      System.out.printf("hello %s", arg);
                  }
              }
              """
          )
        );
    }
}
