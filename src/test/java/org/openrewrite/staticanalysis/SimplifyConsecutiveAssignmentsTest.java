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

@SuppressWarnings("ConstantValue")
class SimplifyConsecutiveAssignmentsTest implements RewriteTest {
    @Override
    public void defaults(RecipeSpec spec) {
        spec.recipe(new SimplifyConsecutiveAssignments());
    }

    @Test
    @DocumentExample
    void assignmentAndIncrement() {
        rewriteRun(
          //language=java
          java(
            """
              class Test {
                  int test() {
                      int x = 1;
                      x++;
                      x *= 2;
                      return x;
                  }
              }
              """,
            """
              class Test {
                  int test() {
                      int x = (1 + 1) * 2;
                      return x;
                  }
              }
              """
          )
        );
    }

    @Test
    void intermediatePrint() {
        rewriteRun(
          //language=java
          java(
            """
              class Test {
                  int test() {
                      int x = 1;
                      System.out.println("hi");
                      x++;
                      return x;
                  }
              }
              """
          )
        );
    }

    @Test
    void assignmentAsLastStatement() {
        rewriteRun(
          //language=java
          java(
            """
              class Test {
                  int test() {
                      int x = 1;
                      if (true) {
                          x = 2;
                      }
                      return x;
                  }
              }
              """
          )
        );
    }
}
