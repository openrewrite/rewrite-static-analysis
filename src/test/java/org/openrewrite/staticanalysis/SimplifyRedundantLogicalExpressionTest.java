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
import static org.openrewrite.javascript.Assertions.typescript;
import static org.openrewrite.kotlin.Assertions.kotlin;

class SimplifyRedundantLogicalExpressionTest implements RewriteTest {

    @Override
    public void defaults(RecipeSpec spec) {
        spec.recipe(new SimplifyRedundantLogicalExpression());
    }

    @DocumentExample
    @Test
    void simplifyLogicalAnd() {
        rewriteRun(
          //language=java
          java(
            """
              class Test {
                  boolean test(boolean a) {
                      return a && a;
                  }
              }
              """,
            """
              class Test {
                  boolean test(boolean a) {
                      return a;
                  }
              }
              """
          )
        );
    }

    @Test
    void simplifyLogicalOr() {
        rewriteRun(
          //language=java
          java(
            """
              class Test {
                  boolean test(boolean a) {
                      return a || a;
                  }
              }
              """,
            """
              class Test {
                  boolean test(boolean a) {
                      return a;
                  }
              }
              """
          )
        );
    }

    @Test
    void simplifyBitwiseAnd() {
        rewriteRun(
          //language=java
          java(
            """
              class Test {
                  int test(int x) {
                      return x & x;
                  }
              }
              """,
            """
              class Test {
                  int test(int x) {
                      return x;
                  }
              }
              """
          )
        );
    }

    @Test
    void simplifyBitwiseOr() {
        rewriteRun(
          //language=java
          java(
            """
              class Test {
                  int test(int x) {
                      return x | x;
                  }
              }
              """,
            """
              class Test {
                  int test(int x) {
                      return x;
                  }
              }
              """
          )
        );
    }

    @Test
    void doNotChangeMethodInvocationsWithHiddenState() {
        rewriteRun(
          //language=java
          java(
            """
              import java.util.Iterator;
              class Test {
                  boolean test(Iterator<Boolean> it) {
                      return it.next() && it.next();
                  }
              }
              """
          )
        );
    }

    @Test
    void simplifyWithParenthesesOnOneSide() {
        rewriteRun(
          //language=java
          java(
            """
              class Test {
                  boolean test(boolean a) {
                      return (a) && a;
                  }
              }
              """,
            """
              class Test {
                  boolean test(boolean a) {
                      return a;
                  }
              }
              """
          )
        );
    }

    @Test
    void simplifyWithParenthesesOnRightSide() {
        rewriteRun(
          //language=java
          java(
            """
              class Test {
                  boolean test(boolean a) {
                      return a && (a);
                  }
              }
              """,
            """
              class Test {
                  boolean test(boolean a) {
                      return a;
                  }
              }
              """
          )
        );
    }

    @Test
    void simplifyWithParenthesesOnBothSides() {
        rewriteRun(
          //language=java
          java(
            """
              class Test {
                  boolean test(boolean a) {
                      return (a) && (a);
                  }
              }
              """,
            """
              class Test {
                  boolean test(boolean a) {
                      return a;
                  }
              }
              """
          )
        );
    }

    @Test
    void doNotChangeDifferentOperands() {
        rewriteRun(
          //language=java
          java(
            """
              class Test {
                  boolean test(boolean a, boolean b) {
                      return a && b;
                  }
              }
              """
          )
        );
    }

    @Test
    void doNotChangeArithmeticOperators() {
        rewriteRun(
          //language=java
          java(
            """
              class Test {
                  int test(int x) {
                      return x + x;
                  }
              }
              """
          )
        );
    }

    @Test
    void doNotChangeComparisonOperators() {
        rewriteRun(
          //language=java
          java(
            """
              class Test {
                  @SuppressWarnings("all")
                  boolean test(int x) {
                      return x == x;
                  }
              }
              """
          )
        );
    }

    @Test
    void simplifyLogicalAndKotlin() {
        rewriteRun(
          //language=kotlin
          kotlin(
            """
              fun test(a: Boolean): Boolean {
                  return a && a
              }
              """,
            """
              fun test(a: Boolean): Boolean {
                  return a
              }
              """
          )
        );
    }

    @Test
    void simplifyLogicalAndGroovy() {
        rewriteRun(
          //language=groovy
          groovy(
            """
              boolean test(boolean a) {
                  return a && a
              }
              """,
            """
              boolean test(boolean a) {
                  return a
              }
              """
          )
        );
    }

    @Test
    void simplifyLogicalAndTypeScript() {
        rewriteRun(
          //language=typescript
          typescript(
            """
              function test(a: boolean) {
                  return a && a;
              }
              """,
            """
              function test(a: boolean) {
                  return a;
              }
              """
          )
        );
    }
}
