/*
 * Copyright 2025 the original author or authors.
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
import org.openrewrite.test.RewriteTest;

import static org.openrewrite.java.Assertions.java;

class ReorderAnnotationsTest implements RewriteTest {
    @DocumentExample
    @Test
    void reordersMethodAnnotations() {
        rewriteRun(
          spec -> spec.recipe(new ReorderAnnotations()),
          //language=java
          java(
            """
              import org.junit.jupiter.api.Test;
              import org.junitpioneer.jupiter.ExpectedToFail;
              import org.junitpioneer.jupiter.Issue;
              class A {
                  @Issue("https://github.com/openrewrite/rewrite/issues/2973")
                  @Test
                  @ExpectedToFail
                  void explicitImplementationClassInApi() {
                  }
              }
              """,
            """
              import org.junit.jupiter.api.Test;
              import org.junitpioneer.jupiter.ExpectedToFail;
              import org.junitpioneer.jupiter.Issue;
              class A {
                  @ExpectedToFail
                  @Issue("https://github.com/openrewrite/rewrite/issues/2973")
                  @Test
                  void explicitImplementationClassInApi() {
                  }
              }
              """
          )
        );
    }

    @Test
    void reordersClassAnnotations() {
        rewriteRun(
          spec -> spec.recipe(new ReorderAnnotations()),
          //language=java
          java(
            """
              import org.junit.jupiter.api.Disabled;

              @SuppressWarnings("all")
              @Disabled
              class A {
              }
              """,
            """
              import org.junit.jupiter.api.Disabled;

              @Disabled
              @SuppressWarnings("all")
              class A {
              }
              """
          )
        );
    }

    @Test
    void withComments() {
        // Not entirely sure if we'd want to keep comments in the same place, but this at least documents what we do now
        rewriteRun(
          spec -> spec.recipe(new ReorderAnnotations()),
          //language=java
          java(
            """
              import org.junit.jupiter.api.Test;
              import org.junitpioneer.jupiter.ExpectedToFail;
              import org.junitpioneer.jupiter.Issue;
              class A {
                  // Before first
                  @Issue("https://github.com/openrewrite/rewrite/issues/2973")
                  // Before second
                  @Test
                  // Before third
                  @ExpectedToFail
                  // Before method
                  void explicitImplementationClassInApi() {
                  }
              }
              """,
            """
              import org.junit.jupiter.api.Test;
              import org.junitpioneer.jupiter.ExpectedToFail;
              import org.junitpioneer.jupiter.Issue;
              class A {
                  // Before first
                  @ExpectedToFail
                  // Before second
                  @Issue("https://github.com/openrewrite/rewrite/issues/2973")
                  // Before third
                  @Test
                  // Before method
                  void explicitImplementationClassInApi() {
                  }
              }
              """
          )
        );
    }

    @Nested
    class NoChange {
        @Test
        void alreadySorted() {
            rewriteRun(
              spec -> spec.recipe(new ReorderAnnotations()),
              //language=java
              java(
                """
                  import org.junit.jupiter.api.Test;
                  import org.junitpioneer.jupiter.ExpectedToFail;
                  import org.junitpioneer.jupiter.Issue;
                  class A {
                      @ExpectedToFail
                      @Issue("https://github.com/openrewrite/rewrite/issues/2973")
                      @Test
                      void explicitImplementationClassInApi() {
                      }
                  }
                  """
              )
            );
        }
    }
}
