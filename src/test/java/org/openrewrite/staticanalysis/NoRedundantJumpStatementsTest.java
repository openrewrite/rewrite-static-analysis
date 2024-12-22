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
import org.openrewrite.test.RewriteTest;

import static org.openrewrite.java.Assertions.java;

@SuppressWarnings({"UnnecessaryContinue", "UnnecessaryReturnStatement"})
class NoRedundantJumpStatementsTest implements RewriteTest {

    @DocumentExample
    @Test
    void noRedundantJumpStatements() {
        rewriteRun(
          spec -> spec.recipe(new NoRedundantJumpStatements()),
          //language=java
          java(
            """
              class Test {
                  boolean condition1;
                  boolean condition2;
                  void test() {
                      while (condition1) {
                          if (condition2) {
                              continue;
                          } else {
                              System.out.println();
                          }
                      }
                      return;
                  }
              }
              """,
            """
              class Test {
                  boolean condition1;
                  boolean condition2;
                  void test() {
                      while (condition1) {
                          if (!condition2) {
                              System.out.println();
                          }
                      }
                  }
              }
              """
          )
        );
    }
}
