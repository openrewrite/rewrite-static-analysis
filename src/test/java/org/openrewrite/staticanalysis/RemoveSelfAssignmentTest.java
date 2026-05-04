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

@SuppressWarnings("SelfAssignment")
class RemoveSelfAssignmentTest implements RewriteTest {

    @Override
    public void defaults(RecipeSpec spec) {
        spec.recipe(new RemoveSelfAssignment());
    }

    @DocumentExample
    @Test
    void removeSelfAssignment() {
        rewriteRun(
          //language=java
          java(
            """
              class Test {
                  void test(int x) {
                      x = x;
                  }
              }
              """,
            """
              class Test {
                  void test(int x) {
                  }
              }
              """
          )
        );
    }

    @Test
    void removeSelfAssignmentOfField() {
        rewriteRun(
          //language=java
          java(
            """
              class Test {
                  int x;
                  void test() {
                      this.x = this.x;
                  }
              }
              """,
            """
              class Test {
                  int x;
                  void test() {
                  }
              }
              """
          )
        );
    }

    @Test
    void doNotChangeFieldAssignedFromParameter() {
        rewriteRun(
          //language=java
          java(
            """
              class Test {
                  int x;
                  void test(int x) {
                      this.x = x;
                  }
              }
              """
          )
        );
    }

    @Test
    void doNotChangeDifferentVariables() {
        rewriteRun(
          //language=java
          java(
            """
              class Test {
                  void test(int x, int y) {
                      x = y;
                  }
              }
              """
          )
        );
    }

    @Test
    void doNotChangeCompoundAssignment() {
        rewriteRun(
          //language=java
          java(
            """
              class Test {
                  void test(int x) {
                      x += x;
                  }
              }
              """
          )
        );
    }

    @Test
    void removeSelfAssignmentPreservingSurroundingStatements() {
        rewriteRun(
          //language=java
          java(
            """
              class Test {
                  void test(int x) {
                      System.out.println("before");
                      x = x;
                      System.out.println("after");
                  }
              }
              """,
            """
              class Test {
                  void test(int x) {
                      System.out.println("before");
                      System.out.println("after");
                  }
              }
              """
          )
        );
    }
}
