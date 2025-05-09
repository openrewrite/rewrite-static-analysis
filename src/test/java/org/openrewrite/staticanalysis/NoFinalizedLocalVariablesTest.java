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
import org.openrewrite.DocumentExample;
import org.openrewrite.test.RecipeSpec;
import org.openrewrite.test.RewriteTest;

import static org.openrewrite.java.Assertions.java;

@SuppressWarnings("ALL")
class NoFinalizedLocalVariablesTest implements RewriteTest {

    @Override
    public void defaults(RecipeSpec spec) {
        spec.recipe(new NoFinalizedLocalVariables(null));
    }

    @DocumentExample
    @Test
    void removeFinal() {
        rewriteRun(
          //language=java
          java(
            """
              import java.util.function.Supplier;
              class T {
                  final int field = 0;
                  public void test(final String s) {
                      final int n = 0;
                      new Supplier<>() {
                          final int innerField = 0;
                          public String get() {
                              return s;
                          }
                      };
                  }
              }
              """,
            """
              import java.util.function.Supplier;
              class T {
                  final int field = 0;
                  public void test(String s) {
                      int n = 0;
                      new Supplier<>() {
                          final int innerField = 0;
                          public String get() {
                              return s;
                          }
                      };
                  }
              }
              """
          )
        );
    }

    @Test
    void removeFinalFromWithinTryBlock() {
        rewriteRun(
          //language=java
          java(
            """
              class T {
                  final int field = 0;
                  public void test(final String s) {
                      final int n = 0;
                      try {
                          final int innerField = 0;
                      } catch (final RuntimeException e) {
                          final int inCatchField = 0;
                      };
                  }
              }
              """,
            """
              class T {
                  final int field = 0;
                  public void test(String s) {
                      int n = 0;
                      try {
                          int innerField = 0;
                      } catch (RuntimeException e) {
                          int inCatchField = 0;
                      };
                  }
              }
              """
          )
        );
    }

    @Test
    void retainPrefix() {
        rewriteRun(
          //language=java
          java(
            """
              class T {
                  public void test(@SuppressWarnings("ALL") final String s) {
                  }
              }
              """,
            """
              class T {
                  public void test(@SuppressWarnings("ALL") String s) {
                  }
              }
              """
          )
        );
    }

    @Nested
    class WithExcludeMethodParametersTrue {
        @Test
        void removesFinalExceptOnMethodParameters() {
            rewriteRun(
              spec -> spec.recipe(new NoFinalizedLocalVariables(true)),
              //language=java
              java(
                """
                  import java.util.function.Function;
                  import java.util.function.Supplier;
                  class T {
                      final int field = 0;
                      final Function<String, Boolean> lambda = (final String t) -> {
                          final int j = 1;
                          return true;
                      };
                      public void test(final String s) {
                          final int n = 0;
                          new Supplier<>() {
                              final int innerField = 0;
                              public String get() {
                                  return s;
                              }
                              private void set(final Boolean u) {
                                  final Function<String, Boolean> innerLambda = (final String x) -> {
                                      final int k = 2;
                                      return u;
                                  };
                              }
                          };
                      }
                  }
                  """,
                """
                  import java.util.function.Function;
                  import java.util.function.Supplier;
                  class T {
                      final int field = 0;
                      final Function<String, Boolean> lambda = (final String t) -> {
                          int j = 1;
                          return true;
                      };
                      public void test(final String s) {
                          int n = 0;
                          new Supplier<>() {
                              final int innerField = 0;
                              public String get() {
                                  return s;
                              }
                              private void set(final Boolean u) {
                                  Function<String, Boolean> innerLambda = (final String x) -> {
                                      int k = 2;
                                      return u;
                                  };
                              }
                          };
                      }
                  }
                  """
              )
            );
        }
    }

    @Nested
    class WithExcludeMethodParametersFalseOrNull {
        @Test
        void withFalseRemovesFinal() {
            rewriteRun(
              spec -> spec.recipe(new NoFinalizedLocalVariables(false)),
              //language=java
              java(
                """
                  import java.util.function.Function;
                  import java.util.function.Supplier;
                  class T {
                      final int field = 0;
                      final Function<String, Boolean> lambda = (final String t) -> {
                          final int j = 1;
                          return true;
                      };
                      public void test(final String s) {
                          final int n = 0;
                          new Supplier<>() {
                              final int innerField = 0;
                              public String get() {
                                  return s;
                              }
                              private void set(final Boolean u) {
                                  final Function<String, Boolean> innerLambda = (final String x) -> {
                                      final int k = 2;
                                      return u;
                                  };
                              }
                          };
                      }
                  }
                  """,
                """
                  import java.util.function.Function;
                  import java.util.function.Supplier;
                  class T {
                      final int field = 0;
                      final Function<String, Boolean> lambda = (String t) -> {
                          int j = 1;
                          return true;
                      };
                      public void test(String s) {
                          int n = 0;
                          new Supplier<>() {
                              final int innerField = 0;
                              public String get() {
                                  return s;
                              }
                              private void set(Boolean u) {
                                  Function<String, Boolean> innerLambda = (String x) -> {
                                      int k = 2;
                                      return u;
                                  };
                              }
                          };
                      }
                  }
                  """
              )
            );
        }

        @Test
        void withNullRemovesFinal() {
            rewriteRun(
              spec -> spec.recipe(new NoFinalizedLocalVariables(null)),
              //language=java
              java(
                """
                  import java.util.function.Function;
                  import java.util.function.Supplier;
                  class T {
                      final int field = 0;
                      final Function<String, Boolean> lambda = (final String t) -> {
                          final int j = 1;
                          return true;
                      };
                      public void test(final String s) {
                          final int n = 0;
                          new Supplier<>() {
                              final int innerField = 0;
                              public String get() {
                                  return s;
                              }
                              private void set(final Boolean u) {
                                  final Function<String, Boolean> innerLambda = (final String x) -> {
                                      final int k = 2;
                                      return u;
                                  };
                              }
                          };
                      }
                  }
                  """,
                """
                  import java.util.function.Function;
                  import java.util.function.Supplier;
                  class T {
                      final int field = 0;
                      final Function<String, Boolean> lambda = (String t) -> {
                          int j = 1;
                          return true;
                      };
                      public void test(String s) {
                          int n = 0;
                          new Supplier<>() {
                              final int innerField = 0;
                              public String get() {
                                  return s;
                              }
                              private void set(Boolean u) {
                                  Function<String, Boolean> innerLambda = (String x) -> {
                                      int k = 2;
                                      return u;
                                  };
                              }
                          };
                      }
                  }
                  """
              )
            );
        }
    }
}
