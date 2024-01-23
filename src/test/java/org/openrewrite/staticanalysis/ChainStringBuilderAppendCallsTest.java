/*
 * Copyright 2023 the original author or authors.
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

@SuppressWarnings({"StringConcatenationInsideStringBufferAppend", "StringBufferReplaceableByString"})
class ChainStringBuilderAppendCallsTest implements RewriteTest {
    @Override
    public void defaults(RecipeSpec spec) {
        spec.recipe(new ChainStringBuilderAppendCalls());
    }

    @DocumentExample(value = "Chain `StringBuilder.append()` calls instead of the '+' operator to efficiently concatenate strings and numbers.")
    @Test
    void objectsConcatenation() {
        rewriteRun(
          //language=java
          java(
            """
              class A {
                  void method1() {
                      StringBuilder sb = new StringBuilder();
                      String op = "+";
                      sb.append("A" + op + "B");
                      sb.append(1 + op + 2);
                  }
              }
              """,
            """
              class A {
                  void method1() {
                      StringBuilder sb = new StringBuilder();
                      String op = "+";
                      sb.append("A").append(op).append("B");
                      sb.append(1).append(op).append(2);
                  }
              }
              """
          )
        );
    }

    @Test
    void literalConcatenationIgnored() {
        rewriteRun(
          //language=java
          java(
            """
              class A {
                  void method1() {
                      StringBuilder sb = new StringBuilder();
                      sb.append("A" + "B" + "C");
                  }
              }
              """
          )
        );
    }

    @DocumentExample("Grouping concatenation.")
    @Test
    void groupedStringsConcatenation() {
        rewriteRun(
          //language=java
          java(
            """
              class A {
                  void method1() {
                      StringBuilder sb = new StringBuilder();
                      String op = "+";
                      sb.append("A" + "B" + "C" + op + "D" + "E");
                  }
              }
              """,
            """
              class A {
                  void method1() {
                      StringBuilder sb = new StringBuilder();
                      String op = "+";
                      sb.append("A" + "B" + "C").append(op).append("D" + "E");
                  }
              }
              """
          )
        );
    }

    @Test
    void unWrap() {
        rewriteRun(
          //language=java
          java(
            """
              class A {
                  void method1() {
                      StringBuilder sb = new StringBuilder();
                      String op = "+";
                      sb.append(("A" + op + "B"));
                  }
              }
              """,
            """
              class A {
                  void method1() {
                      StringBuilder sb = new StringBuilder();
                      String op = "+";
                      sb.append("A").append(op).append("B");
                  }
              }
              """
          )
        );
    }

    @Test
    void chainedAppend() {
        rewriteRun(
          //language=java
          java(
            """
              class A {
                  void method1() {
                      StringBuilder sb = new StringBuilder();
                      String op = "+";
                      sb.append(("A" + op)).append("B");
                  }
              }
              """,
            """
              class A {
                  void method1() {
                      StringBuilder sb = new StringBuilder();
                      String op = "+";
                      sb.append("A").append(op).append("B");
                  }
              }
              """
          )
        );
    }

    @Test
    void runMultipleTimes() {
        rewriteRun(
          //language=java
          java(
            """
              class A {
                  void method1() {
                      StringBuilder sb = new StringBuilder();
                      String op = "+";
                      sb.append(("A" + op) + "B");
                  }
              }
              """,
            """
              class A {
                  void method1() {
                      StringBuilder sb = new StringBuilder();
                      String op = "+";
                      sb.append("A").append(op).append("B");
                  }
              }
              """
          )
        );
    }

    @Test
    void correctlyGroupConcatenations() {
        rewriteRun(
          //language=java
          java(
            """
              class A {
                  void method1() {
                      StringBuilder sb = new StringBuilder();
                      String op = "+";
                      sb.append(op + 1 + 2 + "A" + "B" + 'x');
                      sb.append(1 + 2 + op + 3 + 4);
                      sb.append(1 + 2 + name() + 3 + 4);
                      sb.append(op + (1 + 2));
                  }
                  String name() { return "name"; }
              }
              """,
            """
              class A {
                  void method1() {
                      StringBuilder sb = new StringBuilder();
                      String op = "+";
                      sb.append(op).append(1).append(2).append("A" + "B").append('x');
                      sb.append(1 + 2).append(op).append(3).append(4);
                      sb.append(1 + 2).append(name()).append(3).append(4);
                      sb.append(op).append(1 + 2);
                  }
                  String name() { return "name"; }
              }
              """
          )
        );
    }

    @Test
    void appendMethods() {
        rewriteRun(
          //language=java
          java(
            """
              class A {
                  void method1() {
                      StringBuilder sb = new StringBuilder();
                      sb.append(str1() + str2() + str3());
                  }

                  String str1() { return "A"; }
                  String str2() { return "B"; }
                  String str3() { return "C"; }
              }
              """,
            """
              class A {
                  void method1() {
                      StringBuilder sb = new StringBuilder();
                      sb.append(str1()).append(str2()).append(str3());
                  }

                  String str1() { return "A"; }
                  String str2() { return "B"; }
                  String str3() { return "C"; }
              }
              """
          )
        );
    }

    @Test
    void ChainedAppendWithConstructor() {
        rewriteRun(
          //language=java
          java(
            """
              class A {
                  void method1() {
                      StringBuilder sb = new StringBuilder().append("A" + operator() + "B");
                  }

                  String operator() { return "+"; }
              }
              """,
            """
              class A {
                  void method1() {
                      StringBuilder sb = new StringBuilder().append("A").append(operator()).append("B");
                  }

                  String operator() { return "+"; }
              }
              """
          )
        );
    }

    @Test
    void methodArgument() {
        rewriteRun(
          //language=java
          java(
            """
              class A {
                  void method1() {
                      String op = "+";
                      print(new StringBuilder().append("A" + op + "C").toString());
                  }

                  void print(String str) {
                  }
              }
              """,
            """
              class A {
                  void method1() {
                      String op = "+";
                      print(new StringBuilder().append("A").append(op).append("C").toString());
                  }

                  void print(String str) {
                  }
              }
              """
          )
        );
    }
}
