/*
 * Copyright 2021 the original author or authors.
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
import org.openrewrite.*;
import org.openrewrite.csharp.tree.Cs;
import org.openrewrite.java.JavaVisitor;
import org.openrewrite.java.tree.*;
import org.openrewrite.marker.Markers;
import org.openrewrite.test.RecipeSpec;
import org.openrewrite.test.RewriteTest;

import static org.assertj.core.api.Assertions.assertThat;
import static org.openrewrite.java.Assertions.java;

@SuppressWarnings("ALL")
class CatchClauseOnlyRethrowsTest implements RewriteTest {

    @Override
    public void defaults(RecipeSpec spec) {
        spec.recipe(new CatchClauseOnlyRethrows());
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
                      } catch(Exception e) {
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
                      } catch(Exception e) {
                          System.out.println(e.getMessage());
                      } catch(Throwable t) {
                          t.printStackTrace();
                      }
                  }
              }
              """
          )
        );
    }

    @Test
    void catchShouldBePreservedBecauseLessSpecificCatchFollowsWithMultiCast() {
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
                      } catch(Exception | Throwable t) {
                          t.printStackTrace();
                      }
                  }
              }
              """
          )
        );
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
                      } catch(IOException | ArrayIndexOutOfBoundsException e) {
                          throw e;
                      } catch(Exception e) {
                          throw e;
                      }
                  }
              }
              """,
            """
              import java.io.FileReader;
              import java.io.IOException;
              import java.io.FileNotFoundException;

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
                      } catch(IOException | ArrayIndexOutOfBoundsException e) {
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

    @Test
    void verifyCsharpImplicitThrow() {
        rewriteRun(
          spec -> spec.recipe(Recipe.noop()),
          //language=java
          java(
            """
              class A {
                  void foo() throws IllegalAccessException {
                      try {
                          throw new IllegalAccessException();
                      } catch (Exception e) {
                          throw e; // C# can rethrow the caught exception implicitly and so the `e` Identifier is removed by the inline visitor below
                      }
                  }
              }"""
            , spec -> spec.beforeRecipe(compUnit -> {
                  Cs.CompilationUnit cSharpCompUnit = (Cs.CompilationUnit) new JavaVisitor<ExecutionContext>() {
                    @Override
                    public J visitThrow(J.Throw thrown, ExecutionContext executionContext) {
                        if (thrown.getException() instanceof J.Identifier) {
                            return thrown.withException(new J.Empty(Tree.randomId(), Space.EMPTY, Markers.EMPTY));
                        }
                        return thrown;
                    }
                }.visit(JavaToCsharp.compilationUnit(compUnit), new InMemoryExecutionContext());

                  assertThat(cSharpCompUnit.getMembers().get(0)).isInstanceOf(J.ClassDeclaration.class);
                  assertThat(((J.ClassDeclaration)cSharpCompUnit.getMembers().get(0)).getBody().getStatements().get(0)).isInstanceOf(J.MethodDeclaration.class);
                  J.MethodDeclaration md = (J.MethodDeclaration) ((J.ClassDeclaration)cSharpCompUnit.getMembers().get(0)).getBody().getStatements().get(0);
                  assertThat(md.getBody().getStatements().get(0)).isInstanceOf(J.Try.class);

                  Cs.CompilationUnit output = (Cs.CompilationUnit) new CatchClauseOnlyRethrows().getVisitor().visit(cSharpCompUnit, new InMemoryExecutionContext());

                  assertThat(output.getMembers().get(0)).isInstanceOf(J.ClassDeclaration.class);
                  assertThat(((J.ClassDeclaration)output.getMembers().get(0)).getBody().getStatements().get(0)).isInstanceOf(J.MethodDeclaration.class);
                   md = (J.MethodDeclaration) ((J.ClassDeclaration)output.getMembers().get(0)).getBody().getStatements().get(0);
                  assertThat(md.getBody().getStatements().get(0)).isInstanceOf(J.Throw.class);
            }
            )
          )
        );
    }

    public JavaVisitor<ExecutionContext> getJavaToCsharpVisitor() {
        return new JavaVisitor<ExecutionContext>() {


        };
    }
}
