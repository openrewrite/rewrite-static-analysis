/*
 * Copyright 2024 the original author or authors.
 * <p>
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * <p>
 * https://www.apache.org/licenses/LICENSE-2.0
 * <p>
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.openrewrite.staticanalysis;

import org.junit.jupiter.api.Test;
import org.openrewrite.java.JavaParser;
import org.openrewrite.test.RecipeSpec;
import org.openrewrite.test.RewriteTest;

import static org.openrewrite.java.Assertions.java;
import static org.openrewrite.java.Assertions.javaVersion;

class AnnotateNullableMethodsTest implements RewriteTest {
    @Override
    public void defaults(RecipeSpec spec) {
        spec.recipe(new AnnotateNullableMethods()).parser(JavaParser.fromJavaVersion()
            .classpath("jspecify"))
          .allSources(sourceSpec -> sourceSpec.markers(javaVersion(17)));
    }

    @Test
    void methodReturnsNullLiteral() {
        rewriteRun(
          spec -> spec.recipe(new AnnotateNullableMethods()),
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
                  @Nullable
                  public String getString() {
                      return null;
                  }
              
                  @Nullable
                  public String getStringWithMultipleReturn() {
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
          spec -> spec.recipe(new AnnotateNullableMethods()),
          //language=java
          java("""
            import org.jspecify.annotations.Nullable;
            
            public class Test {
                @Nullable
                public String getString() {
                    return null;
                }
            
                @Nullable
                public String getStringWithMultipleReturn() {
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
          spec -> spec.recipe(new AnnotateNullableMethods()),
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
          spec -> spec.recipe(new AnnotateNullableMethods()),
          //language=java
          java(
            """
              import java.util.HashMap;
              import java.util.Map;
              
              public class Test {
                  public String getString() {
                      Map<String, String> map = new HashMap<>();
                      return map.get("key");
                  }
              }
              """,
            """
              import org.jspecify.annotations.Nullable;
              
              import java.util.HashMap;
              import java.util.Map;
              
              public class Test {
                  @Nullable
                  public String getString() {
                      Map<String, String> map = new HashMap<>();
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
          spec -> spec.recipe(new AnnotateNullableMethods()),
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
}
