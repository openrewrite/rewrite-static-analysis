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

@Issue("https://github.com/openrewrite/rewrite/issues/811")
@SuppressWarnings("StatementWithEmptyBody")
class NoEqualityInForConditionTest implements RewriteTest {
    @Override
    public void defaults(RecipeSpec spec) {
        spec.recipe(new NoEqualityInForCondition());
    }

    @Test
    @DocumentExample
    void replaceWithRelationalOperator() {
        rewriteRun(
          //language=java
          java(
            """
              class Test {
                  void test() {
                      for (int i = 0; i != 10; i++) {
                      }
                      for (int i = 10; i != 0; i--) {
                      }
                  }
              }
              """,
            """
              class Test {
                  void test() {
                      for (int i = 0; i < 10; i++) {
                      }
                      for (int i = 10; i > 0; i--) {
                      }
                  }
              }
              """
          )
        );
    }

    @Test
    void allowNullCheck() {
        rewriteRun(
          //language=java
          java(
            """
              class Test {
                  int[] arr;
                  void test() {
                      for (int i = 0; arr[i] == null; i++) {
                      }
                  }
              }
              """
          )
        );
    }

    @Test
    void retainCharComparison() {
        rewriteRun(
          //language=java
          java(
            """
              class Test {
                  int[] arr;
                  void test() {
                      for (int i = 0; arr[i] != '\\n'; i++) {
                      }
                  }
              }
              """
          )
        );
    }

    @Test
    void retainMismatchedComparison() {
        rewriteRun(
          //language=java
          java(
            """
              class Test {
                  void test() {
                      int y = 9;
                      for (int x = 0; y != 10; x++) {
                      }
                  }
              }
              """
          )
        );
    }
}
