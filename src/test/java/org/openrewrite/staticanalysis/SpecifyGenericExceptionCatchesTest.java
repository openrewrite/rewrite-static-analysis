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
import org.openrewrite.test.RecipeSpec;
import org.openrewrite.test.RewriteTest;
import org.openrewrite.test.SourceSpecs;

import static org.openrewrite.java.Assertions.java;

class SpecifyGenericExceptionCatchesTest implements RewriteTest {

    @Override
    public void defaults(RecipeSpec spec) {
        spec.recipe(new SpecifyGenericExceptionCatches());
    }

    // Helper SourceSpecs for mock classes that throw checked exceptions
    private static final SourceSpecs MOCK_THROWING_CLASS = java("""
      package com.example;

      public class MockThrowingClass {

          public MockThrowingClass() {
          }

           public MockThrowingClass(boolean throwSQLException) throws java.sql.SQLException {
              if (throwSQLException) {
                  throw new java.sql.SQLException("Test Constructor SQL Exception");
              }
           }

          public void throwsIOException() throws java.io.IOException {
              throw new java.io.IOException("Test IO Exception");
          }

          public void throwsSQLException() throws java.sql.SQLException {
              throw new java.sql.SQLException("Test SQL Exception");
          }

          public void throwsBothIOAndSQL() throws java.io.IOException, java.sql.SQLException {
              throw new java.io.IOException("Test IO or SQL Exception");
          }

          public void throwsNothing() {
              // This method throws no checked exceptions
          }

          public void throwsRuntimeException() throws RuntimeException {
              throw new RuntimeException("Test Runtime Exception");
          }
      }
      """);

    @DocumentExample
    @Test
    void shouldReplaceExceptionWithSingleCheckedException() {
        rewriteRun(MOCK_THROWING_CLASS, java("""
          package com.example;

          public class MyService {
              public void doSomething() {
                  try {
                      new MockThrowingClass().throwsIOException();
                  } catch (Exception e) {
                      System.out.println("Caught exception: " + e.getMessage());
                  }
              }
          }
          """, """
          package com.example;

          import java.io.IOException;

          public class MyService {
              public void doSomething() {
                  try {
                      new MockThrowingClass().throwsIOException();
                  } catch (IOException e) {
                      System.out.println("Caught exception: " + e.getMessage());
                  }
              }
          }
          """));
    }

    @Test
    void shouldReplaceExceptionWithMultipleCheckedExceptions() {
        rewriteRun(MOCK_THROWING_CLASS, java("""
          package com.example;

          public class MyService {
              public void doSomething() {
                  try {
                      new MockThrowingClass().throwsIOException();
                      new MockThrowingClass().throwsSQLException();
                  } catch (Exception e) {
                      System.out.println("Caught exception: " + e.getMessage());
                  }
              }
          }
          """, """
          package com.example;

          import java.io.IOException;
          import java.sql.SQLException;

          public class MyService {
              public void doSomething() {
                  try {
                      new MockThrowingClass().throwsIOException();
                      new MockThrowingClass().throwsSQLException();
                  } catch (IOException | SQLException e) {
                      System.out.println("Caught exception: " + e.getMessage());
                  }
              }
          }
          """));
    }

    @Test
    void shouldReplaceExceptionWithMultipleCheckedExceptionsFromSingleCall() {
        rewriteRun(MOCK_THROWING_CLASS, java("""
          package com.example;

          public class MyService {
              public void doSomething() {
                  try {
                      new MockThrowingClass().throwsBothIOAndSQL();
                  } catch (Exception e) {
                      System.out.println("Caught exception: " + e.getMessage());
                  }
              }
          }
          """, """
          package com.example;

          import java.io.IOException;
          import java.sql.SQLException;

          public class MyService {
              public void doSomething() {
                  try {
                      new MockThrowingClass().throwsBothIOAndSQL();
                  } catch (IOException | SQLException e) {
                      System.out.println("Caught exception: " + e.getMessage());
                  }
              }
          }
          """));
    }

    @Test
    void shouldKeepSpecificCatchAndRemoveGenericWhenCovered() {
        rewriteRun(MOCK_THROWING_CLASS, java("""
          package com.example;

          import java.io.IOException;
          import java.sql.SQLException;

          public class MyService {
              public void doSomething() {
                  try {
                      new MockThrowingClass().throwsBothIOAndSQL();
                  } catch (IOException e) {
                      System.out.println("Caught IO: " + e.getMessage());
                  } catch (Exception e) {
                      System.out.println("Caught generic: " + e.getMessage());
                  }
              }
          }
          """, """
          package com.example;

          import java.io.IOException;
          import java.sql.SQLException;

          public class MyService {
              public void doSomething() {
                  try {
                      new MockThrowingClass().throwsBothIOAndSQL();
                  } catch (IOException e) {
                      System.out.println("Caught IO: " + e.getMessage());
                  } catch (SQLException e) {
                      System.out.println("Caught generic: " + e.getMessage());
                  }
              }
          }
          """));
    }

    @Test
    void shouldNotModifyWhenOnlySpecificCatchesExist() {
        rewriteRun(MOCK_THROWING_CLASS, java("""
          package com.example;

          import java.io.IOException;

          public class MyService {
              public void doSomething() {
                  try {
                      new MockThrowingClass().throwsIOException();
                  } catch (IOException e) {
                      System.out.println("Caught IO: " + e.getMessage());
                  }
              }
          }
          """));
    }

    @Test
    void shouldNotModifyWhenNoCheckedExceptionIsThrown() {
        rewriteRun(MOCK_THROWING_CLASS, java("""
          package com.example;

          public class MyService {
              public void doSomething() {
                  try {
                      new MockThrowingClass(); // Nothing thrown
                  } catch (Exception e) {
                      System.out.println("Caught something unusual: " + e.getMessage());
                  }
              }
          }
          """));
    }

    @Test
    void shouldHandleConstructorThrowingException() {
        rewriteRun(MOCK_THROWING_CLASS, java("""
          package com.example;

          public class MyService {
              public void createObject() {
                  try {
                      new MockThrowingClass(true); // Throws SQLException
                  } catch (Exception e) {
                      System.out.println("Caught constructor exception: " + e.getMessage());
                  }
              }
          }
          """, """
          package com.example;

          import java.sql.SQLException;

          public class MyService {
              public void createObject() {
                  try {
                      new MockThrowingClass(true); // Throws SQLException
                  } catch (SQLException e) {
                      System.out.println("Caught constructor exception: " + e.getMessage());
                  }
              }
          }
          """));
    }

    @Test
    void shouldHandleConstructorThrowingSpecificExceptionAndAnotherGenericCatch() {
        rewriteRun(MOCK_THROWING_CLASS, java("""
          package com.example;

          import java.io.IOException;

          public class MyService {
              public void createAndHandle() {
                  try {
                      new MockThrowingClass(true); // Throws SQLException
                      new MockThrowingClass().throwsIOException();
                  } catch (Exception e) {
                      System.out.println("Caught constructor exception: " + e.getMessage());
                  }
              }
          }
          """, """
          package com.example;

          import java.io.IOException;
          import java.sql.SQLException;

          public class MyService {
              public void createAndHandle() {
                  try {
                      new MockThrowingClass(true); // Throws SQLException
                      new MockThrowingClass().throwsIOException();
                  } catch (IOException | SQLException e) {
                      System.out.println("Caught constructor exception: " + e.getMessage());
                  }
              }
          }
          """));
    }

    @Test
    void shouldHandleConstructorThrowingExceptionWithExistingSpecificCatch() {
        rewriteRun(MOCK_THROWING_CLASS, java("""
          package com.example;

          import java.io.IOException;
          import java.sql.SQLException;

          public class MyService {
              public void createAndHandle() {
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
          """, """
          package com.example;

          import java.io.IOException;
          import java.sql.SQLException;

          public class MyService {
              public void createAndHandle() {
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
          """));
    }
}
