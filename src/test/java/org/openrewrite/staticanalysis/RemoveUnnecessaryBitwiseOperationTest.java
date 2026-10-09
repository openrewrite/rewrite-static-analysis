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
                      x |= 1;
                      x &= 0;
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
    void keepsCommentsOnTheirSide() {
        rewriteRun(
          //language=java
          java(
            """
              class Test {
                  void use(int v, int w) {
                  }

                  long test(int x, int y) {
                      int a = x /* before operator */ | 0;
                      int b = x | /* before literal */ 0;
                      int c = 0 | /* before operand */ x;
                      int d = (/* inside parentheses */ x | 0);
                      int e = (x | 0 /* trailing */);
                      int f = x /* left */ & -1 | y;
                      int g = 1 + (x /* nested */ | 0);
                      int h = x |= /* compound */ 0, i = 2;
                      use(x /* argument */ | 0, (x) /* parenthesized */ ^ 0);
                      long j = (long) (x /* cast */ | 0);
                      return(/* returned */ x | 0);
                  }
              }
              """,
            """
              class Test {
                  void use(int v, int w) {
                  }

                  long test(int x, int y) {
                      int a = x /* before operator */;
                      int b = x /* before literal */;
                      int c = /* before operand */ x;
                      int d = /* inside parentheses */ x;
                      int e = x /* trailing */;
                      int f = x /* left */ | y;
                      int g = 1 + x /* nested */;
                      int h = x /* compound */, i = 2;
                      use(x /* argument */, x /* parenthesized */);
                      long j = (long) x /* cast */;
                      return/* returned */ x;
                  }
              }
              """
          )
        );
    }

    @Test
    void keepsLineCommentOnItsLine() {
        rewriteRun(
          //language=java
          java(
            """
              class Test {
                  void test(int x) {
                      int f = x // line
                              | 0;
                  }
              }
              """,
            """
              class Test {
                  void test(int x) {
                      int f = x // line
                              ;
                  }
              }
              """
          )
        );
    }

    @Test
    void keepsCommentsOnTheirSideAcrossTernaryAssertAndAnnotationDefault() {
        rewriteRun(
          //language=java
          java(
            """
              class Test {
                  static final int A = 1;

                  int test(int x, int y, boolean c) {
                      int a = c ? x /* true */ | 0 : y;
                      int b = (x /* condition */ | 0) > y ? x : y;
                      int d = c ? y : x /* false */ | 0;
                      assert (x /* assertion */ | 0) > 0 : "message";
                      assert y < (x /* last */ | 0);
                      return a + b + d;
                  }

                  @interface Ann {
                      int value() default A /* default */ | 0;
                  }
              }
              """,
            """
              class Test {
                  static final int A = 1;

                  int test(int x, int y, boolean c) {
                      int a = c ? x /* true */ : y;
                      int b = x /* condition */ > y ? x : y;
                      int d = c ? y : x /* false */;
                      assert x /* assertion */ > 0 : "message";
                      assert y < x /* last */;
                      return a + b + d;
                  }

                  @interface Ann {
                      int value() default A /* default */;
                  }
              }
              """
          )
        );
    }

    @Test
    void removeParenthesesInCasts() {
        rewriteRun(
          //language=java
          java(
            """
              class Test {
                  long test(int x, int[] a) {
                      long b = (long)(x | 0);
                      long c = (long) (x | 0);
                      long d = (long)((x | 0));
                      long e = (long) (a[0] & -1);
                      long f = (long) (x + 1 | 0);
                      return (long) (x |= 0);
                  }
              }
              """,
            """
              class Test {
                  long test(int x, int[] a) {
                      long b = (long) x;
                      long c = (long) x;
                      long d = (long) x;
                      long e = (long) a[0];
                      long f = (long) (x + 1);
                      return (long) x;
                  }
              }
              """
          )
        );
    }

    @Test
    void removeRedundantParenthesesFromKeptOperand() {
        rewriteRun(
          //language=java
          java(
            """
              class Test {
                  void test(int x, int y) {
                      int a = ((x) | 0);
                      int b = (x) | 0;
                      int c = ((x)) & -1;
                      int d = (x + y) | 0;
                  }
              }
              """,
            """
              class Test {
                  void test(int x, int y) {
                      int a = x;
                      int b = x;
                      int c = x;
                      int d = (x + y);
                  }
              }
              """
          )
        );
    }

    @Test
    void removeNoOpCompoundAssignmentStatements() {
        rewriteRun(
          //language=java
          java(
            """
              class Test {
                  static int counter;
                  int field;

                  void test(int x, long l, byte b, char c) {
                      x |= 0;
                      x &= -1;
                      x ^= 0;
                      l &= 0xFFFFFFFF;
                      b |= 0;
                      c ^= 0;
                      field &= -1;
                      this.field |= 0;
                      Test.counter ^= 0;
                      System.out.println(x);
                  }
              }
              """,
            """
              class Test {
                  static int counter;
                  int field;

                  void test(int x, long l, byte b, char c) {
                      System.out.println(x);
                  }
              }
              """
          )
        );
    }

    @Test
    void replaceNoOpCompoundAssignmentUsedAsValue() {
        rewriteRun(
          //language=java
          java(
            """
              class Test {
                  void use(int v) {
                  }

                  int test(int x, int[] a, int i, byte b) {
                      int y = x |= 0;
                      use(x &= -1);
                      int z = a[i++] |= 0;
                      int w = (x ^= 0) + 1;
                      byte v = (b |= 0);
                      y = x |= 0;
                      return(x |= 0);
                  }
              }
              """,
            """
              class Test {
                  void use(int v) {
                  }

                  int test(int x, int[] a, int i, byte b) {
                      int y = x;
                      use(x);
                      int z = a[i++];
                      int w = x + 1;
                      byte v = b;
                      y = x;
                      return x;
                  }
              }
              """
          )
        );
    }

    @Test
    void replaceNoOpCompoundAssignmentBodiesWithEmptyBlock() {
        rewriteRun(
          //language=java
          java(
            """
              import java.util.function.IntSupplier;

              class Test {
                  int count;

                  void test(int x, boolean c, int[] a) {
                      if (c) x |= 0;
                      if (c) x |= 0; else x &= -1;
                      while (c) x ^= 0;
                      do x |= 0; while (c);
                      for (int v : a) x |= 0;
                      for (int j = 0; j < 1; j++, x |= 0) {
                      }
                      for (int j = 0; j < 1; x |= 0) {
                      }
                      label: x |= 0;
                      Runnable r = () -> count |= 0;
                      IntSupplier s = () -> count |= 0;
                      switch (x) {
                          case 1 -> x |= 0;
                          default -> {
                          }
                      }
                      switch (x) {
                          case 1:
                              x |= 0;
                              break;
                          default:
                      }
                      int y = switch (x) {
                          case 1 -> x |= 0;
                          default -> 0;
                      };
                  }
              }
              """,
            """
              import java.util.function.IntSupplier;

              class Test {
                  int count;

                  void test(int x, boolean c, int[] a) {
                      if (c) {}
                      if (c) {} else {}
                      while (c) {}
                      do {} while (c);
                      for (int v : a) {}
                      for (int j = 0; j < 1; j++) {
                      }
                      for (int j = 0; j < 1;) {
                      }
                      label: {}
                      Runnable r = () -> {};
                      IntSupplier s = () -> count;
                      switch (x) {
                          case 1 -> {}
                          default -> {
                          }
                      }
                      switch (x) {
                          case 1:
                              break;
                          default:
                      }
                      int y = switch (x) {
                          case 1 -> x;
                          default -> 0;
                      };
                  }
              }
              """
          )
        );
    }

    @Test
    void unchangedNoOpCompoundAssignmentStatements() {
        rewriteRun(
          //language=java
          java(
            """
              class Test {
                  volatile int flags;
                  int count;
                  Test next;

                  void test(int x, int i, int[] a, boolean c, Integer boxed) {
                      a[i++] |= 0;
                      next.count |= 0;
                      flags |= 0;
                      boxed |= 0;
                      if (c) a[i++] |= 0;
                      switch (x) {
                          case 1:
                              x |= 0;
                      }
                      x |= 0; // trailing
                      // leading
                      x |= 0;
                      x |= /* inner */ 0;
                      System.out.println(x);
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
