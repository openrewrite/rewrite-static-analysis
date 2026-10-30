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
import org.openrewrite.Issue;
import org.openrewrite.test.RecipeSpec;
import org.openrewrite.test.RewriteTest;

import static org.openrewrite.java.Assertions.java;

@Issue("https://github.com/openrewrite/rewrite-static-analysis/issues/1050")
class RemoveMethodsOnlyCallSuperCommentsTest implements RewriteTest {
    @Override
    public void defaults(RecipeSpec spec) {
        spec.recipe(new RemoveMethodsOnlyCallSuper())
          .cycles(2)
          .expectedCyclesThatMakeChanges(0);
    }

    @Test
    void retainOrdinaryTodoInMethodPrefix() {
        rewriteRun(
          //language=java
          java(
            """
              class Parent {
                  void foo() {
                  }
              }
              """
          ),
          //language=java
          java(
            """
              class Child extends Parent {
                  // TODO implement validation in this override.
                  @Override
                  void foo() {
                      super.foo();
                  }
              }
              """
          )
        );
    }

    @Test
    void retainBodyLineCommentBeforeSuperCall() {
        rewriteRun(
          //language=java
          java(
            """
              class Parent {
                  void foo() {
                  }
              }
              """
          ),
          //language=java
          java(
            """
              class Child extends Parent {
                  @Override
                  void foo() {
                      // TODO replace this placeholder with the real implementation.
                      super.foo();
                  }
              }
              """
          )
        );
    }

    @Test
    void retainBodyBlockCommentBeforeSuperCall() {
        rewriteRun(
          //language=java
          java(
            """
              class Parent {
                  void foo() {
                  }
              }
              """
          ),
          //language=java
          java(
            """
              class Child extends Parent {
                  @Override
                  void foo() {
                      /* TODO add the child-specific implementation. */
                      super.foo();
                  }
              }
              """
          )
        );
    }

    @Test
    void retainTrailingBodyEndComment() {
        rewriteRun(
          //language=java
          java(
            """
              class Parent {
                  void foo() {
                  }
              }
              """
          ),
          //language=java
          java(
            """
              class Child extends Parent {
                  @Override
                  void foo() {
                      super.foo();
                      // TODO perform child-specific work after the parent call.
                  }
              }
              """
          )
        );
    }

    @Test
    void retainInlineSuperArgumentComment() {
        rewriteRun(
          //language=java
          java(
            """
              class Parent {
                  void foo(int value) {
                  }
              }
              """
          ),
          //language=java
          java(
            """
              class Child extends Parent {
                  @Override
                  void foo(int value) {
                      super.foo(/* TODO validate this value in the child implementation. */ value);
                  }
              }
              """
          )
        );
    }

    @Test
    void removeUncommentedOverrideDespiteUnrelatedParentAndClassComments() {
        rewriteRun(
          spec -> spec.expectedCyclesThatMakeChanges(1),
          //language=java
          java(
            """
              // The parent owns the shared implementation.
              class Parent {
                  // TODO improve the parent implementation independently.
                  void foo() {
                  }
              }
              """
          ),
          //language=java
          java(
            """
              // This comment describes the child class, not its override.
              class Child extends Parent {
                  @Override
                  void foo() {
                      super.foo();
                  }
              }
              """,
            """
              // This comment describes the child class, not its override.
              class Child extends Parent {
              }
              """
          )
        );
    }
}
