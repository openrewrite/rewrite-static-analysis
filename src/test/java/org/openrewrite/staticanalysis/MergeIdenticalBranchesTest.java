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

class MergeIdenticalBranchesTest implements RewriteTest {

    @Override
    public void defaults(RecipeSpec spec) {
        spec.recipe(new MergeIdenticalBranches());
    }

    @DocumentExample
    @Test
    void mergeTwoBranchesWithIdenticalBodies() {
        rewriteRun(
          //language=java
          java(
            """
              class Test {
                  void test(boolean a, boolean b) {
                      if (a) {
                          System.out.println("same");
                      } else if (b) {
                          System.out.println("same");
                      } else {
                          System.out.println("different");
                      }
                  }
              }
              """,
            """
              class Test {
                  void test(boolean a, boolean b) {
                      if (a || b) {
                          System.out.println("same");
                      } else {
                          System.out.println("different");
                      }
                  }
              }
              """
          )
        );
    }

    @Test
    void mergeThreeConsecutiveIdenticalBranches() {
        rewriteRun(
          //language=java
          java(
            """
              class Test {
                  void test(boolean a, boolean b, boolean c) {
                      if (a) {
                          System.out.println("same");
                      } else if (b) {
                          System.out.println("same");
                      } else if (c) {
                          System.out.println("same");
                      } else {
                          System.out.println("different");
                      }
                  }
              }
              """,
            """
              class Test {
                  void test(boolean a, boolean b, boolean c) {
                      if (a || b || c) {
                          System.out.println("same");
                      } else {
                          System.out.println("different");
                      }
                  }
              }
              """
          )
        );
    }

    @Test
    void mergeNonConsecutiveIdenticalBranches() {
        rewriteRun(
          //language=java
          java(
            """
              class Test {
                  void test(boolean a, boolean b, boolean c) {
                      if (a) {
                          System.out.println("x");
                      } else if (b) {
                          System.out.println("y");
                      } else if (c) {
                          System.out.println("y");
                      } else {
                          System.out.println("z");
                      }
                  }
              }
              """,
            """
              class Test {
                  void test(boolean a, boolean b, boolean c) {
                      if (a) {
                          System.out.println("x");
                      } else if (b || c) {
                          System.out.println("y");
                      } else {
                          System.out.println("z");
                      }
                  }
              }
              """
          )
        );
    }

    @Test
    void mergeWithoutFinalElse() {
        rewriteRun(
          //language=java
          java(
            """
              class Test {
                  void test(boolean a, boolean b) {
                      if (a) {
                          System.out.println("same");
                      } else if (b) {
                          System.out.println("same");
                      }
                  }
              }
              """,
            """
              class Test {
                  void test(boolean a, boolean b) {
                      if (a || b) {
                          System.out.println("same");
                      }
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
                  void test(boolean a, boolean b) {
                      if (a) {
                          System.out.println("one");
                      } else if (b) {
                          System.out.println("two");
                      } else {
                          System.out.println("three");
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
