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

import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.Test;
import org.openrewrite.DocumentExample;
import org.openrewrite.java.JavaIsoVisitor;
import org.openrewrite.java.tree.J;
import org.openrewrite.test.RecipeSpec;
import org.openrewrite.test.RewriteTest;

import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
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
              """,
            spec -> spec.afterRecipe(cu -> assertThat(formatStringValue(cu))
              .isEqualTo("Hello %s,%n\nWelcome to our application\nHave a nice day!\n"))
          )
        );
    }

    @Test
    void doNotChangeEscapedBackslashFollowedByN() {
        rewriteRun(
          java(
            """
              class Test {
                  void test() {
                      String path = String.format("C:\\\\new %s", 1);
                      String page = String.format("\\\\newpage %n");
                  }
              }
              """
          )
        );
    }

    @Test
    void replaceOnlyNewlineEscapesNotPrecededByEscapedBackslash() {
        rewriteRun(
          java(
            """
              class Test {
                  void test() {
                      String a = String.format("\\\\\\newpage %n");
                      String b = String.format("aaa\\\\naaa\\\\\\\\naaa\\\\\\\\\\na");
                  }
              }
              """,
            """
              class Test {
                  void test() {
                      String a = String.format("\\\\%newpage %n");
                      String b = String.format("aaa\\\\naaa\\\\\\\\naaa\\\\\\\\%na");
                  }
              }
              """
          )
        );
    }

    @Test
    void doNotChangeExplicitCrLf() {
        rewriteRun(
          java(
            """
              class Test {
                  void test() {
                      String header = String.format("HTTP/1.1 %d OK\\r\\nContent-Length: %d\\r\\n", 200, 0);
                      String mixed = String.format("a\\r\\nb\\n");
                  }
              }
              """
          )
        );
    }

    private static @Nullable String formatStringValue(J.CompilationUnit cu) {
        var value = new AtomicReference<@Nullable String>();
        new JavaIsoVisitor<Integer>() {
            @Override
            public J.MethodInvocation visitMethodInvocation(J.MethodInvocation method, Integer p) {
                if (method.getArguments().getFirst() instanceof J.Literal literal) {
                    value.set((String) literal.getValue());
                }
                return method;
            }
        }.visit(cu, 0);
        return value.get();
    }
}
