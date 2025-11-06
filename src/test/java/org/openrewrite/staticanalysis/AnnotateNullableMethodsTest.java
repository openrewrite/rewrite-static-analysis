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
import org.openrewrite.Issue;
import org.openrewrite.test.RecipeSpec;
import org.openrewrite.test.RewriteTest;
import org.openrewrite.test.SourceSpec;

import static org.assertj.core.api.Assertions.assertThat;
import static org.openrewrite.java.Assertions.java;
import static org.openrewrite.javascript.Assertions.typescript;


class AnnotateNullableMethodsTest implements RewriteTest {

    @Override
    public void defaults(RecipeSpec spec) {
        spec
          .recipe(new AnnotateNullableMethods(null));
    }

    @DocumentExample
    @Test
    void methodReturnsNullLiteral() {
        rewriteRun(
          //language=java
          java(
            """
              public class Test {

                  public String getString() {
                      return null;
                  }

                  public String getStringWithMultipleReturn() {
                      if (System.currentTimeMillis() % 2 == 0) {
                          return "Not null";
                      }
                      return null;
                  }
              }
              """,
            """
              import org.jspecify.annotations.Nullable;

              public class Test {

                  public @Nullable String getString() {
                      return null;
                  }

                  public @Nullable String getStringWithMultipleReturn() {
                      if (System.currentTimeMillis() % 2 == 0) {
                          return "Not null";
                      }
                      return null;
                  }
              }
              """
          )
        );
    }

    @Test
    void methodReturnNullButIsAlreadyAnnotated() {
        rewriteRun(
          //language=java
          java(
            """
              import org.jspecify.annotations.Nullable;

              public class Test {
                  public @Nullable String getString() {
                      return null;
                  }

                  public @Nullable String getStringWithMultipleReturn() {
                      if (System.currentTimeMillis() % 2 == 0) {
                          return "Not null";
                      }
                      return null;
                  }
              }
              """
          )
        );
    }

    @Test
    void methodDoesNotReturnNull() {
        rewriteRun(
          //language=java
          java(
            """
              package org.example;

              public class Test {
                  public String getString() {
                      return "Hello";
                  }
              }
              """
          )
        );
    }

    @Test
    void methodReturnsDelegateKnowNullableMethod() {
        rewriteRun(
          //language=java
          java(
            """
              import java.util.Map;

              public class Test {

                  public String getString(Map<String, String> map) {
                      return map.get("key");
                  }
              }
              """,
            """
              import org.jspecify.annotations.Nullable;

              import java.util.Map;

              public class Test {

                  public @Nullable String getString(Map<String, String> map) {
                      return map.get("key");
                  }
              }
              """
          )
        );
    }

    @Test
    void methodWithLambdaShouldNotBeAnnotated() {
        rewriteRun(
          //language=java
          java(
            """
                import java.util.stream.Stream;
              class A {
                  public Runnable getRunnable() {
                      return () -> null;
                  }

                  public Integer someStream(){
                      // Stream with lambda class.
                        return Stream.of(1, 2, 3)
                            .map(i -> {if (i == 2) return null; else return i;})
                            .reduce((a, b) -> a + b)
                            .orElse(null);
                  }
              }
              """
          )
        );
    }

    @Test
    void privateMethodsShouldNotBeAnnotated() {
        rewriteRun(
          //language=java
          java(
            """
              public class Test {
                  private String getString() {
                      return null;
                  }
              }
              """
          )
        );
    }

    @Test
    void returnWithinNewClass() {
        rewriteRun(
          //language=java
          java(
            """
              import java.util.concurrent.Callable;

              public class Test {

                  public Callable<String> getString() {
                      Callable<String> callable = new Callable<>() {
                          @Override
                          public String call() throws Exception {
                              return null;
                          }
                      };
                      return callable;
                  }

              }
              """,
            """
              import org.jspecify.annotations.Nullable;

              import java.util.concurrent.Callable;

              public class Test {

                  public Callable<String> getString() {
                      Callable<String> callable = new Callable<>() {

                          @Override
                          public @Nullable String call() throws Exception {
                              return null;
                          }
                      };
                      return callable;
                  }

              }
              """
          )
        );
    }

    @Test
    void provideCustomNullableAnnotationOption() {
        rewriteRun(
          spec -> spec.recipe(new AnnotateNullableMethods("my.custom.Nullable")),
          //language=java
          java(
            """
              public class Test {

                  public String getString() {
                      return null;
                  }
              }
              """,
            """
              import my.custom.Nullable;

              public class Test {

                  public @Nullable String getString() {
                      return null;
                  }
              }
              """
          )
        );
    }

    @Test
    void validate() {
        assertThat(new AnnotateNullableMethods("Nullable").validate().isInvalid()).isTrue();
    }

    @Test
    void returnStaticNestInnerClassAnnotation() {
        rewriteRun(
          //language=java
          java(
            """
              import org.jspecify.annotations.Nullable;

              public class Outer {
                  public static Outer.@Nullable Inner test() { return null; }
                  static class Inner {}
              }
              """
          )
        );
    }

    @Test
    void methodReturnsNullInTernary() {
        rewriteRun(
          //language=java
          java(
            """
              import java.util.Random;

              public class Test {

                  public String getString() {
                      return new Random().nextBoolean() ? "Not null" : null;
                  }
              }
              """,
            """
              import org.jspecify.annotations.Nullable;

              import java.util.Random;

              public class Test {

                  public @Nullable String getString() {
                      return new Random().nextBoolean() ? "Not null" : null;
                  }
              }
              """
          )
        );
    }

    @Test
    void methodWithTernaryNullButNeverReturnsNull() {
        rewriteRun(
          //language=java
          java(
            """
              import java.util.Random;

              public class Test {

                  public String getString() {
                      var value = new Random().nextBoolean() ? "Not null" : null;
                      return value != null ? value : "Unknown";
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
            """
              package a;
              public class B {
                  public static class C {}
              }
              """,
            SourceSpec::skip
          ),
          //language=java
          java(
            """
              import a.B;
              public class Foo {
                  public B.C bar() {
                      return null;
                  }
              }
              """,
            """
              import a.B;
              import org.jspecify.annotations.Nullable;

              public class Foo {

                  public  B.@Nullable C bar() {
                      return null;
                  }
              }
              """
          )
        );
    }

    @Test
    void nullableMethodsInvocationsWithDefaultNullableClass() {
        rewriteRun(
          //language=java
          java(
            """
              import org.jspecify.annotations.Nullable;

              import java.util.Random;

              public class Test {
                  public @Nullable String maybeNullString() {
                      return new Random().nextBoolean() ? "Not null" : null;
                  }

                  public String getString() {
                      return maybeNullString();
                  }
              }
              """,
            """
              import org.jspecify.annotations.Nullable;

              import java.util.Random;

              public class Test {
                  public @Nullable String maybeNullString() {
                      return new Random().nextBoolean() ? "Not null" : null;
                  }

                  public @Nullable String getString() {
                      return maybeNullString();
                  }
              }
              """
          )
        );
    }

    @Test
    void nullableMethodsInvocationsWithCustomNullableClass() {
        rewriteRun(
          spec -> spec.recipe(new AnnotateNullableMethods("org.openrewrite.jgit.annotations.Nullable")),
          //language=java
          java(
            """
              import org.openrewrite.jgit.annotations.Nullable;

              import java.util.Random;

              public class Test {
                  public @Nullable String maybeNullString() {
                      return new Random().nextBoolean() ? "Not null" : null;
                  }

                  public String getString() {
                      return maybeNullString();
                  }
              }
              """,
            """
              import org.openrewrite.jgit.annotations.Nullable;

              import java.util.Random;

              public class Test {
                  public @Nullable String maybeNullString() {
                      return new Random().nextBoolean() ? "Not null" : null;
                  }

                  public @Nullable String getString() {
                      return maybeNullString();
                  }
              }
              """
          )
        );
    }

    @Issue("https://github.com/openrewrite/rewrite-static-analysis/pull/738")
    @Test
    void repeatUntilStable() {
        rewriteRun(
          //language=java
          java(
            """
              public class Test {

                  public String getString() {
                      return null;
                  }

                  public String getString2() {
                      return getString();
                  }

                  public String getString3() {
                      return getString2();
                  }

                  public String getString4() {
                      return getString3();
                  }

                  public String getString5() {
                      return getString4();
                  }

              }
              """,
            """
              import org.jspecify.annotations.Nullable;

              public class Test {

                  public @Nullable String getString() {
                      return null;
                  }

                  public @Nullable String getString2() {
                      return getString();
                  }

                  public @Nullable String getString3() {
                      return getString2();
                  }

                  public @Nullable String getString4() {
                      return getString3();
                  }

                  public @Nullable String getString5() {
                      return getString4();
                  }

              }
              """
          )
        );
    }

    @Test
    void typescriptCode() {
        rewriteRun(
          //language=typescript
          typescript(
            """
            class A {
              public f(n: number) {
                  if (n <= 1) return undefined;
                  return this.f(n - 1);
              }
            }
            """
          )
        );
    }
}
