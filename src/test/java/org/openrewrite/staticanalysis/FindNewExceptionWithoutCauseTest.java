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
import org.openrewrite.staticanalysis.table.ExceptionsWithoutCause;
import org.openrewrite.test.RecipeSpec;
import org.openrewrite.test.RewriteTest;

import static org.assertj.core.api.Assertions.assertThat;
import static org.openrewrite.java.Assertions.java;

@SuppressWarnings({"ThrowablePrintStackTrace", "unused", "RedundantThrows", "CallToPrintStackTrace", "UnnecessaryLocalVariable", "CaughtExceptionImmediatelyRethrown"})
class FindNewExceptionWithoutCauseTest implements RewriteTest {

    @Override
    public void defaults(RecipeSpec spec) {
        spec.recipe(new FindNewExceptionWithoutCause());
    }

    @DocumentExample
    @Test
    void throwsNewExceptionWithoutReferencingCaught() {
        rewriteRun(
          //language=java
          java(
            """
              import java.io.IOException;
              class A {
                  void risky() throws IOException {}
                  void foo() {
                      try {
                          risky();
                      } catch (IOException e) {
                          throw new RuntimeException("Failed");
                      }
                  }
              }
              """,
            """
              import java.io.IOException;
              class A {
                  void risky() throws IOException {}
                  void foo() {
                      try {
                          risky();
                      } catch (IOException e) {
                          throw /*~~>*/new RuntimeException("Failed");
                      }
                  }
              }
              """
          )
        );
    }

    @Test
    void caughtExceptionUsedForLoggingButNotChained() {
        rewriteRun(
          //language=java
          java(
            """
              import java.io.IOException;
              class A {
                  void risky() throws IOException {}
                  void foo() {
                      try {
                          risky();
                      } catch (IOException e) {
                          e.printStackTrace();
                          throw new RuntimeException("Failed");
                      }
                  }
              }
              """,
            """
              import java.io.IOException;
              class A {
                  void risky() throws IOException {}
                  void foo() {
                      try {
                          risky();
                      } catch (IOException e) {
                          e.printStackTrace();
                          throw /*~~>*/new RuntimeException("Failed");
                      }
                  }
              }
              """
          )
        );
    }

    @Test
    void doNotChangeWhenCaughtExceptionPassedAsCause() {
        rewriteRun(
          //language=java
          java(
            """
              import java.io.IOException;
              class A {
                  void risky() throws IOException {}
                  void foo() {
                      try {
                          risky();
                      } catch (IOException e) {
                          throw new RuntimeException("Failed", e);
                      }
                  }
              }
              """
          )
        );
    }

    @Test
    void doNotChangeWhenCaughtMessageReferencedDirectly() {
        rewriteRun(
          //language=java
          java(
            """
              import java.io.IOException;
              class A {
                  void risky() throws IOException {}
                  void foo() {
                      try {
                          risky();
                      } catch (IOException e) {
                          throw new IllegalStateException(e.getMessage());
                      }
                  }
              }
              """
          )
        );
    }

    @Test
    void doNotChangeWhenCaughtFlowsThroughLocalVariable() {
        rewriteRun(
          //language=java
          java(
            """
              import java.io.IOException;
              class A {
                  void risky() throws IOException {}
                  void foo() {
                      try {
                          risky();
                      } catch (IOException e) {
                          String message = "Failed: " + e.getMessage();
                          throw new IllegalStateException(message);
                      }
                  }
              }
              """
          )
        );
    }

    @Test
    void doNotChangeWhenCaughtExceptionAliasedThenThrown() {
        rewriteRun(
          //language=java
          java(
            """
              import java.io.IOException;
              class A {
                  void risky() throws IOException {}
                  void foo() {
                      try {
                          risky();
                      } catch (IOException e) {
                          Throwable cause = e;
                          throw new RuntimeException("Failed", cause);
                      }
                  }
              }
              """
          )
        );
    }

    @Test
    void doNotChangeOnPlainRethrow() {
        rewriteRun(
          //language=java
          java(
            """
              import java.io.IOException;
              class A {
                  void risky() throws IOException {}
                  void foo() throws IOException {
                      try {
                          risky();
                      } catch (IOException e) {
                          throw e;
                      }
                  }
              }
              """
          )
        );
    }

    @Test
    void doNotFlagThrowInsideNestedTryBody() {
        rewriteRun(
          //language=java
          java(
            """
              import java.io.IOException;
              class A {
                  void risky() throws IOException {}
                  void foo() {
                      try {
                          risky();
                      } catch (IOException e) {
                          try {
                              throw new RuntimeException("inner", e);
                          } catch (RuntimeException re) {
                              throw new RuntimeException(re);
                          }
                      }
                  }
              }
              """
          )
        );
    }

    @Test
    void nestedCatchIsEvaluatedAgainstItsOwnException() {
        rewriteRun(
          //language=java
          java(
            """
              import java.io.IOException;
              class A {
                  void risky() throws IOException {}
                  void foo() {
                      try {
                          risky();
                      } catch (IOException e) {
                          try {
                              risky();
                          } catch (IOException inner) {
                              throw new RuntimeException("dropped");
                          }
                      }
                  }
              }
              """,
            """
              import java.io.IOException;
              class A {
                  void risky() throws IOException {}
                  void foo() {
                      try {
                          risky();
                      } catch (IOException e) {
                          try {
                              risky();
                          } catch (IOException inner) {
                              throw /*~~>*/new RuntimeException("dropped");
                          }
                      }
                  }
              }
              """
          )
        );
    }

    @Test
    void multiCatchWithoutReferenceIsFlagged() {
        rewriteRun(
          //language=java
          java(
            """
              import java.io.IOException;
              class A {
                  void risky() throws IOException {}
                  void foo() {
                      try {
                          risky();
                      } catch (IOException | RuntimeException e) {
                          throw new IllegalStateException("boom");
                      }
                  }
              }
              """,
            """
              import java.io.IOException;
              class A {
                  void risky() throws IOException {}
                  void foo() {
                      try {
                          risky();
                      } catch (IOException | RuntimeException e) {
                          throw /*~~>*/new IllegalStateException("boom");
                      }
                  }
              }
              """
          )
        );
    }

    @Test
    void recordsDataTableRow() {
        rewriteRun(
          spec -> spec.dataTable(ExceptionsWithoutCause.Row.class, rows -> {
              assertThat(rows).hasSize(1);
              ExceptionsWithoutCause.Row row = rows.getFirst();
              assertThat(row.getSourcePath()).isEqualTo("A.java");
              assertThat(row.getCaughtType()).isEqualTo("java.io.IOException");
              assertThat(row.getThrownType()).isEqualTo("java.lang.RuntimeException");
          }),
          //language=java
          java(
            """
              import java.io.IOException;
              class A {
                  void risky() throws IOException {}
                  void foo() {
                      try {
                          risky();
                      } catch (IOException e) {
                          throw new RuntimeException("Failed");
                      }
                  }
              }
              """,
            """
              import java.io.IOException;
              class A {
                  void risky() throws IOException {}
                  void foo() {
                      try {
                          risky();
                      } catch (IOException e) {
                          throw /*~~>*/new RuntimeException("Failed");
                      }
                  }
              }
              """
          )
        );
    }
}
