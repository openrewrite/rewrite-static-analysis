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

import org.junit.jupiter.api.Test;
import org.openrewrite.DocumentExample;
import org.openrewrite.java.JavaParser;
import org.openrewrite.test.RecipeSpec;
import org.openrewrite.test.RewriteTest;

import static org.openrewrite.java.Assertions.java;

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
                      } catch (IOException e) {
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
                      } catch (IOException | SQLException e) {
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
                      } catch (IOException | SQLException e) {
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
                      } catch (SQLException e) {
                          System.out.println("Caught generic: " + e.getMessage());
                      }
                  }
              }
              """
          )
        );
    }

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
                      } catch (SQLException e) {
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
                      } catch (IOException | SQLException e) {
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
                      } catch (IOException e) {
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
                      } catch (SQLException e) {
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
                      } catch (java.lang.RuntimeException e) {
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
}
