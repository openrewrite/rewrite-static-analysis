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
import org.openrewrite.test.RecipeSpec;
import org.openrewrite.test.RewriteTest;

import static org.openrewrite.java.Assertions.java;
import static org.openrewrite.java.Assertions.javaVersion;

@SuppressWarnings({"ConstantConditions", "TryFinallyCanBeTryWithResources", "unused"})
class UseTryWithResourcesTest implements RewriteTest {

    @Override
    public void defaults(RecipeSpec spec) {
        spec.recipe(new UseTryWithResources())
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
    void nullGuardedCloseWithoutBraces() {
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
                          if (in != null)
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
    void resourceUsedAfterTryUsesJava9Syntax() {
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
              """,
            """
              import java.io.*;

              class Test {
                  void method() throws IOException {
                      InputStream in = new FileInputStream("file.txt");
                      try (in) {
                          int data = in.read();
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
    void finallyWithExtraLogicKeepsRemainingStatements() {
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
              """,
            """
              import java.io.*;

              class Test {
                  void method() throws IOException {
                      try (InputStream in = new FileInputStream("file.txt")) {
                          int data = in.read();
                      } finally {
                          System.out.println("closing");
                      }
                  }
              }
              """
          )
        );
    }

    @Test
    void doNotChangeQualifiedCloseMethodCall() {
        rewriteRun(
          //language=java
          java(
            """
              import java.io.*;

              class Test {
                  static class Wrapper {
                      InputStream stream;
                      Wrapper(InputStream s) { this.stream = s; }
                      InputStream getStream() { return stream; }
                  }

                  void method() throws IOException {
                      Wrapper wrapper = new Wrapper(new FileInputStream("file.txt"));
                      try {
                          int data = wrapper.getStream().read();
                      } finally {
                          wrapper.getStream().close();
                      }
                  }
              }
              """
          )
        );
    }

    @Test
    void doNotChangeResourceAssignedToField() {
        rewriteRun(
          //language=java
          java(
            """
              import java.io.*;

              class Test {
                  private InputStream fieldStream;

                  void method() throws IOException {
                      fieldStream = new FileInputStream("file.txt");
                      try {
                          int data = fieldStream.read();
                      } finally {
                          fieldStream.close();
                      }
                  }
              }
              """
          )
        );
    }

    @Test
    void addResourceToExistingTryWithResources() {
        rewriteRun(
          //language=java
          java(
            """
              import java.io.*;

              class Test {
                  void method() throws IOException {
                      OutputStream out = new FileOutputStream("out.txt");
                      try (InputStream in = new FileInputStream("in.txt")) {
                          out.write(in.read());
                      } finally {
                          out.close();
                      }
                  }
              }
              """,
            """
              import java.io.*;

              class Test {
                  void method() throws IOException {
                      try (InputStream in = new FileInputStream("in.txt"); OutputStream out = new FileOutputStream("out.txt")) {
                          out.write(in.read());
                      }
                  }
              }
              """
          )
        );
    }

    @Test
    void doNotChangeNullInitializedResourceClosedInCatch() {
        rewriteRun(
          //language=java
          java(
            """
              import java.io.*;

              class Test {
                  void method() {
                      InputStream in = null;
                      try {
                          in = new FileInputStream("file.txt");
                          int data = in.read();
                      } catch (IOException e) {
                          if (in != null) {
                              try {
                                  in.close();
                              } catch (IOException ignored) {
                              }
                          }
                          throw new RuntimeException(e);
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
              """
          )
        );
    }

    @Test
    void nonConsecutiveWithInterveningStatement() {
        rewriteRun(
          //language=java
          java(
            """
              import java.io.*;

              class Test {
                  void method(String path) throws IOException {
                      InputStream in = new FileInputStream(path);
                      int x = 1;
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
                  void method(String path) throws IOException {
                      InputStream in = new FileInputStream(path);
                      int x = 1;
                      try (in) {
                          int data = in.read();
                      }
                  }
              }
              """
          )
        );
    }

    @Test
    void nonConsecutiveWithNullGuardedClose() {
        rewriteRun(
          //language=java
          java(
            """
              import java.io.*;

              class Test {
                  void method(String path) throws IOException {
                      InputStream in = new FileInputStream(path);
                      int x = 1;
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
                  void method(String path) throws IOException {
                      InputStream in = new FileInputStream(path);
                      int x = 1;
                      try (in) {
                          int data = in.read();
                      }
                  }
              }
              """
          )
        );
    }

    @Test
    void nonConsecutiveResourceUsedBetween() {
        rewriteRun(
          //language=java
          java(
            """
              import java.io.*;

              class Test {
                  void method(String path) throws IOException {
                      InputStream in = new FileInputStream(path);
                      System.out.println(in.available());
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
                  void method(String path) throws IOException {
                      InputStream in = new FileInputStream(path);
                      System.out.println(in.available());
                      try (in) {
                          int data = in.read();
                      }
                  }
              }
              """
          )
        );
    }

    @Test
    void nonConsecutivePreservesCatchBlocks() {
        rewriteRun(
          //language=java
          java(
            """
              import java.io.*;

              class Test {
                  void method(String path) {
                      InputStream in = new FileInputStream(path);
                      int x = 1;
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
                  void method(String path) {
                      InputStream in = new FileInputStream(path);
                      int x = 1;
                      try (in) {
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
    void doNotChangeNonConsecutiveWhenReassignedBetween() {
        rewriteRun(
          //language=java
          java(
            """
              import java.io.*;

              class Test {
                  void method(String path) throws IOException {
                      InputStream in = new FileInputStream(path);
                      in = new FileInputStream("other.txt");
                      try {
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
    void multipleClosesInFinallyPicksMatchingVariable() {
        rewriteRun(
          spec -> spec.expectedCyclesThatMakeChanges(2),
          //language=java
          java(
            """
              import java.io.*;

              class Test {
                  void method(String path) throws IOException {
                      OutputStream out = new FileOutputStream("out.txt");
                      int x = 1;
                      InputStream in = new FileInputStream(path);
                      try {
                          out.write(in.read());
                      } finally {
                          out.close();
                          in.close();
                      }
                  }
              }
              """,
            """
              import java.io.*;

              class Test {
                  void method(String path) throws IOException {
                      OutputStream out = new FileOutputStream("out.txt");
                      int x = 1;
                      try (InputStream in = new FileInputStream(path); out) {
                          out.write(in.read());
                      }
                  }
              }
              """
          )
        );
    }

    @Test
    void doNotChangeNonConsecutiveNullInitialized() {
        rewriteRun(
          //language=java
          java(
            """
              import java.io.*;

              class Test {
                  void method(String path) throws IOException {
                      InputStream in = null;
                      in = new FileInputStream(path);
                      try {
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
}
