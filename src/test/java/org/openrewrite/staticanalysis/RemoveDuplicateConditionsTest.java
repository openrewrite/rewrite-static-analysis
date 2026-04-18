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

class RemoveDuplicateConditionsTest implements RewriteTest {

    @Override
    public void defaults(RecipeSpec spec) {
        spec.recipe(new RemoveDuplicateConditions());
    }

    @DocumentExample
    @Test
    void removeDuplicateElseIf() {
        rewriteRun(
          //language=java
          java(
            """
              class Test {
                  void test(int x) {
                      if (x > 0) {
                          System.out.println("positive");
                      } else if (x > 0) {
                          System.out.println("also positive");
                      } else {
                          System.out.println("non-positive");
                      }
                  }
              }
              """,
            """
              class Test {
                  void test(int x) {
                      if (x > 0) {
                          System.out.println("positive");
                      } else {
                          System.out.println("non-positive");
                      }
                  }
              }
              """
          )
        );
    }

    @Test
    void removeDuplicateInThreeBranchChain() {
        rewriteRun(
          //language=java
          java(
            """
              class Test {
                  void test(int x) {
                      if (x > 0) {
                          System.out.println("first");
                      } else if (x < 0) {
                          System.out.println("second");
                      } else if (x > 0) {
                          System.out.println("duplicate");
                      } else {
                          System.out.println("default");
                      }
                  }
              }
              """,
            """
              class Test {
                  void test(int x) {
                      if (x > 0) {
                          System.out.println("first");
                      } else if (x < 0) {
                          System.out.println("second");
                      } else {
                          System.out.println("default");
                      }
                  }
              }
              """
          )
        );
    }

    @Test
    void removeDuplicateWithoutFinalElse() {
        rewriteRun(
          //language=java
          java(
            """
              class Test {
                  void test(int x) {
                      if (x > 0) {
                          System.out.println("first");
                      } else if (x > 0) {
                          System.out.println("duplicate");
                      }
                  }
              }
              """,
            """
              class Test {
                  void test(int x) {
                      if (x > 0) {
                          System.out.println("first");
                      }
                  }
              }
              """
          )
        );
    }

    @Test
    void doNotChangeDifferentConditions() {
        rewriteRun(
          //language=java
          java(
            """
              class Test {
                  void test(int x) {
                      if (x > 0) {
                          System.out.println("positive");
                      } else if (x < 0) {
                          System.out.println("negative");
                      } else {
                          System.out.println("zero");
                      }
                  }
              }
              """
          )
        );
    }

    @Test
    void doNotChangeSimpleIfElse() {
        rewriteRun(
          //language=java
          java(
            """
              class Test {
                  void test(boolean a) {
                      if (a) {
                          System.out.println("yes");
                      } else {
                          System.out.println("no");
                      }
                  }
              }
              """
          )
        );
    }
}
