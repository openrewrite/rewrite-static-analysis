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
import static org.openrewrite.javascript.Assertions.javascript;
import static org.openrewrite.kotlin.Assertions.kotlin;

class AllBranchesIdenticalTest implements RewriteTest {

    @Override
    public void defaults(RecipeSpec spec) {
        spec.recipe(new AllBranchesIdentical());
    }

    @DocumentExample
    @Test
    void collapseIfElseWithIdenticalBodies() {
        rewriteRun(
          //language=java
          java(
            """
              class Test {
                  void test(boolean a) {
                      if (a) {
                          System.out.println("hello");
                      } else {
                          System.out.println("hello");
                      }
                  }
              }
              """,
            """
              class Test {
                  void test(boolean a) {
                      System.out.println("hello");
                  }
              }
              """
          )
        );
    }

    @Test
    void collapseIfElseIfElse() {
        rewriteRun(
          //language=java
          java(
            """
              class Test {
                  void test(int x) {
                      if (x > 0) {
                          System.out.println("same");
                      } else if (x < 0) {
                          System.out.println("same");
                      } else {
                          System.out.println("same");
                      }
                  }
              }
              """,
            """
              class Test {
                  void test(int x) {
                      System.out.println("same");
                  }
              }
              """
          )
        );
    }

    @Test
    void collapseWithMultipleStatements() {
        rewriteRun(
          //language=java
          java(
            """
              class Test {
                  void test(boolean a) {
                      if (a) {
                          int x = 1;
                          System.out.println(x);
                      } else {
                          int x = 1;
                          System.out.println(x);
                      }
                  }
              }
              """,
            """
              class Test {
                  void test(boolean a) {
                      int x = 1;
                      System.out.println(x);
                  }
              }
              """
          )
        );
    }

    @Test
    void doNotChangeDifferentBodies() {
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

    @Test
    void doNotChangeIfWithoutElse() {
        rewriteRun(
          //language=java
          java(
            """
              class Test {
                  void test(boolean a) {
                      if (a) {
                          System.out.println("hello");
                      }
                  }
              }
              """
          )
        );
    }

    @Test
    void doNotChangeIfElseIfWithoutFinalElse() {
        rewriteRun(
          //language=java
          java(
            """
              class Test {
                  void test(int x) {
                      if (x > 0) {
                          System.out.println("same");
                      } else if (x < 0) {
                          System.out.println("same");
                      }
                  }
              }
              """
          )
        );
    }

    @Test
    void doNotChangeWhenOneBranchDiffers() {
        rewriteRun(
          //language=java
          java(
            """
              class Test {
                  void test(int x) {
                      if (x > 0) {
                          System.out.println("same");
                      } else if (x < 0) {
                          System.out.println("different");
                      } else {
                          System.out.println("same");
                      }
                  }
              }
              """
          )
        );
    }

    @Test
    void collapseIdenticalBranchesKotlin() {
        rewriteRun(
          //language=kotlin
          kotlin(
            """
              fun test(a: Boolean) {
                  if (a) {
                      println("hello")
                  } else {
                      println("hello")
                  }
              }
              """,
            """
              fun test(a: Boolean) {
                  println("hello")
              }
              """
          )
        );
    }

    @Test
    void collapseIdenticalBranchesGroovy() {
        rewriteRun(
          //language=groovy
          groovy(
            """
              void test(boolean a) {
                  if (a) {
                      println("hello")
                  } else {
                      println("hello")
                  }
              }
              """,
            """
              void test(boolean a) {
                  println("hello")
              }
              """
          )
        );
    }

    @Test
    void collapseIdenticalBranchesJavaScript() {
        rewriteRun(
          //language=javascript
          javascript(
            """
              function test(a) {
                  if (a) {
                      console.log("hi");
                  } else {
                      console.log("hi");
                  }
              }
              """,
            """
              function test(a) {
                  console.log("hi");
              }
              """
          )
        );
    }
}
