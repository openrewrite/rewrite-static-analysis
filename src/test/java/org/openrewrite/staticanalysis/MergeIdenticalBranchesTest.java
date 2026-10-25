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

import static org.openrewrite.golang.Assertions.go;
import static org.openrewrite.groovy.Assertions.groovy;
import static org.openrewrite.java.Assertions.java;
import static org.openrewrite.javascript.Assertions.typescript;
import static org.openrewrite.kotlin.Assertions.kotlin;
import static org.openrewrite.python.Assertions.python;

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

    @Test
    void mergeIdenticalBranchesKotlin() {
        rewriteRun(
          //language=kotlin
          kotlin(
            """
              fun test(a: Boolean, b: Boolean) {
                  if (a) {
                      println("same")
                  } else if (b) {
                      println("same")
                  } else {
                      println("different")
                  }
              }
              """,
            """
              fun test(a: Boolean, b: Boolean) {
                  if (a || b) {
                      println("same")
                  } else {
                      println("different")
                  }
              }
              """
          )
        );
    }

    @Test
    void mergeIdenticalBranchesGroovy() {
        rewriteRun(
          //language=groovy
          groovy(
            """
              void test(boolean a, boolean b) {
                  if (a) {
                      println("same")
                  } else if (b) {
                      println("same")
                  } else {
                      println("different")
                  }
              }
              """,
            """
              void test(boolean a, boolean b) {
                  if (a || b) {
                      println("same")
                  } else {
                      println("different")
                  }
              }
              """
          )
        );
    }

    @Test
    void mergeIdenticalBranchesTypeScript() {
        rewriteRun(
          //language=typescript
          typescript(
            """
              function test(a: boolean, b: boolean) {
                  if (a) {
                      console.log("same");
                  } else if (b) {
                      console.log("same");
                  } else {
                      console.log("different");
                  }
              }
              """,
            """
              function test(a: boolean, b: boolean) {
                  if (a || b) {
                      console.log("same");
                  } else {
                      console.log("different");
                  }
              }
              """
          )
        );
    }

    @Test
    void mergeIdenticalBranchesPython() {
        rewriteRun(
          //language=python
          python(
            """
              def test(a, b):
                  if a:
                      print("same")
                  elif b:
                      print("same")
                  else:
                      print("different")
              """,
            """
              def test(a, b):
                  if a or b:
                      print("same")
                  else:
                      print("different")
              """
          )
        );
    }

    @Test
    void mergeIdenticalBranchesGo() {
        rewriteRun(
          //language=go
          go(
            """
              package main

              type Order struct {
                  Expedited   bool
                  GiftWrapped bool
              }

              func route(order Order) {
                  if order.Expedited {
                      queueFastLane(order)
                  } else if order.GiftWrapped {
                      queueFastLane(order)
                  } else {
                      queueStandard(order)
                  }
              }

              func queueFastLane(order Order) {}
              func queueStandard(order Order) {}
              """,
            """
              package main

              type Order struct {
                  Expedited   bool
                  GiftWrapped bool
              }

              func route(order Order) {
                  if order.Expedited || order.GiftWrapped {
                      queueFastLane(order)
                  } else {
                      queueStandard(order)
                  }
              }

              func queueFastLane(order Order) {}
              func queueStandard(order Order) {}
              """
          )
        );
    }
}
