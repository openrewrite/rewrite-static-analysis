/*
 * Copyright 2026 the original author or authors.
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

import static org.openrewrite.groovy.Assertions.groovy;
import static org.openrewrite.java.Assertions.java;

@SuppressWarnings({"PointlessBitwiseExpression", "unused"})
class RemoveUnnecessaryBitwiseOperationTest implements RewriteTest {

    @Override
    public void defaults(RecipeSpec spec) {
        spec.recipe(new RemoveUnnecessaryBitwiseOperation());
    }

    @DocumentExample
    @Test
    void removeIdentityOperations() {
        rewriteRun(
          //language=java
          java(
            """
              class Test {
                  void test(int x) {
                      int a = x & -1;
                      int b = -1 & x;
                      int c = x | 0;
                      int d = 0 | x;
                      int e = x ^ 0;
                      int f = 0 ^ x;
                  }
              }
              """,
            """
              class Test {
                  void test(int x) {
                      int a = x;
                      int b = x;
                      int c = x;
                      int d = x;
                      int e = x;
                      int f = x;
                  }
              }
              """
          )
        );
    }

    @Test
    void otherLiteralForms() {
        rewriteRun(
          //language=java
          java(
            """
              class Test {
                  void test(int x) {
                      int a = x & 0xFFFFFFFF;
                      int b = x & (-1);
                      int c = x & -(1);
                      int d = x & ~0;
                      int e = x | (0);
                      int f = x | +0;
                  }
              }
              """,
            """
              class Test {
                  void test(int x) {
                      int a = x;
                      int b = x;
                      int c = x;
                      int d = x;
                      int e = x;
                      int f = x;
                  }
              }
              """
          )
        );
    }

    @Test
    void longOperandWithSignExtendedIntLiteral() {
        rewriteRun(
          //language=java
          java(
            """
              class Test {
                  void test(long x) {
                      long a = x & -1;
                      long b = x & 0xFFFFFFFF;
                      long c = x & ~0L;
                      long d = x | 0L;
                      long e = x ^ 0;
                      long f = x & 0xFFFFFFFFL;
                  }
              }
              """,
            """
              class Test {
                  void test(long x) {
                      long a = x;
                      long b = x;
                      long c = x;
                      long d = x;
                      long e = x;
                      long f = x & 0xFFFFFFFFL;
                  }
              }
              """
          )
        );
    }

    @Test
    void unchangedWhenOperandIsPromoted() {
        rewriteRun(
          //language=java
          java(
            """
              class Test {
                  void test(byte b, short s, char c, int i, Integer boxed) {
                      int a = b | 0;
                      int d = s & -1;
                      int e = c ^ 0;
                      long f = i | 0L;
                      int g = boxed | 0;
                  }
              }
              """
          )
        );
    }

    @Test
    void unchangedWhenNotAnIdentityOnLiterals() {
        rewriteRun(
          //language=java
          java(
            """
              class Test {
                  static final int NO_FLAGS = 0;

                  void test(int x) {
                      int a = x & 0;
                      int b = x | -1;
                      int c = x ^ -1;
                      int d = x | NO_FLAGS;
                      x |= 0;
                      x &= -1;
                  }
              }
              """
          )
        );
    }

    @Test
    void removeParenthesesAroundKeptOperand() {
        rewriteRun(
          //language=java
          java(
            """
              class Test {
                  int test(int x, int y, String s) {
                      int a = (x | 0);
                      String b = s + (x ^ 0);
                      int c = -(x & -1);
                      int d = -(-1 | 0);
                      return (x | 0) & -1 ^ y;
                  }
              }
              """,
            """
              class Test {
                  int test(int x, int y, String s) {
                      int a = x;
                      String b = s + x;
                      int c = -x;
                      int d = -(-1);
                      return x ^ y;
                  }
              }
              """
          )
        );
    }

    @Test
    void keepsSeparatingWhitespace() {
        rewriteRun(
          //language=java
          java(
            """
              class Test {
                  static final int A = 1;

                  int count() {
                      return 1;
                  }

                  void use(int x) {
                      use( count() | 0);
                  }

                  int returnParenthesized(int x) {
                      return(x | 0);
                  }

                  int returnNegativeLiteral(int x) {
                      return-1&x;
                  }

                  int leftmostInEnclosingExpression(int x, int y) {
                      assert(x | 0) > 0;
                      if (y > 0) {
                          return(x | 0) > y ? 1 : 2;
                      }
                      return-1&x|y;
                  }

                  int switchExpression(int x) {
                      return switch (x) {
                          case(A | 0) -> 0;
                          default -> {
                              yield(x | 0);
                          }
                      };
                  }

                  int guard(Object o, int x) {
                      return switch (o) {
                          case Integer i when(x | 0) > 0 -> 1;
                          default -> 0;
                      };
                  }

                  @interface Ann {
                      int value() default(A | 0);
                  }
              }
              """,
            """
              class Test {
                  static final int A = 1;

                  int count() {
                      return 1;
                  }

                  void use(int x) {
                      use( count());
                  }

                  int returnParenthesized(int x) {
                      return x;
                  }

                  int returnNegativeLiteral(int x) {
                      return x;
                  }

                  int leftmostInEnclosingExpression(int x, int y) {
                      assert x > 0;
                      if (y > 0) {
                          return x > y ? 1 : 2;
                      }
                      return x|y;
                  }

                  int switchExpression(int x) {
                      return switch (x) {
                          case A -> 0;
                          default -> {
                              yield x;
                          }
                      };
                  }

                  int guard(Object o, int x) {
                      return switch (o) {
                          case Integer i when x > 0 -> 1;
                          default -> 0;
                      };
                  }

                  @interface Ann {
                      int value() default A;
                  }
              }
              """
          )
        );
    }

    @Test
    void keepsComments() {
        rewriteRun(
          //language=java
          java(
            """
              class Test {
                  void test(int x) {
                      int a = x /* before operator */ | 0;
                      int b = x | /* before literal */ 0;
                      int c = 0 | /* before operand */ x;
                      int d = (/* inside parentheses */ x | 0);
                  }
              }
              """,
            """
              class Test {
                  void test(int x) {
                      int a = x /* before operator */ | 0;
                      int b = x | /* before literal */ 0;
                      int c = 0 | /* before operand */ x;
                      int d = (/* inside parentheses */ x);
                  }
              }
              """
          )
        );
    }

    @Test
    void unchangedInGroovy() {
        rewriteRun(
          //language=groovy
          groovy(
            """
              int test(int x) {
                  x | 0
              }
              """
          )
        );
    }
}
