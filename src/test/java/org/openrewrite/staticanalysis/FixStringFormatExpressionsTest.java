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
        // `{}` is SLF4J-style placeholder syntax, not a `String#format` conversion specifier. A
        // format string using it has zero real `%` specifiers, so the naive fix treats `original`
        // and `shadow` as dead code and deletes them -- but they're almost certainly meant to be
        // substituted, just via the wrong API. Leaving the call alone keeps the underlying bug
        // (wrong templating syntax) visible instead of erasing the evidence of it.
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
        // The `{}`-unresolved-placeholder guard only skips the arg-trimming step; the unrelated
        // newline-to-%n replacement above it must still apply.
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
