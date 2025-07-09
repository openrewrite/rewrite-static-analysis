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

@SuppressWarnings("StringConcatenationMissingWhitespace")
class ReplaceStringConcatenationWithStringValueOfTest implements RewriteTest {

    @Override
    public void defaults(RecipeSpec spec) {
        spec.recipe(new ReplaceStringConcatenationWithStringValueOf());
    }

    @DocumentExample
    @Test
    void replaceIntegerConcatenation() {
        rewriteRun(
          //language=java
          java(
            """
              class Test {
                  void method() {
                      String s = "" + 1;
                  }
              }
              """,
            """
              class Test {
                  void method() {
                      String s = String.valueOf(1);
                  }
              }
              """
          )
        );
    }

    @Test
    void replaceBooleanConcatenation() {
        rewriteRun(
          //language=java
          java(
            """
              class Test {
                  void method() {
                      String s = "" + true;
                  }
              }
              """,
            """
              class Test {
                  void method() {
                      String s = String.valueOf(true);
                  }
              }
              """
          )
        );
    }

    @Test
    void replaceDoubleConcatenation() {
        rewriteRun(
          //language=java
          java(
            """
              class Test {
                  void method() {
                      String s = "" + 3.14;
                  }
              }
              """,
            """
              class Test {
                  void method() {
                      String s = String.valueOf(3.14);
                  }
              }
              """
          )
        );
    }

    @Test
    void replaceCharConcatenation() {
        rewriteRun(
          //language=java
          java(
            """
              class Test {
                  void method() {
                      String s = "" + 'a';
                  }
              }
              """,
            """
              class Test {
                  void method() {
                      String s = String.valueOf('a');
                  }
              }
              """
          )
        );
    }

    @Test
    void replaceObjectConcatenation() {
        rewriteRun(
          //language=java
          java(
            """
              class Test {
                  void method() {
                      Object obj = new Object();
                      String s = "" + obj;
                  }
              }
              """,
            """
              class Test {
                  void method() {
                      Object obj = new Object();
                      String s = String.valueOf(obj);
                  }
              }
              """
          )
        );
    }

    @Test
    void replaceMethodCallConcatenation() {
        rewriteRun(
          //language=java
          java(
            """
              class Test {
                  void method() {
                      String s = "" + System.currentTimeMillis();
                  }
              }
              """,
            """
              class Test {
                  void method() {
                      String s = String.valueOf(System.currentTimeMillis());
                  }
              }
              """
          )
        );
    }

    @Test
    void replaceExpressionConcatenation() {
        rewriteRun(
          //language=java
          java(
            """
              class Test {
                  void method(int a, int b) {
                      String s = "" + (a + b);
                  }
              }
              """,
            """
              class Test {
                  void method(int a, int b) {
                      String s = String.valueOf(a + b);
                  }
              }
              """
          )
        );
    }

    @Test
    void doNotChangeStringConcatenation() {
        rewriteRun(
          //language=java
          java(
            """
              class Test {
                  void method() {
                      String s = "" + "hello";
                  }
              }
              """
          )
        );
    }

    @Test
    void doNotChangeNullConcatenation() {
        rewriteRun(
          //language=java
          java(
            """
              class Test {
                  void method() {
                      String s = "" + null;
                  }
              }
              """
          )
        );
    }

    @Test
    void doNotChangeNonEmptyStringConcatenation() {
        rewriteRun(
          //language=java
          java(
            """
              class Test {
                  void method() {
                      String s = "prefix: " + 123;
                  }
              }
              """
          )
        );
    }

    @Test
    void doNotChangeWhenEmptyStringIsOnRight() {
        rewriteRun(
          //language=java
          java(
            """
              class Test {
                  void method() {
                      String s = 123 + "";
                  }
              }
              """
          )
        );
    }

    @Test
    void replaceInComplexExpression() {
        rewriteRun(
          //language=java
          java(
            """
              class Test {
                  void method() {
                      String result = "Result: " + ("" + 42);
                  }
              }
              """,
            """
              class Test {
                  void method() {
                      String result = "Result: " + (String.valueOf(42));
                  }
              }
              """
          )
        );
    }

    @Test
    void preserveComments() {
        rewriteRun(
          //language=java
          java(
            """
              class Test {
                  void method() {
                      String s = "" + /* comment */ 123;
                  }
              }
              """,
            """
              class Test {
                  void method() {
                      String s = String.valueOf(123);
                  }
              }
              """
          )
        );
    }
}
