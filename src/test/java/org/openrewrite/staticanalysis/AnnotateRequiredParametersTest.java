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

@SuppressWarnings("ConstantConditions")
class AnnotateRequiredParametersTest implements RewriteTest {

    @Override
    public void defaults(RecipeSpec spec) {
        spec.recipe(new AnnotateRequiredParameters(null));
    }

    @DocumentExample
    @Test
    void annotateParameterWithNullCheckThrowingException() {
        rewriteRun(
          //language=java
          java(
            """
              class Test {
                  public void process(String value) {
                      if (value == null) {
                          throw new IllegalArgumentException("value cannot be null");
                      }
                      System.out.println(value);
                  }
              }
              """,
            """
              import org.jspecify.annotations.NonNull;

              class Test {
                  public void process(@NonNull String value) {
                      System.out.println(value);
                  }
              }
              """
          )
        );
    }

    @Test
    void annotateParameterWithReversedNullCheck() {
        rewriteRun(
          //language=java
          java(
            """
              class Test {
                  public void process(String value) {
                      if (null == value) {
                          throw new NullPointerException();
                      }
                      System.out.println(value);
                  }
              }
              """,
            """
              import org.jspecify.annotations.NonNull;

              class Test {
                  public void process(@NonNull String value) {
                      System.out.println(value);
                  }
              }
              """
          )
        );
    }

    @Test
    void annotateParameterWithObjectsRequireNonNull() {
        rewriteRun(
          //language=java
          java(
            """
              import java.util.Objects;

              class Test {
                  public void process(String value) {
                      Objects.requireNonNull(value);
                      System.out.println(value);
                  }
              }
              """,
            """
              import org.jspecify.annotations.NonNull;

              import java.util.Objects;

              class Test {
                  public void process(@NonNull String value) {
                      System.out.println(value);
                  }
              }
              """
          )
        );
    }

    @Test
    void annotateParameterWithObjectsRequireNonNullWithMessage() {
        rewriteRun(
          //language=java
          java(
            """
              import java.util.Objects;

              class Test {
                  public void process(String value) {
                      Objects.requireNonNull(value, "value cannot be null");
                      System.out.println(value);
                  }
              }
              """,
            """
              import org.jspecify.annotations.NonNull;

              import java.util.Objects;

              class Test {
                  public void process(@NonNull String value) {
                      System.out.println(value);
                  }
              }
              """
          )
        );
    }

    @Test
    void annotateMultipleParameters() {
        rewriteRun(
          //language=java
          java(
            """
              class Test {
                  public void process(String first, String second) {
                      if (first == null) {
                          throw new IllegalArgumentException();
                      }
                      if (second == null) {
                          throw new IllegalArgumentException();
                      }
                      System.out.println(first + second);
                  }
              }
              """,
            """
              import org.jspecify.annotations.NonNull;

              class Test {
                  public void process(@NonNull String first, @NonNull String second) {
                      System.out.println(first + second);
                  }
              }
              """
          )
        );
    }

    @Test
    void annotateOnlyRequiredParameters() {
        rewriteRun(
          //language=java
          java(
            """
              class Test {
                  public void process(String required, String optional) {
                      if (required == null) {
                          throw new IllegalArgumentException();
                      }
                      if (optional != null) {
                          System.out.println(optional);
                      }
                  }
              }
              """,
            """
              import org.jspecify.annotations.NonNull;

              class Test {
                  public void process(@NonNull String required, String optional) {
                      if (optional != null) {
                          System.out.println(optional);
                      }
                  }
              }
              """
          )
        );
    }

    @Test
    void doNotAnnotateWhenNullCheckDoesNotThrow() {
        rewriteRun(
          //language=java
          java(
            """
              class Test {
                  public void process(String value) {
                      if (value == null) {
                          value = "default";
                      }
                      System.out.println(value);
                  }
              }
              """
          )
        );
    }

    @Test
    void doNotAnnotateWhenAlreadyAnnotated() {
        rewriteRun(
          //language=java
          java(
            """
              import org.jspecify.annotations.NonNull;

              class Test {
                  public void process(@NonNull String value) {
                      if (value == null) {
                          throw new IllegalArgumentException();
                      }
                      System.out.println(value);
                  }
              }
              """,
            """
              import org.jspecify.annotations.NonNull;

              class Test {
                  public void process(@NonNull String value) {
                      System.out.println(value);
                  }
              }
              """
          )
        );
    }

    @Test
    void doNotAnnotatePrivateMethods() {
        rewriteRun(
          //language=java
          java(
            """
              class Test {
                  private void process(String value) {
                      if (value == null) {
                          throw new IllegalArgumentException();
                      }
                      System.out.println(value);
                  }
              }
              """
          )
        );
    }

    @Test
    void doNotAnnotateOverriddenMethods() {
        rewriteRun(
          //language=java
          java(
            """
              abstract class Base {
                  public abstract void process(String value);
              }

              class Test extends Base {
                  @Override
                  public void process(String value) {
                      if (value == null) {
                          throw new IllegalArgumentException();
                      }
                      System.out.println(value);
                  }
              }
              """
          )
        );
    }

    @Test
    void annotateWithDifferentExceptionTypes() {
        rewriteRun(
          //language=java
          java(
            """
              class Test {
                  public void process(String value) {
                      if (value == null) {
                          throw new RuntimeException("Required parameter");
                      }
                      System.out.println(value);
                  }
              }
              """,
            """
              import org.jspecify.annotations.NonNull;

              class Test {
                  public void process(@NonNull String value) {
                      System.out.println(value);
                  }
              }
              """
          )
        );
    }

    @Test
    void annotateWithBlockInIfStatement() {
        rewriteRun(
          //language=java
          java(
            """
              class Test {
                  public void process(String value) {
                      if (value == null) {
                          System.err.println("Error: value is null");
                          throw new IllegalArgumentException();
                      }
                      System.out.println(value);
                  }
              }
              """,
            """
              import org.jspecify.annotations.NonNull;

              class Test {
                  public void process(@NonNull String value) {
                      System.out.println(value);
                  }
              }
              """
          )
        );
    }

    @Test
    void doNotAnnotateWhenThrowIsNotFirstStatement() {
        rewriteRun(
          //language=java
          java(
            """
              class Test {
                  public void process(String value) {
                      if (value == null) {
                          System.err.println("Error");
                          // More code that could potentially handle null
                          value = "default";
                      }
                      System.out.println(value);
                  }
              }
              """
          )
        );
    }

    @Test
    void annotateWithCustomAnnotationClass() {
        rewriteRun(
          spec -> spec.recipe(new AnnotateRequiredParameters("jakarta.annotation.Nonnull")),
          //language=java
          java(
            """
              class Test {
                  public void process(String value) {
                      if (value == null) {
                          throw new IllegalArgumentException();
                      }
                      System.out.println(value);
                  }
              }
              """,
            """
              import jakarta.annotation.Nonnull;

              class Test {
                  public void process(@Nonnull String value) {
                      System.out.println(value);
                  }
              }
              """
          )
        );
    }

    @Test
    void annotateMultipleParametersInSameCondition() {
        rewriteRun(
          //language=java
          java(
            """
              class Test {
                  public void process(String first, String second) {
                      if (first == null || second == null) {
                          throw new IllegalArgumentException();
                      }
                      System.out.println(first + second);
                  }
              }
              """,
            """
              import org.jspecify.annotations.NonNull;

              class Test {
                  public void process(@NonNull String first, @NonNull String second) {
                      System.out.println(first + second);
                  }
              }
              """
          )
        );
    }

    @Test
    void doNotAnnotateWhenAndConditionAllowsOneNull() {
        rewriteRun(
          //language=java
          java(
            """
              class Test {
                  public void process(String first, String second) {
                      if (first == null && second == null) {
                          throw new IllegalArgumentException();
                      }
                      System.out.println(first + second);
                  }
              }
              """
          )
        );
    }
}
