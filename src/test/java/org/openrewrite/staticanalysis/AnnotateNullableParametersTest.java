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

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.openrewrite.DocumentExample;
import org.openrewrite.test.RecipeSpec;
import org.openrewrite.test.RewriteTest;
import org.openrewrite.test.SourceSpec;

import static org.openrewrite.java.Assertions.java;

class AnnotateNullableParametersTest implements RewriteTest {

    @Override
    public void defaults(RecipeSpec spec) {
        spec.recipe(new AnnotateNullableParameters(null));
    }


    @Nested
    class SimpleNullComparison {
        @Test
        @DocumentExample
        void singleEqualsNullCase() {
            rewriteRun(
              //language=java
              java(
                """
                  public class PersonBuilder {
                      private String name = "Unknown";

                      public PersonBuilder setName(String name) {
                          if (name == null) {
                              return this;
                          }
                          this.name = name.substring(0, 1).toUpperCase() + name.substring(1);
                          return this;
                      }
                  }
                  """,
                """
                  import org.jspecify.annotations.Nullable;

                  public class PersonBuilder {
                      private String name = "Unknown";

                      public PersonBuilder setName(@Nullable String name) {
                          if (name == null) {
                              return this;
                          }
                          this.name = name.substring(0, 1).toUpperCase() + name.substring(1);
                          return this;
                      }
                  }
                  """
              )
            );
        }

        @Test
        void singleEqualsNotNullCase() {
            rewriteRun(
              //language=java
              java(
                """
                  public class PersonBuilder {
                      private String name = "Unknown";

                      public PersonBuilder setName(String name) {
                          if (name != null) {
                              this.name = name.substring(0, 1).toUpperCase() + name.substring(1);
                          }
                          return this;
                      }
                  }
                  """,
                """
                  import org.jspecify.annotations.Nullable;

                  public class PersonBuilder {
                      private String name = "Unknown";

                      public PersonBuilder setName(@Nullable String name) {
                          if (name != null) {
                              this.name = name.substring(0, 1).toUpperCase() + name.substring(1);
                          }
                          return this;
                      }
                  }
                  """
              )
            );
        }

        @Test
        void compoundCondition() {
            rewriteRun(
              //language=java
              java(
                """
                  public class PersonBuilder {
                      private String name = "Unknown";

                      public PersonBuilder setName(String name) {
                          if (name == null || name.isBlank()) {
                              return this;
                          }
                          this.name = name.substring(0, 1).toUpperCase() + name.substring(1);
                          return this;
                      }
                  }
                  """,
                """
                  import org.jspecify.annotations.Nullable;

                  public class PersonBuilder {
                      private String name = "Unknown";

                      public PersonBuilder setName(@Nullable String name) {
                          if (name == null || name.isBlank()) {
                              return this;
                          }
                          this.name = name.substring(0, 1).toUpperCase() + name.substring(1);
                          return this;
                      }
                  }
                  """
              )
            );
        }

        @Test
        void nestedType() {
            rewriteRun(
              //language=java
              java(
                "package a; public class B { public static class C {} }",
                SourceSpec::skip
              ),
              //language=java
              java(
                """
                  import a.B;
                  public class PersonBuilder {
                      public PersonBuilder setName(B.C name) {
                          if (name == null) {
                              return this;
                          }
                          return this;
                      }
                  }
                  """,
                """
                  import a.B;
                  import org.jspecify.annotations.Nullable;

                  public class PersonBuilder {
                      public PersonBuilder setName(B.@Nullable C name) {
                          if (name == null) {
                              return this;
                          }
                          return this;
                      }
                  }
                  """
              )
            );
        }

        @Test
        void finalVariableSpacing() {
            rewriteRun(
              //language=java
              java(
                """
                  public class Foo {
                      public void bar(final String name) {
                          if (name == null) {}
                      }
                  }
                  """,
                """
                  import org.jspecify.annotations.Nullable;

                  public class Foo {
                      public void bar(@Nullable final String name) {
                          if (name == null) {}
                      }
                  }
                  """
              )
            );
        }
    }

    @Nested
    class UnchangedCode {

        @ParameterizedTest
        @ValueSource(strings = {"", "protected", "private"})
        void methodIsNotPublic(String accessModifier) {
            rewriteRun(
              //language=java
              java(
                """
                  package org.example;

                  public class Test {
                      %s String toUpperCase(String text) {
                          if (text == null) {
                              return "NA";
                          }
                          return text.toUpperCase();
                      }
                  }
                  """.formatted(accessModifier)
              )
            );
        }

        @Test
        void parameterIsNullableButIsAlreadyAnnotated() {
            rewriteRun(
              //language=java
              java(
                """
                  import org.apache.commons.lang3.StringUtils;
                  import org.jspecify.annotations.Nullable;

                  public class PersonBuilder {
                      private String first = "Unknown";
                      private String last = "Unknown";

                      public PersonBuilder setName(@Nullable String first, @Nullable String last) {
                          if (first != null) {
                              this.first = first;
                          }
                          if (last != null) {
                              this.last = last;
                          }
                          return this;
                      }
                  }
                  """
              )
            );
        }

        @Test
        void parameterIsNotNullChecked() {
            rewriteRun(
              //language=java
              java(
                """
                  package org.example;

                  public class Test {
                      public String toUpperCase(String text) {
                          return text.toUpperCase();
                      }
                  }
                  """
              )
            );
        }

        @Test
        void noChangeForOverrides() {
            rewriteRun(
              //language=java
              java(
                """
                  public class Foo {
                      @Override
                      public boolean equals(Object obj) {
                          if (obj == null) {
                              return false;
                          }
                          return true;
                      }
                  }
                  """
              )
            );
        }

        @Test
        void noChangeForDifferentScopeVariableCheck() {
            rewriteRun(
              //language=java
              java(
                """
                  public class Foo {
                      public void foo(Object obj) {
                          new Object() {
                              boolean bar(Object obj) {
                                  if (obj == null) {
                                      return false;
                                  }
                                 return true;
                              }
                          };
                          return true;
                      }
                  }
                  """
              )
            );
        }
    }


    @Nested
    class KnownNullCheckers {

        @ParameterizedTest
        @CsvSource({
          "java.util.Objects, Objects.nonNull",
          "org.apache.commons.lang3.StringUtils, StringUtils.isNotBlank",
          "org.apache.commons.lang3.StringUtils, StringUtils.isNotEmpty",
        })
        void knownMethodsPositiveInvocation(String pkg, String methodCall) {
            rewriteRun(
              //language=java
              java(
                """
                  import %s;
                  import org.jspecify.annotations.Nullable;

                  public class PersonBuilder {
                      private String name = "Unknown";

                      public PersonBuilder setName(String name) {
                          if (%s(name)) {
                            this.name = name;
                          }
                          return this;
                      }
                  }
                  """.formatted(pkg, methodCall),
                """
                  import %s;
                  import org.jspecify.annotations.Nullable;

                  public class PersonBuilder {
                      private String name = "Unknown";

                      public PersonBuilder setName(@Nullable String name) {
                          if (%s(name)) {
                            this.name = name;
                          }
                          return this;
                      }
                  }
                  """.formatted(pkg, methodCall)
              )
            );
        }

        @ParameterizedTest
        @CsvSource({
          "java.util.Objects, Objects.isNull",
          "org.apache.commons.lang3.StringUtils, StringUtils.isBlank",
          "org.apache.commons.lang3.StringUtils, StringUtils.isEmpty",
        })
        void knownMethodsNegatedUnary(String pkg, String methodCall) {
            rewriteRun(
              //language=java
              java(
                """
                  import %s;
                  import org.jspecify.annotations.Nullable;

                  public class PersonBuilder {
                      private String name = "Unknown";

                      public PersonBuilder setName(String name) {
                          if (!%s(name)) {
                              this.name = name;
                          }
                          return this;
                      }
                  }
                  """.formatted(pkg, methodCall),
                """
                  import %s;
                  import org.jspecify.annotations.Nullable;

                  public class PersonBuilder {
                      private String name = "Unknown";

                      public PersonBuilder setName(@Nullable String name) {
                          if (!%s(name)) {
                              this.name = name;
                          }
                          return this;
                      }
                  }
                  """.formatted(pkg, methodCall)
              )
            );
        }

        @Test
        void withExpandedTrueFalseCheck() {
            rewriteRun(
              //language=java
              java(
                """
                  import org.apache.commons.lang3.StringUtils;

                  public class PersonBuilder {
                      private String name = "Unknown";
                      private String surname = "Unknown";

                      public PersonBuilder setName(String name) {
                          if (StringUtils.isEmpty(name) == true) {
                              return this;
                          }
                          this.name = name.substring(0, 1).toUpperCase() + name.substring(1);
                          return this;
                      }

                      public PersonBuilder setSurname(String surname) {
                          if (StringUtils.isEmpty(surname) == false) {
                              this.surname = surname.substring(0, 1).toUpperCase() + surname.substring(1);
                          }
                          return this;
                      }
                  }
                  """,
                """
                  import org.apache.commons.lang3.StringUtils;
                  import org.jspecify.annotations.Nullable;

                  public class PersonBuilder {
                      private String name = "Unknown";
                      private String surname = "Unknown";

                      public PersonBuilder setName(@Nullable String name) {
                          if (StringUtils.isEmpty(name) == true) {
                              return this;
                          }
                          this.name = name.substring(0, 1).toUpperCase() + name.substring(1);
                          return this;
                      }

                      public PersonBuilder setSurname(@Nullable String surname) {
                          if (StringUtils.isEmpty(surname) == false) {
                              this.surname = surname.substring(0, 1).toUpperCase() + surname.substring(1);
                          }
                          return this;
                      }
                  }
                  """
              )
            );
        }
    }

    @Test
    void provideCustomNullableAnnotation() {
        rewriteRun(
          spec -> spec.recipe(new AnnotateNullableParameters("org.openrewrite.jgit.annotations.Nullable")),
          //language=java
          java(
            """
              public class PersonBuilder {
                  private String name = "Unknown";

                  public PersonBuilder setName(String name) {
                      if (name == null) {
                          return this;
                      }
                      this.name = name.substring(0, 1).toUpperCase() + name.substring(1);
                      return this;
                  }
              }
              """,
            """
              import org.openrewrite.jgit.annotations.Nullable;

              public class PersonBuilder {
                  private String name = "Unknown";

                  public PersonBuilder setName(@Nullable String name) {
                      if (name == null) {
                          return this;
                      }
                      this.name = name.substring(0, 1).toUpperCase() + name.substring(1);
                      return this;
                  }
              }
              """
          )
        );
    }
}
