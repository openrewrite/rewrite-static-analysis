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
import org.junitpioneer.jupiter.ExpectedToFail;
import org.openrewrite.DocumentExample;
import org.openrewrite.Issue;
import org.openrewrite.test.RecipeSpec;
import org.openrewrite.test.RewriteTest;

import static org.openrewrite.java.Assertions.java;

class ReplaceStringBuilderWithStringTest implements RewriteTest {

    @Override
    public void defaults(RecipeSpec spec) {
        spec.recipe(new ReplaceStringBuilderWithString());
    }

    @DocumentExample
    @Test
    void replaceLiteralConcatenation() {
        rewriteRun(
          //language=java
          java(
            """
              class A {
                  void foo() {
                      String s = new StringBuilder().append("A").append("B").toString();
                  }
              }
              """,
            """
              class A {
                  void foo() {
                      String s = "A" + "B";
                  }
              }
              """
          )
        );
    }

    @Issue("https://github.com/openrewrite/rewrite-static-analysis/issues/88")
    @Test
    void replaceWhileMaintainingSpaces() {
        rewriteRun(
          //language=java
          java(
            """
              class A {
                  void foo() {
                      String scenarioOne = new StringBuilder()
                          .append("A")
                          .append("B")
                          .append("C")
                          .toString();
                      String scenarioTwo = new StringBuilder("A")
                          .append("B")
                          .append("C")
                          .toString();
                      String scenarioThree = new StringBuilder()
                          .append("A")
                          .append("B")
                          .append("C")
                          .append(testString())
                          .toString();
                  }

                  String testString() {
                      return "testString";
                  }
              }
              """,
            """
              class A {
                  void foo() {
                      String scenarioOne = "A" +
                          "B" +
                          "C";
                      String scenarioTwo = "A" +
                          "B" +
                          "C";
                      String scenarioThree = "A" +
                          "B" +
                          "C" +
                          testString();
                  }

                  String testString() {
                      return "testString";
                  }
              }
              """
          )
        );
    }

    @Test
    void replaceLiteralConcatenationWithReturn() {
        rewriteRun(
          //language=java
          java(
            """
              class A {
                  String foo() {
                      return new StringBuilder().append("A").append("B").toString();
                  }
              }
              """,
            """
              class A {
                  String foo() {
                      return "A" + "B";
                  }
              }
              """
          )
        );
    }

    @Test
    void replaceCombinedConcatenation() {
        rewriteRun(
          //language=java
          java(
            """
              class A {
                  String bar() {
                      String str1 = "Hello";
                      String str2 = name();
                      return new StringBuilder().append(str1).append(str2).append(getSuffix()).toString();
                  }
                  String name() {
                      return "world";
                  }
                  String getSuffix() {
                      return "!";
                  }
              }
              """,
            """
              class A {
                  String bar() {
                      String str1 = "Hello";
                      String str2 = name();
                      return str1 + str2 + getSuffix();
                  }
                  String name() {
                      return "world";
                  }
                  String getSuffix() {
                      return "!";
                  }
              }
              """
          )
        );
    }

    @Test
    void replaceInChainedMethods() {
        rewriteRun(
          //language=java
          java(
            """
              class A {
                  void foo() {
                      int len = new StringBuilder().append("A").append("B").toString().length();
                  }
              }
              """,
            """
              class A {
                  void foo() {
                      int len = ("A" + "B").length();
                  }
              }
              """
          )
        );
    }

    @Issue("https://github.com/openrewrite/rewrite/issues/2930")
    @Test
    void withConstructor() {
        rewriteRun(
          //language=java
          java(
            """
              class A {
                  void method() {
                      String key1 = new StringBuilder(10).append("_").append("a").toString();
                      String key2 = new StringBuilder(name()).append("_").append("a").toString();
                      String key3 = new StringBuilder("m").append("_").append("a").toString();
                      String key4 = new StringBuilder("A" + "B").append("C").toString();
                  }
                  String name() {
                      return "name";
                  }
              }
              """,
            """
              class A {
                  void method() {
                      String key1 = "_" + "a";
                      String key2 = name() + "_" + "a";
                      String key3 = "m" + "_" + "a";
                      String key4 = "A" + "B" + "C";
                  }
                  String name() {
                      return "name";
                  }
              }
              """
          )
        );
    }

    @Issue("https://github.com/openrewrite/rewrite-static-analysis/issues/88")
    @Test
    void retainComments() {
        rewriteRun(
          //language=java
          java(
            """
              class A {
                  void foo() {
                      String scenarioOne = new StringBuilder()
                          // A
                          .append("A")
                          // B
                          .append("B")
                          // C
                          .append("C")
                          .toString();
                      String scenarioTwo = new StringBuilder("A")
                          // B
                          .append("B")
                          // C
                          .append("C")
                          .toString();
                      String scenarioThree = new StringBuilder()
                          // A
                          .append("A")
                          // B
                          .append("B")
                          // C
                          .append("C")
                          // test method
                          .append(testString())
                          .toString();
                      String scenarioFour = new StringBuilder()
                          // 1 + 1
                          .append(1 + 1)
                          // 1
                          .append(1)
                          .toString();
                      String scenarioFive = new StringBuilder()
                          // A
                          .append("A")
                          // 1 + 1
                          .append(1 + 1)
                          // 1
                          .append(1)
                          .toString();
                  }

                  String testString() {
                      return "testString";
                  }
              }
              """,
            """
              class A {
                  void foo() {
                      String scenarioOne =
                          // A
                          "A" +
                          // B
                          "B" +
                          // C
                          "C";
                      String scenarioTwo = "A" +
                          // B
                          "B" +
                          // C
                          "C";
                      String scenarioThree =
                          // A
                          "A" +
                          // B
                          "B" +
                          // C
                          "C" +
                          // test method
                          testString();
                      String scenarioFour =
                          // 1 + 1
                          String.valueOf(1 + 1) +
                          // 1
                          1;
                      String scenarioFive =
                          // A
                          "A" +
                          // 1 + 1
                          (1 + 1) +
                          // 1
                          1;
                  }

                  String testString() {
                      return "testString";
                  }
              }
              """
          )
        );
    }

    @Test
    void objectsGrouping() {
        rewriteRun(
          //language=java
          java(
            """
              class A {
                  void method() {
                      String key0 = new StringBuilder().append(1 + 2).append(3).toString(); // "33"
                      String key1 = new StringBuilder().append(1.5).append(2).append(3).toString(); // "1.523"
                      String key2 = new StringBuilder("m").append(1 + 2).append(3).toString(); // "m33"
                      String key3 = new StringBuilder(10).append("_").append(1).toString(); // "_1"
                      String key4 = new StringBuilder("A" + "B").append(1 + 2).append(3).toString(); // "AB33"
                      String key5 = new StringBuilder().append(count()).append(1).toString(); // 101
                  }
                  int count() {
                      return 10;
                  }
              }
              """,
            """
              class A {
                  void method() {
                      String key0 = String.valueOf(1 + 2) + 3; // "33"
                      String key1 = "1.5" + 2 + 3; // "1.523"
                      String key2 = "m" + (1 + 2) + 3; // "m33"
                      String key3 = "_" + 1; // "_1"
                      String key4 = "A" + "B" + (1 + 2) + 3; // "AB33"
                      String key5 = String.valueOf(count()) + 1; // 101
                  }
                  int count() {
                      return 10;
                  }
              }
              """
          )
        );
    }

    @Test
    void builderToString() {
        rewriteRun(
          //language=java
          java(
            """
              class A {
                  void method1() {
                      CharSequence foo = "foo";
                      print("foo", new StringBuilder(foo).toString());
                  }

                  void print(String str, String str2) {
                      new StringBuilder(str).append(str2);
                  }
              }
              """
          )
        );
    }

    @Test
    void characterLiterals() {
        rewriteRun(
          //language=java
          java(
            """
              class A {
                  String track(Object consumer) {
                      return "tracked";
                  }

                  String method(String text, Object consumer) {
                      return new StringBuilder(50).append('[').append(track(consumer)).append("]: ").append(text).toString();
                  }
              }
              """,
            """
              class A {
                  String track(Object consumer) {
                      return "tracked";
                  }

                  String method(String text, Object consumer) {
                      return "[" + track(consumer) + "]: " + text;
                  }
              }
              """
          )
        );
    }

    @Test
    void charArrayAppendKeepsCharacterRendering() {
        rewriteRun(
          //language=java
          java(
            """
              class A {
                  String render(char[] chars) {
                      return new StringBuilder()
                              .append("prefix:")
                              .append(chars)
                              .toString();
                  }
              }
              """,
            """
              class A {
                  String render(char[] chars) {
                      return "prefix:" +
                              String.valueOf(chars);
                  }
              }
              """
          )
        );
    }

    @Test
    void charArrayAppendInEveryChainPosition() {
        rewriteRun(
          //language=java
          java(
            """
              class A {
                  String only(char[] chars) {
                      return new StringBuilder().append(chars).toString();
                  }
                  String first(char[] chars) {
                      return new StringBuilder().append(chars).append("suffix").toString();
                  }
                  String middle(char[] chars) {
                      return new StringBuilder().append("a").append(chars).append("b").toString();
                  }
                  String last(char[] chars) {
                      return new StringBuilder().append("prefix:").append(chars).toString();
                  }
                  String multiple(char[] a, char[] b) {
                      return new StringBuilder().append(a).append("-").append(b).toString();
                  }
              }
              """,
            """
              class A {
                  String only(char[] chars) {
                      return String.valueOf(chars);
                  }
                  String first(char[] chars) {
                      return String.valueOf(chars) + "suffix";
                  }
                  String middle(char[] chars) {
                      return "a" + String.valueOf(chars) + "b";
                  }
                  String last(char[] chars) {
                      return "prefix:" + String.valueOf(chars);
                  }
                  String multiple(char[] a, char[] b) {
                      return String.valueOf(a) + "-" + String.valueOf(b);
                  }
              }
              """
          )
        );
    }

    @Test
    void charArrayAppendOfAnyExpressionShape() {
        rewriteRun(
          //language=java
          java(
            """
              class A {
                  char[] field = {'f'};
                  char[] make() {
                      return new char[]{'m'};
                  }
                  String fromField() {
                      return new StringBuilder().append("a").append(field).toString();
                  }
                  String fromMethod() {
                      return new StringBuilder().append("a").append(make()).toString();
                  }
                  String fromNewArray() {
                      return new StringBuilder().append("a").append(new char[]{'n'}).toString();
                  }
                  String fromCast(Object o) {
                      return new StringBuilder().append("a").append((char[]) o).toString();
                  }
                  String fromTernary(boolean b, char[] x, char[] y) {
                      return new StringBuilder().append("a").append(b ? x : y).toString();
                  }
                  String fromNull() {
                      return new StringBuilder().append("a").append((char[]) null).toString();
                  }
              }
              """,
            """
              class A {
                  char[] field = {'f'};
                  char[] make() {
                      return new char[]{'m'};
                  }
                  String fromField() {
                      return "a" + String.valueOf(field);
                  }
                  String fromMethod() {
                      return "a" + String.valueOf(make());
                  }
                  String fromNewArray() {
                      return "a" + String.valueOf(new char[]{'n'});
                  }
                  String fromCast(Object o) {
                      return "a" + String.valueOf((char[]) o);
                  }
                  String fromTernary(boolean b, char[] x, char[] y) {
                      return "a" + String.valueOf(b ? x : y);
                  }
                  String fromNull() {
                      return "a" + String.valueOf((char[]) null);
                  }
              }
              """
          )
        );
    }

    @Test
    void doNotWrapObjectAppendHoldingCharArray() {
        rewriteRun(
          //language=java
          java(
            """
              class A {
                  String render(char[] chars) {
                      Object o = chars;
                      return new StringBuilder().append("a").append(o).toString();
                  }
              }
              """,
            """
              class A {
                  String render(char[] chars) {
                      Object o = chars;
                      return "a" + o;
                  }
              }
              """
          )
        );
    }

    @ExpectedToFail("Comment inside the append argument list is dropped when the chain is flattened")
    @Test
    void retainCommentInsideAppendArguments() {
        rewriteRun(
          //language=java
          java(
            """
              class A {
                  String render(char[] chars) {
                      return new StringBuilder().append("a").append(chars /* the array */).toString();
                  }
              }
              """,
            """
              class A {
                  String render(char[] chars) {
                      return "a" + String.valueOf(chars /* the array */);
                  }
              }
              """
          )
        );
    }

    @Test
    void doNotChangeCharArrayRangeAppend() {
        rewriteRun(
          //language=java
          java(
            """
              class A {
                  String render(char[] chars) {
                      return new StringBuilder().append("a").append(chars, 0, 2).toString();
                  }
              }
              """
          )
        );
    }

    @ExpectedToFail("append(char[], int, int) is not yet flattened to String.valueOf(char[], int, int); doNotChangeCharArrayRangeAppend pins the current conservative behavior")
    @Test
    void convertCharArrayRangeAppend() {
        rewriteRun(
          //language=java
          java(
            """
              class A {
                  String render(char[] chars) {
                      return new StringBuilder().append("a").append(chars, 0, 2).toString();
                  }
              }
              """,
            """
              class A {
                  String render(char[] chars) {
                      return "a" + String.valueOf(chars, 0, 2);
                  }
              }
              """
          )
        );
    }
}
