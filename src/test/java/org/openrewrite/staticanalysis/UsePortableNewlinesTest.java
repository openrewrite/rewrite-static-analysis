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

@SuppressWarnings("ConstantConditions")
class UsePortableNewlinesTest implements RewriteTest {

    @Override
    public void defaults(RecipeSpec spec) {
        spec.recipe(new UsePortableNewlines());
    }

    @DocumentExample
    @Test
    void replaceNewlineInPrintfWithPrintStream() {
        rewriteRun(
          java(
            """
              class Test {
                  void test(String name) {
                      System.out.printf("Hello %s\\n", name);
                  }
              }
              """,
            """
              class Test {
                  void test(String name) {
                      System.out.printf("Hello %s%n", name);
                  }
              }
              """
          )
        );
    }

    @Test
    void replaceNewlineInPrintfWithPrintWriter() {
        rewriteRun(
          java(
            """
              import java.io.PrintWriter;

              class Test {
                  void test(PrintWriter pw, String name) {
                      pw.printf("Hello %s\\n", name);
                  }
              }
              """,
            """
              import java.io.PrintWriter;

              class Test {
                  void test(PrintWriter pw, String name) {
                      pw.printf("Hello %s%n", name);
                  }
              }
              """
          )
        );
    }

    @Test
    void replaceMultipleNewlines() {
        rewriteRun(
          java(
            """
              class Test {
                  void test() {
                      String message = String.format("Line 1\\nLine 2\\nLine 3\\n");
                  }
              }
              """,
            """
              class Test {
                  void test() {
                      String message = String.format("Line 1%nLine 2%nLine 3%n");
                  }
              }
              """
          )
        );
    }

    @Test
    void replaceNewlineInFormattedMethod() {
        rewriteRun(
          java(
            """
              class Test {
                  void test(String name) {
                      String message = "Hello %s\\n".formatted(name);
                  }
              }
              """,
            """
              class Test {
                  void test(String name) {
                      String message = "Hello %s%n".formatted(name);
                  }
              }
              """
          )
        );
    }

    @Test
    void replaceNewlineInFormatterFormat() {
        rewriteRun(
          java(
            """
              import java.util.Formatter;

              class Test {
                  void test(String name) {
                      Formatter formatter = new Formatter();
                      formatter.format("Hello %s\\n", name);
                  }
              }
              """,
            """
              import java.util.Formatter;

              class Test {
                  void test(String name) {
                      Formatter formatter = new Formatter();
                      formatter.format("Hello %s%n", name);
                  }
              }
              """
          )
        );
    }

    @Test
    void replaceNewlineInConsolePrintf() {
        rewriteRun(
          java(
            """
              import java.io.Console;

              class Test {
                  void test(Console console, String name) {
                      console.printf("Hello %s\\n", name);
                  }
              }
              """,
            """
              import java.io.Console;

              class Test {
                  void test(Console console, String name) {
                      console.printf("Hello %s%n", name);
                  }
              }
              """
          )
        );
    }

    @Test
    void doNotChangeWhenAlreadyUsingPortableNewline() {
        rewriteRun(
          java(
            """
              class Test {
                  void test(String arg) {
                      String formatString = "hello %s%n";
                      System.out.print(String.format(formatString, arg));
                  }
              }
              """
          )
        );
    }

    @Test
    void doNotChangeNonFormatMethods() {
        rewriteRun(
          java(
            """
              class Test {
                  void test() {
                      String message = "Hello\\n";
                      System.out.println(message);
                  }
              }
              """
          )
        );
    }

    @Test
    void doNotChangeWhenNotLiteral() {
        rewriteRun(
          java(
            """
              class Test {
                  void test(String format, String arg) {
                      System.out.print(String.format(format, arg));
                  }
              }
              """
          )
        );
    }

    @Test
    void handleEscapedQuotes() {
        rewriteRun(
          java(
            """
              class Test {
                  void test() {
                      String message = String.format("Say \\"Hello\\"\\n");
                  }
              }
              """,
            """
              class Test {
                  void test() {
                      String message = String.format("Say \\"Hello\\"%n");
                  }
              }
              """
          )
        );
    }

    @Test
    void doNotReplaceWhenNotLiteral() {
        rewriteRun(
          java(
            """
              class Test {
                  void test(String arg) {
                      String formatString = "hello %s\\n";
                      System.out.print(String.format(formatString, arg));
                  }
              }
              """
          )
        );
    }

    @Test
    void doNotReplaceOtherNewlinesInTextBlocks() {
        rewriteRun(
          java(
            """
              class Test {
                  void test(String name) {
                      String message = String.format(\"""
                          Hello %s,\\n
                          Welcome to our application
                          Have a nice day!
                          \""", name);
                  }
              }
              """,
            """
              class Test {
                  void test(String name) {
                      String message = String.format(\"""
                          Hello %s,%n
                          Welcome to our application
                          Have a nice day!
                          \""", name);
                  }
              }
              """
          )
        );
    }

    @Test
    void doesNotCorruptEscapedBackslashNewlineInFormattedTextBlock() {
        rewriteRun(
          //language=java
          java(
            """
              class Test {
                  String script(String payload) {
                      return \"""
                          printf '%%s\\\\n' '%s'
                          \""".formatted(payload);
                  }
              }
              """
          )
        );
    }

    @Test
    void replaceNewlineWithoutChangingEscapedBackslashNewline() {
        rewriteRun(
          //language=java
          java(
            """
              class Test {
                  String message(String value) {
                      return String.format("line=%s\\nscript=printf '%%s\\\\n'", value);
                  }
              }
              """,
            """
              class Test {
                  String message(String value) {
                      return String.format("line=%s%nscript=printf '%%s\\\\n'", value);
                  }
              }
              """
          )
        );
    }

    @Test
    void accountForUnicodeEscapedBackslash() {
        rewriteRun(
          //language=java
          java(
            """
              class Test {
                  String message() {
                      return String.format("\\u005c\\\\n");
                  }
              }
              """,
            """
              class Test {
                  String message() {
                      return String.format("\\u005c\\%n");
                  }
              }
              """
          )
        );
    }

    @Test
    void accountForCrLfTextBlockContinuation() {
        rewriteRun(
          //language=java
          java(
            """
              class Test {
                  String message() {
                      return \"""
                          foo\\
                          bar\\n
                          \""".formatted();
                  }
              }
              """.replace("\n", "\r\n"),
            """
              class Test {
                  String message() {
                      return \"""
                          foo\\
                          bar%n
                          \""".formatted();
                  }
              }
              """.replace("\n", "\r\n")
          )
        );
    }

    @Test
    void accountForOctalLineFeedBeforeNewlineEscape() {
        rewriteRun(
          //language=java
          java(
            """
              class Test {
                  String message() {
                      return String.format("\\12\\n");
                  }
              }
              """,
            """
              class Test {
                  String message() {
                      return String.format("\\12%n");
                  }
              }
              """
          )
        );
    }

    @Test
    void detectUnicodeEscapedTextBlockDelimiter() {
        rewriteRun(
          //language=java
          java(
            """
              class Test {
                  String message() {
                      return String.format(\\u0022\\u0022\\u0022
                          first line
                          second line\\n
                          \\u0022\\u0022\\u0022);
                  }
              }
              """,
            """
              class Test {
                  String message() {
                      return String.format(\\u0022\\u0022\\u0022
                          first line
                          second line%n
                          \\u0022\\u0022\\u0022);
                  }
              }
              """
          )
        );
    }

    @Test
    void accountForUnicodeEligibilityAfterTranslatedBackslashes() {
        rewriteRun(
          //language=java
          java(
            """
              class Test {
                  String message() {
                      return String.format("§u005c§§§u006e");
                  }
              }
              """.replace("§", "\\")
          )
        );
    }
}
