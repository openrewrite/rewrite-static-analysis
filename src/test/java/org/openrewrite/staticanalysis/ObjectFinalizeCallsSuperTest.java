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
import org.openrewrite.test.TypeValidation;

import static org.openrewrite.java.Assertions.java;

class ObjectFinalizeCallsSuperTest implements RewriteTest {

    @Override
    public void defaults(RecipeSpec spec) {
        spec.recipe(new ObjectFinalizeCallsSuper());
    }

    @DocumentExample
    @Test
    void addsSuperFinalizeInvocation() {
        rewriteRun(
          //language=java
          java(
            """
              class F {
                  Object o = new Object();

                  @Override
                  protected void finalize() throws Throwable {
                      o = null;
                  }
              }
              """,
            """
              class F {
                  Object o = new Object();

                  @Override
                  protected void finalize() throws Throwable {
                      o = null;
                      super.finalize();
                  }
              }
              """
          )
        );
    }

    @Test
    void hasSuperFinalizeInvocation() {
        rewriteRun(
          //language=java
          java(
            """
              class F {
                  Object o = new Object();

                  @Override
                  protected void finalize() throws Throwable {
                      o = null;
                      super.finalize();
                  }
              }
              """
          )
        );
    }

    @Test
    void addsThrowsThrowableWhenOverrideDeclaresNoThrows() {
        rewriteRun(
          //language=java
          java(
            """
              class F {
                  @Override
                  protected void finalize() {
                      cleanup();
                  }

                  void cleanup() {
                  }
              }
              """,
            """
              class F {
                  @Override
                  protected void finalize() throws Throwable {
                      cleanup();
                      super.finalize();
                  }

                  void cleanup() {
                  }
              }
              """
          )
        );
    }

    @Test
    void addsThrowsThrowableToEmptyBody() {
        rewriteRun(
          //language=java
          java(
            """
              class F {
                  @Override
                  protected void finalize() {
                  }
              }
              """,
            """
              class F {
                  @Override
                  protected void finalize() throws Throwable {
                      super.finalize();
                  }
              }
              """
          )
        );
    }

    @Test
    void addsThrowsThrowableRetainingComments() {
        rewriteRun(
          //language=java
          java(
            """
              class F {
                  Object o = new Object();

                  @Override
                  protected void finalize() {
                      // release the reference
                      o = null;
                  }
              }
              """,
            """
              class F {
                  Object o = new Object();

                  @Override
                  protected void finalize() throws Throwable {
                      // release the reference
                      o = null;
                      super.finalize();
                  }
              }
              """
          )
        );
    }

    @Test
    void addsThrowsThrowableWhenSuperclassDeclaresThrowable() {
        rewriteRun(
          // `JavaTemplate` does not attribute the `super` of the generated call when the superclass is
          // declared in the same compilation unit
          spec -> spec.typeValidationOptions(TypeValidation.builder().identifiers(false).build()),
          //language=java
          java(
            """
              class Parent {
                  @Override
                  protected void finalize() throws Throwable {
                      super.finalize();
                  }
              }

              class Child extends Parent {
                  @Override
                  protected void finalize() {
                  }
              }
              """,
            """
              class Parent {
                  @Override
                  protected void finalize() throws Throwable {
                      super.finalize();
                  }
              }

              class Child extends Parent {
                  @Override
                  protected void finalize() throws Throwable {
                      super.finalize();
                  }
              }
              """
          )
        );
    }

    @Test
    void addsSuperFinalizeWithoutThrowsWhenSuperclassDeclaresNoThrows() {
        rewriteRun(
          // `JavaTemplate` does not attribute the `super` of the generated call when the superclass is
          // declared in the same compilation unit
          spec -> spec.typeValidationOptions(TypeValidation.builder().identifiers(false).build()),
          //language=java
          java(
            """
              class Parent {
                  @Override
                  protected void finalize() {
                      try {
                          super.finalize();
                      } catch (Throwable ignored) {
                      }
                  }
              }

              class Child extends Parent {
                  @Override
                  protected void finalize() {
                      cleanup();
                  }

                  void cleanup() {
                  }
              }
              """,
            """
              class Parent {
                  @Override
                  protected void finalize() {
                      try {
                          super.finalize();
                      } catch (Throwable ignored) {
                      }
                  }
              }

              class Child extends Parent {
                  @Override
                  protected void finalize() {
                      cleanup();
                      super.finalize();
                  }

                  void cleanup() {
                  }
              }
              """
          )
        );
    }

    @Test
    void doNotChangeWhenSuperclassNarrowsThrowsToAnotherException() {
        rewriteRun(
          //language=java
          java(
            """
              import java.io.IOException;

              class Parent {
                  @Override
                  protected void finalize() throws IOException {
                  }
              }

              class Child extends Parent {
                  @Override
                  protected void finalize() {
                      cleanup();
                  }

                  void cleanup() {
                  }
              }
              """
          )
        );
    }

    @Test
    void doNotChangeWhenThrowsClauseDoesNotCoverThrowable() {
        rewriteRun(
          //language=java
          java(
            """
              class F {
                  @Override
                  protected void finalize() throws Exception {
                      cleanup();
                  }

                  void cleanup() {
                  }
              }
              """
          )
        );
    }
}
