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

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.openrewrite.DocumentExample;
import org.openrewrite.Issue;
import org.openrewrite.test.RecipeSpec;
import org.openrewrite.test.RewriteTest;

import static org.openrewrite.java.Assertions.java;

class UseStringReplaceTest implements RewriteTest {

    @Override
    public void defaults(RecipeSpec spec) {
        spec.recipe(new UseStringReplace());
    }

    @SuppressWarnings("ReplaceOnLiteralHasNoEffect")
    @Issue("https://github.com/openrewrite/rewrite/issues/2222")
    @Test
    void literalValueSourceAccountsForEscapeCharacters() {
        rewriteRun(
          //language=java
          java(
            """
              class A {
                  String s = "".replaceAll("\\n","\\r\\n");
              }
              """,
            """
              class A {
                  String s = "".replace("\\n","\\r\\n");
              }
              """
          )
        );
    }

    @Issue("https://github.com/openrewrite/rewrite/issues/1781")
    @Test
    void replaceAllContainsEscapedQuotes() {
        rewriteRun(
          //language=java
          java(
            """
              class Test {
                  public String method(String input) {
                      return input.replaceAll("\\"test\\"\\"", "");
                  }
              }
              """,
            """
              class Test {
                  public String method(String input) {
                      return input.replace("\\"test\\"\\"", "");
                  }
              }
              """
          )
        );
    }

    @Test
    @DisplayName("String#replaceAll replaced by String#replace, because first argument is not a regular expression")
    @DocumentExample
    void replaceAllReplacedByReplace() {
        rewriteRun(
          //language=java
          java(
            """
              class Test {
                  public void method() {
                      String someText = "Bob is a Bird... Bob is a Plane... Bob is Superman!";
                      String newText = someText.replaceAll("Bob is", "It's");
                  }
              }
              """,
            """
              class Test {
                  public void method() {
                      String someText = "Bob is a Bird... Bob is a Plane... Bob is Superman!";
                      String newText = someText.replace("Bob is", "It's");
                  }
              }
              """
          )
        );
    }

    @Test
    @DisplayName("String#replaceAll replaced by String#replace, because first argument is not a regular expression and it contains special characters")
    void replaceAllReplacedByReplaceWithSpecialCharacters() {
        rewriteRun(
          //language=java
          java(
            """
              class Test {
                  public void method() {
                      String someText = "Bob is a Bird... Bob is a Plane... Bob is Superman!";
                      String newText = someText.replaceAll("Bob is\\\\.", "It's");
                  }
              }
              """,
            """
              class Test {
                  public void method() {
                      String someText = "Bob is a Bird... Bob is a Plane... Bob is Superman!";
                      String newText = someText.replace("Bob is.", "It's");
                  }
              }
              """
          )
        );
    }

    @Test
    @DisplayName("String#replaceAll is not replaced by String#replace, because first argument is a real regular expression")
    void replaceAllUnchanged() {
        rewriteRun(
          //language=java
          java(
            """
              class Test {
                  public void method() {
                      String someText = "Bob is a Bird... Bob is a Plane... Bob is Superman!";
                      String newText = someText.replaceAll("\\\\w*\\\\sis", "It's");
                  }
              }
              """
          )
        );
    }

    @Test
    @Issue("https://github.com/openrewrite/rewrite-static-analysis/issues/301")
    @DisplayName("String#replaceAll is not replaced by String#replace, because second argument has a backslash in it")
    void replaceAllUnchangedIfBackslashInReplacementString() {
        rewriteRun(
          //language=java
          java(
            """
              class Test {
                public String method() {
                  return "abc".replaceAll("b", "\\\\\\\\\\\\\\\\");
                }
              }
              """
          )
        );
    }

    @Test
    @Issue("https://github.com/openrewrite/rewrite-static-analysis/issues/301")
    @DisplayName("String#replaceAll is not replaced by String#replace, because second argument has a dollar sign in it")
    void replaceAllUnchangedIfDollarInReplacementString() {
        rewriteRun(
          //language=java
          java(
            """
              class Test {
                public String method1() {
                  return "abc".replaceAll("b", "$0");
                }

                public String method2() {
                  String s = "$0";
                  return "abc".replaceAll("b", s);
                }
              }
              """
          )
        );
    }

    @Test
    @Issue("https://github.com/openrewrite/rewrite-static-analysis/issues/330")
    @DisplayName("String#replaceAll is not replaced by String#replace, because second argument has a dollar sign in it")
    void overlyCautiousEqualsSign() {
      java(
        """
          class A {
              String foo(String bar) {
                  return bar.replaceAll("=","|");
              }
          }
          """,
        """
          class A {
              String foo(String bar) {
                  return bar.replace("=","|");
              }
          }
          """
        );
    }

}
