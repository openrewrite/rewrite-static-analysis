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

import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.openrewrite.DocumentExample;
import org.openrewrite.Issue;
import org.openrewrite.java.JavaParser;
import org.openrewrite.test.RecipeSpec;
import org.openrewrite.test.RewriteTest;

import static org.openrewrite.java.Assertions.java;

@SuppressWarnings({"Convert2Lambda", "Anonymous2MethodRef", "CodeBlock2Expr", "WriteOnlyObject", "Convert2Diamond"})
class UseLambdaForFunctionalInterfaceTest implements RewriteTest {
    @Override
    public void defaults(RecipeSpec spec) {
        spec.recipe(new UseLambdaForFunctionalInterface());
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

    @SuppressWarnings("removal")
    @Issue("https://github.com/openrewrite/rewrite-static-analysis/issues/10")
    @Test
    void castingAmbiguity() {
        rewriteRun(
          spec -> spec.recipe(new UseLambdaForFunctionalInterface()),
          //language=java
          java(
            """
              import java.security.AccessController;
              import java.security.PrivilegedAction;
              import java.security.PrivilegedExceptionAction;

              class Test {
                  void test() {
                      AccessController.doPrivileged(new PrivilegedAction<Integer>() {
                          @Override public Integer run() {
                              return 0;
                          }
                      });
                      AccessController.doPrivileged(new PrivilegedExceptionAction<Integer>() {
                          @Override public Integer run() throws Exception {
                              throw new Exception("i feel privileged to throw a checked exception");
                          }
                      });
                  }
              }
              """,
            """
              import java.security.AccessController;
              import java.security.PrivilegedAction;
              import java.security.PrivilegedExceptionAction;

              class Test {
                  void test() {
                      AccessController.doPrivileged((PrivilegedAction<Integer>) () -> 0);
                      AccessController.doPrivileged((PrivilegedExceptionAction<Integer>) () -> {
                          throw new Exception("i feel privileged to throw a checked exception");
                      });
                  }
              }
              """
          )
        );
    }

    @SuppressWarnings("ConstantConditions")
    @Issue("https://github.com/openrewrite/rewrite-static-analysis/issues/194")
    @Test
    void gson() {
        rewriteRun(
          spec -> spec.recipe(new UseLambdaForFunctionalInterface())
            .parser(JavaParser.fromJavaVersion().classpath("gson")),
          //language=java
          java(
            """
              import com.google.gson.JsonSerializationContext;
              import com.google.gson.GsonBuilder;
              import com.google.gson.JsonElement;
              import com.google.gson.JsonPrimitive;
              import com.google.gson.JsonSerializer;
              import java.time.LocalDateTime;
              import java.lang.reflect.Type;

              class Test {
                  void test() {
                      new GsonBuilder().registerTypeAdapter(LocalDateTime.class, new JsonSerializer<LocalDateTime>() {
                          @Override
                          public JsonElement serialize(LocalDateTime object, Type type, JsonSerializationContext context) {
                              return new JsonPrimitive(object.format(null));
                          }
                      });
                  }
              }
              """,
            """
              import com.google.gson.GsonBuilder;
              import com.google.gson.JsonPrimitive;
              import com.google.gson.JsonSerializer;
              import java.time.LocalDateTime;

              class Test {
                  void test() {
                      new GsonBuilder().registerTypeAdapter(LocalDateTime.class, (JsonSerializer<LocalDateTime>) (object, type, context) -> new JsonPrimitive(object.format(null)));
                  }
              }
              """
          )
        );
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
                      bar(i ->
                              bar(i2 -> {
                              }));
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

    @Test
    @Issue("https://github.com/moderneinc/support-app/issues/17")
    void lambdaWithComplexTypeInference() {
        rewriteRun(
          //language=java
          java(
            """
              import java.util.LinkedHashMap;
              import java.util.Map;
              import java.util.function.Supplier;
              import java.util.stream.Collectors;

              class Test {
                  void method() {
                      Object o = new MapDropdownChoice<String, Integer>(
                            new Supplier<Map<String, Integer>>() {
                                @Override
                                public Map<String, Integer> getObject() {
                                    Map<String, Integer> choices = Map.of("id1", 1);
                                    return choices.entrySet().stream()
                                        .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue, (e1, e2) -> e1, LinkedHashMap::new));
                                }
                            });
                      Object o2 = new MapDropdownChoice<String, Integer>(
                            new Supplier<Map<String, Integer>>() {
                                @Override
                                public Map<String, Integer> getObject() {
                                    Map<String, Integer> choices = Map.of("id1", 2);
                                    return choices.entrySet().stream()
                                        .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));
                                }
                            });
                  }
              }

              class MapDropdownChoice<K, V> {
                  public MapDropdownChoice(Supplier<? extends Map<K, ? extends V>> choiceMap) {
                  }
              }
              """,
            """
              import java.util.LinkedHashMap;
              import java.util.Map;
              import java.util.function.Supplier;
              import java.util.stream.Collectors;

              class Test {
                  void method() {
                      Object o = new MapDropdownChoice<String, Integer>(
                              (Supplier<Map<String, Integer>>) () -> {
                                  Map<String, Integer> choices = Map.of("id1", 1);
                                  return choices.entrySet().stream()
                                          .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue, (e1, e2) -> e1, LinkedHashMap::new));
                              });
                      Object o2 = new MapDropdownChoice<String, Integer>(
                              (Supplier<Map<String, Integer>>) () -> {
                                  Map<String, Integer> choices = Map.of("id1", 2);
                                  return choices.entrySet().stream()
                                          .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));
                              });
                  }
              }

              class MapDropdownChoice<K, V> {
                  public MapDropdownChoice(Supplier<? extends Map<K, ? extends V>> choiceMap) {
                  }
              }
              """
          )
        );
    }

    @Test
    @Issue("https://github.com/openrewrite/rewrite-static-analysis/issues/309")
    void dontUseLambdaForMethodWithTypeParameter() {
        //language=java
        rewriteRun(
          spec -> spec.parser(JavaParser.fromJavaVersion().dependsOn(
            """
              package com.helloworld;

              import java.util.List;

              public interface I {
                <T> List<T> call();
              }
              """
          )),
          java(
            // can't transform to lambda because of the type argument of I#call()
            """
              package com.helloworld;

              import java.util.List;

              class Hello {
                public void hello() {
                  final I i = new I() {
                    @Override
                    public <T> List<T> call() {
                      return null;
                    }
                  };
                  final List<String> list = i.call();
                }
              }
              """
          )
        );
    }

    @Test
    @Issue("https://github.com/openrewrite/rewrite-static-analysis/issues/413")
    void dontUseLambdaWhenEnumAccessesStaticFieldFromConstructor() {
        rewriteRun(
          //language=java
          java(
            """
              import java.time.LocalDate;
              import java.time.format.DateTimeFormatter;
              enum Test {
                  A, B;

                  private static final DateTimeFormatter DATE_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd");

                  Test() {
                      Runnable r = new Runnable() {
                          @Override
                          public void run() {
                              DATE_FORMAT.format(LocalDate.now());
                          }
                      };
                  }
              }
              """
          )
        );
    }

    @Test
    @Issue("https://github.com/openrewrite/rewrite-static-analysis/issues/413")
    void dontUseLambdaWhenEnumAccessesStaticFieldFromFromMethod() {
        rewriteRun(
          //language=java
          java(
            """
              import java.time.LocalDate;
              import java.time.format.DateTimeFormatter;
              enum Test {
                  A, B;

                  private static final DateTimeFormatter DATE_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd");

                  void test() {
                      Runnable r = new Runnable() {
                          @Override
                          public void run() {
                              DATE_FORMAT.format(LocalDate.now());
                          }
                      };
                  }
              }
              """
          )
        );
    }
}
