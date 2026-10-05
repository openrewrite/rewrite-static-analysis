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
import org.openrewrite.test.TypeValidation;

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
    void doNotChangeWhenCaughtFlowsThroughDerivedLocals() {
        rewriteRun(
          //language=java
          java(
            """
              import java.io.IOException;
              class A {
                  static class Result {
                      Throwable getFailureCause() { return null; }
                  }
                  static class RetryException extends IOException {
                      Result getLastAttempt() { return new Result(); }
                  }
                  void risky() throws RetryException {}
                  Throwable unwrapRootCause(Throwable t) { return t.getCause(); }
                  void oneHop() {
                      try {
                          risky();
                      } catch (RetryException e) {
                          Throwable cause = unwrapRootCause(e);
                          throw new IllegalStateException(cause);
                      }
                  }
                  void twoHops() {
                      try {
                          risky();
                      } catch (RetryException e) {
                          Result r = e.getLastAttempt();
                          Throwable cause = r.getFailureCause();
                          throw new IllegalStateException(cause);
                      }
                  }
              }
              """
          )
        );
    }

    @Test
    void doNotChangeWhenEnclosingCatchExceptionUsed() {
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
                      } catch (Exception e) {
                          try {
                              throw (IllegalStateException) e;
                          } catch (ClassCastException ex) {
                              throw new RuntimeException(e);
                          }
                      }
                  }
              }
              """
          )
        );
    }

    @Test
    void fluentThrowIsFlaggedOnlyWhenNoInvocationInTheChainCarriesTheCause() {
        rewriteRun(
          //language=java
          java(
            """
              import java.io.IOException;
              class A {
                  static class ServiceError extends RuntimeException {
                      ServiceError withCode(int code) { return this; }
                      ServiceError withMessage(String message) { return this; }
                  }
                  void risky() throws IOException {}
                  void dropped() {
                      try {
                          risky();
                      } catch (IOException e) {
                          throw new ServiceError().withCode(1).withMessage("could not process");
                      }
                  }
                  void preserved() {
                      try {
                          risky();
                      } catch (IOException e) {
                          throw new ServiceError().withCode(1).withMessage(e.getMessage());
                      }
                  }
              }
              """,
            """
              import java.io.IOException;
              class A {
                  static class ServiceError extends RuntimeException {
                      ServiceError withCode(int code) { return this; }
                      ServiceError withMessage(String message) { return this; }
                  }
                  void risky() throws IOException {}
                  void dropped() {
                      try {
                          risky();
                      } catch (IOException e) {
                          throw /*~~>*/new ServiceError().withCode(1).withMessage("could not process");
                      }
                  }
                  void preserved() {
                      try {
                          risky();
                      } catch (IOException e) {
                          throw new ServiceError().withCode(1).withMessage(e.getMessage());
                      }
                  }
              }
              """
          )
        );
    }

    @Test
    void fallsBackToSyntacticTypeNamesWhenTypesAreUnresolved() {
        rewriteRun(
          spec -> spec
            .typeValidationOptions(TypeValidation.none())
            .dataTable(ExceptionsWithoutCause.Row.class, rows -> {
                ExceptionsWithoutCause.Row row = rows.getFirst();
                assertThat(row.getCaughtType()).isEqualTo("org.acme.BoomException");
                assertThat(row.getThrownType()).isEqualTo("org.acme.WrappedException");
                assertThat(row.isTypeResolved()).isFalse();
            }),
          //language=java
          java(
            """
              class A {
                  void risky() throws org.acme.BoomException {}
                  void foo() {
                      try {
                          risky();
                      } catch (org.acme.BoomException e) {
                          throw new org.acme.WrappedException("failed");
                      }
                  }
              }
              """,
            """
              class A {
                  void risky() throws org.acme.BoomException {}
                  void foo() {
                      try {
                          risky();
                      } catch (org.acme.BoomException e) {
                          throw /*~~>*/new org.acme.WrappedException("failed");
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
    void methodNameMatchingATaintedLocalIsNotAReference() {
        rewriteRun(
          //language=java
          java(
            """
              import java.io.IOException;
              class A {
                  static class Config {
                      String message() { return "config"; }
                  }
                  void risky() throws IOException {}
                  void foo(Config cfg) {
                      try {
                          risky();
                      } catch (IOException e) {
                          String message = e.getMessage();
                          System.out.println(message);
                          throw new IllegalStateException(cfg.message());
                      }
                  }
              }
              """,
            """
              import java.io.IOException;
              class A {
                  static class Config {
                      String message() { return "config"; }
                  }
                  void risky() throws IOException {}
                  void foo(Config cfg) {
                      try {
                          risky();
                      } catch (IOException e) {
                          String message = e.getMessage();
                          System.out.println(message);
                          throw /*~~>*/new IllegalStateException(cfg.message());
                      }
                  }
              }
              """
          )
        );
    }

    @Test
    void recordsTheThrownTypeNotTheChainRootType() {
        rewriteRun(
          spec -> spec.dataTable(ExceptionsWithoutCause.Row.class, rows ->
            assertThat(rows.getFirst().getThrownType()).isEqualTo("A$ServiceError")),
          //language=java
          java(
            """
              import java.io.IOException;
              class A {
                  static class ServiceError extends RuntimeException {}
                  static class ErrorBuilder {
                      ErrorBuilder withCode(int code) { return this; }
                      ServiceError build() { return new ServiceError(); }
                  }
                  void risky() throws IOException {}
                  void foo() {
                      try {
                          risky();
                      } catch (IOException e) {
                          throw new ErrorBuilder().withCode(1).build();
                      }
                  }
              }
              """,
            """
              import java.io.IOException;
              class A {
                  static class ServiceError extends RuntimeException {}
                  static class ErrorBuilder {
                      ErrorBuilder withCode(int code) { return this; }
                      ServiceError build() { return new ServiceError(); }
                  }
                  void risky() throws IOException {}
                  void foo() {
                      try {
                          risky();
                      } catch (IOException e) {
                          throw /*~~>*/new ErrorBuilder().withCode(1).build();
                      }
                  }
              }
              """
          )
        );
    }

    @Test
    void positionsAreRecordedPerSourceFile() {
        rewriteRun(
          spec -> spec.dataTable(ExceptionsWithoutCause.Row.class, rows -> {
              assertThat(rows).hasSize(2);
              assertThat(rows).allSatisfy(row -> assertThat(row.getLineNumber()).isPositive());
          }),
          //language=java
          java(
            """
              class A {
                  void foo() {
                      try {
                          throw new java.io.IOException();
                      } catch (java.io.IOException e) {
                          throw new RuntimeException("a");
                      }
                  }
              }
              """,
            """
              class A {
                  void foo() {
                      try {
                          throw new java.io.IOException();
                      } catch (java.io.IOException e) {
                          throw /*~~>*/new RuntimeException("a");
                      }
                  }
              }
              """
          ),
          //language=java
          java(
            """
              class B {
                  void foo() {
                      try {
                          throw new java.io.IOException();
                      } catch (java.io.IOException e) {
                          throw new RuntimeException("b");
                      }
                  }
              }
              """,
            """
              class B {
                  void foo() {
                      try {
                          throw new java.io.IOException();
                      } catch (java.io.IOException e) {
                          throw /*~~>*/new RuntimeException("b");
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
          spec -> spec.dataTable(ExceptionsWithoutCause.Row.class, rows -> {
              assertThat(rows.getFirst().getCaughtType()).isEqualTo("java.io.IOException | java.lang.RuntimeException");
              assertThat(rows.getFirst().isTypeResolved()).isTrue();
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
              assertThat(row.isTypeResolved()).isTrue();
              assertThat(row.getLineNumber()).isEqualTo(8);
              assertThat(row.getColumnNumber()).isEqualTo(12);
              assertThat(row.getThrowSnippet()).isEqualTo("throw new RuntimeException(\"Failed\")");
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
