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

import org.junit.jupiter.api.Test;
import org.openrewrite.DocumentExample;
import org.openrewrite.test.RecipeSpec;
import org.openrewrite.test.RewriteTest;
import org.openrewrite.test.TypeValidation;

import static org.openrewrite.java.Assertions.java;

class TryWithResourcesTest implements RewriteTest {
    @Override
    public void defaults(RecipeSpec spec) {
        spec.recipe(new TryWithResources())
          .typeValidationOptions(TypeValidation.none());
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
                    try (InputStream ignored = new FileInputStream("file.txt")) {
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
}
