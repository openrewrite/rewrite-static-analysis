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

/*
 * Copyright 2020 the original author or authors.
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

import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.openrewrite.DocumentExample;
import org.openrewrite.Issue;
import org.openrewrite.Tree;
import org.openrewrite.java.JavaParser;
import org.openrewrite.java.style.UnnecessaryParenthesesStyle;
import org.openrewrite.style.NamedStyles;
import org.openrewrite.test.RecipeSpec;
import org.openrewrite.test.RewriteTest;

import java.util.function.Consumer;
import java.util.function.UnaryOperator;

import static java.util.Collections.emptySet;
import static java.util.Collections.singletonList;
import static org.openrewrite.java.Assertions.java;

@SuppressWarnings({"ConstantConditions", "UnusedAssignment", "IdempotentLoopBody", "ParameterCanBeLocal", "UnnecessaryLocalVariable"})
class UnnecessaryParenthesesTest implements RewriteTest {
    @Override
    public void defaults(RecipeSpec spec) {
        spec.recipe(new UnnecessaryParentheses());
    }

    private static Consumer<RecipeSpec> unnecessaryParentheses(UnaryOperator<UnnecessaryParenthesesStyle> with) {
        return spec -> spec.parser(JavaParser.fromJavaVersion().styles(
          singletonList(
            new NamedStyles(
              Tree.randomId(), "test", "test", "test", emptySet(),
              singletonList(with.apply(new UnnecessaryParenthesesStyle(false, false, false, false, false,
                false, false, false, false, false, false, false, false, false,
                false, false, false, false, false, false, false, false, false))))))
        );
    }

    @Issue("https://github.com/openrewrite/rewrite/issues/2170")
    @Test
    void minimumSpace() {
        //language=java
        rewriteRun(
          java(
            """
              class Test {
                  int test() {
                      return (1);
                  }
              }
              """,
            """
              class Test {
                  int test() {
                      return 1;
                  }
              }
              """
          )
        );
    }

    @DocumentExample
    @Test
    void fullUnwrappingDefault() {
        //language=java
        rewriteRun(
          java(
            """
              import java.util.*;

              class Test {
                  int square(int a, int b) {
                      int square = (a * b);

                      int sumOfSquares = 0;
                      for (int i = (0); i < 10; i++) {
                          sumOfSquares += (square(i * i, i));
                      }
                      double num = (10.0);

                      List<String> list = Arrays.asList("a1", "b1", "c1");
                      list.stream()
                              .filter((s) -> s.startsWith("c"))
                              .forEach(System.out::println);

                      return (square);
                  }
              }
              """,
            """
              import java.util.*;

              class Test {
                  int square(int a, int b) {
                      int square = a * b;

                      int sumOfSquares = 0;
                      for (int i = 0; i < 10; i++) {
                          sumOfSquares += square(i * i, i);
                      }
                      double num = 10.0;

                      List<String> list = Arrays.asList("a1", "b1", "c1");
                      list.stream()
                              .filter(s -> s.startsWith("c"))
                              .forEach(System.out::println);

                      return square;
                  }
              }
              """
          )
        );
    }

    @Issue("https://github.com/openrewrite/rewrite/issues/798")
    @Disabled
    @Test
    void unwrapExpr() {
        //language=java
        rewriteRun(
          unnecessaryParentheses(style -> style.withExpr(true)),
          java(
            """
              class Test {
                  void method(int x, int y, boolean a) {
                      if (a && ((x + y > 0))) {
                          int q = ((1 + 2) + 3);
                          int z = (q + q) * q;
                      }
                  }
              }
              """,
            """
              class Test {
                  void method(int x, int y, boolean a) {
                      if (a && (x + y > 0)) {
                          int q = (1 + 2) + 3;
                          int z = (q + q) * q;
                      }
                  }
              }
              """
          )
        );
    }

    @Test
    void unwrapIdent() {
        //language=java
        rewriteRun(
          unnecessaryParentheses(style -> style.withIdent(true)),
          java(
            """
              class Test {
                  double doNothing() {
                      double num = (10.0);
                      return (num);
                  }
              }
              """,
            """
              class Test {
                  double doNothing() {
                      double num = (10.0);
                      return num;
                  }
              }
              """
          )
        );
    }

    @Test
    void unwrapNum() {
        //language=java
        rewriteRun(
          unnecessaryParentheses(style -> style.withNumDouble(true).withNumFloat(true)
            .withNumInt(true).withNumLong(true)),
          java(
            """
              class Test {
                  void doNothing() {
                      double a = (1000.0);
                      if ((1000.0) == a) {
                          a = (1000.0);
                      }
                      float b = (1000.0f);
                      int c = (1000);
                      long d = (1000L);
                  }
              }
              """,
            """
              class Test {
                  void doNothing() {
                      double a = 1000.0;
                      if (1000.0 == a) {
                          a = 1000.0;
                      }
                      float b = 1000.0f;
                      int c = 1000;
                      long d = 1000L;
                  }
              }
              """
          )
        );
    }

    @SuppressWarnings({"PointlessBooleanExpression", "MismatchedStringCase"})
    @Test
    void unwrapLiteral() {
        //language=java
        rewriteRun(
          unnecessaryParentheses(style -> style.withLiteralFalse(true).withLiteralTrue(true)
            .withLiteralNull(true).withStringLiteral(true)),
          java(
            """
              class Test {
                  void doNothing() {
                      boolean a = (true);
                      boolean b = (false);
                      if (a == (true)) {
                          b = (false);
                      } else if (b == (false)) {
                          a = (true);
                      }

                      String s = ("literallyString");
                      String t = ("literallyString" + "stringLiteral");
                      if (s == (null)) {
                          s = (null);
                      } else if ((("someLiteral").toLowerCase()).equals(s)) {
                          s = null;
                      }
                  }
              }
              """,
            """
              class Test {
                  void doNothing() {
                      boolean a = true;
                      boolean b = false;
                      if (a == true) {
                          b = false;
                      } else if (b == false) {
                          a = true;
                      }

                      String s = "literallyString";
                      String t = ("literallyString" + "stringLiteral");
                      if (s == null) {
                          s = null;
                      } else if (("someLiteral".toLowerCase()).equals(s)) {
                          s = null;
                      }
                  }
              }
              """
          )
        );
    }

    @SuppressWarnings("SillyAssignment")
    @Test
    void unwrapAssignment() {
        //language=java
        rewriteRun(
          unnecessaryParentheses(style -> style.withAssign(true)),
          java(
            """
              class Test {
                  void doNothing() {
                      double a = (10.0);
                      a = (10.0);
                      double b = (a);
                      b = b; // identity assignment
                      b += (b);
                      double c = (a + (b));
                      c = (a + b);
                      c = a + b; // binary operation
                      c *= (c);

                      String d = ("example") + ("assignment");
                      d = ("example" + "assignment");
                      d += ("example") + ("assignment");
                      d = (("example") + ("assignment"));
                  }
              }
              """,
            """
              class Test {
                  void doNothing() {
                      double a = 10.0;
                      a = 10.0;
                      double b = a;
                      b = b; // identity assignment
                      b += (b);
                      double c = a + (b);
                      c = a + b;
                      c = a + b; // binary operation
                      c *= (c);

                      String d = ("example") + ("assignment");
                      d = "example" + "assignment";
                      d += ("example") + ("assignment");
                      d = ("example") + ("assignment");
                  }
              }
              """
          )
        );
    }

    @Test
    void unwrapBandAssign() {
        //language=java
        rewriteRun(
          unnecessaryParentheses(style -> style.withBitAndAssign(true)),
          java(
            """
              class Test {
                  int a = 5;
                  int b = 7;

                  void bitwiseAnd() {
                      int c = (a & b);
                      c &= (c);
                  }
              }
              """,
            """
              class Test {
                  int a = 5;
                  int b = 7;

                  void bitwiseAnd() {
                      int c = (a & b);
                      c &= c;
                  }
              }
              """
          )
        );
    }

    @Test
    void unwrapBorAssign() {
        //language=java
        rewriteRun(
          unnecessaryParentheses(style -> style.withBitOrAssign(true)),
          java(
            """
              class Test {
                  int a = 5;
                  int b = 7;

                  void bitwiseOr() {
                      int c = (a | b);
                      c |= (c);
                  }
              }
              """,
            """
              class Test {
                  int a = 5;
                  int b = 7;

                  void bitwiseOr() {
                      int c = (a | b);
                      c |= c;
                  }
              }
              """
          )
        );
    }

    @Test
    void unwrapBsrAssign() {
        //language=java
        rewriteRun(
          unnecessaryParentheses(style -> style.withBitShiftRightAssign(true)),
          java(
            """
              class Test {
                  int a = -1;

                  void unsignedRightShiftAssignment() {
                      int b = a >>> 1;
                      b >>>= (b);
                  }
              }
              """,
            """
              class Test {
                  int a = -1;

                  void unsignedRightShiftAssignment() {
                      int b = a >>> 1;
                      b >>>= b;
                  }
              }
              """
          )
        );
    }

    @Test
    void unwrapBxorAssign() {
        //language=java
        rewriteRun(
          unnecessaryParentheses(style -> style.withBitXorAssign(true)),
          java(
            """
              class Test {
                  boolean a = true;
                  boolean b = false;

                  void bitwiseExclusiveOr() {
                      boolean c = (a ^ b);
                      c ^= (c);
                  }
              }
              """,
            """
              class Test {
                  boolean a = true;
                  boolean b = false;

                  void bitwiseExclusiveOr() {
                      boolean c = (a ^ b);
                      c ^= c;
                  }
              }
              """
          )
        );
    }

    @Test
    void unwrapDivAssign() {
        //language=java
        rewriteRun(
          unnecessaryParentheses(style -> style.withDivAssign(true)),
          java(
            """
              class Test {
                  int a = 10;
                  int b = 5;

                  void divisionAssignmentOperator() {
                      int c = (a / b);
                      c /= (c);
                  }
              }
              """,
            """
              class Test {
                  int a = 10;
                  int b = 5;

                  void divisionAssignmentOperator() {
                      int c = (a / b);
                      c /= c;
                  }
              }
              """
          )
        );
    }

    @Test
    void unwrapMinusAssign() {
        //language=java
        rewriteRun(
          unnecessaryParentheses(style -> style.withMinusAssign(true)),
          java(
            """
              class Test {
                  int a = 10;
                  int b = 5;

                  void minusAssignment() {
                      int c = (a - b);
                      c -= (c);
                  }
              }
              """,
            """
              class Test {
                  int a = 10;
                  int b = 5;

                  void minusAssignment() {
                      int c = (a - b);
                      c -= c;
                  }
              }
              """
          )
        );
    }

    @Issue("https://github.com/openrewrite/rewrite/issues/1486")
    @Test
    void unwrapMinusReturnExpression() {
        //language=java
        rewriteRun(
          unnecessaryParentheses(style -> style.withExpr(true)),
          java(
            """
              class T {
                  int getInt() {
                      return (4 - 5);
                  }
              }
              """,
            """
              class T {
                  int getInt() {
                      return 4 - 5;
                  }
              }
              """
          )
        );
    }

    @Test
    void unwrapModAssign() {
        rewriteRun(
          unnecessaryParentheses(style -> style.withModAssign(true)),
          java(
            """
              class Test {
                  int a = 5;
                  int b = 3;

                  void remainderAssignment() {
                      int c = a % b;
                      c %= (c);
                  }
              }
              """,
            """
              class Test {
                  int a = 5;
                  int b = 3;

                  void remainderAssignment() {
                      int c = a % b;
                      c %= c;
                  }
              }
              """
          )
        );
    }

    @Test
    void unwrapPlusAssign() {
        //language=java
        rewriteRun(
          unnecessaryParentheses(style -> style.withPlusAssign(true)),
          java(
            """
              class Test {
                  int a = 1;
                  int b = 1;

                  void plusAssignment() {
                      int c = a + b;
                      c += (c);
                  }
              }
              """,
            """
              class Test {
                  int a = 1;
                  int b = 1;

                  void plusAssignment() {
                      int c = a + b;
                      c += c;
                  }
              }
              """
          )
        );
    }

    @Test
    void unwrapSlAssign() {
        //language=java
        rewriteRun(
          unnecessaryParentheses(style -> style.withShiftLeftAssign(true)),
          java(
            """
              class Test {
                  int a = 1;
                  int b = 1;

                  void leftShiftAssignment() {
                      int c = a << b;
                      c <<= (c);
                  }
              }
              """,
            """
              class Test {
                  int a = 1;
                  int b = 1;

                  void leftShiftAssignment() {
                      int c = a << b;
                      c <<= c;
                  }
              }
              """
          )
        );
    }

    @Test
    void unwrapSrAssign() {
        //language=java
        rewriteRun(
          unnecessaryParentheses(style -> style.withShiftRightAssign(true)),
          java(
            """
              class Test {
                  int a = 1;
                  int b = 1;

                  void signedRightShiftAssignment() {
                      int c = a >> b;
                      c >>= (c);
                  }
              }
              """,
            """
              class Test {
                  int a = 1;
                  int b = 1;

                  void signedRightShiftAssignment() {
                      int c = a >> b;
                      c >>= c;
                  }
              }
              """
          )
        );
    }

    @Test
    void unwrapStarAssign() {
        //language=java
        rewriteRun(
          unnecessaryParentheses(style -> style.withStarAssign(true)),
          java(
            """
              class Test {
                  int a = 1;
                  int b = 1;

                  void multiplicationAssignmentOperator() {
                      int c = a * b;
                      c *= (c);
                  }
              }
              """,
            """
              class Test {
                  int a = 1;
                  int b = 1;

                  void multiplicationAssignmentOperator() {
                      int c = a * b;
                      c *= c;
                  }
              }
              """
          )
        );
    }

    @Test
    void unwrapLambda() {
        //language=java
        rewriteRun(
          unnecessaryParentheses(style -> style.withLambda(true)),
          java(
            """
              import java.util.*;

              class Test {
                  void doNothing() {
                      List<String> list = Arrays.asList("a1", "b1", "c1");
                      list.stream()
                              .filter((s) -> s.startsWith("c"))
                              .forEach(System.out::println);
                  }
              }
              """,
            """
              import java.util.*;

              class Test {
                  void doNothing() {
                      List<String> list = Arrays.asList("a1", "b1", "c1");
                      list.stream()
                              .filter(s -> s.startsWith("c"))
                              .forEach(System.out::println);
                  }
              }
              """
          )
        );
    }

    @Issue("https://github.com/openrewrite/rewrite/issues/798")
    @Test
    void unwrapDoubleParens() {
        //language=java
        rewriteRun(
          java(
            """
              class Test {
                  void test() {
                      int sum = 1 + ((2 + 3));
                  }
              }
              """,
            """
              class Test {
                  void test() {
                      int sum = 1 + (2 + 3);
                  }
              }
              """
          )
        );
    }

    @Test
    void unwrapIfParens() {
        rewriteRun(
          java(
            """
              class Test {
                  void test(String s) {
                      if ((s == null || s.isEmpty())) {
                          System.out.println("empty");
                      }
                  }
              }
              """,
            """
              class Test {
                  void test(String s) {
                      if (s == null || s.isEmpty()) {
                          System.out.println("empty");
                      }
                  }
              }
              """
          )
        );
    }

    @Test
    void doNotUnwrapIfNoParens() {
        //language=java
        rewriteRun(
          java(
            """
              class Test {
                  void test(String s) {
                      if (s == null || s.isEmpty()) {
                          System.out.println("empty");
                      }
                  }
              }
              """
          )
        );
    }

    @Test
    void doNotUnwrapNegatedIfParens() {
        //language=java
        rewriteRun(
          java(
            """
              class Test {
                  void test(String s) {
                      if (!(s == null || s.isEmpty())) {
                          System.out.println("empty");
                      }
                  }
              }
              """
          )
        );
    }

    @SuppressWarnings("PointlessBooleanExpression")
    @Test
    void doNotUnwrapIfParens() {
        //language=java
        rewriteRun(
          java(
            """
              class Test {
                  void test(String s) {
                      if ((s == null || s.isEmpty()) || false) {
                          System.out.println("empty");
                      }
                  }
              }
              """
          )
        );
    }

    @Test
    void unwrapWhileParens() {
        //language=java
        rewriteRun(
          java(
            """
              class Test {
                  void test(String s) {
                      while ((s == null || s.isEmpty())) {
                          s = "not empty";
                      }
                  }
              }
              """,
            """
              class Test {
                  void test(String s) {
                      while (s == null || s.isEmpty()) {
                          s = "not empty";
                      }
                  }
              }
              """
          )
        );
    }

    @Test
    void unwrapDoWhileParens() {
        //language=java
        rewriteRun(
          java(
            """
              class Test {
                  void test(String s) {
                       do {
                          s = "not empty";
                      } while ((s == null || s.isEmpty()));
                  }
              }
              """,
            """
              class Test {
                  void test(String s) {
                       do {
                          s = "not empty";
                      } while (s == null || s.isEmpty());
                  }
              }
              """
          )
        );
    }

    @Test
    void unwrapForControlParens() {
        //language=java
        rewriteRun(
          java(
            """
              class Test {
                  void test(String s) {
                      for (int i = 0; (i < 10); i++) {
                          System.out.println(i);
                      }
                  }
              }
              """,
            """
              class Test {
                  void test(String s) {
                      for (int i = 0; i < 10; i++) {
                          System.out.println(i);
                      }
                  }
              }
              """
          )
        );
    }

    @Test
    void ternaryCondition() {
        rewriteRun(
          java(
            """
              class Test {
                  String test(String s) {
                      return (s.isEmpty()) ? "true" : "false";
                  }
              }
              """,
            """
              class Test {
                  String test(String s) {
                      return s.isEmpty() ? "true" : "false";
                  }
              }
              """
          )
        );
    }
}

