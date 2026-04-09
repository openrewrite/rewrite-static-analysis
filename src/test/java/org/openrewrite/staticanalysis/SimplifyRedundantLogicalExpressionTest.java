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
import org.openrewrite.test.RecipeSpec;
import org.openrewrite.test.RewriteTest;

import static org.openrewrite.java.Assertions.java;

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
    void simplifyWithMethodCall() {
        rewriteRun(
          //language=java
          java(
            """
              class Test {
                  boolean test(String s) {
                      return s.isEmpty() && s.isEmpty();
                  }
              }
              """,
            """
              class Test {
                  boolean test(String s) {
                      return s.isEmpty();
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
}
