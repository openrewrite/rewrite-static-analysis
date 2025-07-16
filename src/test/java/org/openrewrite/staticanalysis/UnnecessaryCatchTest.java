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

@SuppressWarnings("RedundantThrows")
class UnnecessaryCatchTest implements RewriteTest {

    @Override
    public void defaults(RecipeSpec spec) {
        spec.recipe(new UnnecessaryCatch(false, false));
    }

    @DocumentExample
    @Test
    void unwrapTry() {
        rewriteRun(
          java(
            """
              import java.io.IOException;

              class AnExample {
                  void method() {
                      try {
                          java.util.Base64.getDecoder().decode("abc".getBytes());
                      } catch (IOException e) {
                          System.out.println("an exception!");
                      }
                  }
              }
              """,
            """
              class AnExample {
                  void method() {
                      java.util.Base64.getDecoder().decode("abc".getBytes());
                  }
              }
              """
          )
        );
    }

    @Test
    void removeCatch() {
        rewriteRun(
          //language=java
          java(
            """
              import java.io.IOException;

              class AnExample {
                  void method() {
                      try {
                          java.util.Base64.getDecoder().decode("abc".getBytes());
                      } catch (IOException e1) {
                          System.out.println("an exception!");
                      } catch (IllegalStateException e2) {
                          System.out.println("another exception!");
                      }
                  }
              }
              """,
            """
              class AnExample {
                  void method() {
                      try {
                          java.util.Base64.getDecoder().decode("abc".getBytes());
                      } catch (IllegalStateException e2) {
                          System.out.println("another exception!");
                      }
                  }
              }
              """
          )
        );
    }

    @Test
    void removeFirstFromMultiCatch() {
        rewriteRun(
          //language=java
          java(
            """
              import java.io.IOException;

              class AnExample {
                  void method() {
                      try {
                          java.util.Base64.getDecoder().decode("abc".getBytes());
                      } catch (IOException | IllegalArgumentException | IllegalStateException e) {
                          System.out.println("an exception!");
                      }
                  }
              }
              """,
            """
              class AnExample {
                  void method() {
                      try {
                          java.util.Base64.getDecoder().decode("abc".getBytes());
                      } catch (IllegalArgumentException | IllegalStateException e) {
                          System.out.println("an exception!");
                      }
                  }
              }
              """
          )
        );
    }

    @Test
    void removeMiddleFromMultiCatch() {
        rewriteRun(
          //language=java
          java(
            """
              import java.io.IOException;

              class AnExample {
                  void method() {
                      try {
                          java.util.Base64.getDecoder().decode("abc".getBytes());
                      } catch (IllegalArgumentException | IllegalStateException | IOException e) {
                          System.out.println("an exception!");
                      }
                  }
              }
              """,
            """
              class AnExample {
                  void method() {
                      try {
                          java.util.Base64.getDecoder().decode("abc".getBytes());
                      } catch (IllegalArgumentException | IllegalStateException e) {
                          System.out.println("an exception!");
                      }
                  }
              }
              """
          )
        );
    }

    @Test
    void removeMultiCatch() {
        rewriteRun(
          //language=java
          java(
            """
              import java.io.IOException;
              import java.sql.SQLException;

              class AnExample {
                  void method() {
                      try {
                          java.util.Base64.getDecoder().decode("abc".getBytes());
                      } catch (IOException | SQLException e) {
                          System.out.println("an exception!");
                      }
                  }
              }
              """,
            """
              class AnExample {
                  void method() {
                      java.util.Base64.getDecoder().decode("abc".getBytes());
                  }
              }
              """
          )
        );
    }

    @Test
    void doNotRemoveRuntimeException() {
        rewriteRun(
          //language=java
          java(
            """
              class AnExample {
                  void method() {
                      try {
                          java.util.Base64.getDecoder().decode("abc".getBytes());
                      } catch (IllegalStateException e) {
                          System.out.println("an exception!");
                      }
                  }
              }
              """
          )
        );
    }

    @Test
    void doNotRemoveThrownException() {
        rewriteRun(
          //language=java
          java(
            """
              import java.io.IOException;

              class AnExample {
                  void method() {
                      try {
                          fred();
                      } catch (IOException e) {
                          System.out.println("an exception!");
                      }
                  }

                  void fred() throws IOException {
                  }
              }
              """
          )
        );
    }

    @Test
    void doNotRemoveThrownExceptionFromConstructor() {
        rewriteRun(
          //language=java
          java(
            """
              import java.io.IOException;

              class AnExample {
                  void method() {
                      try {
                          new Fred();
                      } catch (IOException e) {
                          System.out.println("an exception!");
                      }
                  }

                  static class Fred {
                      Fred() throws IOException {}
                  }

              }
              """
          )
        );
    }

    @Test
    void doNotRemoveJavaLangException() {
        rewriteRun(
          //language=java
          java(
            """
              class Scratch {
                  void method() {
                      try {
                          throw new RuntimeException();
                      } catch (Exception e) {
                          System.out.println("an exception!");
                      }
                  }
              }
              """
          )
        );
    }

    @Test
    void removeJavaLangException() {
        rewriteRun(
          spec -> spec.recipe(new UnnecessaryCatch(true, false)),
          //language=java
          java(
            """
              class Scratch {
                  void method() {
                      try {
                          throw new RuntimeException();
                      } catch (Exception e) {
                          System.out.println("an exception!");
                      }
                  }
              }
              """,
            """
              class Scratch {
                  void method() {
                      throw new RuntimeException();
                  }
              }
              """
          )
        );
    }

    @Test
    void doNotRemoveJavaLangThrowable() {
        rewriteRun(
          //language=java
          java(
            """
              class Scratch {
                  void method() {
                      try {
                          throw new RuntimeException();
                      } catch (Throwable e) {
                          System.out.println("an exception!");
                      }
                  }
              }
              """
          )
        );
    }

    @Test
    void removeJavaLangThrowable() {
        rewriteRun(
          spec -> spec.recipe(new UnnecessaryCatch(false, true)),
          //language=java
          java(
            """
              class Scratch {
                  void method() {
                      try {
                          throw new RuntimeException();
                      } catch (Throwable e) {
                          System.out.println("an exception!");
                      }
                  }
              }
              """,
            """
              class Scratch {
                  void method() {
                      throw new RuntimeException();
                  }
              }
              """
          )
        );
    }

    @Test
    void doNotRemoveCatchOfThrownException() {
        rewriteRun(
          //language=java
          java(
            """
              class Scratch {
                  void method() {
                      try {
                          throw new ClassNotFoundException();
                      } catch (ClassNotFoundException e) {
                          // Caught
                      }
                  }
              }
              """
          )
        );
    }

    @Test
    void doNotRemoveCatchForCloseOnTryWithResources() {
        rewriteRun(
          //language=java
          java(
            """
              import java.io.IOException;
              import java.io.StringWriter;

              class Scratch {
                  void method() {
                      try (StringWriter sw = new StringWriter()) {
                      } catch (IOException e) {
                          // Caught on .close()
                      }
                  }
              }
              """
          )
        );
    }

    @Test
    void doNotRemoveCatchForConstructorThatThrows() {
        rewriteRun(
          //language=java
          java(
            """
              import java.util.jar.JarFile;
              import java.util.zip.ZipException;

              class Scratch {
                  JarFile method(String name) {
                      try {
                          return new JarFile(name); // throws IOException
                      } catch (ZipException e) { // extends IOException
                          // Catches subset
                      }
                  }
              }
              """
          )
        );
    }
}
