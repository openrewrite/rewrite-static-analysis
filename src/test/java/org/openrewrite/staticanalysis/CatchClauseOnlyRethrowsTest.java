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
import org.openrewrite.ExecutionContext;
import org.openrewrite.InMemoryExecutionContext;
import org.openrewrite.java.JavaIsoVisitor;
import org.openrewrite.java.JavaParser;
import org.openrewrite.java.tree.J;
import org.openrewrite.test.RecipeSpec;
import org.openrewrite.test.RewriteTest;

import static java.util.Collections.emptyList;
import static org.assertj.core.api.Assertions.assertThat;
import static org.openrewrite.java.Assertions.java;
import static org.openrewrite.kotlin.Assertions.kotlin;

@SuppressWarnings("ALL")
class CatchClauseOnlyRethrowsTest implements RewriteTest {

    @Override
    public void defaults(RecipeSpec spec) {
        spec.recipe(new CatchClauseOnlyRethrows());
    }

    @Test
    void preserveKotlinRethrowBeforeThrowableHandler() {
        rewriteRun(
          kotlin(
            """
              class Aborted : RuntimeException()
              fun run() {
                  try {
                      throw Aborted()
                  } catch (e: Aborted) {
                      throw e
                  } catch (e: Throwable) {
                      println("handled")
                  }
              }
              """
          )
        );
    }

    @Test
    void preserveUnnamedCatchThrowingOuterVariable() {
        ExecutionContext ctx = new InMemoryExecutionContext();
        J.CompilationUnit parsed = (J.CompilationUnit) JavaParser.fromJavaVersion().build().parse(ctx,
          """
            class Test {
                void run(Exception outer) throws Exception {
                    try {
                        System.out.println("run");
                    } catch (Exception ignored) {
                        throw outer;
                    }
                }
            }
            """).findFirst().orElseThrow();
        // C# permits a typed catch without a variable binding; its body may throw an outer variable.
        J.CompilationUnit unnamed = new JavaIsoVisitor<ExecutionContext>() {
            @Override
            public J.Try.Catch visitCatch(J.Try.Catch aCatch, ExecutionContext context) {
                return aCatch.withParameter(aCatch.getParameter().withTree(
                  aCatch.getParameter().getTree().withVariables(emptyList())));
            }
        }.visitCompilationUnit(parsed, ctx);
        assertThat(new CatchClauseOnlyRethrows().getVisitor().visit(unnamed, ctx)).isSameAs(unnamed);
    }

    @DocumentExample
    @Test
    void tryCanBeRemoved() {
        rewriteRun(
          //language=java
          java(
            """
              import java.io.FileReader;
              import java.io.IOException;

              class A {
                  void foo() throws IOException {
                      try {
                          new FileReader("").read();
                      } catch (IOException e) {
                          throw e;
                      }
                  }
              }
              """,
            """
              import java.io.FileReader;
              import java.io.IOException;

              class A {
                  void foo() throws IOException {
                      new FileReader("").read();
                  }
              }
              """
          )
        );
    }

    @Test
    void rethrownButWithDifferentMessage() {
        rewriteRun(
          //language=java
          java(
            """
              import java.io.FileReader;
              import java.io.IOException;

              class A {
                  void foo() throws IOException {
                      try {
                          new FileReader("").read();
                      } catch (IOException e) {
                          throw new IOException("another message", e);
                      } catch (Exception e) {
                          throw new Exception("another message");
                      }
                  }
              }
              """
          )
        );
    }

    @Test
    void catchShouldBePreservedBecauseLessSpecificCatchFollows() {
        rewriteRun(
          //language=java
          java(
            """
              import java.io.FileReader;
              import java.io.IOException;

              class A {
                  void foo() throws IOException {
                      try {
                          new FileReader("").read();
                      } catch (IOException e) {
                          throw e;
                      } catch (Exception e) {
                          System.out.println(e.getMessage());
                      } catch (Throwable t) {
                          t.printStackTrace();
                      }
                  }
              }
              """
          )
        );
    }

    @Test
    void catchShouldBePreservedBecauseLessSpecificCatchFollowsLater() {
        rewriteRun(
          //language=java
          java(
            """
              import java.io.FileReader;
              import java.io.IOException;
              import java.io.FileNotFoundException;

              class A {
                  void foo() throws IOException {
                      try {
                          new FileReader("").read();
                      } catch (FileNotFoundException e) {
                          throw e;
                      } catch (IOException e) {
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
    void multiCatchShouldBePreservedBecauseLessSpecificCatchFollows() {
        rewriteRun(
          //language=java
          java(
            """
              import java.io.FileReader;
              import java.io.IOException;

              class A {
                  void foo() throws IOException {
                      try {
                          new FileReader("").read();
                      } catch (IOException | RuntimeException e) {
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
    void catchShouldBePreservedBecauseLessSpecificCatchFollowsWithMultiCatch() {
        rewriteRun(
          //language=java
          java(
            """
              import java.io.FileReader;
              import java.io.IOException;

              class A {
                  void foo() throws IOException {
                      try {
                          new FileReader("").read();
                      } catch (IOException e) {
                          throw e;
                      } catch (Exception | Throwable t) {
                          t.printStackTrace();
                      }
                  }
              }
              """
          )
        );
    }

    @Test
    void mltiCatchShouldBePreservedBecauseLessSpecificCatchFollowsWithMultiCatch() {
        rewriteRun(
          //language=java
          java(
            """
              import java.io.FileReader;
              import java.io.IOException;

              class A {
                  void foo() throws IOException {
                      try {
                          new FileReader("").read();
                      } catch (IOException | RuntimeException e) {
                          throw e;
                      } catch (Exception | Throwable t) {
                          t.printStackTrace();
                      }
                  }
              }
              """
          )
        );
    }

    @Test
    void tryCanBeRemovedWithMultiCatch() {
        rewriteRun(
          //language=java
          java(
            """
              import java.io.FileReader;
              import java.io.IOException;
              import java.io.FileNotFoundException;

              class A {
                  void foo() throws IOException {
                      try {
                          new FileReader("").read();
                      } catch (FileNotFoundException e) {
                          throw e;
                      } catch (IOException | ArrayIndexOutOfBoundsException e) {
                          throw e;
                      } catch (Exception e) {
                          throw e;
                      }
                  }
              }
              """,
            """
              import java.io.FileReader;
              import java.io.IOException;

              class A {
                  void foo() throws IOException {
                      new FileReader("").read();
                  }
              }
              """
          )
        );
    }

    @Test
    void multiCatchPreservedOnDifferentThrow() {
        rewriteRun(
          //language=java
          java(
            """
              import java.io.FileReader;
              import java.io.IOException;
              import java.io.FileNotFoundException;

              class A {
                  void foo() throws IOException {
                      try {
                          new FileReader("").read();
                      } catch (FileNotFoundException e) {
                          throw e;
                      } catch (IOException | ArrayIndexOutOfBoundsException e) {
                          throw new IOException("another message", e);
                      }
                  }
              }
              """
          )
        );
    }

    @Test
    void tryShouldBePreservedBecauseFinally() {
        rewriteRun(
          //language=java
          java(
            """
              import java.io.FileReader;
              import java.io.IOException;

              class A {
                  void foo() throws IOException {
                      try {
                          new FileReader("").read();
                      } catch (IOException e) {
                          throw e;
                      } finally {
                          // should be untouched since this might do something
                      }
                  }
              }
              """,
            """
              import java.io.FileReader;
              import java.io.IOException;

              class A {
                  void foo() throws IOException {
                      try {
                          new FileReader("").read();
                      } finally {
                          // should be untouched since this might do something
                      }
                  }
              }
              """
          )
        );
    }

    @Test
    void tryShouldBePreservedBecauseResources() {
        rewriteRun(
          //language=java
          java(
            """
              import java.io.FileReader;
              import java.io.IOException;

              class A {
                  void foo() throws IOException {
                      try(FileReader fr = new FileReader("")) {
                          fr.read();
                      } catch (IOException e) {
                          throw e;
                      }
                  }
              }
              """,
            """
              import java.io.FileReader;
              import java.io.IOException;

              class A {
                  void foo() throws IOException {
                      try(FileReader fr = new FileReader("")) {
                          fr.read();
                      }
                  }
              }
              """
          )
        );
    }

    @Test
    void wrappingAndRethrowingIsUnchanged() {
        rewriteRun(
          //language=java
          java(
            """
              import java.io.FileReader;
              import java.io.IOException;

              class A {
                  void foo() {
                      try {
                          new FileReader("").read();
                      } catch (IOException e) {
                          throw new RuntimeException(e);
                      }
                  }
              }
              """
          )
        );
    }

    @Test
    void loggingAndRethrowingIsUnchanged() {
        rewriteRun(
          //language=java
          java(
            """
              import java.io.FileReader;
              import java.io.IOException;

              class A {
                  void foo() throws IOException {
                      try {
                          new FileReader("").read();
                      } catch (IOException e) {
                          System.out.println("Oh no an exception");
                          throw e;
                      }
                  }
              }
              """
          )
        );
    }
}
