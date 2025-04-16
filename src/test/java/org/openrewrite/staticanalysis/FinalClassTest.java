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
import org.openrewrite.Issue;
import org.openrewrite.test.RecipeSpec;
import org.openrewrite.test.RewriteTest;

import static org.openrewrite.java.Assertions.java;

class FinalClassTest implements RewriteTest {

    @Override
    public void defaults(RecipeSpec spec) {
        spec.recipe(new FinalClass());
    }

    @DocumentExample
    @Test
    void finalizeClass() {
        rewriteRun(
          //language=java
          java(
            """
              public class A {
                  private A(String s) {
                  }

                  private A() {
                  }
              }
              """,
            """
              public final class A {
                  private A(String s) {
                  }

                  private A() {
                  }
              }
              """
          )
        );
    }

    @Test
    @Issue("https://github.com/openrewrite/rewrite/issues/2954")
    void nestedClassWithSubclass() {
        rewriteRun(
          //language=java
          java(
            """
              class A {
                  private A() {
                  }

                  private static class C extends B {
                      private C() {
                      }

                      private static class D extends C {
                      }
                  }

                  private static class B extends A {
                      private B() {
                      }
                  }
              }
              """
          )
        );
    }

    @Test
    void hasPublicConstructor() {
        rewriteRun(
          //language=java
          java(
            """
              public class A {
                  private A(String s) {
                  }

                  public A() {
                  }
              }
              """
          )
        );
    }

    @Test
    void hasImplicitConstructor() {
        rewriteRun(
          //language=java
          java(
            """
              public class A {
              }
              """
          )
        );
    }

    @Test
    void innerClass() {
        rewriteRun(
          //language=java
          java(
            """
              class A {

                  class B {
                      private B() {}
                  }
              }
              """,
            """
              class A {

                  final class B {
                      private B() {}
                  }
              }
              """
          )
        );
    }

    @Issue("https://github.com/openrewrite/rewrite/issues/1061")
    @Test
    void abstractClass() {
        rewriteRun(
          //language=java
          java(
            """
              public abstract class A {
                  public static void foo() {
                  }

                  private A() {
                  }
              }
              """
          )
        );
    }

    @Issue("https://github.com/openrewrite/rewrite/issues/2339")
    @Test
    void classWithAnnotation() {
        rewriteRun(
          //language=java
          java(
            """
              class A {

                  @Deprecated
                  class B {
                      private B() {}
                  }
              }
              """,
            """
              class A {

                  @Deprecated
                  final class B {
                      private B() {}
                  }
              }
              """
          )
        );
    }
}
