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

class CleanupTryWithResourcesTest implements RewriteTest {

    @Override
    public void defaults(RecipeSpec spec) {
        spec.recipe(new CleanupTryWithResources());
    }

    @DocumentExample
    @Test
    void removeFinalModifier() {
        rewriteRun(
          //language=java
          java(
            """
              import java.io.ByteArrayInputStream;
              import java.io.IOException;

              class Test {
                  void method() throws IOException {
                      try (final var input = new ByteArrayInputStream(new byte[0])) {
                          int x = input.read();
                      }
                  }
              }
              """,
            """
              import java.io.ByteArrayInputStream;
              import java.io.IOException;

              class Test {
                  void method() throws IOException {
                      try (var input = new ByteArrayInputStream(new byte[0])) {
                          int x = input.read();
                      }
                  }
              }
              """
          )
        );
    }

    @Test
    void cleanupMultiline() {
        rewriteRun(
          //language=java
          java(
            """
              import java.io.ByteArrayInputStream;
              import java.io.IOException;

              class Test {
                  void method() throws IOException {
                      try (final
                           var
                               input = new ByteArrayInputStream(new byte[0])) {
                          int x = input.read();
                      }
                  }
              }
              """,
            """
              import java.io.ByteArrayInputStream;
              import java.io.IOException;

              class Test {
                  void method() throws IOException {
                      try (var input = new ByteArrayInputStream(new byte[0])) {
                          int x = input.read();
                      }
                  }
              }
              """
          )
        );
    }

    @Test
    void removeFinalWithExplicitType() {
        rewriteRun(
          //language=java
          java(
            """
              import java.io.ByteArrayInputStream;
              import java.io.IOException;

              class Test {
                  void method() throws IOException {
                      try (final ByteArrayInputStream input = new ByteArrayInputStream(new byte[0])) {
                          int x = input.read();
                      }
                  }
              }
              """,
            """
              import java.io.ByteArrayInputStream;
              import java.io.IOException;

              class Test {
                  void method() throws IOException {
                      try (ByteArrayInputStream input = new ByteArrayInputStream(new byte[0])) {
                          int x = input.read();
                      }
                  }
              }
              """
          )
        );
    }

    @Test
    void removeFinalFromEachOfMultipleResources() {
        rewriteRun(
          //language=java
          java(
            """
              import java.io.ByteArrayInputStream;
              import java.io.IOException;

              class Test {
                  void method() throws IOException {
                      try (final var a = new ByteArrayInputStream(new byte[0]); final var b = new ByteArrayInputStream(new byte[0])) {
                          int x = a.read() + b.read();
                      }
                  }
              }
              """,
            """
              import java.io.ByteArrayInputStream;
              import java.io.IOException;

              class Test {
                  void method() throws IOException {
                      try (var a = new ByteArrayInputStream(new byte[0]); var b = new ByteArrayInputStream(new byte[0])) {
                          int x = a.read() + b.read();
                      }
                  }
              }
              """
          )
        );
    }

    @Test
    void removeFinalOnlyFromTheResourceThatHasIt() {
        rewriteRun(
          //language=java
          java(
            """
              import java.io.ByteArrayInputStream;
              import java.io.IOException;

              class Test {
                  void method() throws IOException {
                      try (var a = new ByteArrayInputStream(new byte[0]); final var b = new ByteArrayInputStream(new byte[0])) {
                          int x = a.read() + b.read();
                      }
                  }
              }
              """,
            """
              import java.io.ByteArrayInputStream;
              import java.io.IOException;

              class Test {
                  void method() throws IOException {
                      try (var a = new ByteArrayInputStream(new byte[0]); var b = new ByteArrayInputStream(new byte[0])) {
                          int x = a.read() + b.read();
                      }
                  }
              }
              """
          )
        );
    }

    @Test
    void retainLeadingAnnotationWhenRemovingFinal() {
        rewriteRun(
          //language=java
          java(
            """
              import java.io.ByteArrayInputStream;
              import java.io.IOException;

              class Test {
                  void method() throws IOException {
                      try (@SuppressWarnings("unused") final var input = new ByteArrayInputStream(new byte[0])) {
                          int x = input.read();
                      }
                  }
              }
              """,
            """
              import java.io.ByteArrayInputStream;
              import java.io.IOException;

              class Test {
                  void method() throws IOException {
                      try (@SuppressWarnings("unused") var input = new ByteArrayInputStream(new byte[0])) {
                          int x = input.read();
                      }
                  }
              }
              """
          )
        );
    }

    @Test
    void doNotChangeResourceWithoutFinal() {
        rewriteRun(
          //language=java
          java(
            """
              import java.io.ByteArrayInputStream;
              import java.io.IOException;

              class Test {
                  void method() throws IOException {
                      try (var input = new ByteArrayInputStream(new byte[0])) {
                          int x = input.read();
                      }
                  }
              }
              """
          )
        );
    }

    @Test
    void doNotChangeResourceReferencingExistingVariable() {
        rewriteRun(
          //language=java
          java(
            """
              import java.io.ByteArrayInputStream;
              import java.io.IOException;

              class Test {
                  void method(ByteArrayInputStream input) throws IOException {
                      try (input) {
                          int x = input.read();
                      }
                  }
              }
              """
          )
        );
    }

    @Test
    void retainCommentBetweenFinalAndType() {
        rewriteRun(
          //language=java
          java(
            """
              import java.io.ByteArrayInputStream;
              import java.io.IOException;

              class Test {
                  void method() throws IOException {
                      try (final /* why */ var input = new ByteArrayInputStream(new byte[0])) {
                          int x = input.read();
                      }
                  }
              }
              """,
            """
              import java.io.ByteArrayInputStream;
              import java.io.IOException;

              class Test {
                  void method() throws IOException {
                      try (/* why */ var input = new ByteArrayInputStream(new byte[0])) {
                          int x = input.read();
                      }
                  }
              }
              """
          )
        );
    }

    @Test
    void retainCommentBetweenAnnotationAndFinal() {
        rewriteRun(
          //language=java
          java(
            """
              import java.io.ByteArrayInputStream;
              import java.io.IOException;

              class Test {
                  void method() throws IOException {
                      try (@SuppressWarnings("unused") /* keep */ final var input = new ByteArrayInputStream(new byte[0])) {
                          int x = input.read();
                      }
                  }
              }
              """,
            """
              import java.io.ByteArrayInputStream;
              import java.io.IOException;

              class Test {
                  void method() throws IOException {
                      try (@SuppressWarnings("unused") /* keep */ var input = new ByteArrayInputStream(new byte[0])) {
                          int x = input.read();
                      }
                  }
              }
              """
          )
        );
    }

    @Test
    void retainLineCommentAndItsLineBreak() {
        rewriteRun(
          //language=java
          java(
            """
              import java.io.ByteArrayInputStream;
              import java.io.IOException;

              class Test {
                  void method() throws IOException {
                      try (final // trailing
                           var input = new ByteArrayInputStream(new byte[0])) {
                          int x = input.read();
                      }
                  }
              }
              """,
            """
              import java.io.ByteArrayInputStream;
              import java.io.IOException;

              class Test {
                  void method() throws IOException {
                      try (// trailing
                           var input = new ByteArrayInputStream(new byte[0])) {
                          int x = input.read();
                      }
                  }
              }
              """
          )
        );
    }

    @Test
    void doNotReflowInitializerAcrossLines() {
        rewriteRun(
          //language=java
          java(
            """
              import java.io.ByteArrayInputStream;
              import java.io.IOException;

              class Test {
                  void method() throws IOException {
                      try (final var input =
                               new ByteArrayInputStream(new byte[0])) {
                          int x = input.read();
                      }
                  }
              }
              """,
            """
              import java.io.ByteArrayInputStream;
              import java.io.IOException;

              class Test {
                  void method() throws IOException {
                      try (var input =
                               new ByteArrayInputStream(new byte[0])) {
                          int x = input.read();
                      }
                  }
              }
              """
          )
        );
    }
}
