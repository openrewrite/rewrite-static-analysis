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

import static org.openrewrite.java.Assertions.java;
import static org.openrewrite.kotlin.Assertions.kotlin;

@SuppressWarnings({"UnusedLabel", "unused"})
class RemoveUnusedLabelsTest implements RewriteTest {

    @Override
    public void defaults(RecipeSpec spec) {
        spec.recipe(new RemoveUnusedLabels());
    }

    @DocumentExample
    @Test
    void unusedLabelOnForLoop() {
        rewriteRun(
          //language=java
          java(
            """
              class A {
                  void foo() {
                      label: for (int i = 0; i < 10; i++) {
                          System.out.println(i);
                      }
                  }
              }
              """,
            """
              class A {
                  void foo() {
                      for (int i = 0; i < 10; i++) {
                          System.out.println(i);
                      }
                  }
              }
              """
          )
        );
    }

    @Test
    void unusedLabelOnWhileLoopWithUnlabeledBreak() {
        rewriteRun(
          //language=java
          java(
            """
              class A {
                  void foo() {
                      loop: while (true) {
                          break;
                      }
                  }
              }
              """,
            """
              class A {
                  void foo() {
                      while (true) {
                          break;
                      }
                  }
              }
              """
          )
        );
    }

    @Test
    void doNotChangeUsedLabelWithBreak() {
        rewriteRun(
          //language=java
          java(
            """
              class A {
                  void foo() {
                      outer: for (int i = 0; i < 10; i++) {
                          for (int j = 0; j < 10; j++) {
                              if (j == 5) break outer;
                          }
                      }
                  }
              }
              """
          )
        );
    }

    @Test
    void doNotChangeUsedLabelWithContinue() {
        rewriteRun(
          //language=java
          java(
            """
              class A {
                  void foo() {
                      outer: for (int i = 0; i < 10; i++) {
                          for (int j = 0; j < 10; j++) {
                              if (j == 5) continue outer;
                          }
                      }
                  }
              }
              """
          )
        );
    }

    @Test
    void removeUnusedInnerLabelKeepUsedOuterLabel() {
        rewriteRun(
          //language=java
          java(
            """
              class A {
                  void foo() {
                      outer: for (int i = 0; i < 10; i++) {
                          inner: for (int j = 0; j < 10; j++) {
                              if (j == 5) break outer;
                          }
                      }
                  }
              }
              """,
            """
              class A {
                  void foo() {
                      outer: for (int i = 0; i < 10; i++) {
                          for (int j = 0; j < 10; j++) {
                              if (j == 5) break outer;
                          }
                      }
                  }
              }
              """
          )
        );
    }

    @Test
    void preserveBlockCommentAfterLabel() {
        rewriteRun(
          //language=java
          java(
            """
              class A {
                  void foo() {
                      label: /* why this loop exists */
                      while (true) {
                          break;
                      }
                  }
              }
              """,
            """
              class A {
                  void foo() {
                      /* why this loop exists */
                      while (true) {
                          break;
                      }
                  }
              }
              """
          )
        );
    }

    @Test
    void preserveCommentsAroundLabel() {
        rewriteRun(
          //language=java
          java(
            """
              class A {
                  void foo() {
                      // before the label
                      label: /* after the colon */ // end of line
                      for (int i = 0; i < 10; i++) {
                          System.out.println(i);
                      }
                  }
              }
              """,
            """
              class A {
                  void foo() {
                      // before the label
                      /* after the colon */ // end of line
                      for (int i = 0; i < 10; i++) {
                          System.out.println(i);
                      }
                  }
              }
              """
          )
        );
    }

    @Test
    void preserveCommentBeforeLabelColon() {
        rewriteRun(
          //language=java
          java(
            """
              class A {
                  void foo() {
                      label /* an odd place */ : while (true) {
                          break;
                      }
                  }
              }
              """,
            """
              class A {
                  void foo() {
                      /* an odd place */ while (true) {
                          break;
                      }
                  }
              }
              """
          )
        );
    }

    @Test
    void preserveCommentsOnEveryLabeledStatementShape() {
        rewriteRun(
          //language=java
          java(
            """
              class A {
                  void foo(int i) {
                      block: /* a block */ {
                          System.out.println("hello");
                      }
                      loop: /* a do while */ do {
                          System.out.println("hello");
                      } while (true);
                      choice: /* a switch */ switch (i) {
                          default:
                              break;
                      }
                      statement: /* an expression */ System.out.println("hello");
                  }
              }
              """,
            """
              class A {
                  void foo(int i) {
                      /* a block */ {
                          System.out.println("hello");
                      }
                      /* a do while */ do {
                          System.out.println("hello");
                      } while (true);
                      /* a switch */ switch (i) {
                          default:
                              break;
                      }
                      /* an expression */ System.out.println("hello");
                  }
              }
              """
          )
        );
    }

    @Test
    void removeUnusedNestedLabelsKeepingComments() {
        rewriteRun(
          //language=java
          java(
            """
              class A {
                  void foo() {
                      outer: /* outer loop */
                      for (int i = 0; i < 10; i++) {
                          inner: /* inner loop */
                          for (int j = 0; j < 10; j++) {
                              System.out.println(j);
                          }
                      }
                  }
              }
              """,
            """
              class A {
                  void foo() {
                      /* outer loop */
                      for (int i = 0; i < 10; i++) {
                          /* inner loop */
                          for (int j = 0; j < 10; j++) {
                              System.out.println(j);
                          }
                      }
                  }
              }
              """
          )
        );
    }

    @Test
    void doNotChangeUsedLabelWithComment() {
        rewriteRun(
          //language=java
          java(
            """
              class A {
                  void foo() {
                      outer: /* why this loop exists */
                      for (int i = 0; i < 10; i++) {
                          for (int j = 0; j < 10; j++) {
                              if (j == 5) continue outer;
                          }
                      }
                  }
              }
              """
          )
        );
    }

    @Test
    void unusedLabelOnBlock() {
        rewriteRun(
          //language=java
          java(
            """
              class A {
                  void foo() {
                      block: {
                          System.out.println("hello");
                      }
                  }
              }
              """,
            """
              class A {
                  void foo() {
                      {
                          System.out.println("hello");
                      }
                  }
              }
              """
          )
        );
    }

    @Test
    void doNotChangeKotlinLabelUsedByLabeledReturn() {
        rewriteRun(
          //language=kotlin
          kotlin(
            """
              class A {
                  fun foo(items: List<Int>) {
                      items.forEach lit@{
                          if (it == 0) return@lit
                          println(it)
                      }
                  }
              }
              """
          )
        );
    }

    @Test
    void doNotChangeKotlinLabelUsedByQualifiedThis() {
        rewriteRun(
          //language=kotlin
          kotlin(
            """
              class A {
                  fun render(): String {
                      val f = outer@ fun StringBuilder.(): Unit {
                          this@outer.append("x")
                      }
                      return StringBuilder().apply(f).toString()
                  }
              }
              """
          )
        );
    }

    @Test
    void doNotChangeKotlinLabelWhenNestedLambdaLabelHasSameName() {
        // `return@lit` binds to the inner lambda label, so the name-based check keeps both rather than scope them
        rewriteRun(
          //language=kotlin
          kotlin(
            """
              class A {
                  fun foo(items: List<Int>) {
                      lit@ for (i in items) {
                          items.forEach lit@{
                              if (it == 0) return@lit
                              println(it)
                          }
                      }
                  }
              }
              """
          )
        );
    }

    @Test
    void removeUnusedKotlinLabel() {
        rewriteRun(
          //language=kotlin
          kotlin(
            """
              class A {
                  fun foo() {
                      unused@ while (true) {
                          break
                      }
                  }
              }
              """,
            """
              class A {
                  fun foo() {
                      while (true) {
                          break
                      }
                  }
              }
              """
          )
        );
    }

    @Test
    void removeUnusedKotlinLabelOnLambda() {
        rewriteRun(
          //language=kotlin
          kotlin(
            """
              class A {
                  fun foo(items: List<Int>) {
                      items.forEach lit@{
                          println(it)
                      }
                  }
              }
              """,
            """
              class A {
                  fun foo(items: List<Int>) {
                      items.forEach {
                          println(it)
                      }
                  }
              }
              """
          )
        );
    }
}
