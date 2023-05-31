/*
 * Copyright 2021 the original author or authors.
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

import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.openrewrite.DocumentExample;
import org.openrewrite.Issue;
import org.openrewrite.test.RecipeSpec;
import org.openrewrite.test.RewriteTest;

import static org.openrewrite.java.Assertions.java;

@SuppressWarnings({"Convert2Lambda", "Anonymous2MethodRef", "CodeBlock2Expr", "WriteOnlyObject", "Convert2Diamond"})
class UseLambdaForFunctionalInterfaceTest implements RewriteTest {
    @Override
    public void defaults(RecipeSpec spec) {
        spec.recipe(new UseLambdaForFunctionalInterface());
    }

    @SuppressWarnings({"Convert2Lambda", "TrivialFunctionalExpressionUsage"})
    @Test
    void usedAsStatementWithNonInferrableType() {
        rewriteRun(
          spec -> spec.recipe(new UseLambdaForFunctionalInterface()),
          //language=java
          java(
            """
              import java.util.function.Consumer;
              class Test {
                  public void test(int n) {
                      new Consumer<Integer>() {
                          public void accept(Integer n2) {
                          }
                      }.accept(n);
                  }
              }
              """
          )
        );
    }

    @Disabled("The recipe currently avoids simplifying anonymous classes that use the this keyword.")
    @Test
    void useLambdaThenSimplifyFurther() {
        rewriteRun(
          spec -> spec.recipes(
            new UseLambdaForFunctionalInterface(),
            new ReplaceLambdaWithMethodReference()
          ),
          //language=java
          java(
            """
              class Test {
                  Runnable r = new Runnable() {
                      @Override
                      public void run() {
                          Test.this.execute();
                      }
                  };
                  
                  void execute() {}
              }
              """,
            """
              class Test {
                  Runnable r = Test.this::execute;
                  
                  void execute() {}
              }
              """
          )
        );
    }

    @DocumentExample
    @Test
    void useLambda() {
        rewriteRun(
          //language=java
          java(
            """
              import java.util.function.Function;
              class Test {
                  Function<Integer, Integer> f = new Function<Integer, Integer>() {
                      @Override
                      public Integer apply(Integer n) {
                          return n + 1;
                      }
                  };
              }
              """,
            """
              import java.util.function.Function;
              class Test {
                  Function<Integer, Integer> f = n -> n + 1;
              }
              """
          )
        );
    }

    @Test
    void useLambdaNoParameters() {
        rewriteRun(
          //language=java
          java(
            """
              import java.util.function.Supplier;
              class Test {
                  Supplier<Integer> s = new Supplier<Integer>() {
                      @Override
                      public Integer get() {
                          return 1;
                      }
                  };
              }
              """,
            """
              import java.util.function.Supplier;
              class Test {
                  Supplier<Integer> s = () -> 1;
              }
              """
          )
        );
    }

    @SuppressWarnings("UnusedAssignment")
    @Test
    void emptyLambda() {
        rewriteRun(
          //language=java
          java(
            """
              import java.util.function.Consumer;
                            
              class Test {
                  void foo() {
                      Consumer<Integer> s;
                      s = new Consumer<Integer>() {
                          @Override
                          public void accept(Integer i) {
                          }
                      };
                  }
              }
              """,
            """
              import java.util.function.Consumer;
                            
              class Test {
                  void foo() {
                      Consumer<Integer> s;
                      s = i -> {
                      };
                  }
              }
              """
          )
        );
    }

    @Issue("https://github.com/openrewrite/rewrite/issues/1828")
    @Test
    void nestedLambdaInMethodArgument() {
        rewriteRun(
          //language=java
          java(
            """
              import java.util.function.Consumer;
                            
              class Test {
                  void bar(Consumer<Integer> c) {
                  }
                  void foo() {
                      bar(new Consumer<Integer>() {
                          @Override
                          public void accept(Integer i) {
                              bar(new Consumer<Integer>() {
                                  @Override
                                  public void accept(Integer i2) {
                                  }
                              });
                          }
                      });
                  }
              }
              """,
            """
              import java.util.function.Consumer;
                            
              class Test {
                  void bar(Consumer<Integer> c) {
                  }
                  void foo() {
                      bar(i -> {
                          bar(i2 -> {
                          });
                      });
                  }
              }
              """
          )
        );
    }

    @Test
    void dontUseLambdaWhenThis() {
        rewriteRun(
          //language=java
          java(
            """
              import java.util.function.Function;
              class Test {
                  int n;
                  Function<Integer, Integer> f = new Function<Integer, Integer>() {
                      @Override
                      public Integer apply(Integer n) {
                          return this.n;
                      }
                  };
              }
              """
          )
        );
    }

    @SuppressWarnings("UnnecessaryLocalVariable")
    @Test
    void dontUseLambdaWhenShadowsLocalVariable() {
        rewriteRun(
          //language=java
          java(
            """
              import java.util.function.Supplier;
              class Test {
                  void test() {
                      int n = 1;
                      Supplier<Integer> f = new Supplier<Integer>() {
                          @Override
                          public Integer get() {
                              int n = 0;
                              return n;
                          }
                      };
                  }
              }
              """
          )
        );
    }

    @SuppressWarnings("UnnecessaryLocalVariable")
    @Issue("https://github.com/openrewrite/rewrite/issues/1915")
    @Test
    void dontUseLambdaWhenShadowsClassField() {
        rewriteRun(
          //language=java
          java(
            """
              import java.util.function.Supplier;
              class Test {
                  int n = 1;
                  void test() {
                      Supplier<Integer> f = new Supplier<Integer>() {
                          @Override
                          public Integer get() {
                              int n = 0;
                              return n;
                          }
                      };
                  }
              }
              """
          )
        );
    }

    @SuppressWarnings("UnnecessaryLocalVariable")
    @Issue("https://github.com/openrewrite/rewrite/issues/1915")
    @Test
    void dontUseLambdaWhenShadowsMethodDeclarationParam() {
        rewriteRun(
          //language=java
          java(
            """
              import java.util.function.Supplier;
              class Test {
                  void test(int n) {
                      Supplier<Integer> f = new Supplier<Integer>() {
                          @Override
                          public Integer get() {
                              int n = 0;
                              return n;
                          }
                      };
                  }
              }
              """
          )
        );
    }

    @Test
    void finalParameters() {
        rewriteRun(
          //language=java
          java(
            """
              import java.util.function.Function;
              class Test {
                  Function<Integer, Integer> f = new Function<Integer, Integer>() {
                      @Override
                      public Integer apply(final Integer n) {
                          return n + 1;
                      }
                  };
              }
              """,
            """
              import java.util.function.Function;
              class Test {
                  Function<Integer, Integer> f = n -> n + 1;
              }
              """
          )
        );
    }

    @Test
    void useLambdaThenRemoveUnusedImports() {
        rewriteRun(
          //language=java
          java(
            """
              import java.util.HashMap;
              import java.util.function.Function;

              public class Temp {
                  public static void foo(){
                      new HashMap<Integer, String>().computeIfAbsent(3, new Function<Integer, String>() {
                          @Override
                          public String apply(Integer integer) {
                              return String.valueOf(integer + 1);
                          }
                      });
                  }
              }
              """,
            """
              import java.util.HashMap;

              public class Temp {
                  public static void foo(){
                      new HashMap<Integer, String>().computeIfAbsent(3, integer -> String.valueOf(integer + 1));
                  }
              }
              """
          )
        );
    }

    @Test
    @SuppressWarnings("DataFlowIssue")
    void noReplaceOnReferenceToUninitializedFinalField() {
        rewriteRun(
          //language=java
          java(
            """
              import java.util.function.Supplier;

              public class Temp {
                  final Supplier<Integer> supplier;
                  final Supplier<Integer> supplier1 = new Supplier<>() {
                      @Override
                      public Integer get() {
                          return supplier.get();
                      }
                  };
                  public Temp() {
                      supplier = null;
                  }
              }
              """
          )
        );
    }

    @Test
    void replaceOnReferenceToUninitializedNonFinalField() {
        rewriteRun(
          //language=java
          java(
            """
              import java.util.function.Supplier;

              public class Temp {
                  Supplier<Integer> supplier;
                  final Supplier<Integer> supplier1 = new Supplier<>() {
                      @Override
                      public Integer get() {
                          return supplier.get();
                      }
                  };
                  public Temp() {
                      supplier = null;
                  }
              }
              """,
            """
              import java.util.function.Supplier;

              public class Temp {
                  Supplier<Integer> supplier;
                  final Supplier<Integer> supplier1 = () -> supplier.get();
                  public Temp() {
                      supplier = null;
                  }
              }
              """
          )
        );
    }

    @Test
    void arrayTypes() {
        rewriteRun(
          //language=java
          java(
            """
              class Temp {
                  final TrustStrategy strategy = new TrustStrategy() {
                      @Override
                      public boolean isTrusted(Integer[] var1, String var2) {
                          return true;
                      }
                  };
                  public interface TrustStrategy {
                      boolean isTrusted(Integer[] var1, String var2);
                  }
              }
              """,
            """
              class Temp {
                  final TrustStrategy strategy = (var1, var2) -> true;
                  public interface TrustStrategy {
                      boolean isTrusted(Integer[] var1, String var2);
                  }
              }
              """
          )
        );
    }

    @Test
    void noChangeIfHasShallowVariable() {
        rewriteRun(
          //language=java
          java(
            """
              class A {
                  void run(Runnable task) {}

                  void method() {
                      String name = "foo";
                      for (int i = 0 ; i < 10; i ++) {
                          run(new Runnable() {
                              @Override
                              public void run() {
                                  String name = "bar";
                              }
                          });
                      }
                  }
              }
              """
          )
        );
    }

    @Test
    void outOfNameScopesShallowVariable() {
        rewriteRun(
          //language=java
          java(
            """
              class A {
                  void run(Runnable task) {}

                  void method() {
                      {
                          String name = "foo";
                      }
                      for (int i = 0 ; i < 10; i ++) {
                          run(new Runnable() {
                              @Override
                              public void run() {
                                  String name = "bar";
                              }
                          });
                      }
                  }
              }
              """,
            """
              class A {
                  void run(Runnable task) {}

                  void method() {
                      {
                          String name = "foo";
                      }
                      for (int i = 0 ; i < 10; i ++) {
                          run(() -> {
                              String name = "bar";
                          });
                      }
                  }
              }
              """
          )
        );
    }

    @Test
    void noChangeIfHasShallowVariableForAndWhileLoop() {
        rewriteRun(
          //language=java
          java(
            """
              class A {
                  void run(Runnable task) {}

                  void method() {
                      for (int i = 0 ; i < 10; i ++) {
                          run(new Runnable() {
                              @Override
                              public void run() {
                                  for (int i = 0 ; i < 10; i ++) {
                                  }
                              }
                          });
                      }
                      
                      int j = 0;
                      while (j < 20) {
                          run(new Runnable() {
                              @Override
                              public void run() {
                                  for (int j = 0 ; j < 20; j ++) {
                                  }
                              }
                          });
                      }
                  }
              }
              """
          )
        );
    }
}
