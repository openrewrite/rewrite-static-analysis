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

@SuppressWarnings("ALL")
class FixStringFormatExpressionsTest implements RewriteTest {
    @Override
    public void defaults(RecipeSpec spec) {
        spec.recipe(new FixStringFormatExpressions());
    }

    @DocumentExample
    @Test
    void newLineFormat() {
        rewriteRun(
          //language=java
          java(
            """
              class T {
                  static {
                      String s = String.format("hello world\\n%s", "again");
                      String s2 = "hello world\\n%s".formatted("again");
                  }
              }
              """,
                """
              class T {
                  static {
                      String s = String.format("hello world%n%s", "again");
                      String s2 = "hello world%n%s".formatted("again");
                  }
              }
              """
          )
        );
    }

    @Test
    void trimUnusedArguments() {
        rewriteRun(
          //language=java
          java(
            """
              class T {
                  static {
                      String s = String.format("count: %d, %d, %d, %d", 1, 3, 2, 4, 5);
                      String f = "count: %d, %d, %d, %d".formatted(1, 3, 2, 4, 5);
                  }
              }
              """,
            """
              class T {
                  static {
                      String s = String.format("count: %d, %d, %d, %d", 1, 3, 2, 4);
                      String f = "count: %d, %d, %d, %d".formatted(1, 3, 2, 4);
                  }
              }
              """
          )
        );
    }

    @Test
    void doNotStripArgsWithNoSpecifiersEvenWithoutPlaceholders() {
        // With zero specifiers matched there is no evidence the args are unused, so leave the call
        // alone. Also covers `.formatted()`, whose index 0 is the first arg, not the format string.
        rewriteRun(
          //language=java
          java(
            """
              class T {
                  static {
                      String s = String.format("no specifiers here", 1, 2);
                      String f = "no specifiers here".formatted(1, 2);
                  }
              }
              """
          )
        );
    }

    @Test
    void stripArgsWhenRealSpecifierPresentDespiteUnresolvedPlaceholder() {
        // A real specifier is present, so trimming still applies despite the ambiguous `{}`.
        rewriteRun(
          //language=java
          java(
            """
              class T {
                  static {
                      String s = String.format("count: %d, and {}", 1, 2);
                  }
              }
              """,
            """
              class T {
                  static {
                      String s = String.format("count: %d, and {}", 1);
                  }
              }
              """
          )
        );
    }

    @Test
    void trimUnusedArgumentsWithLiteralPercentAndRealSpecifier() {
        // `%%` is not counted, but the real `%d` still trims its excess argument.
        rewriteRun(
          //language=java
          java(
            """
              class T {
                  static {
                      String s = String.format("100%% done: %d", 1, 2);
                  }
              }
              """,
            """
              class T {
                  static {
                      String s = String.format("100%% done: %d", 1);
                  }
              }
              """
          )
        );
    }

    @Test
    void doNotStripArgsWithUnresolvedPlaceholdersAndLiteralPercent() {
        // `%%`, like `%n`, consumes no argument, so `%%` plus `{}` still counts as zero specifiers.
        rewriteRun(
          //language=java
          java(
            """
              class T {
                  static {
                      String s = String.format("100%% {} sure, actual {}", 1, 2);
                  }
              }
              """
          )
        );
    }

    @Test
    void allArgsAreUsed() {
        rewriteRun(
          //language=java
          java(
            """
              class T {
                  static {
                      String s = String.format("count: %d, %d, %d, %d", 1, 3, 2, 4);
                      String f = "count: %d, %d, %d, %d".formatted(1, 3, 2, 4);
                  }
              }
              """
          )
        );
    }

    @Issue("https://github.com/openrewrite/rewrite-static-analysis/issues/260")
    @Test
    void escapedNewline() {
        rewriteRun(
          //language=java
          java(
            """
              class A {
                  String foo(String bar) {
                      return ""\"
                              \\n
                              \\\\n
                              \\\\\\n
                              \\\\\\\\n
                              ""\".formatted(bar);
                  }
              }
              """,
            """
              class A {
                  String foo(String bar) {
                      return ""\"
                              %n
                              \\\\n
                              \\\\\\n
                              \\\\\\\\n
                              ""\".formatted(bar);
                  }
              }
              """
          )
        );
    }

    @Test
    void doNotStripArgsWithUnresolvedPlaceholders() {
        // SLF4J-style `{}` is not a `String#format` specifier, so don't delete the args as dead code.
        rewriteRun(
          //language=java
          java(
            """
              class T {
                  static void reportMismatch(String label, String message) {}

                  static void test(int original, int shadow) {
                      if (original != shadow) {
                          reportMismatch("check", String.format("expect {}, actual {}", original, shadow));
                      }
                  }
              }
              """
          )
        );
    }

    @Test
    void doNotStripArgsWithUnresolvedPlaceholdersFormatted() {
        rewriteRun(
          //language=java
          java(
            """
              class T {
                  static void reportMismatch(String label, String message) {}

                  static void test(int original, int shadow) {
                      if (original != shadow) {
                          reportMismatch("check", "expect {}, actual {}".formatted(original, shadow));
                      }
                  }
              }
              """
          )
        );
    }

    @Test
    void newlineStillReplacedWhenArgsAreNotStripped() {
        // The bailout only skips arg-trimming; newline-to-%n replacement must still apply.
        rewriteRun(
          //language=java
          java(
            """
              class T {
                  static void test(int original, int shadow) {
                      String s = String.format("expect {}\\nactual {}", original, shadow);
                  }
              }
              """,
            """
              class T {
                  static void test(int original, int shadow) {
                      String s = String.format("expect {}%nactual {}", original, shadow);
                  }
              }
              """
          )
        );
    }

    @Test
    void shadowClientMismatchReportingIsNotCorrupted() {
        // Motivating bug: mismatch reports built with String#format but SLF4J-style `{}` placeholders,
        // where the recipe used to delete the args and reduce every report to "expect {}, actual {}".
        rewriteRun(
          //language=java
          java(
            """
              import java.util.concurrent.Future;

              class ShadowStatement {
                  void reportMismatch(String method, String reason) {}
                  void reportMismatch(String method, String sql, String reason) {}

                  int executeUpdate(String sql, Future<Integer> shadowFuture, int original) throws Exception {
                      int shadow = shadowFuture.get();
                      if (original != shadow) {
                          reportMismatch("execute", sql, String.format("expect {}, actual {}", original, shadow));
                      }
                      return original;
                  }

                  boolean getMoreResults(Future<Boolean> shadowFuture, boolean original) throws Exception {
                      boolean shadow = shadowFuture.get();
                      if (original != shadow) {
                          reportMismatch("getMoreResults", String.format("expect {}, actual {}", original, shadow));
                      }
                      return original;
                  }
              }
              """
          )
        );
    }

    @Test
    void textBlockWithNewlinesShouldNotBeModified() {
        // Text blocks contain actual newline characters in their value, not \n escape sequences.
        // The recipe should not modify these since changing actual newlines to %n in text blocks
        // would change the semantics and the valueSource wouldn't match the value.
        rewriteRun(
          //language=java
          java(
            """
              class T {
                  static {
                      String s = ""\"
                          Hello
                          World
                          ""\".formatted();
                  }
              }
              """
          )
        );
    }
}
