/*
 * Copyright 2025 the original author or authors.
 * <p>
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * <p>
 * https://www.apache.org/licenses/LICENSE-2.0
 * <p>
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.openrewrite.staticanalysis;

import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.openrewrite.DocumentExample;
import org.openrewrite.test.RecipeSpec;
import org.openrewrite.test.RewriteTest;

import static org.openrewrite.java.Assertions.java;

@SuppressWarnings("TryFinallyCanBeTryWithResources")
class TryWithResourcesTest implements RewriteTest {
    @Override
    public void defaults(RecipeSpec spec) {
        spec.recipe(new TryWithResources());
    }

    @DocumentExample
    @Test
    void basicTransformation() {
        rewriteRun(
          java(
            """
              import java.io.*;

              class Test {
                  void method() throws IOException {
                      InputStream in = new FileInputStream("file.txt");
                      try {
                          int data = in.read();
                          // Process data
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
                          // Process data
                      }
                  }
              }
              """
          )
        );
    }

    @Test
    void multipleResources() {
        rewriteRun(
          java(
            """
              import java.io.*;

              class Test {
                  void method() throws IOException {
                      InputStream in = new FileInputStream("input.txt");
                      OutputStream out = new FileOutputStream("output.txt");
                      try {
                          int data = in.read();
                          out.write(data);
                      } finally {
                          in.close();
                          out.close();
                      }
                  }
              }
              """,
            """
              import java.io.*;

              class Test {
                  void method() throws IOException {
                      try (InputStream in = new FileInputStream("input.txt");
                           OutputStream out = new FileOutputStream("output.txt")) {
                          int data = in.read();
                          out.write(data);
                      }
                  }
              }
              """
          )
        );
    }

    @Test
    void nullCheckInFinally() {
        rewriteRun(
          java(
            """
              import java.io.*;

              class Test {
                  void method() throws IOException {
                      InputStream in = new FileInputStream("file.txt");
                      try {
                          int data = in.read();
                          // Process data
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
                          // Process data
                      }
                  }
              }
              """
          )
        );
    }

    @Test
    void complexFinallyBlock() {
        rewriteRun(
          java(
            """
              import java.io.*;

              class Test {
                  void method() throws IOException {
                      InputStream in = new FileInputStream("file.txt");
                      try {
                          int data = in.read();
                          // Process data
                      } finally {
                          in.close();
                          System.out.println("Processing complete");
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
                          // Process data
                      } finally {
                          System.out.println("Processing complete");
                      }
                  }
              }
              """
          )
        );
    }

    @Test
    void renameIgnoredIfUnused() {
        rewriteRun(
          java(
            """
            import java.io.*;

            class Test {
                public void testConnection() throws IOException {
                    InputStream in = null;
                    try {
                        in = new FileInputStream("file.txt");
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
                public void testConnection() throws IOException {
                    try (InputStream in = new FileInputStream("file.txt")) {
                    }
                }
            }
            """
          )
        );
    }

    @Test
    void nestedTryBlocks() {
        rewriteRun(
          java(
            """
              import java.io.*;

              class Test {
                  void method() throws IOException {
                      InputStream in = new FileInputStream("file.txt");
                      try {
                          OutputStream out = new FileOutputStream("output.txt");
                          try {
                              int data = in.read();
                              out.write(data);
                          } finally {
                              out.close();
                          }
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
                          try (OutputStream out = new FileOutputStream("output.txt")) {
                              int data = in.read();
                              out.write(data);
                          }
                      }
                  }
              }
              """
          )
        );
    }

    @Test
    void tryCatchFinally() {
        rewriteRun(
          java(
            """
              import java.io.*;

              class Test {
                  void method() {
                      InputStream in = null;
                      try {
                          in = new FileInputStream("file.txt");
                          int data = in.read();
                          // Process data
                      } catch (IOException e) {
                          e.printStackTrace();
                      } finally {
                          if (in != null) {
                              try {
                                  in.close();
                              } catch (IOException e) {
                                  // Ignore
                              }
                          }
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
                          // Process data
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
    void doNotTransformWhenResourceNotClosed() {
        rewriteRun(
          java(
            """
              import java.io.*;

              class Test {
                  void method() throws IOException {
                      InputStream in = new FileInputStream("file.txt");
                      try {
                          int data = in.read();
                          // Process data
                      } finally {
                          // Resource not closed
                          System.out.println("Processing complete");
                      }
                  }
              }
              """
          )
        );
    }

    @Disabled("This is rather tricky and quite uncommon")
    @Test
    void multipleVariableDeclarations() {
        rewriteRun(
          java(
            """
              import java.io.*;

              class Test {
                  void method() throws IOException {
                      InputStream in1, in2 = new FileInputStream("file2.txt");
                      in1 = new FileInputStream("file1.txt");
                      try {
                          int data1 = in1.read();
                          int data2 = in2.read();
                      } finally {
                          in1.close();
                          in2.close();
                      }
                  }
              }
              """,
            """
              import java.io.*;

              class Test {
                  void method() throws IOException {
                      try (InputStream in1 = new FileInputStream("file1.txt");
                           InputStream in2 = new FileInputStream("file2.txt")) {
                          int data1 = in1.read();
                          int data2 = in2.read();
                      }
                  }
              }
              """
          )
        );
    }

    @Test
    void resourceClosedInCatchBlock() {
        rewriteRun(
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
    void qualifiedCloseMethodCall() {
        rewriteRun(
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
    void nonAutoCloseableResource() {
        rewriteRun(
          java(
            """
              import java.io.*;

              class Test {
                  static class CustomResource {
                      public void close() {
                          // Custom close logic
                      }
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
    void resourceAssignedToField() {
        rewriteRun(
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
    void resourceWithComplexFinallyLogic() {
        rewriteRun(
          java(
            """
              import java.io.*;

              class Test {
                  void method() throws IOException {
                      InputStream in = new FileInputStream("file.txt");
                      boolean success = false;
                      try {
                          int data = in.read();
                          success = true;
                      } finally {
                          if (success) {
                              System.out.println("Success!");
                          } else {
                              System.out.println("Failed!");
                          }
                          if (in != null) {
                              in.close();
                          }
                          System.out.println("Cleanup done");
                      }
                  }
              }
              """,
            """
              import java.io.*;

              class Test {
                  void method() throws IOException {
                      boolean success = false;
                      try (InputStream in = new FileInputStream("file.txt")) {
                          int data = in.read();
                          success = true;
                      } finally {
                          if (success) {
                              System.out.println("Success!");
                          } else {
                              System.out.println("Failed!");
                          }
                          System.out.println("Cleanup done");
                      }
                  }
              }
              """
          )
        );
    }

    @Test
    void resourceUsedAfterTryBlock() {
        rewriteRun(
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
                      // Resource is referenced after try - can still use try(in) syntax
                      System.out.println("Stream was: " + in);
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
                      // Resource is referenced after try - can still use try(in) syntax
                      System.out.println("Stream was: " + in);
                  }
              }
              """
          )
        );
    }

    @Test
    void resourceReassignedInTryBlock() {
        rewriteRun(
          java(
            """
              import java.io.*;

              class Test {
                  void method() throws IOException {
                      InputStream in = new FileInputStream("file1.txt");
                      try {
                          if (Math.random() > 0.5) {
                              in = new FileInputStream("file2.txt");
                          }
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
    void resourceWithStaticCloseCall() {
        rewriteRun(
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
}
