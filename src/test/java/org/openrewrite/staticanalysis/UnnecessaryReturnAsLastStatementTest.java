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
import org.openrewrite.DocumentExample;
import org.openrewrite.test.RecipeSpec;
import org.openrewrite.test.RewriteTest;

import static org.openrewrite.java.Assertions.java;

class UnnecessaryReturnAsLastStatementTest implements RewriteTest {
    @Override
    public void defaults(RecipeSpec spec) {
        spec.recipe(new UnnecessaryReturnAsLastStatement());
    }

    @Test
    @DocumentExample
    void simpleReturn() {
        //language=java
        rewriteRun(
          java(
            """
              class Hello {
                  void world() {
                      System.out.println("Hello world");
                      return;
                  }
              }
              """,
            """
              class Hello {
                  void world() {
                      System.out.println("Hello world");
                  }
              }
              """
          )
        );
    }

    @Test
    void ifBranches() {
        //language=java
        rewriteRun(
          java(
            """
              class Hello {
                  void world(int i) {
                      if (i > 0) {
                        System.out.println("Positive");
                        return;
                      } else {
                        System.out.println("Zero or negative");
                        return;
                      }
                  }
              }
              """,
            """
              class Hello {
                  void world(int i) {
                      if (i > 0) {
                        System.out.println("Positive");
                      } else {
                        System.out.println("Zero or negative");
                      }
                  }
              }
              """
          )
        );
    }

    @Test
    void ifWithoutElse() {
        //language=java
        rewriteRun(
          java(
            """
              class Hello {
                  void world(int i) {
                      if (i > 0) {
                        System.out.println("Positive");
                        return;
                      }
                  }
              }
              """,
            """
              class Hello {
                  void world(int i) {
                      if (i > 0) {
                        System.out.println("Positive");
                      }
                  }
              }
              """
          )
        );
    }

    @Test
    void ifElseIf() {
        //language=java
        rewriteRun(
          java(
            """
              class Hello {
                  void world(int i) {
                      if (i > 0) {
                        System.out.println("Positive");
                        return;
                      } else if (i == 0) {
                        System.out.println("Zero");
                        return;
                      } else {
                        System.out.println("Negative");
                        return;
                      }
                  }
              }
              """,
            """
              class Hello {
                  void world(int i) {
                      if (i > 0) {
                        System.out.println("Positive");
                      } else if (i == 0) {
                        System.out.println("Zero");
                      } else {
                        System.out.println("Negative");
                      }
                  }
              }
              """
          )
        );
    }

    @Test
    void ifIsNotTheLast() {
        //language=java
        rewriteRun(
          java(
            """
              class Hello {
                  void world(int i) {
                      if (i > 0) {
                        System.out.println("Positive");
                        return;
                      } else {
                        System.out.println("Zero or negative");
                      }
                      System.out.println("Some extra logic");
                  }
              }
              """
          )
        );
    }

    @Test
    void elseWithJustAReturnStatement() {
        //language=java
        rewriteRun(
          java(
            """
              class Hello {
                  void world(int i) {
                      if (i > 0) {
                        System.out.println("Positive");
                        return;
                      } else return;
                  }
              }
              """,
            """
              class Hello {
                  void world(int i) {
                      if (i > 0) {
                        System.out.println("Positive");
                      } else return;
                  }
              }
              """
          )
        );
    }

    @Test
    void ifThenBeingJustAReturnStatement() {
        //language=java
        rewriteRun(
          java(
            """
              class Hello {
                  void world(int i) {
                      if (i == 436) return; else {
                        System.out.println("I don't like it");
                        return;
                      }
                  }
              }
              """,
            """
              class Hello {
                  void world(int i) {
                      if (i == 436) return; else {
                        System.out.println("I don't like it");
                      }
                  }
              }
              """
          )
        );
    }

    @Test
    void notChangingNonVoidMethods() {
        //language=java
        rewriteRun(
          java(
            """
              class Hello {
                  int world(int i) {
                      return i + 436;
                  }
              }
              """
          )
        );
    }

    @Test
    void notChangingLambdas() {
        //language=java
        rewriteRun(
          java(
            """
              class Hello {
                java.util.function.Consumer<Integer> c = i -> {
                    return;
                };
              }
              """
          )
        );
    }

    @Test
    void notChangingLoops() {
        //language=java
        rewriteRun(
          java(
            """
              class Main {
                public static void main(String[] argv) {
                  while (true) {
                      return;
                  }
                }
              }
              """
          )
        );
    }

    @Test
    void newClass() {
        //language=java
        rewriteRun(
          java(
            """
              import java.util.concurrent.Callable;
              class Hello {
                      Callable<String> callable = new Callable<>() {
                          @Override
                          public String call() throws Exception {
                              otherMethod();
                              return "success";
                          }
                          private void otherMethod() {
                              return;
                          }
                      };
              }
              """,
            """
              import java.util.concurrent.Callable;
              class Hello {
                      Callable<String> callable = new Callable<>() {
                          @Override
                          public String call() throws Exception {
                              otherMethod();
                              return "success";
                          }
                          private void otherMethod() {
                          }
                      };
              }
              """
          )
        );
    }
}
