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

class RemoveUnreachableMultiCatchAlternativeTest implements RewriteTest {

    @Override
    public void defaults(RecipeSpec spec) {
        spec.recipe(new RemoveUnreachableMultiCatchAlternative());
    }

    @DocumentExample
    @Test
    void dropAlternativeShadowedByEarlierCatch() {
        rewriteRun(
          //language=java
          java(
            """
              import java.io.IOException;
              import java.io.FileNotFoundException;

              class A {
                  void m() {
                      try {
                          throw new FileNotFoundException();
                      } catch (FileNotFoundException e) {
                          System.out.println(e);
                      } catch (FileNotFoundException | IOException e) {
                          System.out.println(e);
                      }
                  }
              }
              """,
            """
              import java.io.IOException;
              import java.io.FileNotFoundException;

              class A {
                  void m() {
                      try {
                          throw new FileNotFoundException();
                      } catch (FileNotFoundException e) {
                          System.out.println(e);
                      } catch (IOException e) {
                          System.out.println(e);
                      }
                  }
              }
              """
          )
        );
    }

    @Test
    void dropTrailingAlternativeShadowedByEarlierCatch() {
        rewriteRun(
          //language=java
          java(
            """
              import java.io.IOException;
              import java.io.FileNotFoundException;

              class A {
                  void m() {
                      try {
                          throw new FileNotFoundException();
                      } catch (FileNotFoundException e) {
                          System.out.println(e);
                      } catch (IOException | FileNotFoundException e) {
                          System.out.println(e);
                      }
                  }
              }
              """,
            """
              import java.io.IOException;
              import java.io.FileNotFoundException;

              class A {
                  void m() {
                      try {
                          throw new FileNotFoundException();
                      } catch (FileNotFoundException e) {
                          System.out.println(e);
                      } catch (IOException e) {
                          System.out.println(e);
                      }
                  }
              }
              """
          )
        );
    }

    @Test
    void dropMiddleAlternativeShadowedByEarlierCatch() {
        rewriteRun(
          //language=java
          java(
            """
              import java.io.IOException;
              import java.io.FileNotFoundException;

              class A {
                  void m() {
                      try {
                          throw new FileNotFoundException();
                      } catch (FileNotFoundException e) {
                          System.out.println(e);
                      } catch (IOException | FileNotFoundException | IllegalStateException e) {
                          System.out.println(e);
                      }
                  }
              }
              """,
            """
              import java.io.IOException;
              import java.io.FileNotFoundException;

              class A {
                  void m() {
                      try {
                          throw new FileNotFoundException();
                      } catch (FileNotFoundException e) {
                          System.out.println(e);
                      } catch (IOException | IllegalStateException e) {
                          System.out.println(e);
                      }
                  }
              }
              """
          )
        );
    }

    @Test
    void dropSubtypeAlternativeShadowedByEarlierCatch() {
        rewriteRun(
          //language=java
          java(
            """
              import java.io.IOException;
              import java.io.FileNotFoundException;

              class A {
                  void m() {
                      try {
                          throw new FileNotFoundException();
                      } catch (IOException e) {
                          System.out.println(e);
                      } catch (FileNotFoundException | RuntimeException e) {
                          System.out.println(e);
                      }
                  }
              }
              """,
            """
              import java.io.IOException;
              import java.io.FileNotFoundException;

              class A {
                  void m() {
                      try {
                          throw new FileNotFoundException();
                      } catch (IOException e) {
                          System.out.println(e);
                      } catch (RuntimeException e) {
                          System.out.println(e);
                      }
                  }
              }
              """
          )
        );
    }

    @Test
    void dropEntireCatchWhenAllAlternativesAreShadowed() {
        rewriteRun(
          //language=java
          java(
            """
              import java.io.IOException;
              import java.io.FileNotFoundException;

              class A {
                  void m() {
                      try {
                          throw new IOException();
                      } catch (IOException e) {
                          System.out.println(e);
                      } catch (FileNotFoundException | java.io.EOFException e) {
                          System.out.println(e);
                      }
                  }
              }
              """,
            """
              import java.io.IOException;

              class A {
                  void m() {
                      try {
                          throw new IOException();
                      } catch (IOException e) {
                          System.out.println(e);
                      }
                  }
              }
              """
          )
        );
    }

    @Test
    void dropEntireSingleTypeCatchWhenShadowed() {
        rewriteRun(
          //language=java
          java(
            """
              import java.io.IOException;
              import java.io.FileNotFoundException;

              class A {
                  void m() {
                      try {
                          throw new IOException();
                      } catch (IOException e) {
                          System.out.println(e);
                      } catch (FileNotFoundException e) {
                          System.out.println(e);
                      }
                  }
              }
              """,
            """
              import java.io.IOException;

              class A {
                  void m() {
                      try {
                          throw new IOException();
                      } catch (IOException e) {
                          System.out.println(e);
                      }
                  }
              }
              """
          )
        );
    }

    @Test
    void doNothingWhenOrderingMeansNothingIsShadowed() {
        rewriteRun(
          //language=java
          java(
            """
              import java.io.IOException;
              import java.io.FileNotFoundException;

              class A {
                  void m() {
                      try {
                          throw new FileNotFoundException();
                      } catch (FileNotFoundException e) {
                          System.out.println(e);
                      } catch (IOException e) {
                          System.out.println(e);
                      }
                  }
              }
              """
          )
        );
    }

    @Test
    void doNothingWhenSpecificCatchesPrecedeCatchAllException() {
        rewriteRun(
          //language=java
          java(
            """
              import java.io.IOException;

              class A {
                  void m() {
                      try {
                          throw new IOException();
                      } catch (IOException e) {
                          System.out.println(e);
                      } catch (IllegalStateException e) {
                          System.out.println(e);
                      } catch (Exception e) {
                          System.out.println(e);
                      }
                  }
              }
              """
          )
        );
    }

    @Test
    void doNothingWhenMultiCatchAlternativesAreAllNeeded() {
        rewriteRun(
          //language=java
          java(
            """
              import java.io.IOException;

              class A {
                  void m() {
                      try {
                          throw new IOException();
                      } catch (IOException | IllegalStateException e) {
                          System.out.println(e);
                      }
                  }
              }
              """
          )
        );
    }
}
