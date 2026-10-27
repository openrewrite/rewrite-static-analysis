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

import static org.openrewrite.groovy.Assertions.groovy;
import static org.openrewrite.java.Assertions.java;
import static org.openrewrite.kotlin.Assertions.kotlin;

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
    void addsThrowsThrowableWhenSuperclassDeclaresThrowable() {
        rewriteRun(
          //language=java
          java(
            """
              class Parent {
                  @Override
                  protected void finalize() throws Throwable {
                      super.finalize();
                  }
              }
              """
          ),
          //language=java
          java(
            """
              class Child extends Parent {
                  @Override
                  protected void finalize() {
                  }
              }
              """,
            """
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
                      super.finalize();
                  }
              }
              """
          )
        );
    }

    @Test
    void addsSuperFinalizeWhenThrowsClauseCoversSuperclassThrows() {
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
                  protected void finalize() throws IOException {
                  }
              }
              """,
            """
              import java.io.IOException;

              class Parent {
                  @Override
                  protected void finalize() throws IOException {
                  }
              }

              class Child extends Parent {
                  @Override
                  protected void finalize() throws IOException {
                      super.finalize();
                  }
              }
              """
          )
        );
    }

    @Test
    void declaresExceptionsOfSuperclassWhenOverrideDeclaresNoThrows() {
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
              """
          ),
          //language=java
          java(
            """
              class Child extends Parent {
                  @Override
                  protected void finalize() {
                  }
              }
              """,
            """
              import java.io.IOException;

              class Child extends Parent {
                  @Override
                  protected void finalize() throws IOException {
                      super.finalize();
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
                  }
              }
              """
          )
        );
    }

    @Test
    void addsSuperFinalizeInvocationInGroovy() {
        rewriteRun(
          //language=groovy
          groovy(
            """
              class F {
                  Object o = new Object()

                  @Override
                  protected void finalize() throws Throwable {
                      o = null
                  }
              }
              """,
            """
              class F {
                  Object o = new Object()

                  @Override
                  protected void finalize() throws Throwable {
                      o = null
                      super.finalize()
                  }
              }
              """
          )
        );
    }

    @Test
    void doNotChangeKotlin() {
        rewriteRun(
          //language=kotlin
          kotlin(
            """
              class F {
                  var o: Any? = Any()

                  protected fun finalize() {
                      o = null
                  }
              }
              """
          )
        );
    }
}
