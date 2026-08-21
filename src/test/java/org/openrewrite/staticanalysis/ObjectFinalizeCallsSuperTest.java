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
import org.junitpioneer.jupiter.ExpectedToFail;
import org.openrewrite.DocumentExample;
import org.openrewrite.Issue;
import org.openrewrite.test.RecipeSpec;
import org.openrewrite.test.RewriteTest;

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

    @ExpectedToFail("Widens neither throws clause, and the second cycle inserts a duplicate super.finalize() into the subclass; needs hierarchy-aware handling")
    @Issue("https://github.com/openrewrite/rewrite-static-analysis/pull/969")
    @Test
    void addsSuperFinalizeAndWidensThrowsAcrossClassHierarchy() {
        rewriteRun(
          //language=java
          java(
            """
              class A {
                  @Override
                  protected void finalize() {
                  }
              }

              class B extends A {
                  @Override
                  protected void finalize() {
                  }
              }
              """,
            """
              class A {
                  @Override
                  protected void finalize() throws Throwable {
                      super.finalize();
                  }
              }

              class B extends A {
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
}
