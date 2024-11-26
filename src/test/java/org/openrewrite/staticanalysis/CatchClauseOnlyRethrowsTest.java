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
import org.openrewrite.DocumentExample;
import org.openrewrite.FileAttributes;
import org.openrewrite.InMemoryExecutionContext;
import org.openrewrite.Tree;
import org.openrewrite.csharp.tree.Cs;
import org.openrewrite.java.tree.*;
import org.openrewrite.marker.Markers;
import org.openrewrite.test.RecipeSpec;
import org.openrewrite.test.RewriteTest;

import java.nio.file.Path;
import java.util.Collections;
import java.util.List;

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
    void testCsharpImplicitThrow() {
        Cs.CompilationUnit compilationUnit = new Cs.CompilationUnit(Tree.randomId(), Space.EMPTY,
          Markers.EMPTY, Path.of("test.cs"),
          new FileAttributes(null, null, null, true, true, true, 0l),
          null, false, null, null, null, null,
          List.of(JRightPadded.build(
            new J.ClassDeclaration(Tree.randomId(), Space.EMPTY, Markers.EMPTY,
              Collections.emptyList(), Collections.emptyList(),
              new J.ClassDeclaration.Kind(Tree.randomId(), Space.EMPTY, Markers.EMPTY, Collections.emptyList(), J.ClassDeclaration.Kind.Type.Class),
              new J.Identifier(Tree.randomId(), Space.EMPTY, Markers.EMPTY, Collections.emptyList(), "doSome", null, null),
              null, null, null, null, null,
              new J.Block(Tree.randomId(), Space.EMPTY, Markers.EMPTY, JRightPadded.build(false),
                List.of(JRightPadded.build(new J.Try(Tree.randomId(), Space.EMPTY, Markers.EMPTY, null,
                  new J.Block(Tree.randomId(), Space.EMPTY, Markers.EMPTY, JRightPadded.build(false),
                    List.of(JRightPadded.build(new J.Throw(Tree.randomId(), Space.EMPTY, Markers.EMPTY, new J.NewClass(
                      Tree.randomId(), Space.EMPTY, Markers.EMPTY, null, Space.EMPTY, TypeTree.build("java.lang.IllegalAccessException"), JContainer.empty(), null, null)))),
                    Space.EMPTY),
                  List.of(new J.Try.Catch(Tree.randomId(), Space.EMPTY, Markers.EMPTY, new J.ControlParentheses<J.VariableDeclarations>(Tree.randomId(), Space.EMPTY, Markers.EMPTY,
                    JRightPadded.build(new J.VariableDeclarations(Tree.randomId(), Space.EMPTY, Markers.EMPTY, Collections.emptyList(), Collections.emptyList(), TypeTree.build("java.lang.IllegalAccessException"), null, List.of(), List.of(JRightPadded.build(
                      new J.VariableDeclarations.NamedVariable(Tree.randomId(), Space.EMPTY, Markers.EMPTY,
                        new J.Identifier(Tree.randomId(), Space.SINGLE_SPACE, Markers.EMPTY, Collections.emptyList(), "e", null, null),
                        List.of(), null, null)))))),
                    new J.Block(Tree.randomId(), Space.EMPTY, Markers.EMPTY, JRightPadded.build(false),
                      List.of(JRightPadded.build(new J.Throw(Tree.randomId(), Space.EMPTY, Markers.EMPTY, new J.Empty(Tree.randomId(), Space.EMPTY, Markers.EMPTY)))),
                      Space.EMPTY))),
                  null))),
                Space.EMPTY),
              null)))
          , Space.EMPTY);

        assertThat(compilationUnit.getMembers().get(0)).isInstanceOf(J.ClassDeclaration.class);
        assertThat(((J.ClassDeclaration)compilationUnit.getMembers().get(0)).getBody().getStatements().get(0)).isInstanceOf(J.Try.class);

        Cs.CompilationUnit output = (Cs.CompilationUnit) new CatchClauseOnlyRethrows().getVisitor().visit(compilationUnit, new InMemoryExecutionContext());
        assertThat(output.getMembers().get(0)).isInstanceOf(J.ClassDeclaration.class);
        assertThat(((J.ClassDeclaration)output.getMembers().get(0)).getBody().getStatements().get(0)).isInstanceOf(J.Throw.class);
    }
}
