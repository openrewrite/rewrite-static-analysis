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

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.openrewrite.DocumentExample;
import org.openrewrite.Issue;
import org.openrewrite.test.RecipeSpec;
import org.openrewrite.test.RewriteTest;

import static org.openrewrite.java.Assertions.java;
import static org.openrewrite.kotlin.Assertions.kotlin;

@SuppressWarnings("UnnecessaryModifier")
@Issue("https://github.com/openrewrite/rewrite/issues/466")
class ModifierOrderTest implements RewriteTest {

    @Override
    public void defaults(RecipeSpec spec) {
        spec.recipe(new ModifierOrder());
    }

    @DocumentExample
    @Test
    void changeModifierOrder() {
        rewriteRun(
          //language=java
          java(
            """
              import org.openrewrite.internal.lang.Nullable;
              class Test {
                  static /* comment */ public strictfp @Nullable transient Integer test() {
                  }
              }
              """,
            """
              import org.openrewrite.internal.lang.Nullable;
              class Test {
                  public /* comment */ static transient @Nullable strictfp Integer test() {
                  }
              }
              """
          )
        );
    }

    @Test
    void dontChangeOrderedModifiers() {
        rewriteRun(
          //language=java
          java(
            """
              class Test {
                  public static void main(String[] args) {
                  }
              }
              """
          )
        );
    }

    @Test
    @Issue("https://github.com/openrewrite/rewrite-static-analysis/issues/187")
    void putDefaultModifierAtJLSRightPosition() {
        // default modifier must be placed between abstract and static modifiers
        rewriteRun(
          //language=java
          java(
            """
              interface Foo {
                  public static default void bar() {
                      int i = 5;
                  }

                  default private static void baz() {
                      int i = 5;
                  }

                  static default protected void qux() {
                      int i = 5;
                  }
              }
              """
          ,
                """
              interface Foo {
                  public default static void bar() {
                      int i = 5;
                  }

                  private default static void baz() {
                      int i = 5;
                  }

                  protected default static void qux() {
                      int i = 5;
                  }
              }
              """
          )
        );
    }

    @Nested
    class KotlinTest {
        @Test
        void constModifier() {
            rewriteRun(
              kotlin(
                """
                  object Test {
                      private const val CLIENT = "ABC"
                  }
                  """
              )
            );
        }

        @Test
        void overrideModifier() {
            rewriteRun(
              kotlin(
                """
                  open class Shape {
                      public open fun draw() {
                      }
                  }

                  class Circle : Shape() {
                      public override fun draw() {
                      }
                  }
                  """
              )
            );
        }
    }
}
