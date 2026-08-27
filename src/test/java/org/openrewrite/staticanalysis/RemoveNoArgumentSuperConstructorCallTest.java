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

@SuppressWarnings("ALL")
class RemoveNoArgumentSuperConstructorCallTest implements RewriteTest {

    @Override
    public void defaults(RecipeSpec spec) {
        spec.recipe(new RemoveNoArgumentSuperConstructorCall());
    }

    @DocumentExample
    @Test
    void removeNoArgumentSuperConstructorCall() {
        rewriteRun(
          //language=java
          java(
            """
              class A {
                  private final String name;

                  A(String name) {
                      super();
                      this.name = name;
                  }
              }
              """,
            """
              class A {
                  private final String name;

                  A(String name) {
                      this.name = name;
                  }
              }
              """
          )
        );
    }

    @Test
    void removeOnlyStatementInConstructor() {
        rewriteRun(
          //language=java
          java(
            """
              class A {
                  A() {
                      super();
                  }
              }
              """,
            """
              class A {
                  A() {
                  }
              }
              """
          )
        );
    }

    @Test
    void retainComments() {
        rewriteRun(
          //language=java
          java(
            """
              class A {
                  A() {
                      // Initialize the base class
                      super();
                      init();
                  }

                  void init() {
                  }
              }
              """,
            """
              class A {
                  A() {
                      // Initialize the base class
                      init();
                  }

                  void init() {
                  }
              }
              """
          )
        );
    }

    @Test
    void retainCommentsWhenRemovingOnlyStatement() {
        rewriteRun(
          //language=java
          java(
            """
              class A {
                  A() {
                      // Initialize the base class
                      super();
                  }
              }
              """,
            """
              class A {
                  A() {
                      // Initialize the base class
                  }
              }
              """
          )
        );
    }

    @Test
    void retainTrailingComment() {
        rewriteRun(
          //language=java
          java(
            """
              class A {
                  A() {
                      super(); // Initialize the base class
                      init();
                  }

                  void init() {
                  }
              }
              """,
            """
              class A {
                  A() {
                      // Initialize the base class
                      init();
                  }

                  void init() {
                  }
              }
              """
          )
        );
    }

    @Test
    void doNotChangeSuperCallWithArguments() {
        rewriteRun(
          //language=java
          java(
            """
              class A extends Exception {
                  A(String message) {
                      super(message);
                  }
              }
              """
          )
        );
    }

    @Test
    void doNotChangeThisConstructorCall() {
        rewriteRun(
          //language=java
          java(
            """
              class A {
                  A() {
                      this("default");
                  }

                  A(String name) {
                  }
              }
              """
          )
        );
    }

    @Test
    void doNotChangeQualifiedSuperConstructorCall() {
        rewriteRun(
          //language=java
          java(
            """
              class Outer {
                  class Inner {
                  }
              }

              class Sub extends Outer.Inner {
                  Sub(Outer outer) {
                      outer.super();
                  }
              }
              """
          )
        );
    }

    @Test
    void doNotChangeSuperCallWithExplicitTypeArgument() {
        rewriteRun(
          //language=java
          java(
            """
              class B {
                  <T> B() {
                  }
              }

              class A extends B {
                  A() {
                      <String>super();
                  }
              }
              """
          )
        );
    }

    @Test
    void doNotChangeSuperMethodCall() {
        rewriteRun(
          //language=java
          java(
            """
              class A {
                  A() {
                      super.hashCode();
                  }
              }
              """
          )
        );
    }
}
