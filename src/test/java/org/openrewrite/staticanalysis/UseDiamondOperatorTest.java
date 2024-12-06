/*
 * Copyright 2022 the original author or authors.
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

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.openrewrite.DocumentExample;
import org.openrewrite.Issue;
import org.openrewrite.java.JavaParser;
import org.openrewrite.test.RecipeSpec;
import org.openrewrite.test.RewriteTest;

import static org.openrewrite.java.Assertions.java;
import static org.openrewrite.java.Assertions.javaVersion;
import static org.openrewrite.kotlin.Assertions.kotlin;

@SuppressWarnings({"Convert2Diamond", "unchecked", "rawtypes"})
class UseDiamondOperatorTest implements RewriteTest {

    @Override
    public void defaults(RecipeSpec spec) {
        spec.recipe(new UseDiamondOperator());
    }

    @DocumentExample
    @Test
    void useDiamondOperator() {
        rewriteRun(
          //language=java
          java(
            """
              import java.util.*;

              class Test<X, Y> {
                  void test() {
                      List<String> ls = new ArrayList<String>();
                      Map<X,Y> map = new HashMap<X,Y>();
                      List<String> ls2 = new ArrayList<String>() {
                      };
                  }
              }
              """,
            """
              import java.util.*;

              class Test<X, Y> {
                  void test() {
                      List<String> ls = new ArrayList<>();
                      Map<X,Y> map = new HashMap<>();
                      List<String> ls2 = new ArrayList<String>() {
                      };
                  }
              }
              """
          )
        );
    }

    @Test
    void varArgIsParameterizedNewClass() {
        rewriteRun(
          //language=java
          java(
            """
              import java.util.*;

              class Foo {
                  void something(List<Integer>... lists) {}
                  void somethingElse(Object[] o, List<Integer> s){}
                  void doSomething() {
                      something(new ArrayList<Integer>(), new ArrayList<Integer>());
                      something(new ArrayList<Integer>());
                      somethingElse(new String[0], new ArrayList<Integer>());
                  }
              }
              """,
            """
              import java.util.*;

              class Foo {
                  void something(List<Integer>... lists) {}
                  void somethingElse(Object[] o, List<Integer> s){}
                  void doSomething() {
                      something(new ArrayList<>(), new ArrayList<>());
                      something(new ArrayList<>());
                      somethingElse(new String[0], new ArrayList<>());
                  }
              }
              """
          )
        );
    }

    @Test
    void useDiamondOperatorTest2() {
        rewriteRun(
          //language=java
          java(
            """
              import java.util.ArrayList;
              import java.util.HashMap;
              import java.util.function.Predicate;
              import java.util.List;
              import java.util.Map;

              class Foo<T> {
                  Map<String, Integer> map;
                  Map unknownMap;
                  public Foo(Predicate<T> p) {}
                  public void something(Foo<List<String>> foos){}
                  public void somethingEasy(List<List<String>> l){}

                  Foo getFoo() {
                      // variable type initializer
                      Foo<List<String>> f = new Foo<List<String>>(it -> it.stream().anyMatch(baz -> true));
                      // assignment
                      map = new HashMap<String, Integer>();
                      unknownMap = new HashMap<String, Integer>();
                      // method argument type assignment
                      something(new Foo<List<String>>(it -> it.stream().anyMatch(b -> true)));
                      somethingEasy(new ArrayList<List<String>>());
                      // return type and assignment type unknown
                      Object o = new Foo<List<String>>(it -> it.stream().anyMatch(baz -> true));
                      // return type unknown
                      return new Foo<List<String>>(it -> it.stream().anyMatch(baz -> true));
                  }

                  Foo<List<String>> getFoo2() {
                      // return type expression
                      return new Foo<List<String>>(it -> it.stream().anyMatch(baz -> true));
                  }
              }
              """,
            """
              import java.util.ArrayList;
              import java.util.HashMap;
              import java.util.function.Predicate;
              import java.util.List;
              import java.util.Map;

              class Foo<T> {
                  Map<String, Integer> map;
                  Map unknownMap;
                  public Foo(Predicate<T> p) {}
                  public void something(Foo<List<String>> foos){}
                  public void somethingEasy(List<List<String>> l){}

                  Foo getFoo() {
                      // variable type initializer
                      Foo<List<String>> f = new Foo<>(it -> it.stream().anyMatch(baz -> true));
                      // assignment
                      map = new HashMap<>();
                      unknownMap = new HashMap<String, Integer>();
                      // method argument type assignment
                      something(new Foo<>(it -> it.stream().anyMatch(b -> true)));
                      somethingEasy(new ArrayList<>());
                      // return type and assignment type unknown
                      Object o = new Foo<List<String>>(it -> it.stream().anyMatch(baz -> true));
                      // return type unknown
                      return new Foo<List<String>>(it -> it.stream().anyMatch(baz -> true));
                  }

                  Foo<List<String>> getFoo2() {
                      // return type expression
                      return new Foo<>(it -> it.stream().anyMatch(baz -> true));
                  }
              }
              """
          )
        );

    }

    @Issue("https://github.com/openrewrite/rewrite/issues/2274")
    @Test
    void returnTypeParamsDoNotMatchNewClassParams() {
        rewriteRun(
          //language=java
          java(
            """
              import java.util.List;
              import java.util.function.Predicate;

              class Test {
                  interface MyInterface<T> { }
                  class MyClass<S, T> implements MyInterface<T>{
                      public MyClass(Predicate<S> p, T check) {}
                  }

                  public MyInterface<Integer> a() {
                      return new MyClass<List<String>, Integer>(l -> l.stream().anyMatch(String::isEmpty), 0);
                  }
                  public MyClass<List<String>, Integer> b() {
                      return new MyClass<List<String>, Integer>(l -> l.stream().anyMatch(String::isEmpty), 0);
                  }
              }
              """,
            """
              import java.util.List;
              import java.util.function.Predicate;

              class Test {
                  interface MyInterface<T> { }
                  class MyClass<S, T> implements MyInterface<T>{
                      public MyClass(Predicate<S> p, T check) {}
                  }

                  public MyInterface<Integer> a() {
                      return new MyClass<List<String>, Integer>(l -> l.stream().anyMatch(String::isEmpty), 0);
                  }
                  public MyClass<List<String>, Integer> b() {
                      return new MyClass<>(l -> l.stream().anyMatch(String::isEmpty), 0);
                  }
              }
              """
          )
        );
    }

    @Issue("https://github.com/openrewrite/rewrite/issues/1297")
    @Test
    void doNotUseDiamondOperatorsForVariablesHavingNullOrUnknownTypes() {
        rewriteRun(
          //language=java
          java(
            """
              import lombok.val;
              import java.util.ArrayList;

              class Test<X, Y> {
                  void test() {
                      var ls1 = new ArrayList<String>();
                      List<String> ls2 = new ArrayList<String>();
                  }
              }
              """,
            """
              import lombok.val;
              import java.util.ArrayList;

              class Test<X, Y> {
                  void test() {
                      var ls1 = new ArrayList<String>();
                      List<String> ls2 = new ArrayList<>();
                  }
              }
              """
          )
        );
    }

    @Test
    void noLeftSide() {
        rewriteRun(
          //language=java
          java(
            """
              import java.util.HashMap;
              class Test {
                  static {
                      new HashMap<String, String>();
                  }
              }
              """
          )
        );
    }

    @Test
    void notAsAChainedMethodInvocation() {
        rewriteRun(
          //language=java
          java(
            """
              class Test {
                  public static ResponseBuilder<String> bResponseEntity(String entity) {
                      return new ResponseBuilder<String>().entity(entity);
                  }
                  public static ResponseBuilder<String> bResponse(String entity) {
                      return new ResponseBuilder<String>();
                  }

                  public static class ResponseBuilder<T> {
                      public ResponseBuilder<T> entity(T entity) {
                          return this;
                      }
                  }
              }
              """,
            """
              class Test {
                  public static ResponseBuilder<String> bResponseEntity(String entity) {
                      return new ResponseBuilder<String>().entity(entity);
                  }
                  public static ResponseBuilder<String> bResponse(String entity) {
                      return new ResponseBuilder<>();
                  }

                  public static class ResponseBuilder<T> {
                      public ResponseBuilder<T> entity(T entity) {
                          return this;
                      }
                  }
              }
              """
          )
        );
    }

    @Test
    void doNotConvertVar() {
        rewriteRun(
          //language=java
          java(
            """
              import java.util.*;
              class Test {
                  void test() {
                      var ls1 = new ArrayList<String>();
                      List<String> ls2 = new ArrayList<String>();
                  }
              }
              """,
            """
              import java.util.*;
              class Test {
                  void test() {
                      var ls1 = new ArrayList<String>();
                      List<String> ls2 = new ArrayList<>();
                  }
              }
              """
          )
        );
    }

    @Test
    void doNotChangeIfAsParam() {
        rewriteRun(
          //language=java
          java(
            """
              import java.util.function.BiFunction;
              import java.util.function.Consumer;

              public class Observable<T> {

                  public static <T> Observable<T> from(Iterable<? extends T> iterable) {
                      return new Observable();
                  }

                  public final <R> Observable<R> reduce(R initialValue, BiFunction<R, ? super T, R> accumulator) {
                      return new Observable();
                  }

                  void subscribe(Consumer<? super T> onNext) {
                  }
              }
              """
          ),
          //language=java
          java(
            """
              import java.util.ArrayList;
              import java.util.List;

              class A {
                  void print(List<String> strings) {
                  }

                  void method() {
                      List<String> regions = new ArrayList<>();
                      Observable.from(regions)
                          .reduce(new ArrayList<String>(), (list, t) -> list)
                          .subscribe(result -> print(result)
                      );
                  }
              }
              """
          )
        );
    }

    @Test
    void anonymousNewClassJava9Plus() {
        rewriteRun(
          spec -> spec.allSources(s -> s.markers(javaVersion(11))),
          //language=java
          java(
            """
              import java.util.*;

              class Foo {
                  List<String> l = new ArrayList<String>() {};
              }
              """,
            """
              import java.util.*;

              class Foo {
                  List<String> l = new ArrayList<>() {};
              }
              """
          )
        );
    }

    @Test
    void anonymousNewClassInferTypesJava9Plus() {
        rewriteRun(
          spec -> spec.allSources(s -> s.markers(javaVersion(11))),
          //language=java
          java(
            """
              interface Serializer<T> {
                  byte[] serialize(T t);
              }

              public class Printer {
                  public static void setSerializerGenericType(Serializer<?> serializer) {}
                  public static void setSerializerConcreteType(Serializer<Integer> serializer) {}
              }
              """
          ),
          //language=java
          java(
            """
              class Test {
                  void method() {
                      // Generic type, no infer type, can NOT use diamond operator
                      Printer.setSerializerGenericType(new Serializer<Integer>() {
                          @Override
                          public byte[] serialize(Integer integer) {
                              return new byte[0];
                          }
                      });

                      // Concrete type, OK to use diamond operator
                      Printer.setSerializerConcreteType(new Serializer<Integer>() {
                          @Override
                          public byte[] serialize(Integer integer) {
                              return new byte[0];
                          }
                      });
                  }
              }
              """,
            """
              class Test {
                  void method() {
                      // Generic type, no infer type, can NOT use diamond operator
                      Printer.setSerializerGenericType(new Serializer<Integer>() {
                          @Override
                          public byte[] serialize(Integer integer) {
                              return new byte[0];
                          }
                      });

                      // Concrete type, OK to use diamond operator
                      Printer.setSerializerConcreteType(new Serializer<>() {
                          @Override
                          public byte[] serialize(Integer integer) {
                              return new byte[0];
                          }
                      });
                  }
              }
              """
          )
        );
    }

    @Nested
    class kotlinTest {
        @Test
        void doNotChange() {
            rewriteRun(
              //language=kotlin
              kotlin(
                """
                  class test {
                      fun method() {
                         val foo = listOf<String>()
                         var schemaPaths = mutableListOf<Any>("a")
                         var typeMapping = mutableMapOf<String, String>()
                      }
                  }
                  """
              )
            );
        }
    }

    @Test
    void doNotChangeInferredGenericTypes() {
        rewriteRun(
          spec -> spec.allSources(s -> s.markers(javaVersion(9))),
          //language=java
          java(
                """
            @FunctionalInterface
            public interface IVisitor<T, R> {
                void visit(T object, R ret);
            }
            """
          ),
          //language=java
          java(
                """
            class Test {
                public <S, R> R method(IVisitor<S, R> visitor) {
                    return null;
                }
                private void test(Object t) {
                    String s = method(new IVisitor<Integer, String>() {
                        @Override
                        public void visit(Integer object, String ret) { }
                    });
                }
            }
            """
          )
        );
    }

    @Test
    void doNotChangeAnnotatedTypeParameters() {
        rewriteRun(
          spec -> spec
            .allSources(s -> s.markers(javaVersion(9)))
            .parser(JavaParser.fromJavaVersion().classpath("annotations")),
          //language=java
          java(
                """
            import org.jetbrains.annotations.Nullable;
            import java.util.ArrayList;
            import java.util.List;

            class Test {
                private void test(Object t) {
                    List<List<String>> l = new ArrayList<List<@Nullable String>>();
                }
            }
            """
          )
        );
    }

}
