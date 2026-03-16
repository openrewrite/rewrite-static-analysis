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

import static org.openrewrite.java.Assertions.java;
import static org.openrewrite.java.Assertions.javaVersion;

@SuppressWarnings({"ConstantConditions", "TryFinallyCanBeTryWithResources", "unused"})
class TryWithResourcesTest implements RewriteTest {

    @Override
    public void defaults(RecipeSpec spec) {
        spec.recipe(new TryWithResources())
            .allSources(s -> s.markers(javaVersion(9)));
    }

    @DocumentExample
    @Test
    void simpleResourceWithDirectClose() {
        rewriteRun(
          //language=java
          java(
            """
              import java.io.*;

              class Test {
                  void method() throws IOException {
                      InputStream in = new FileInputStream("file.txt");
                      try {
                          int data = in.read();
                      } finally {
                          in.close();
                      }
                  }
              }
              """,
            """
              import java.io.*;

              class Test {
                  void method() throws IOException {
                      try (InputStream in = new FileInputStream("file.txt")) {
                          int data = in.read();
                      }
                  }
              }
              """
          )
        );
    }

    @Test
    void nullGuardedClose() {
        rewriteRun(
          //language=java
          java(
            """
              import java.io.*;

              class Test {
                  void method() throws IOException {
                      InputStream in = new FileInputStream("file.txt");
                      try {
                          int data = in.read();
                      } finally {
                          if (in != null) {
                              in.close();
                          }
                      }
                  }
              }
              """,
            """
              import java.io.*;

              class Test {
                  void method() throws IOException {
                      try (InputStream in = new FileInputStream("file.txt")) {
                          int data = in.read();
                      }
                  }
              }
              """
          )
        );
    }

    @Test
    void tryCatchWrappedClose() {
        rewriteRun(
          //language=java
          java(
            """
              import java.io.*;

              class Test {
                  void method() throws IOException {
                      InputStream in = new FileInputStream("file.txt");
                      try {
                          int data = in.read();
                      } finally {
                          try {
                              in.close();
                          } catch (IOException ignored) {
                          }
                      }
                  }
              }
              """,
            """
              import java.io.*;

              class Test {
                  void method() throws IOException {
                      try (InputStream in = new FileInputStream("file.txt")) {
                          int data = in.read();
                      }
                  }
              }
              """
          )
        );
    }

    @Test
    void nullGuardedWithTryCatchClose() {
        rewriteRun(
          //language=java
          java(
            """
              import java.io.*;

              class Test {
                  void method() throws IOException {
                      InputStream in = new FileInputStream("file.txt");
                      try {
                          int data = in.read();
                      } finally {
                          if (in != null) {
                              try {
                                  in.close();
                              } catch (IOException ignored) {
                              }
                          }
                      }
                  }
              }
              """,
            """
              import java.io.*;

              class Test {
                  void method() throws IOException {
                      try (InputStream in = new FileInputStream("file.txt")) {
                          int data = in.read();
                      }
                  }
              }
              """
          )
        );
    }

    @Test
    void preservesCatchBlocks() {
        rewriteRun(
          //language=java
          java(
            """
              import java.io.*;

              class Test {
                  void method() {
                      InputStream in = new FileInputStream("file.txt");
                      try {
                          int data = in.read();
                      } catch (IOException e) {
                          e.printStackTrace();
                      } finally {
                          in.close();
                      }
                  }
              }
              """,
            """
              import java.io.*;

              class Test {
                  void method() {
                      try (InputStream in = new FileInputStream("file.txt")) {
                          int data = in.read();
                      } catch (IOException e) {
                          e.printStackTrace();
                      }
                  }
              }
              """
          )
        );
    }

    @Test
    void doNotChangeResourceUsedAfterTry() {
        rewriteRun(
          //language=java
          java(
            """
              import java.io.*;

              class Test {
                  void method() throws IOException {
                      InputStream in = new FileInputStream("file.txt");
                      try {
                          int data = in.read();
                      } finally {
                          in.close();
                      }
                      System.out.println(in);
                  }
              }
              """
          )
        );
    }

    @Test
    void doNotChangeResourceReassignedInTry() {
        rewriteRun(
          //language=java
          java(
            """
              import java.io.*;

              class Test {
                  void method() throws IOException {
                      InputStream in = new FileInputStream("file1.txt");
                      try {
                          in = new FileInputStream("file2.txt");
                          int data = in.read();
                      } finally {
                          in.close();
                      }
                  }
              }
              """
          )
        );
    }

    @Test
    void doNotChangeNonAutoCloseable() {
        rewriteRun(
          //language=java
          java(
            """
              class Test {
                  static class CustomResource {
                      public void close() {}
                      public void doSomething() {}
                  }

                  void method() {
                      CustomResource resource = new CustomResource();
                      try {
                          resource.doSomething();
                      } finally {
                          resource.close();
                      }
                  }
              }
              """
          )
        );
    }

    @Test
    void doNotChangeAlreadyTryWithResources() {
        rewriteRun(
          //language=java
          java(
            """
              import java.io.*;

              class Test {
                  void method() throws IOException {
                      try (InputStream in = new FileInputStream("file.txt")) {
                          int data = in.read();
                      }
                  }
              }
              """
          )
        );
    }

    @Test
    void doNotChangeCloseViaHelperMethod() {
        rewriteRun(
          //language=java
          java(
            """
              import java.io.*;

              class Test {
                  static void closeQuietly(InputStream stream) {
                      try {
                          if (stream != null) {
                              stream.close();
                          }
                      } catch (IOException ignored) {}
                  }

                  void method() throws IOException {
                      InputStream in = new FileInputStream("file.txt");
                      try {
                          int data = in.read();
                      } finally {
                          closeQuietly(in);
                      }
                  }
              }
              """
          )
        );
    }

    @Test
    void doNotChangeResourceClosedInCatch() {
        rewriteRun(
          //language=java
          java(
            """
              import java.io.*;

              class Test {
                  void method() throws IOException {
                      InputStream in = new FileInputStream("file.txt");
                      try {
                          int data = in.read();
                      } catch (IOException e) {
                          in.close();
                          throw e;
                      } finally {
                          in.close();
                      }
                  }
              }
              """
          )
        );
    }

    @Test
    void doNotChangeFinallyWithExtraLogic() {
        rewriteRun(
          //language=java
          java(
            """
              import java.io.*;

              class Test {
                  void method() throws IOException {
                      InputStream in = new FileInputStream("file.txt");
                      try {
                          int data = in.read();
                      } finally {
                          System.out.println("closing");
                          in.close();
                      }
                  }
              }
              """
          )
        );
    }
}
