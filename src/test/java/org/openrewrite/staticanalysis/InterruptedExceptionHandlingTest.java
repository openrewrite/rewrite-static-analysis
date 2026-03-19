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

import static org.openrewrite.java.Assertions.java;

@SuppressWarnings({"EmptyCatchBlock", "CatchMayIgnoreException", "InterruptedExceptionSwallowed"})
class InterruptedExceptionHandlingTest implements RewriteTest {

    @Override
    public void defaults(RecipeSpec spec) {
        spec.recipe(new InterruptedExceptionHandling());
    }

    @DocumentExample
    @Test
    void addInterruptToEmptyCatch() {
        rewriteRun(
          //language=java
          java(
            """
              class Test {
                  void foo() {
                      try {
                          Thread.sleep(1000);
                      } catch (InterruptedException e) {
                      }
                  }
              }
              """,
            """
              class Test {
                  void foo() {
                      try {
                          Thread.sleep(1000);
                      } catch (InterruptedException e) {
                          Thread.currentThread().interrupt();
                      }
                  }
              }
              """
          )
        );
    }

    @Test
    void addInterruptBeforeExistingStatements() {
        rewriteRun(
          //language=java
          java(
            """
              class Test {
                  void foo() {
                      try {
                          Thread.sleep(1000);
                      } catch (InterruptedException e) {
                          System.out.println("interrupted");
                      }
                  }
              }
              """,
            """
              class Test {
                  void foo() {
                      try {
                          Thread.sleep(1000);
                      } catch (InterruptedException e) {
                          Thread.currentThread().interrupt();
                          System.out.println("interrupted");
                      }
                  }
              }
              """
          )
        );
    }

    @Test
    void doNotChangeWhenInterruptAlreadyPresent() {
        rewriteRun(
          //language=java
          java(
            """
              class Test {
                  void foo() {
                      try {
                          Thread.sleep(1000);
                      } catch (InterruptedException e) {
                          Thread.currentThread().interrupt();
                          System.out.println("interrupted");
                      }
                  }
              }
              """
          )
        );
    }

    @Test
    void multiCatchWithInterruptedException() {
        rewriteRun(
          //language=java
          java(
            """
              import java.io.IOException;

              class Test {
                  void foo() {
                      try {
                          Thread.sleep(1000);
                      } catch (IOException | InterruptedException e) {
                          System.out.println("error");
                      }
                  }
              }
              """,
            """
              import java.io.IOException;

              class Test {
                  void foo() {
                      try {
                          Thread.sleep(1000);
                      } catch (IOException | InterruptedException e) {
                          if (e instanceof InterruptedException) {
                              Thread.currentThread().interrupt();
                          }
                          System.out.println("error");
                      }
                  }
              }
              """
          )
        );
    }

    @Test
    void doNotChangeForNonInterruptedException() {
        rewriteRun(
          //language=java
          java(
            """
              import java.io.IOException;

              class Test {
                  void foo() {
                      try {
                          System.out.println("hello");
                      } catch (IOException e) {
                          System.out.println("error");
                      }
                  }
              }
              """
          )
        );
    }

    @Test
    void doNotChangeWhenInterruptExistsInMultiCatch() {
        rewriteRun(
          //language=java
          java(
            """
              import java.io.IOException;

              class Test {
                  void foo() {
                      try {
                          Thread.sleep(1000);
                      } catch (IOException | InterruptedException e) {
                          if (e instanceof InterruptedException) {
                              Thread.currentThread().interrupt();
                          }
                      }
                  }
              }
              """
          )
        );
    }
}
