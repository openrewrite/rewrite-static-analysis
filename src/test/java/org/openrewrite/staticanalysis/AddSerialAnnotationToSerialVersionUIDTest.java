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
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.openrewrite.DocumentExample;
import org.openrewrite.test.RecipeSpec;
import org.openrewrite.test.RewriteTest;

import static org.openrewrite.java.Assertions.java;
import static org.openrewrite.java.Assertions.javaVersion;

class AddSerialAnnotationToSerialVersionUIDTest implements RewriteTest {
    @Override
    public void defaults(RecipeSpec spec) {
        spec.recipe(new AddSerialAnnotationToSerialVersionUID())
          .allSources(sourceSpec -> sourceSpec.markers(javaVersion(17)));
    }

    @Test
    @DocumentExample
    void addSerialAnnotation() {
        rewriteRun(
          //language=java
          java(
            """
              import java.io.Serializable;

              class Example implements Serializable {
                  private static final long serialVersionUID = 1L;
              }
              """,
            """
              import java.io.Serial;
              import java.io.Serializable;

              class Example implements Serializable {
                  @Serial
                  private static final long serialVersionUID = 1L;
              }
              """
          )
        );
    }

    @Test
    void shouldAddToNewFieldWhenChained() {
        rewriteRun(
          spec -> spec.recipes(
            new AddSerialVersionUidToSerializable(null),
            new AddSerialAnnotationToSerialVersionUID()),
          //language=java
          java(
            """
              import java.io.Serializable;

              class Example implements Serializable {
              }
              """,
            """
              import java.io.Serial;
              import java.io.Serializable;

              class Example implements Serializable {
                  @Serial
                  private static final long serialVersionUID = 1;
              }
              """
          )
        );
    }

    @Test
    void shouldNoopIfAlreadyPresent() {
        rewriteRun(
          //language=java
          java(
            """
              import java.io.Serializable;
              import java.io.Serial;

              class Example implements Serializable {
                  String var1 = "first variable";
                  @Serial
                  private static final long serialVersionUID = 1L;
                  int var3 = 666;
              }
              """
          )
        );
    }

    @Test
    void shouldNotAnnotateNonSerializableClass() {
        rewriteRun(
          //language=java
          java(
            """
              class Example {
                  private static final long serialVersionUID = 1L;
              }
              """
          )
        );
    }

    @Test
    void shouldNotAnnotateOnJava11() {
        rewriteRun(
          //language=java
          java(
            """
              import java.io.Serializable;

              class Example implements Serializable {
                  private static final long serialVersionUID = 1L;
              }
              """,
            spec -> spec.markers(javaVersion(11))
          )
        );
    }

    @ParameterizedTest
    @ValueSource(strings = {
      "static final long",
        "private final long" ,
        "private static long" ,
        "private static final int"
    })
    void shouldNotAnnotateOtherFields(String modifiers) {
        rewriteRun(
          //language=java
          java(
            """
              import java.io.Serializable;

              class Example implements Serializable {
                  %s serialVersionUID = 1L;
                  private static final long foo = 1L;

                  void doSomething() {
                      long serialVersionUID = 1L;
                  }
              }
              """.formatted(modifiers)
          )
        );
    }

    @Test
    void shouldAnnotatedFieldsInInnerClasses() {
        rewriteRun(
          //language=java
          java(
            """
              import java.io.Serializable;

              class Outer implements Serializable {
                  private static final long serialVersionUID = 1;
                  static class Inner implements Serializable {
                      private static final long serialVersionUID = 1;
                  }
              }
              """,
            """
              import java.io.Serial;
              import java.io.Serializable;

              class Outer implements Serializable {
                  @Serial
                  private static final long serialVersionUID = 1;
                  static class Inner implements Serializable {
                      @Serial
                      private static final long serialVersionUID = 1;
                  }
              }
              """
          )
        );
    }
}
