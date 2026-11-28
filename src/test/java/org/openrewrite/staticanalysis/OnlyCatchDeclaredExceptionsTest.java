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
import org.openrewrite.Issue;
import org.openrewrite.java.JavaParser;
import org.openrewrite.test.RecipeSpec;
import org.openrewrite.test.RewriteTest;

import static org.openrewrite.java.Assertions.java;
import static org.openrewrite.kotlin.Assertions.kotlin;

class OnlyCatchDeclaredExceptionsTest implements RewriteTest {

    @Override
    public void defaults(RecipeSpec spec) {
        spec.recipe(new OnlyCatchDeclaredExceptions())
          .parser(JavaParser.fromJavaVersion().dependsOn(
              //language=java
              """
                class MockThrowingClass {

                    MockThrowingClass() {
                    }

                     MockThrowingClass(boolean throwSQLException) throws java.sql.SQLException {
                        if (throwSQLException) {
                            throw new java.sql.SQLException("Test Constructor SQL Exception");
                        }
                     }

                    void throwsIOException() throws java.io.IOException {
                        throw new java.io.IOException("Test IO Exception");
                    }

                    void throwsSQLException() throws java.sql.SQLException {
                        throw new java.sql.SQLException("Test SQL Exception");
                    }

                    void throwsBothIOAndSQL() throws java.io.IOException, java.sql.SQLException {
                        throw new java.io.IOException("Test IO or SQL Exception");
                    }

                    void throwsNothing() {
                        // This method throws no checked exceptions
                    }

                    void throwsDeclaredRuntimeException() throws RuntimeException {
                        throw new RuntimeException("Test Runtime Exception");
                    }

                    void throwsUndeclaredRuntimeException() {
                        throw new RuntimeException("Test Runtime Exception");
                    }
                }
                """
            )
          );
    }

    @DocumentExample
    @Test
    void shouldReplaceExceptionWithSingleCheckedException() {
        rewriteRun(
          java(
            """
              class MyService {
                  void doSomething() {
                      try {
                          new MockThrowingClass().throwsIOException();
                      } catch (Exception e) {
                          System.out.println("Caught exception: " + e.getMessage());
                      }
                  }
              }
              """,
            """
              import java.io.IOException;

              class MyService {
                  void doSomething() {
                      try {
                          new MockThrowingClass().throwsIOException();
                      } catch (IOException | RuntimeException e) {
                          System.out.println("Caught exception: " + e.getMessage());
                      }
                  }
              }
              """
          )
        );
    }

    @Test
    void shouldReplaceExceptionWithMultipleCheckedExceptions() {
        rewriteRun(
          java(
            """
              class MyService {
                  void doSomething() {
                      try {
                          new MockThrowingClass().throwsIOException();
                          new MockThrowingClass().throwsSQLException();
                      } catch (Exception e) {
                          System.out.println("Caught exception: " + e.getMessage());
                      }
                  }
              }
              """,
            """
              import java.io.IOException;
              import java.sql.SQLException;

              class MyService {
                  void doSomething() {
                      try {
                          new MockThrowingClass().throwsIOException();
                          new MockThrowingClass().throwsSQLException();
                      } catch (IOException | SQLException | RuntimeException e) {
                          System.out.println("Caught exception: " + e.getMessage());
                      }
                  }
              }
              """
          )
        );
    }

    @Test
    void shouldReplaceExceptionWithMultipleCheckedExceptionsFromSingleCall() {
        rewriteRun(
          java(
            """
              class MyService {
                  void doSomething() {
                      try {
                          new MockThrowingClass().throwsBothIOAndSQL();
                      } catch (Exception e) {
                          System.out.println("Caught exception: " + e.getMessage());
                      }
                  }
              }
              """,
            """
              import java.io.IOException;
              import java.sql.SQLException;

              class MyService {
                  void doSomething() {
                      try {
                          new MockThrowingClass().throwsBothIOAndSQL();
                      } catch (IOException | SQLException | RuntimeException e) {
                          System.out.println("Caught exception: " + e.getMessage());
                      }
                  }
              }
              """
          )
        );
    }

    @Test
    void shouldKeepSpecificCatchAndRemoveGenericWhenCovered() {
        rewriteRun(
          java(
            """
              import java.io.IOException;
              import java.sql.SQLException;

              class MyService {
                  void doSomething() {
                      try {
                          new MockThrowingClass().throwsBothIOAndSQL();
                      } catch (IOException e) {
                          System.out.println("Caught IO: " + e.getMessage());
                      } catch (Exception e) {
                          System.out.println("Caught generic: " + e.getMessage());
                      }
                  }
              }
              """,
            """
              import java.io.IOException;
              import java.sql.SQLException;

              class MyService {
                  void doSomething() {
                      try {
                          new MockThrowingClass().throwsBothIOAndSQL();
                      } catch (IOException e) {
                          System.out.println("Caught IO: " + e.getMessage());
                      } catch (SQLException | RuntimeException e) {
                          System.out.println("Caught generic: " + e.getMessage());
                      }
                  }
              }
              """
          )
        );
    }

    @Test
    void shouldHandleConstructorThrowingException() {
        rewriteRun(
          java(
            """
              class MyService {
                  void createObject() {
                      try {
                          new MockThrowingClass(true); // Throws SQLException
                      } catch (Exception e) {
                          System.out.println("Caught constructor exception: " + e.getMessage());
                      }
                  }
              }
              """,
            """
              import java.sql.SQLException;

              class MyService {
                  void createObject() {
                      try {
                          new MockThrowingClass(true); // Throws SQLException
                      } catch (SQLException | RuntimeException e) {
                          System.out.println("Caught constructor exception: " + e.getMessage());
                      }
                  }
              }
              """
          )
        );
    }

    @Test
    void shouldHandleConstructorThrowingSpecificExceptionAndAnotherGenericCatch() {
        rewriteRun(
          java(
            """
              import java.io.IOException;

              class MyService {
                  void createAndHandle() {
                      try {
                          new MockThrowingClass(true); // Throws SQLException
                          new MockThrowingClass().throwsIOException();
                      } catch (Exception e) {
                          System.out.println("Caught constructor exception: " + e.getMessage());
                      }
                  }
              }
              """,
            """
              import java.io.IOException;
              import java.sql.SQLException;

              class MyService {
                  void createAndHandle() {
                      try {
                          new MockThrowingClass(true); // Throws SQLException
                          new MockThrowingClass().throwsIOException();
                      } catch (IOException | SQLException | RuntimeException e) {
                          System.out.println("Caught constructor exception: " + e.getMessage());
                      }
                  }
              }
              """
          )
        );
    }

    @Test
    void shouldHandleConstructorThrowingExceptionWithExistingSpecificCatch() {
        rewriteRun(
          java(
            """
              import java.io.IOException;
              import java.sql.SQLException;

              class MyService {
                  void createAndHandle() {
                      try {
                          new MockThrowingClass(true); // Throws SQLException
                          new MockThrowingClass().throwsIOException();
                      } catch (SQLException e) {
                          System.out.println("Caught sql exception: " + e.getMessage());
                      } catch (Exception e) {
                          System.out.println("Caught generic: " + e.getMessage());
                      }
                  }
              }
              """,
            """
              import java.io.IOException;
              import java.sql.SQLException;

              class MyService {
                  void createAndHandle() {
                      try {
                          new MockThrowingClass(true); // Throws SQLException
                          new MockThrowingClass().throwsIOException();
                      } catch (SQLException e) {
                          System.out.println("Caught sql exception: " + e.getMessage());
                      } catch (IOException | RuntimeException e) {
                          System.out.println("Caught generic: " + e.getMessage());
                      }
                  }
              }
              """
          )
        );
    }

    @Test
    void shouldHandleExistingMultiCatch() {
        rewriteRun(
          java(
            """
              import java.io.IOException;

              class MyService {
                  void doSomething() {
                      try {
                          new MockThrowingClass().throwsBothIOAndSQL();
                      } catch (IOException | NullPointerException e) {
                          System.out.println("Multi exception: " + e.getMessage());
                      } catch (Exception e) {
                          System.out.println("Caught exception: " + e.getMessage());
                      }
                  }
              }
              """,
            """
              import java.io.IOException;
              import java.sql.SQLException;

              class MyService {
                  void doSomething() {
                      try {
                          new MockThrowingClass().throwsBothIOAndSQL();
                      } catch (IOException | NullPointerException e) {
                          System.out.println("Multi exception: " + e.getMessage());
                      } catch (SQLException | RuntimeException e) {
                          System.out.println("Caught exception: " + e.getMessage());
                      }
                  }
              }
              """
          )
        );
    }

    @Test
    void declaredRuntimeException() {
        rewriteRun(
          java(
            """
              class MyService {
                  void doSomething() {
                      try {
                          new MockThrowingClass().throwsDeclaredRuntimeException();
                      } catch (Exception e) {
                          // This is a generic catch block that should be replaced
                          System.out.println("Caught exception: " + e.getMessage());
                      }
                  }
              }
              """,
            """
              class MyService {
                  void doSomething() {
                      try {
                          new MockThrowingClass().throwsDeclaredRuntimeException();
                      } catch (RuntimeException e) {
                          // This is a generic catch block that should be replaced
                          System.out.println("Caught exception: " + e.getMessage());
                      }
                  }
              }
              """
          )
        );
    }

    @Test
    void sameSimpleNameAsImportedTypeIsFullyQualified() {
        rewriteRun(
          //language=java
          java(
            """
              package com.acme;

              public class ParseException extends Exception {
              }
              """
          ),
          //language=java
          java(
            """
              package com.acme;

              public class Parser {
                  public void parse() throws ParseException, java.text.ParseException {
                  }
              }
              """
          ),
          //language=java
          java(
            """
              import com.acme.ParseException;
              import com.acme.Parser;

              class MyService {
                  void doSomething(Parser parser) {
                      try {
                          parser.parse();
                      } catch (Exception e) {
                          System.out.println(e.getMessage());
                      }
                  }
              }
              """,
            """
              import com.acme.ParseException;
              import com.acme.Parser;

              class MyService {
                  void doSomething(Parser parser) {
                      try {
                          parser.parse();
                      } catch (ParseException | java.text.ParseException | RuntimeException e) {
                          System.out.println(e.getMessage());
                      }
                  }
              }
              """
          )
        );
    }

    @Test
    void sameSimpleNameAsDeclaredTypeIsFullyQualified() {
        rewriteRun(
          //language=java
          java(
            """
              class MyService {
                  static class ParseException {
                  }

                  void doSomething(java.text.DateFormat format) {
                      try {
                          format.parse("x");
                      } catch (Exception e) {
                          System.out.println(e.getMessage());
                      }
                  }
              }
              """,
            """
              class MyService {
                  static class ParseException {
                  }

                  void doSomething(java.text.DateFormat format) {
                      try {
                          format.parse("x");
                      } catch (java.text.ParseException | RuntimeException e) {
                          System.out.println(e.getMessage());
                      }
                  }
              }
              """
          )
        );
    }

    @Test
    void subtypesOfOtherThrownExceptionsAreLeftOut() {
        rewriteRun(
          //language=java
          java(
            """
              import java.io.FileInputStream;

              class MyService {
                  void doSomething() {
                      try {
                          new FileInputStream("file").read();
                          new MockThrowingClass().throwsIOException();
                          Thread.sleep(1);
                      } catch (Exception e) {
                          System.out.println(e.getMessage());
                      }
                  }
              }
              """,
            """
              import java.io.FileInputStream;
              import java.io.IOException;

              class MyService {
                  void doSomething() {
                      try {
                          new FileInputStream("file").read();
                          new MockThrowingClass().throwsIOException();
                          Thread.sleep(1);
                      } catch (IOException | InterruptedException | RuntimeException e) {
                          System.out.println(e.getMessage());
                      }
                  }
              }
              """
          )
        );
    }

    @Test
    void exceptionsCaughtByEarlierClauseAreLeftOut() {
        rewriteRun(
          //language=java
          java(
            """
              import java.io.FileInputStream;
              import java.io.IOException;

              class MyService {
                  void doSomething() {
                      try {
                          new FileInputStream("file");
                          new MockThrowingClass().throwsSQLException();
                      } catch (IOException e) {
                          System.out.println(e.getMessage());
                      } catch (Exception e) {
                          System.out.println(e.getMessage());
                      }
                  }
              }
              """,
            """
              import java.io.FileInputStream;
              import java.io.IOException;
              import java.sql.SQLException;

              class MyService {
                  void doSomething() {
                      try {
                          new FileInputStream("file");
                          new MockThrowingClass().throwsSQLException();
                      } catch (IOException e) {
                          System.out.println(e.getMessage());
                      } catch (SQLException | RuntimeException e) {
                          System.out.println(e.getMessage());
                      }
                  }
              }
              """
          )
        );
    }

    @Test
    void runtimeExceptionCaughtByEarlierClause() {
        rewriteRun(
          //language=java
          java(
            """
              class MyService {
                  void doSomething() {
                      try {
                          new MockThrowingClass().throwsSQLException();
                          Integer.parseInt("1");
                      } catch (IllegalArgumentException | IllegalStateException e) {
                          System.out.println(e.getMessage());
                      } catch (Exception e) {
                          System.out.println(e.getMessage());
                      }
                  }
              }
              """,
            """
              import java.sql.SQLException;

              class MyService {
                  void doSomething() {
                      try {
                          new MockThrowingClass().throwsSQLException();
                          Integer.parseInt("1");
                      } catch (IllegalArgumentException | IllegalStateException e) {
                          System.out.println(e.getMessage());
                      } catch (SQLException | RuntimeException e) {
                          System.out.println(e.getMessage());
                      }
                  }
              }
              """
          )
        );
    }

    @Test
    void castOfParameterToRuntimeException() {
        rewriteRun(
          //language=java
          java(
            """
              class MyService {
                  void doSomething() {
                      try {
                          new MockThrowingClass().throwsSQLException();
                      } catch (Exception e) {
                          if (e instanceof IllegalStateException) {
                              throw (IllegalStateException) e;
                          }
                      }
                  }
              }
              """,
            """
              import java.sql.SQLException;

              class MyService {
                  void doSomething() {
                      try {
                          new MockThrowingClass().throwsSQLException();
                      } catch (SQLException | RuntimeException e) {
                          if (e instanceof IllegalStateException) {
                              throw (IllegalStateException) e;
                          }
                      }
                  }
              }
              """
          )
        );
    }

    @Test
    void exceptionsThrownInLambdasAndAnonymousClassesAreNotThrownInTry() {
        rewriteRun(
          //language=java
          java(
            """
              import java.util.concurrent.Callable;

              class MyService {
                  void doSomething() {
                      try {
                          Callable<Object> lambda = () -> {
                              new MockThrowingClass().throwsIOException();
                              return null;
                          };
                          Callable<Object> anonymous = new Callable<Object>() {
                              @Override
                              public Object call() throws Exception {
                                  return null;
                              }
                          };
                          Runnable reference = new MockThrowingClass()::throwsNothing;
                          new MockThrowingClass().throwsSQLException();
                      } catch (Exception e) {
                          System.out.println(e.getMessage());
                      }
                  }
              }
              """,
            """
              import java.sql.SQLException;
              import java.util.concurrent.Callable;

              class MyService {
                  void doSomething() {
                      try {
                          Callable<Object> lambda = () -> {
                              new MockThrowingClass().throwsIOException();
                              return null;
                          };
                          Callable<Object> anonymous = new Callable<Object>() {
                              @Override
                              public Object call() throws Exception {
                                  return null;
                              }
                          };
                          Runnable reference = new MockThrowingClass()::throwsNothing;
                          new MockThrowingClass().throwsSQLException();
                      } catch (SQLException | RuntimeException e) {
                          System.out.println(e.getMessage());
                      }
                  }
              }
              """
          )
        );
    }

    @Test
    void exceptionsCaughtByNestedTryAreNotThrownInTry() {
        rewriteRun(
          //language=java
          java(
            """
              import java.io.IOException;

              class MyService {
                  void doSomething() {
                      try {
                          try {
                              new MockThrowingClass().throwsBothIOAndSQL();
                          } catch (IOException e) {
                              throw new java.text.ParseException(e.getMessage(), 0);
                          }
                      } catch (Exception e) {
                          System.out.println(e.getMessage());
                      }
                  }
              }
              """,
            """
              import java.io.IOException;
              import java.sql.SQLException;
              import java.text.ParseException;

              class MyService {
                  void doSomething() {
                      try {
                          try {
                              new MockThrowingClass().throwsBothIOAndSQL();
                          } catch (IOException e) {
                              throw new ParseException(e.getMessage(), 0);
                          }
                      } catch (ParseException | SQLException | RuntimeException e) {
                          System.out.println(e.getMessage());
                      }
                  }
              }
              """
          )
        );
    }

    @Test
    void resourceCloseExceptions() {
        rewriteRun(
          //language=java
          java(
            """
              import java.io.BufferedReader;
              import java.io.StringReader;

              class MyService {
                  void doSomething() {
                      try (BufferedReader reader = new BufferedReader(new StringReader(""))) {
                          System.out.println(reader);
                      } catch (Exception e) {
                          System.out.println(e.getMessage());
                      }
                  }
              }
              """,
            """
              import java.io.BufferedReader;
              import java.io.IOException;
              import java.io.StringReader;

              class MyService {
                  void doSomething() {
                      try (BufferedReader reader = new BufferedReader(new StringReader(""))) {
                          System.out.println(reader);
                      } catch (IOException | RuntimeException e) {
                          System.out.println(e.getMessage());
                      }
                  }
              }
              """
          )
        );
    }

    @Test
    void nestedExceptionTypeIsWrittenWithItsOuterType() {
        rewriteRun(
          //language=java
          java(
            """
              package com.acme;

              public class Errors {
                  public static class Failure extends Exception {
                  }

                  public static void fail() throws Failure {
                  }
              }
              """
          ),
          //language=java
          java(
            """
              class MyService {
                  void doSomething() {
                      try {
                          com.acme.Errors.fail();
                      } catch (Exception e) {
                          System.out.println(e.getMessage());
                      }
                  }
              }
              """,
            """
              import com.acme.Errors;

              class MyService {
                  void doSomething() {
                      try {
                          Errors.fail();
                      } catch (Errors.Failure | RuntimeException e) {
                          System.out.println(e.getMessage());
                      }
                  }
              }
              """
          )
        );
    }

    @Test
    void singleTypeWhenParameterIsOnlyPassedOnAndThrown() {
        rewriteRun(
          //language=java
          java(
            """
              class MyService {
                  void doSomething() {
                      try {
                          new MockThrowingClass().throwsDeclaredRuntimeException();
                      } catch (Exception e) {
                          Exception copy = e;
                          System.out.println("Failed: " + e);
                          throw new IllegalStateException(e.getMessage(), copy);
                      }
                  }
              }
              """,
            """
              class MyService {
                  void doSomething() {
                      try {
                          new MockThrowingClass().throwsDeclaredRuntimeException();
                      } catch (RuntimeException e) {
                          Exception copy = e;
                          System.out.println("Failed: " + e);
                          throw new IllegalStateException(e.getMessage(), copy);
                      }
                  }
              }
              """
          )
        );
    }

    @Test
    void anonymousClassInitializersRunInTry() {
        rewriteRun(
          //language=java
          java(
            """
              class MyService {
                  void doSomething() {
                      try {
                          new Object() {
                              {
                                  new MockThrowingClass().throwsIOException();
                              }
                          };
                      } catch (Exception e) {
                          System.out.println(e.getMessage());
                      }
                  }
              }
              """,
            """
              import java.io.IOException;

              class MyService {
                  void doSomething() {
                      try {
                          new Object() {
                              {
                                  new MockThrowingClass().throwsIOException();
                              }
                          };
                      } catch (IOException | RuntimeException e) {
                          System.out.println(e.getMessage());
                      }
                  }
              }
              """
          )
        );
    }

    @Test
    void singleTypeWhenParameterIsTestedForSubtype() {
        rewriteRun(
          //language=java
          java(
            """
              class MyService {
                  void doSomething() {
                      try {
                          new MockThrowingClass().throwsDeclaredRuntimeException();
                      } catch (Exception e) {
                          if (e instanceof IllegalStateException) {
                              throw (IllegalStateException) e;
                          }
                      }
                  }
              }
              """,
            """
              class MyService {
                  void doSomething() {
                      try {
                          new MockThrowingClass().throwsDeclaredRuntimeException();
                      } catch (RuntimeException e) {
                          if (e instanceof IllegalStateException) {
                              throw (IllegalStateException) e;
                          }
                      }
                  }
              }
              """
          )
        );
    }

    @Test
    void nestedNarrowedCatchRethrowingItsParameter() {
        rewriteRun(
          //language=java
          java(
            """
              class MyService {
                  void doSomething() {
                      try {
                          try {
                              new MockThrowingClass().throwsIOException();
                          } catch (Exception inner) {
                              throw inner;
                          }
                      } catch (Exception e) {
                          System.out.println(e.getMessage());
                      }
                  }
              }
              """,
            """
              import java.io.IOException;

              class MyService {
                  void doSomething() {
                      try {
                          try {
                              new MockThrowingClass().throwsIOException();
                          } catch (IOException | RuntimeException inner) {
                              throw inner;
                          }
                      } catch (IOException | RuntimeException e) {
                          System.out.println(e.getMessage());
                      }
                  }
              }
              """
          )
        );
    }

    @Test
    void resourceGivenAsVariable() {
        rewriteRun(
          //language=java
          java(
            """
              import java.io.BufferedReader;

              class MyService {
                  void doSomething(BufferedReader reader) {
                      try (reader) {
                          System.out.println(reader);
                      } catch (Exception e) {
                          System.out.println(e.getMessage());
                      }
                  }
              }
              """,
            """
              import java.io.BufferedReader;
              import java.io.IOException;

              class MyService {
                  void doSomething(BufferedReader reader) {
                      try (reader) {
                          System.out.println(reader);
                      } catch (IOException | RuntimeException e) {
                          System.out.println(e.getMessage());
                      }
                  }
              }
              """
          )
        );
    }

    @Test
    void ownMethodHidesStaticImports() {
        rewriteRun(
          //language=java
          java(
            """
              import static java.util.Objects.*;

              class MyService {
                  void handle(Exception e) {
                  }

                  void doSomething() {
                      try {
                          new MockThrowingClass().throwsDeclaredRuntimeException();
                      } catch (Exception e) {
                          handle(e);
                      }
                  }
              }
              """,
            """
              import static java.util.Objects.*;

              class MyService {
                  void handle(Exception e) {
                  }

                  void doSomething() {
                      try {
                          new MockThrowingClass().throwsDeclaredRuntimeException();
                      } catch (RuntimeException e) {
                          handle(e);
                      }
                  }
              }
              """
          )
        );
    }

    @Test
    void singleTypeWhenParameterIsTestedForInterface() {
        rewriteRun(
          //language=java
          java(
            """
              class MyService {
                  void doSomething() {
                      try {
                          new MockThrowingClass().throwsDeclaredRuntimeException();
                      } catch (Exception e) {
                          if (e instanceof AutoCloseable) {
                              System.out.println(e);
                          }
                      }
                  }
              }
              """,
            """
              class MyService {
                  void doSomething() {
                      try {
                          new MockThrowingClass().throwsDeclaredRuntimeException();
                      } catch (RuntimeException e) {
                          if (e instanceof AutoCloseable) {
                              System.out.println(e);
                          }
                      }
                  }
              }
              """
          )
        );
    }

    @Test
    void privateNestedExceptionOfSameClass() {
        rewriteRun(
          //language=java
          java(
            """
              class MyService {
                  private static class Failure extends Exception {
                  }

                  private void fail() throws Failure {
                  }

                  void doSomething() {
                      try {
                          fail();
                      } catch (Exception e) {
                          System.out.println(e.getMessage());
                      }
                  }
              }
              """,
            """
              class MyService {
                  private static class Failure extends Exception {
                  }

                  private void fail() throws Failure {
                  }

                  void doSomething() {
                      try {
                          fail();
                      } catch (MyService.Failure | RuntimeException e) {
                          System.out.println(e.getMessage());
                      }
                  }
              }
              """
          )
        );
    }

    @Nested
    class NoChange {
        @Test
        void shouldNotModifyWhenOnlySpecificCatchesExist() {
            rewriteRun(
              java(
                """
                  import java.io.IOException;

                  class MyService {
                      void doSomething() {
                          try {
                              new MockThrowingClass().throwsIOException();
                          } catch (IOException e) {
                              System.out.println("Caught IO: " + e.getMessage());
                          }
                      }
                  }
                  """
              )
            );
        }

        @Test
        void shouldNotModifyWhenNoCheckedExceptionIsThrown() {
            rewriteRun(
              java(
                """
                  class MyService {
                      void doSomething() {
                          try {
                              new MockThrowingClass(); // Nothing thrown
                          } catch (Exception e) {
                              System.out.println("Caught something unusual: " + e.getMessage());
                          }
                      }
                  }
                  """
              )
            );
        }

        @Test
        void undeclaredRuntimeException() {
            rewriteRun(
              java(
                """
                  class MyService {
                      void doSomething() {
                          try {
                              new MockThrowingClass().throwsUndeclaredRuntimeException();
                          } catch (Exception e) {
                              // This is a generic catch block that should be replaced
                              System.out.println("Caught exception: " + e.getMessage());
                          }
                      }
                  }
                  """
              )
            );
        }

        @Issue("https://github.com/openrewrite/rewrite-static-analysis/issues/715")
        @Test
        void doNotChangeWhenThrownExceptionIsGeneric() {
            rewriteRun(
              spec -> spec.parser(JavaParser.fromJavaVersion()),
              //language=java
              java(
                """
                  interface ThrowsGenerics<TE extends Throwable> {
                      void get() throws TE;
                  }

                  class MyService {
                      void doSomething(ThrowsGenerics<Exception> t) {
                          try {
                              t.get();
                          } catch (Exception e) {
                              // Should not be changed - thrown exception is generic
                          }
                      }
                  }
                  """
              )
            );
        }

        @Test
        void doNotChangeWhenExceptionIsDeclared() {
            rewriteRun(
              //language=java
              java(
                """
                  class MyService {
                      Object execute() throws Exception {
                          return null;
                      }

                      void doSomething() {
                          try {
                              new MockThrowingClass().throwsIOException();
                              execute();
                          } catch (Exception e) {
                              System.out.println(e.getMessage());
                          }
                      }
                  }
                  """
              )
            );
        }

        @Test
        void doNotNarrowParameterTypeWhenItIsCast() {
            rewriteRun(
              //language=java
              java(
                """
                  class MyService {
                      void doSomething() {
                          try {
                              new MockThrowingClass().throwsSQLException();
                          } catch (RuntimeException e) {
                              throw e;
                          } catch (Exception e) {
                              if (e instanceof IllegalStateException) {
                                  throw (IllegalStateException) e;
                              }
                          }
                      }
                  }
                  """
              )
            );
        }

        @Test
        void doNotChangeWhenResourceCloseThrowsException() {
            rewriteRun(
              //language=java
              java(
                """
                  class MyService {
                      void doSomething(AutoCloseable closeable) {
                          try (AutoCloseable c = closeable) {
                              new MockThrowingClass().throwsIOException();
                          } catch (Exception e) {
                              System.out.println(e.getMessage());
                          }
                      }
                  }
                  """
              )
            );
        }

        @Test
        void doNotChangeWhenParameterIsReassigned() {
            rewriteRun(
              //language=java
              java(
                """
                  class MyService {
                      void doSomething() {
                          try {
                              new MockThrowingClass().throwsIOException();
                          } catch (Exception e) {
                              e = new IllegalStateException(e);
                              System.out.println(e.getMessage());
                          }
                      }
                  }
                  """
              )
            );
        }

        @Test
        void doNotChangeWhenEarlierClauseCatchesEverythingThrown() {
            rewriteRun(
              //language=java
              java(
                """
                  class MyService {
                      void doSomething() {
                          try {
                              new MockThrowingClass().throwsDeclaredRuntimeException();
                          } catch (RuntimeException e) {
                              throw e;
                          } catch (Exception e) {
                              System.out.println(e.getMessage());
                          }
                      }
                  }
                  """
              )
            );
        }

        @Test
        void doNotNarrowToSingleTypeWhenParameterIsTestedWithInstanceof() {
            rewriteRun(
              //language=java
              java(
                """
                  class MyService {
                      void doSomething() {
                          try {
                              new MockThrowingClass().throwsDeclaredRuntimeException();
                          } catch (Exception e) {
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

        @Test
        void doNotNarrowToSingleTypeWhenAnotherOverloadWouldBeCalled() {
            rewriteRun(
              //language=java
              java(
                """
                  class MyService {
                      void handle(Exception e) {
                      }

                      void handle(RuntimeException e) {
                      }

                      void doSomething() {
                          try {
                              new MockThrowingClass().throwsDeclaredRuntimeException();
                          } catch (Exception e) {
                              handle(e);
                          }
                      }
                  }
                  """
              )
            );
        }

        @Test
        void doNotNarrowToSingleTypeWhenParameterInitializesVar() {
            rewriteRun(
              //language=java
              java(
                """
                  class MyService {
                      void doSomething() {
                          try {
                              new MockThrowingClass().throwsDeclaredRuntimeException();
                          } catch (Exception e) {
                              var x = e;
                              x = new Exception();
                          }
                      }
                  }
                  """
              )
            );
        }

        @Test
        void doNotChangeWhenClassNewInstanceIsCalled() {
            rewriteRun(
              //language=java
              java(
                """
                  class MyService {
                      Object create(Class<?> type) {
                          try {
                              return type.newInstance();
                          } catch (Exception e) {
                              return null;
                          }
                      }
                  }
                  """
              )
            );
        }

        @Test
        void doNotChangeWhenLocalExceptionClassIsThrown() {
            rewriteRun(
              //language=java
              java(
                """
                  class MyService {
                      void doSomething() {
                          class LocalException extends Exception {
                          }
                          try {
                              throw new LocalException();
                          } catch (Exception e) {
                              System.out.println(e.getMessage());
                          }
                      }
                  }
                  """
              )
            );
        }

        @Test
        void doNotChangeWhenNestedFinallyDiscardsExceptions() {
            rewriteRun(
              //language=java
              java(
                """
                  class MyService {
                      void doSomething() {
                          try {
                              try {
                                  new MockThrowingClass().throwsIOException();
                              } finally {
                                  return;
                              }
                          } catch (Exception e) {
                              System.out.println(e.getMessage());
                          }
                      }
                  }
                  """
              )
            );
        }

        @Test
        void doNotNarrowWhenSubclassOverloadWouldBeCalled() {
            rewriteRun(
              //language=java
              java(
                """
                  import java.io.IOException;

                  class Base {
                      void handle(Exception e) {
                      }
                  }

                  class Sub extends Base {
                      void handle(IOException e) {
                      }

                      void doSomething() {
                          try {
                              new MockThrowingClass().throwsIOException();
                          } catch (RuntimeException r) {
                              throw r;
                          } catch (Exception e) {
                              handle(e);
                          }
                      }
                  }
                  """
              )
            );
        }

        @Test
        void doNotNarrowWhenTypeIsInferredFromParameter() {
            rewriteRun(
              //language=java
              java(
                """
                  import java.util.Optional;

                  class MyService {
                      void doSomething() {
                          try {
                              new MockThrowingClass().throwsDeclaredRuntimeException();
                          } catch (Exception e) {
                              Exception x = Optional.of(e).orElse(new Exception("x"));
                          }
                      }
                  }
                  """
              )
            );
        }

        @Test
        void doNotChangeWhenCalleeIsSneakyThrows() {
            rewriteRun(
              spec -> spec.parser(JavaParser.fromJavaVersion().dependsOn(
                //language=java
                """
                  package lombok;
                  public @interface SneakyThrows {
                  }
                  """,
                //language=java
                """
                  class MockThrowingClass {
                      void throwsSQLException() throws java.sql.SQLException {
                      }
                  }
                  """
              )),
              //language=java
              java(
                """
                  class MyService {
                      @lombok.SneakyThrows
                      void sneaky() {
                      }

                      void doSomething() {
                          try {
                              sneaky();
                              new MockThrowingClass().throwsSQLException();
                          } catch (Exception e) {
                              System.out.println(e.getMessage());
                          }
                      }
                  }
                  """
              )
            );
        }

        @Test
        void doNotNarrowWhenStaticallyImportedOverloadWouldBeCalled() {
            rewriteRun(
              //language=java
              java(
                """
                  package com.acme;

                  public class Handlers {
                      public static void handle(Exception e) {
                      }

                      public static void handle(RuntimeException e) {
                      }
                  }
                  """
              ),
              //language=java
              java(
                """
                  import static com.acme.Handlers.*;

                  class MyService {
                      void doSomething() {
                          try {
                              new MockThrowingClass().throwsDeclaredRuntimeException();
                          } catch (Exception e) {
                              handle(e);
                          }
                      }
                  }
                  """
              )
            );
        }

        @Test
        void doNotNarrowWhenVarargsOverloadWouldBeCalled() {
            rewriteRun(
              //language=java
              java(
                """
                  class MyService {
                      void report(Exception... e) {
                      }

                      void report(RuntimeException... e) {
                      }

                      void doSomething() {
                          try {
                              new MockThrowingClass().throwsDeclaredRuntimeException();
                          } catch (Exception e) {
                              report(e);
                          }
                      }
                  }
                  """
              )
            );
        }

        @Test
        void doNotNarrowWhenLaterVarargsOverloadWouldBeCalled() {
            rewriteRun(
              //language=java
              java(
                """
                  class MyService {
                      void report(String message, Exception... e) {
                      }

                      void report(String message, RuntimeException... e) {
                      }

                      void doSomething(RuntimeException other) {
                          try {
                              new MockThrowingClass().throwsDeclaredRuntimeException();
                          } catch (Exception e) {
                              report("failed", other, e);
                          }
                      }
                  }
                  """
              )
            );
        }

        @Test
        void doNotNarrowWhenGenericOverloadWouldBeCalled() {
            rewriteRun(
              //language=java
              java(
                """
                  class MyService {
                      static <T extends RuntimeException> void handle(T t) {
                      }

                      static void handle(Object o) {
                      }

                      void doSomething() {
                          try {
                              new MockThrowingClass().throwsDeclaredRuntimeException();
                          } catch (Exception e) {
                              handle(e);
                          }
                      }
                  }
                  """
              )
            );
        }

        @Test
        void doNotChangeWhenExceptionTypeIsNotVisible() {
            rewriteRun(
              //language=java
              java(
                """
                  package a;

                  class Hidden extends Exception {
                  }
                  """
              ),
              //language=java
              java(
                """
                  package a;

                  public class Api {
                      public void go() throws Hidden {
                      }
                  }
                  """
              ),
              //language=java
              java(
                """
                  package b;

                  class MyService {
                      void doSomething(a.Api api) {
                          try {
                              api.go();
                          } catch (Exception e) {
                              System.out.println(e.getMessage());
                          }
                      }
                  }
                  """
              )
            );
        }

        @Test
        void doNotChangeWhenConstructorIsSneakyThrows() {
            rewriteRun(
              //language=java
              java(
                """
                  package lombok;

                  public @interface SneakyThrows {
                  }
                  """
              ),
              //language=java
              java(
                """
                  class Foo {
                      @lombok.SneakyThrows
                      Foo() {
                      }
                  }
                  """
              ),
              //language=java
              java(
                """
                  class MyService {
                      void doSomething() {
                          try {
                              new Foo();
                              new MockThrowingClass().throwsIOException();
                          } catch (Exception e) {
                              System.out.println(e.getMessage());
                          }
                      }
                  }
                  """
              )
            );
        }

        @Test
        void doNotChangeWhenCallingKotlinClass() {
            rewriteRun(
              //language=java
              java(
                """
                  package kotlin;

                  import java.lang.annotation.Retention;
                  import java.lang.annotation.RetentionPolicy;

                  @Retention(RetentionPolicy.RUNTIME)
                  public @interface Metadata {
                  }
                  """
              ),
              //language=java
              java(
                """
                  @kotlin.Metadata
                  public class KotlinClient {
                      public void fetch() {
                      }
                  }
                  """
              ),
              //language=java
              java(
                """
                  class MyService {
                      void doSomething(KotlinClient client) {
                          try {
                              client.fetch();
                              new MockThrowingClass().throwsIOException();
                          } catch (Exception e) {
                              System.out.println(e.getMessage());
                          }
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
                  import java.io.FileInputStream

                  fun read() {
                      try {
                          FileInputStream("x").close()
                      } catch (e: Exception) {
                          println(e.message)
                      }
                  }
                  """
              )
            );
        }

        @Test
        void doNotChangeWhenCallingScalaClass() {
            rewriteRun(
              //language=java
              java(
                """
                  package scala.reflect;

                  import java.lang.annotation.Retention;
                  import java.lang.annotation.RetentionPolicy;

                  @Retention(RetentionPolicy.RUNTIME)
                  public @interface ScalaSignature {
                      String bytes();
                  }
                  """
              ),
              //language=java
              java(
                """
                  @scala.reflect.ScalaSignature(bytes = "")
                  public class ScalaClient {
                      public void fetch() {
                      }
                  }
                  """
              ),
              //language=java
              java(
                """
                  class MyService {
                      void doSomething(ScalaClient client) {
                          try {
                              client.fetch();
                              new MockThrowingClass().throwsIOException();
                          } catch (Exception e) {
                              System.out.println(e.getMessage());
                          }
                      }
                  }
                  """
              )
            );
        }

        @Test
        void doNotChangeWhenCallingGroovyClass() {
            rewriteRun(
              //language=java
              java(
                """
                  package groovy.lang;

                  public interface GroovyObject {
                  }
                  """
              ),
              //language=java
              java(
                """
                  public class GroovyClient implements groovy.lang.GroovyObject {
                      public void fetch() {
                      }
                  }
                  """
              ),
              //language=java
              java(
                """
                  class MyService {
                      void doSomething(GroovyClient client) {
                          try {
                              client.fetch();
                              new MockThrowingClass().throwsIOException();
                          } catch (Exception e) {
                              System.out.println(e.getMessage());
                          }
                      }
                  }
                  """
              )
            );
        }

        @Test
        void doNotNarrowToSingleTypeWhenGetClassIsCalled() {
            rewriteRun(
              //language=java
              java(
                """
                  class MyService {
                      @SuppressWarnings("unchecked")
                      void doSomething() {
                          try {
                              new MockThrowingClass().throwsDeclaredRuntimeException();
                          } catch (Exception e) {
                              Class<Exception> type = (Class<Exception>) e.getClass();
                          }
                      }
                  }
                  """
              )
            );
        }

        @Test
        void doNotNarrowWhenAnonymousClassSuperConstructorOverloadWouldBeCalled() {
            rewriteRun(
              //language=java
              java(
                """
                  class Handler {
                      Handler(Exception e) {
                      }

                      Handler(RuntimeException e) {
                      }
                  }
                  """
              ),
              //language=java
              java(
                """
                  class MyService {
                      void doSomething() {
                          try {
                              new MockThrowingClass().throwsDeclaredRuntimeException();
                          } catch (Exception e) {
                              new Handler(e) {
                              };
                          }
                      }
                  }
                  """
              )
            );
        }

        @Test
        void doNotChangeWhenPrivateExceptionOfAnotherClassIsThrown() {
            rewriteRun(
              //language=java
              java(
                """
                  class MyService {
                      void doSomething() {
                          try {
                              Other.fail();
                          } catch (Exception e) {
                              System.out.println(e.getMessage());
                          }
                      }
                  }

                  class Other {
                      private static class Failure extends Exception {
                      }

                      static void fail() throws Failure {
                      }
                  }
                  """
              )
            );
        }

        @Test
        void doNotNarrowWhenParameterIsReturnedFromLambda() {
            rewriteRun(
              //language=java
              java(
                """
                  import java.util.Optional;

                  class MyService {
                      Exception doSomething() {
                          try {
                              new MockThrowingClass().throwsIOException();
                              return null;
                          } catch (RuntimeException r) {
                              throw r;
                          } catch (Exception e) {
                              return Optional.of(1).map(x -> {
                                  return e;
                              }).orElse(new Exception());
                          }
                      }
                  }
                  """
              )
            );
        }

        @Test
        void doNotChangeWhenLombokCleanupIsUsed() {
            rewriteRun(
              //language=java
              java(
                """
                  package lombok;

                  public @interface Cleanup {
                  }
                  """
              ),
              //language=java
              java(
                """
                  import java.io.StringWriter;

                  class MyService {
                      int doSomething(String s) {
                          try {
                              @lombok.Cleanup StringWriter w = new StringWriter();
                              w.write(s);
                              return Integer.parseInt(w.toString());
                          } catch (Exception e) {
                              return -1;
                          }
                      }
                  }
                  """
              )
            );
        }
    }
}
