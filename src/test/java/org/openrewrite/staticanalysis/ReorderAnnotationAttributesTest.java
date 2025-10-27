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

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.openrewrite.DocumentExample;
import org.openrewrite.test.RecipeSpec;
import org.openrewrite.test.RewriteTest;

import static org.openrewrite.java.Assertions.java;

class ReorderAnnotationAttributesTest implements RewriteTest {
    @Override
    public void defaults(RecipeSpec spec) {
        spec.recipe(new ReorderAnnotationAttributes());
    }

    @DocumentExample
    @Test
    void reordersAnnotationAttributes() {
        rewriteRun(
          //language=java
          java(
            """
              @interface MyAnnotation {
                  String name();
                  int value();
                  boolean enabled();
              }

              @MyAnnotation(value = 123, name = "test", enabled = true)
              class A {
              }
              """,
            """
              @interface MyAnnotation {
                  String name();
                  int value();
                  boolean enabled();
              }

              @MyAnnotation(enabled = true, name = "test", value = 123)
              class A {
              }
              """
          )
        );
    }

    @Test
    void argumentsOnNewLine() {
        rewriteRun(
          //language=java
          java(
            """
              @interface MyAnnotation {
                  String name();
                  int value();
                  boolean enabled();
              }

              @MyAnnotation(
                  value = 123,
                  name = "test",
                  enabled = true)
              class A {
              }
              """,
            """
              @interface MyAnnotation {
                  String name();
                  int value();
                  boolean enabled();
              }

              @MyAnnotation(
                  enabled = true,
                  name = "test",
                  value = 123)
              class A {
              }
              """
          )
        );
    }

    @Test
    void reordersMethodAnnotationAttributes() {
        rewriteRun(
          //language=java
          java(
            """
              @interface MyAnnotation {
                  String name();
                  String description();
              }

              class A {
                  @MyAnnotation(name = "test", description = "A test method")
                  void test() {
                  }
              }
              """,
            """
              @interface MyAnnotation {
                  String name();
                  String description();
              }

              class A {
                  @MyAnnotation(description = "A test method", name = "test")
                  void test() {
                  }
              }
              """
          )
        );
    }

    @Test
    void reordersFieldAnnotationAttributes() {
        rewriteRun(
          //language=java
          java(
            """
              @interface Column {
                  String name();
                  boolean nullable();
                  int length();
              }

              class A {
                  @Column(nullable = false, name = "user_id", length = 255)
                  String userId;
              }
              """,
            """
              @interface Column {
                  String name();
                  boolean nullable();
                  int length();
              }

              class A {
                  @Column(length = 255, name = "user_id", nullable = false)
                  String userId;
              }
              """
          )
        );
    }

    @Test
    void reordersWithArrayValues() {
        rewriteRun(
          //language=java
          java(
            """
              @interface MyAnnotation {
                  String[] values();
                  String name();
              }

              @MyAnnotation(values = {"a", "b", "c"}, name = "test")
              class A {
              }
              """,
            """
              @interface MyAnnotation {
                  String[] values();
                  String name();
              }

              @MyAnnotation(name = "test", values = {"a", "b", "c"})
              class A {
              }
              """
          )
        );
    }

    @Test
    void commentsAssociatedWithValues() {
        rewriteRun(
          //language=java
          java(
            """
              @interface MyAnnotation {
                  String name();
                  int value();
                  boolean enabled();
              }

              @MyAnnotation(
                  // Value
                  value = 123,
                  // Name
                  name = "test",
                  // Enabled
                  enabled = true)
              class A {
              }
              """,
            """
              @interface MyAnnotation {
                  String name();
                  int value();
                  boolean enabled();
              }

              @MyAnnotation(
                  // Enabled
                  enabled = true,
                  // Name
                  name = "test",
                  // Value
                  value = 123)
              class A {
              }
              """
          )
        );
    }

    @Nested
    class NoChange {
        @Test
        void alreadySorted() {
            rewriteRun(
              //language=java
              java(
                """
                  @interface MyAnnotation {
                      String name();
                      int value();
                      boolean enabled();
                  }

                  @MyAnnotation(enabled = true, name = "test", value = 123)
                  class A {
                  }
                  """
              )
            );
        }

        @Test
        void singleAttribute() {
            rewriteRun(
              //language=java
              java(
                """
                  @interface MyAnnotation {
                      String value();
                  }

                  @MyAnnotation(value = "test")
                  class A {
                  }
                  """
              )
            );
        }

        @Test
        void onlyPositionalArgument() {
            rewriteRun(
              //language=java
              java(
                """
                  @SuppressWarnings("unchecked")
                  class A {
                  }
                  """
              )
            );
        }

        @Test
        void preservesPositionalArgumentsAtBeginning() {
            rewriteRun(
              //language=java
              java(
                """
                  @interface MyAnnotation {
                      String value();
                      String name();
                      boolean enabled();
                  }

                  @MyAnnotation("default")
                  class A {
                  }
                  """
              )
            );
        }

        @Test
        void noArguments() {
            rewriteRun(
              //language=java
              java(
                """
                  @Deprecated
                  class A {
                  }
                  """
              )
            );
        }
    }
}
