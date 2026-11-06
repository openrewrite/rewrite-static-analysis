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
import org.openrewrite.Issue;
import org.openrewrite.test.RecipeSpec;
import org.openrewrite.test.RewriteTest;

import static org.openrewrite.golang.Assertions.go;
import static org.openrewrite.groovy.Assertions.groovy;
import static org.openrewrite.java.Assertions.java;
import static org.openrewrite.javascript.Assertions.typescript;
import static org.openrewrite.kotlin.Assertions.kotlin;
import static org.openrewrite.python.Assertions.python;

class AllBranchesIdenticalTest implements RewriteTest {

    @Override
    public void defaults(RecipeSpec spec) {
        spec.recipe(new AllBranchesIdentical());
    }

    @Test
    void keepUnrelatedBlocks() {
        rewriteRun(
          //language=java
          java(
            """
              class Test {
                  void test(boolean a) {
                      {
                          System.out.println("unrelated");
                      }
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
                      {
                          System.out.println("unrelated");
                      }
                      System.out.println("hello");
                  }
              }
              """
          )
        );
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
    void collapseIdenticalBranchesTypeScript() {
        rewriteRun(
          //language=typescript
          typescript(
            """
              function test(a: boolean) {
                  if (a) {
                      console.log("hi");
                  } else {
                      console.log("hi");
                  }
              }
              """,
            """
              function test(a: boolean) {
                  console.log("hi");
              }
              """
          )
        );
    }

    @Test
    void collapseIdenticalBranchesPython() {
        rewriteRun(
          //language=python
          python(
            """
              def test(a):
                  if a:
                      print("hello")
                  else:
                      print("hello")
              """,
            """
              def test(a):
                  print("hello")
              """
          )
        );
    }

    @Issue("https://github.com/openrewrite/rewrite-static-analysis/issues/953")
    @Test
    void doNotChangeWhenConditionHasSideEffects() {
        rewriteRun(
          //language=java
          java(
            """
              import java.util.Iterator;

              class Test {
                  void p(String s) {
                  }

                  void test(Iterator<String> it) {
                      if (it.next() != null) {
                          p("x");
                      } else {
                          p("x");
                      }
                  }
              }
              """
          )
        );
    }

    @Test
    void hoistBareCallConditionWithSideEffect() {
        rewriteRun(
          //language=java
          java(
            """
              class Test {
                  boolean consume() {
                      System.out.println("side effect");
                      return true;
                  }

                  void test() {
                      if (consume()) {
                          System.out.println("hello");
                      } else {
                          System.out.println("hello");
                      }
                  }
              }
              """,
            """
              class Test {
                  boolean consume() {
                      System.out.println("side effect");
                      return true;
                  }

                  void test() {
                      consume();
                      System.out.println("hello");
                  }
              }
              """
          )
        );
    }

    @Test
    void hoistBareCallConditionWithoutBraces() {
        rewriteRun(
          //language=java
          java(
            """
              class Test {
                  boolean consume() {
                      System.out.println("side effect");
                      return true;
                  }

                  void test() {
                      if (consume())
                          System.out.println("hello");
                      else
                          System.out.println("hello");
                  }
              }
              """,
            """
              class Test {
                  boolean consume() {
                      System.out.println("side effect");
                      return true;
                  }

                  void test() {
                      consume();
                      System.out.println("hello");
                  }
              }
              """
          )
        );
    }

    @Test
    void hoistKeepsUnrelatedBlocksAndComments() {
        rewriteRun(
          //language=java
          java(
            """
              import java.util.Iterator;

              class Test {
                  void test(Iterator<String> it) {
                      {
                          System.out.println("unrelated");
                      }
                      // advance
                      if (it.hasNext()) {
                          System.out.println("hello");
                      } else {
                          System.out.println("hello");
                      }
                  }
              }
              """,
            """
              import java.util.Iterator;

              class Test {
                  void test(Iterator<String> it) {
                      {
                          System.out.println("unrelated");
                      }
                      // advance
                      it.hasNext();
                      System.out.println("hello");
                  }
              }
              """
          )
        );
    }

    @Test
    void hoistBareCallConditionWithEmptyBranches() {
        rewriteRun(
          //language=java
          java(
            """
              import java.util.Iterator;

              class Test {
                  void test(Iterator<String> it) {
                      if (it.hasNext()) {
                      } else {
                      }
                  }
              }
              """,
            """
              import java.util.Iterator;

              class Test {
                  void test(Iterator<String> it) {
                      it.hasNext();
                  }
              }
              """
          )
        );
    }

    @Test
    void hoistBareCallConditionKeepingScopingBlock() {
        rewriteRun(
          //language=java
          java(
            """
              import java.util.Iterator;

              class Test {
                  void test(Iterator<String> it) {
                      if (it.hasNext()) {
                          String s = "hello";
                          System.out.println(s);
                      } else {
                          String s = "hello";
                          System.out.println(s);
                      }
                      System.out.println("done");
                  }
              }
              """,
            """
              import java.util.Iterator;

              class Test {
                  void test(Iterator<String> it) {
                      it.hasNext();
                      {
                          String s = "hello";
                          System.out.println(s);
                      }
                      System.out.println("done");
                  }
              }
              """
          )
        );
    }

    @Test
    void hoistBareCallConditionKotlin() {
        rewriteRun(
          //language=kotlin
          kotlin(
            """
              fun test(it: Iterator<String>) {
                  if (it.hasNext()) {
                      println("hello")
                  } else {
                      println("hello")
                  }
              }
              """,
            """
              fun test(it: Iterator<String>) {
                  it.hasNext()
                  println("hello")
              }
              """
          )
        );
    }

    @Test
    void hoistBareCallConditionTypeScript() {
        rewriteRun(
          //language=typescript
          typescript(
            """
              function test(it: Iterator<string>) {
                  if (it.next()) {
                      console.log("hi");
                  } else {
                      console.log("hi");
                  }
              }
              """,
            """
              function test(it: Iterator<string>) {
                  it.next();
                  console.log("hi");
              }
              """
          )
        );
    }

    @Test
    void hoistBareCallConditionGo() {
        rewriteRun(
          //language=go
          go(
            """
              package main

              func applyPromotion() {
                  if consume() {
                      applyDiscount()
                  } else {
                      applyDiscount()
                  }
              }

              func consume() bool { return true }

              func applyDiscount() {}
              """,
            """
              package main

              func applyPromotion() {
                  consume()
                  applyDiscount()
              }

              func consume() bool { return true }

              func applyDiscount() {}
              """
          )
        );
    }

    @Test
    void hoistBareCallConditionWithEmptyBranchesTypeScript() {
        rewriteRun(
          //language=typescript
          typescript(
            """
              function test(it: Iterator<string>, a: number, b: number) {
                  if (it.next()) {
                  } else {
                  }
                  [a, b] = [b, a];
              }
              """,
            """
              function test(it: Iterator<string>, a: number, b: number) {
                  it.next();
                  [a, b] = [b, a];
              }
              """
          )
        );
    }

    @Test
    void hoistBareCallConditionWithEmptyStatements() {
        rewriteRun(
          //language=java
          java(
            """
              class Test {
                  boolean consume() {
                      System.out.println("side effect");
                      return true;
                  }

                  void test() {
                      if (consume()) ; else ;
                  }
              }
              """,
            """
              class Test {
                  boolean consume() {
                      System.out.println("side effect");
                      return true;
                  }

                  void test() {
                      consume();
                  }
              }
              """
          )
        );
    }

    @Test
    void hoistKeepsCommentBeforeBody() {
        rewriteRun(
          //language=java
          java(
            """
              class Test {
                  boolean consume() {
                      System.out.println("side effect");
                      return true;
                  }

                  void test() {
                      if (consume()) /* keep */ {
                          System.out.println("hello");
                      } else {
                          System.out.println("hello");
                      }
                  }
              }
              """,
            """
              class Test {
                  boolean consume() {
                      System.out.println("side effect");
                      return true;
                  }

                  void test() {
                      consume();
                      /* keep */ System.out.println("hello");
                  }
              }
              """
          )
        );
    }

    @Test
    void doNotHoistScopingBlockKotlin() {
        rewriteRun(
          //language=kotlin
          kotlin(
            """
              fun test(it: Iterator<String>) {
                  if (it.hasNext()) {
                      val s = "a"
                      println(s)
                  } else {
                      val s = "a"
                      println(s)
                  }
                  println("done")
              }
              """
          )
        );
    }

    @Test
    void doNotHoistNonBareCallCondition() {
        rewriteRun(
          //language=java
          java(
            """
              class Test {
                  boolean consume() {
                      System.out.println("side effect");
                      return true;
                  }

                  void test() {
                      if (consume() && false) {
                          System.out.println("hello");
                      } else {
                          System.out.println("hello");
                      }
                  }
              }
              """
          )
        );
    }

    @Test
    void doNotHoistThreeBranchChainWithSideEffect() {
        rewriteRun(
          //language=java
          java(
            """
              class Test {
                  boolean consume(int x) {
                      System.out.println(x);
                      return true;
                  }

                  void test() {
                      if (consume(1)) {
                          System.out.println("hello");
                      } else if (consume(2)) {
                          System.out.println("hello");
                      } else {
                          System.out.println("hello");
                      }
                  }
              }
              """
          )
        );
    }

    @Test
    void collapseIdenticalBranchesGo() {
        rewriteRun(
          //language=go
          go(
            """
              package main

              func applyPromotion(eligible bool) {
                  if eligible {
                      applyDiscount()
                  } else {
                      applyDiscount()
                  }
              }

              func applyDiscount() {}
              """,
            """
              package main

              func applyPromotion(eligible bool) {
                  applyDiscount()
              }

              func applyDiscount() {}
              """
          )
        );
    }
}
