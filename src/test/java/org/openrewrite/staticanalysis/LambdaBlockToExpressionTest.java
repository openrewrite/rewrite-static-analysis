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
import org.openrewrite.Issue;
import org.openrewrite.java.JavaParser;
import org.openrewrite.test.RecipeSpec;
import org.openrewrite.test.RewriteTest;

import static org.openrewrite.java.Assertions.java;

class LambdaBlockToExpressionTest implements RewriteTest {

    @Override
    public void defaults(RecipeSpec spec) {
        spec.recipe(new LambdaBlockToExpression())
            .parser(JavaParser.fromJavaVersion().logCompilationWarningsAndErrors(true));
    }

    @DocumentExample
    @SuppressWarnings("CodeBlock2Expr")
    @Test
    void simplifyLambdaBlockToExpression() {
        rewriteRun(
          //language=java
          java(
            """
              import java.util.function.Function;
              class Test {
                  Function<Integer, Integer> f = n -> {
                      return n+1;
                  };
              }
              """,
            """
              import java.util.function.Function;
              class Test {
                  Function<Integer, Integer> f = n -> n+1;
              }
              """
          )
        );
    }

    @Issue("https://github.com/openrewrite/rewrite-static-analysis/issues/1")
    @Test
    void simplifyLambdaBlockToExpressionWithComments() {
        rewriteRun(
          //language=java
          java(
            """
              import java.util.function.Function;
              class Test {
                  Function<Integer, Integer> f = n -> {
                      // The buttonType will always be "cancel", even if we pressed one of the entry type buttons
                      return n + 1;
                  };
              }
              """,
            """
              import java.util.function.Function;
              class Test {
                  Function<Integer, Integer> f = n ->
                      // The buttonType will always be "cancel", even if we pressed one of the entry type buttons
                      n + 1;
              }
              """
          )
        );
    }

    @Test
    void simplifyLambdaNestedInArgumentOfAmbiguousMethod() {
        //language=java
        rewriteRun(
          java(
            """
              import java.util.function.Function;
              import java.util.function.Consumer;
              class A {
                  void aMethod(Consumer<Integer> consumer) {}
                  void aMethod(Function<Integer,String> function) {}
              }
              """
          ),
          java(
            """
              import java.util.Optional;
              class Test {
                  void doTest(A a) {
                      a.aMethod(value -> {
                          return Optional.of(value).map(v -> {
                              return v.toString();
                          }).get();
                      });
                  }
              }
              """,
            """
              import java.util.Optional;
              class Test {
                  void doTest(A a) {
                      a.aMethod(value -> {
                          return Optional.of(value).map(v -> v.toString()).get();
                      });
                  }
              }
              """
          )
        );
    }

    @Test
    void simplifyLambdaPassedToOverloadsTakingTheSameInterface() {
        //language=java
        rewriteRun(
          java(
            """
              import java.util.function.Consumer;
              class A {
                  void aMethod(Consumer<String> consumer, String... strings) {}
                  void aMethod(Consumer<String> consumer, Integer... integers) {}
              }
              """
          ),
          java(
            """
              class Test {
                  void doTest(A a) {
                      a.aMethod(s -> {
                          System.out.println(s);
                      });
                  }
              }
              """,
            """
              class Test {
                  void doTest(A a) {
                      a.aMethod(s ->
                          System.out.println(s));
                  }
              }
              """
          )
        );
    }

    @Test
    void simplifyLambdaWhereOverloadTakesNoInterface() {
        //language=java
        rewriteRun(
          java(
            """
              import java.util.function.Consumer;
              class A {
                  void aMethod(String before, Consumer<String> consumer) {}
                  void aMethod(String before, String after) {}
              }
              """
          ),
          java(
            """
              class Test {
                  void doTest(A a) {
                      a.aMethod("before", s -> {
                          System.out.println(s);
                      });
                  }
              }
              """,
            """
              class Test {
                  void doTest(A a) {
                      a.aMethod("before", s ->
                          System.out.println(s));
                  }
              }
              """
          )
        );
    }

    @Issue("https://github.com/openrewrite/rewrite-static-analysis/issues/236")
    @Test
    void simplifyLambdaBlockReturningVoidAsWell2() {
        //language=java
        rewriteRun(
          java(
            """
              public class Main {

                public void run() {
                  Runnable runHelloWorld = () -> {
                      System.out.println("Hello world!");
                  };
                  runHelloWorld.run();
                }
              }
              """,
            """
              public class Main {

                public void run() {
                  Runnable runHelloWorld = () ->
                      System.out.println("Hello world!");
                  runHelloWorld.run();
                }
              }
              """
          )
        );
    }

    @Issue("https://github.com/openrewrite/rewrite-testing-frameworks/pull/582")
    @Test
    void simplifyAssertThrows() {
        rewriteRun(
          spec-> spec.parser(JavaParser.fromJavaVersion().classpath("junit")),
          //language=java
          java(
            """
            import static org.junit.jupiter.api.Assertions.assertThrows;

            class Test {
                void test() {
                    assertThrows(IllegalArgumentException.class, () -> {
                        foo();
                    });
                }
                void foo() {
                    throw new IllegalArgumentException("boom");
                }
            }
            """,
            """
            import static org.junit.jupiter.api.Assertions.assertThrows;

            class Test {
                void test() {
                    assertThrows(IllegalArgumentException.class, () ->
                        foo());
                }
                void foo() {
                    throw new IllegalArgumentException("boom");
                }
            }
            """
          )
        );
    }

    @Nested
    class NoChange {
        @Issue("https://github.com/openrewrite/rewrite-static-analysis/issues/162")
        @Test
        void noChangeIfLambdaBlockWithAmbiguousMethod() {
            //language=java
            rewriteRun(
              java(
                """
                  import java.util.function.Function;
                  import java.util.function.Consumer;
                  class A {
                      void aMethod(Consumer<Integer> consumer) {}
                      void aMethod(Function<Integer,String> function) {}
                  }
                  """
              ),
              java(
                """
                  class Test {
                      void doTest() {
                          A a = new A();
                          a.aMethod(value -> {
                            return value.toString();
                          });
                      }
                  }
                  """
              )
            );
        }

        @Test
        void noChangeIfLambdaBlockWithOverloadInheritedFromSupertype() {
            //language=java
            rewriteRun(
              java(
                """
                  import java.util.function.Consumer;
                  import java.util.function.Function;
                  class A {
                      void aMethod(Consumer<Integer> consumer) {}
                  }
                  class B extends A {
                      void aMethod(Function<Integer, String> function) {}
                  }
                  """
              ),
              java(
                """
                  class Test {
                      void doTest(B b) {
                          b.aMethod(value -> {
                              return value.toString();
                          });
                      }
                  }
                  """
              )
            );
        }

        @Test
        void noChangeIfLambdaBlockWithVarargsOverload() {
            //language=java
            rewriteRun(
              java(
                """
                  import java.util.concurrent.Callable;
                  class A {
                      void aMethod(Callable<String> callable) {}
                      void aMethod(Runnable runnable, String... strings) {}
                  }
                  """
              ),
              java(
                """
                  class Test {
                      String value() {
                          return "";
                      }
                      void doTest(A a) {
                          a.aMethod(() -> {
                              value();
                          });
                      }
                  }
                  """
              )
            );
        }

        @Test
        void noChangeIfLambdaBlockPassedToAmbiguousConstructor() {
            //language=java
            rewriteRun(
              java(
                """
                  import java.util.function.Consumer;
                  import java.util.function.Function;
                  class A {
                      A(Consumer<Integer> consumer) {}
                      A(Function<Integer, String> function) {}
                  }
                  """
              ),
              java(
                """
                  class Test {
                      Object doTest(boolean b) {
                          return new A(b ? value -> {
                              return value.toString();
                          } : null) {};
                      }
                  }
                  """
              )
            );
        }

        @Test
        void noChangeIfLambdaBlockYieldedToAmbiguousMethod() {
            //language=java
            rewriteRun(
              java(
                """
                  import java.util.function.Consumer;
                  import java.util.function.Function;
                  class A {
                      void aMethod(Consumer<Integer> consumer) {}
                      void aMethod(Function<Integer, String> function) {}
                  }
                  """
              ),
              java(
                """
                  class Test {
                      void doTest(A a, int k) {
                          a.aMethod(switch (k) {
                              default -> value -> {
                                  value.toString();
                              };
                          });
                      }
                  }
                  """
              )
            );
        }

        @SuppressWarnings("CodeBlock2Expr")
        @Test
        void doNotChangeLambdaWithEmptyReturn() {
            rewriteRun(
              //language=java
              java(
                """
                  class Test {
                      Runnable r = () -> { return; };
                  }
                  """
              )
            );
        }
    }
}
