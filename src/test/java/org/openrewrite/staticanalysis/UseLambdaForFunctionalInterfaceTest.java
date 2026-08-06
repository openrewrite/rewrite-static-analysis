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
import org.openrewrite.test.TypeValidation;
import org.openrewrite.test.RewriteTest;

import org.openrewrite.staticanalysis.table.AnonymousFunctionalInterfaceImplementations;

import static org.assertj.core.api.Assertions.assertThat;
import static org.openrewrite.java.Assertions.java;

@SuppressWarnings({"Convert2Lambda", "Anonymous2MethodRef", "WriteOnlyObject", "Convert2Diamond", "Convert2MethodRef", "ResultOfMethodCallIgnored", "StatementWithEmptyBody", "ConstantValue", "NonFinalFieldInEnum", "LoopConditionNotUpdatedInsideLoop"})
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

    @Issue("https://github.com/openrewrite/rewrite-static-analysis/issues/10")
    @SuppressWarnings("removal")
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

    @Issue("https://github.com/openrewrite/rewrite-static-analysis/issues/194")
    @SuppressWarnings("ConstantConditions")
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

    @Issue("https://github.com/openrewrite/rewrite-static-analysis/issues/892")
    @SuppressWarnings("removal")
    @Test
    void diamondOperatorInAnonymousClass() {
        rewriteRun(
          //language=java
          java(
            """
              import java.security.AccessController;
              import java.security.PrivilegedAction;

              class Test {
                  void test() {
                      AccessController.doPrivileged(new PrivilegedAction<>() {
                          @Override public Integer run() {
                              return 0;
                          }
                      });
                  }
              }
              """,
            """
              import java.security.AccessController;
              import java.security.PrivilegedAction;

              class Test {
                  void test() {
                      AccessController.doPrivileged((PrivilegedAction<Object>) () -> 0);
                  }
              }
              """
          )
        );
    }

    @Issue("https://github.com/openrewrite/rewrite-static-analysis/issues/892")
    @Test
    void diamondOperatorWithMultipleTypeParameters() {
        rewriteRun(
          //language=java
          java(
            """
              class Test {
                  interface A<T, R> { R run(T t); }
                  interface B<T, R> { R run(T t); }
                  static <T, R> R x(A<T, R> a, T arg) { return a.run(arg); }
                  static <T, R> R x(B<T, R> b, T arg) { return b.run(arg); }
                  void test() {
                      x(new A<>() {
                          @Override public String run(Integer t) {
                              return t.toString();
                          }
                      }, 1);
                  }
              }
              """,
            """
              class Test {
                  interface A<T, R> { R run(T t); }
                  interface B<T, R> { R run(T t); }
                  static <T, R> R x(A<T, R> a, T arg) { return a.run(arg); }
                  static <T, R> R x(B<T, R> b, T arg) { return b.run(arg); }
                  void test() {
                      x((A<Integer, Object>) t -> t.toString(), 1);
                  }
              }
              """
          )
        );
    }

    @Issue("https://github.com/openrewrite/rewrite-static-analysis/issues/895")
    @Test
    void varLocalVariableReplacedWithInterfaceType() {
        rewriteRun(
          //language=java
          java(
            """
              class Test {
                  interface Action {
                      String run(String input);
                  }

                  void execute() {
                      final var action = new Action() {
                          @Override
                          public String run(String input) {
                              return input.toUpperCase();
                          }
                      };
                      action.run("hello");
                  }
              }
              """,
            """
              class Test {
                  interface Action {
                      String run(String input);
                  }

                  void execute() {
                      final Action action = input -> input.toUpperCase();
                      action.run("hello");
                  }
              }
              """
          )
        );
    }

    @Issue("https://github.com/openrewrite/rewrite-static-analysis/issues/895")
    @Test
    void varLocalVariableWithDiamondReplacedWithResolvedType() {
        rewriteRun(
          //language=java
          java(
            """
              import java.util.function.Function;

              class Test {
                  void execute() {
                      var f = new Function<String, Integer>() {
                          @Override
                          public Integer apply(String s) {
                              return s.length();
                          }
                      };
                      f.apply("hello");
                  }
              }
              """,
            """
              import java.util.function.Function;

              class Test {
                  void execute() {
                      Function<String, Integer> f = s -> s.length();
                      f.apply("hello");
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

    @Issue("https://github.com/openrewrite/rewrite/issues/1915")
    @SuppressWarnings("UnnecessaryLocalVariable")
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

    @Issue("https://github.com/openrewrite/rewrite/issues/1915")
    @SuppressWarnings("UnnecessaryLocalVariable")
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

    @Issue("https://github.com/moderneinc/support-app/issues/17")
    @Test
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
                                public Map<String, Integer> get() {
                                    Map<String, Integer> choices = Map.of("id1", 1);
                                    return choices.entrySet().stream()
                                        .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue, (e1, e2) -> e1, LinkedHashMap::new));
                                }
                            });
                      Object o2 = new MapDropdownChoice<String, Integer>(
                            new Supplier<Map<String, Integer>>() {
                                @Override
                                public Map<String, Integer> get() {
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

    @Issue("https://github.com/openrewrite/rewrite-static-analysis/issues/309")
    @Test
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

    @Issue("https://github.com/openrewrite/rewrite-static-analysis/issues/413")
    @Test
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

    @Issue("https://github.com/openrewrite/rewrite-static-analysis/issues/413")
    @Test
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

    @Issue("https://github.com/openrewrite/rewrite-static-analysis/issues/20")
    @Test
    void anonymousClassInsideParameterizedMethodCall() {
        // given / when / then
        rewriteRun(
          //language=java
          java(
            """
              import java.util.function.Supplier;
              import java.util.concurrent.atomic.AtomicInteger;

              class TypeLiteral<T> {
              }

              class Binder {
                  <T> Binding<T> bind(TypeLiteral<T> typeLiteral) {
                      return new Binding<>();
                  }
              }

              class Binding<T> {
                  void toInstance(T instance) {
                  }
              }

              class Test {
                  void test(Binder binder) {
                      final AtomicInteger suffix = new AtomicInteger();
                      binder.bind(new TypeLiteral<Supplier<String>>() {
                      }).toInstance(new Supplier<String>() {
                          @Override
                          public String get() {
                              return suffix.getAndIncrement() + "";
                          }
                      });
                  }
              }
              """,
            """
              import java.util.function.Supplier;
              import java.util.concurrent.atomic.AtomicInteger;

              class TypeLiteral<T> {
              }

              class Binder {
                  <T> Binding<T> bind(TypeLiteral<T> typeLiteral) {
                      return new Binding<>();
                  }
              }

              class Binding<T> {
                  void toInstance(T instance) {
                  }
              }

              class Test {
                  void test(Binder binder) {
                      final AtomicInteger suffix = new AtomicInteger();
                      binder.bind(new TypeLiteral<Supplier<String>>() {
                      }).toInstance(() -> suffix.getAndIncrement() + "");
                  }
              }
              """
          )
        );
    }

    @Issue("https://github.com/openrewrite/rewrite-static-analysis/issues/961")
    @Test
    void dontUseLambdaWhenOverridingDefaultMethod() {
        rewriteRun(
          //language=java
          java(
            """
              import java.util.List;

              interface BusinessRule {
                  default List<String> getConfigurationTexts() {
                      return List.of();
                  }
              }

              class Test {
                  BusinessRule rule = new BusinessRule() {
                      @Override
                      public List<String> getConfigurationTexts() {
                          return List.of("zero", "one", "two");
                      }
                  };
              }
              """
          )
        );
    }

    @Issue("https://github.com/openrewrite/rewrite-static-analysis/issues/961")
    @Test
    void dontUseLambdaWhenOverridingDefaultMethodOfFunctionalInterface() {
        rewriteRun(
          spec -> spec.dataTable(AnonymousFunctionalInterfaceImplementations.Row.class, rows -> {
              assertThat(rows).hasSize(1);
              assertThat(rows.getFirst().getFunctionalInterface()).isEqualTo("BusinessRule");
              assertThat(rows.getFirst().isConvertible()).isFalse();
              assertThat(rows.getFirst().getReason())
                .isEqualTo("overrides a `default` method rather than the abstract method");
          }),
          //language=java
          java(
            """
              import java.util.List;

              interface BusinessRule {
                  void apply();

                  default List<String> getConfigurationTexts() {
                      return List.of();
                  }
              }

              class Test {
                  BusinessRule rule = new BusinessRule() {
                      @Override
                      public List<String> getConfigurationTexts() {
                          return List.of("zero", "one", "two");
                      }
                  };
              }
              """
          )
        );
    }

    @Test
    void dataTableRecordsConvertibleSite() {
        rewriteRun(
          spec -> spec.dataTable(AnonymousFunctionalInterfaceImplementations.Row.class, rows -> {
              assertThat(rows).hasSize(1);
              AnonymousFunctionalInterfaceImplementations.Row row = rows.getFirst();
              assertThat(row.getSourcePath()).isEqualTo("Test.java");
              assertThat(row.getEnclosingClass()).isEqualTo("Test");
              assertThat(row.getFunctionalInterface()).isEqualTo("java.util.function.Function");
              assertThat(row.getMethod()).isEqualTo("apply");
              assertThat(row.isConvertible()).isTrue();
              assertThat(row.getReason()).isEmpty();
          }),
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
    void dataTableRecordsSiteThatCannotBeConverted() {
        rewriteRun(
          spec -> spec.dataTable(AnonymousFunctionalInterfaceImplementations.Row.class, rows -> {
              assertThat(rows).hasSize(1);
              AnonymousFunctionalInterfaceImplementations.Row row = rows.getFirst();
              assertThat(row.getFunctionalInterface()).isEqualTo("java.util.function.Function");
              assertThat(row.isConvertible()).isFalse();
              assertThat(row.getReason()).isEqualTo("references `this`");
          }),
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

    @Test
    void dataTableRecordsAnonymousClassDeclaringMoreThanTheInterfaceMethod() {
        rewriteRun(
          spec -> spec.dataTable(AnonymousFunctionalInterfaceImplementations.Row.class, rows -> {
              assertThat(rows).hasSize(1);
              assertThat(rows.getFirst().isConvertible()).isFalse();
              assertThat(rows.getFirst().getReason()).isEqualTo("declares more than the interface method");
          }),
          //language=java
          java(
            """
              import java.util.function.Supplier;
              class Test {
                  Supplier<Integer> s = new Supplier<Integer>() {
                      private int calls;

                      @Override
                      public Integer get() {
                          return ++calls;
                      }
                  };
              }
              """
          )
        );
    }

    @Test
    void dataTableRecordsSiteInsideEnum() {
        rewriteRun(
          spec -> spec.dataTable(AnonymousFunctionalInterfaceImplementations.Row.class, rows -> {
              assertThat(rows).hasSize(1);
              assertThat(rows.getFirst().isConvertible()).isFalse();
              assertThat(rows.getFirst().getReason()).isEqualTo("declared inside an enum");
          }),
          //language=java
          java(
            """
              import java.util.function.Supplier;
              enum Test {
                  INSTANCE;

                  Supplier<String> s = new Supplier<String>() {
                      @Override
                      public String get() {
                          return "";
                      }
                  };
              }
              """
          )
        );
    }

    @Test
    void dataTableDoesNotDuplicateUnconvertedSitesWhenTheFileChanges() {
        // `Repeat` re-visits the site it left alone on every cycle, so a row emitted inline would
        // appear once per cycle rather than once per site.
        rewriteRun(
          spec -> spec.dataTable(AnonymousFunctionalInterfaceImplementations.Row.class, rows -> {
              assertThat(rows).hasSize(2);
              assertThat(rows).extracting("convertible").containsExactly(true, false);
          }),
          //language=java
          java(
            """
              import java.util.function.Function;
              import java.util.function.Supplier;
              class Test {
                  int n;
                  Function<Integer, Integer> converted = new Function<Integer, Integer>() {
                      @Override
                      public Integer apply(Integer i) {
                          return i + 1;
                      }
                  };
                  Supplier<Integer> untouched = new Supplier<Integer>() {
                      @Override
                      public Integer get() {
                          return this.n;
                      }
                  };
              }
              """,
            """
              import java.util.function.Function;
              import java.util.function.Supplier;
              class Test {
                  int n;
                  Function<Integer, Integer> converted = i -> i + 1;
                  Supplier<Integer> untouched = new Supplier<Integer>() {
                      @Override
                      public Integer get() {
                          return this.n;
                      }
                  };
              }
              """
          )
        );
    }

    @Test
    void dataTableRecordsInterfaceThatAlsoRedeclaresAnObjectMethod() {
        // Comparator declares both `compare` and `equals`; only `compare` counts toward SAM-ness (JLS 9.8).
        rewriteRun(
          spec -> spec.dataTable(AnonymousFunctionalInterfaceImplementations.Row.class, rows -> {
              assertThat(rows).hasSize(1);
              assertThat(rows.getFirst().getFunctionalInterface()).isEqualTo("java.util.Comparator");
              assertThat(rows.getFirst().getMethod()).isEqualTo("compare");
          }),
          //language=java
          java(
            """
              import java.util.Comparator;
              class Test {
                  Comparator<String> c = new Comparator<String>() {
                      @Override
                      public int compare(String a, String b) {
                          return 0;
                      }
                  };
              }
              """
          )
        );
    }

    @Test
    void dataTableRecordsInheritedSingleAbstractMethod() {
        rewriteRun(
          spec -> spec.dataTable(AnonymousFunctionalInterfaceImplementations.Row.class, rows -> {
              assertThat(rows).hasSize(1);
              assertThat(rows.getFirst().getFunctionalInterface()).isEqualTo("Task");
              assertThat(rows.getFirst().getMethod()).isEqualTo("run");
          }),
          //language=java
          java(
            """
              interface Task extends Runnable {
              }
              class Test {
                  Task t = new Task() {
                      @Override
                      public void run() {
                      }
                  };
              }
              """
          )
        );
    }

    @Test
    void unresolvedSupertypeIsRecordedAsUnconvertible() {
        // Missing type attribution is why a type-aware search can go quiet, so the site is reported with
        // the gap as its reason rather than silently dropped.
        rewriteRun(
          spec -> spec.dataTable(AnonymousFunctionalInterfaceImplementations.Row.class, rows -> {
              assertThat(rows).hasSize(1);
              assertThat(rows.getFirst().getFunctionalInterface()).isEqualTo("Matcher");
              assertThat(rows.getFirst().getEnclosingClass()).isEqualTo("Test");
              assertThat(rows.getFirst().getMethod()).isEmpty();
              assertThat(rows.getFirst().isConvertible()).isFalse();
              assertThat(rows.getFirst().getReason()).isEqualTo("the supertype has no type attribution");
          }).typeValidationOptions(TypeValidation.none()),
          //language=java
          java(
            """
              import com.nowhere.Matcher;
              class Test {
                  Object m = new Matcher() {
                      @Override
                      public boolean matches(Object o) {
                          return true;
                      }
                  };
              }
              """
          )
        );
    }

    @Test
    void defaultMethodsDoNotCountTowardsAbstractMethods() {
        // Some type tables record `Abstract` on interface methods but omit `Default` from the others,
        // which made every interface carrying a default method look like it had several abstract ones.
        rewriteRun(
          spec -> spec.dataTable(AnonymousFunctionalInterfaceImplementations.Row.class, rows -> {
              assertThat(rows).hasSize(1);
              assertThat(rows.getFirst().getFunctionalInterface()).isEqualTo("WithDefaults");
              assertThat(rows.getFirst().getMethod()).isEqualTo("act");
          }),
          //language=java
          java(
            """
              interface WithDefaults {
                  void act();
                  default void before() {
                  }
                  default void after() {
                  }
              }
              class Test {
                  WithDefaults w = new WithDefaults() {
                      @Override
                      public void act() {
                      }
                  };
              }
              """,
            """
              interface WithDefaults {
                  void act();
                  default void before() {
                  }
                  default void after() {
                  }
              }
              class Test {
                  WithDefaults w = () -> {
                  };
              }
              """
          )
        );
    }

    @Test
    void markerInterfaceIsRecordedAsUnconvertible() {
        // An interface with no abstract methods is either a genuine marker or one whose methods never made
        // it into the LST; the two are indistinguishable here, so report what was observed.
        rewriteRun(
          spec -> spec.dataTable(AnonymousFunctionalInterfaceImplementations.Row.class, rows -> {
              assertThat(rows).hasSize(1);
              assertThat(rows.getFirst().getFunctionalInterface()).isEqualTo("Marker");
              assertThat(rows.getFirst().isConvertible()).isFalse();
              assertThat(rows.getFirst().getReason())
                .isEqualTo("the interface has no abstract methods recorded in its type attribution");
          }),
          //language=java
          java(
            """
              interface Marker {
              }
              class Test {
                  Marker m = new Marker() {
                      void helper() {
                      }
                  };
              }
              """
          )
        );
    }

    @Test
    void anonymousSubclassOfAbstractClassIsNotReported() {
        // Genuinely out of scope rather than undecidable, so it stays out of the inventory entirely.
        rewriteRun(
          spec -> spec.dataTable(AnonymousFunctionalInterfaceImplementations.Row.class, rows -> {
              assertThat(rows).hasSize(1);
              assertThat(rows.getFirst().getFunctionalInterface()).isEqualTo("java.util.function.Supplier");
          }),
          //language=java
          java(
            """
              import java.util.function.Supplier;

              abstract class Base {
                  abstract void run();
              }
              class Test {
                  Base b = new Base() {
                      @Override
                      void run() {
                      }
                  };
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

              abstract class Base {
                  abstract void run();
              }
              class Test {
                  Base b = new Base() {
                      @Override
                      void run() {
                      }
                  };
                  Supplier<Integer> s = () -> 1;
              }
              """
          )
        );
    }

    @Test
    void interfaceWithTwoAbstractMethodsIsNotReported() {
        rewriteRun(
          spec -> spec.dataTable(AnonymousFunctionalInterfaceImplementations.Row.class, rows -> {
              assertThat(rows).hasSize(1);
              assertThat(rows.getFirst().getFunctionalInterface()).isEqualTo("java.util.function.Supplier");
          }),
          //language=java
          java(
            """
              import java.util.function.Supplier;

              interface TwoMethods {
                  void first();
                  void second();
              }
              class Test {
                  TwoMethods t = new TwoMethods() {
                      @Override
                      public void first() {
                      }

                      @Override
                      public void second() {
                      }
                  };
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

              interface TwoMethods {
                  void first();
                  void second();
              }
              class Test {
                  TwoMethods t = new TwoMethods() {
                      @Override
                      public void first() {
                      }

                      @Override
                      public void second() {
                      }
                  };
                  Supplier<Integer> s = () -> 1;
              }
              """
          )
        );
    }
}
