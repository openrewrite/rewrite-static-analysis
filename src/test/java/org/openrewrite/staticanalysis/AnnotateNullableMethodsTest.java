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
import org.openrewrite.java.JavaParser;
import org.openrewrite.test.RecipeSpec;
import org.openrewrite.test.RewriteTest;

import static org.openrewrite.java.Assertions.java;

class AnnotateNullableMethodsTest implements RewriteTest {

    @Override
    public void defaults(RecipeSpec spec) {
        spec
          .recipe(new AnnotateNullableMethods())
          .parser(JavaParser.fromJavaVersion().classpath("jspecify"));
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
}
