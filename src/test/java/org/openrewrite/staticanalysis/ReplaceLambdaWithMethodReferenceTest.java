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

import org.junit.jupiter.api.Test;
import org.openrewrite.DocumentExample;
import org.openrewrite.Issue;
import org.openrewrite.java.JavaIsoVisitor;
import org.openrewrite.java.tree.J;
import org.openrewrite.java.tree.TypeUtils;
import org.openrewrite.test.RecipeSpec;
import org.openrewrite.test.RewriteTest;
import org.openrewrite.test.TypeValidation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.openrewrite.java.Assertions.java;

@SuppressWarnings({"unchecked", "RedundantCast", "SimplifyStreamApiCallChains", "Convert2MethodRef", "CodeBlock2Expr", "RedundantOperationOnEmptyContainer", "ResultOfMethodCallIgnored", "rawtypes", "UnusedAssignment", "OptionalGetWithoutIsPresent"})
class ReplaceLambdaWithMethodReferenceTest implements RewriteTest {

    @Override
    public void defaults(RecipeSpec spec) {
        spec.recipe(new ReplaceLambdaWithMethodReference());
    }

    @Test
    void dontSelectCastFromTypeVariable() {
        rewriteRun(
          //language=java
          java(
            """
              import java.util.function.Supplier;
              class Test<T> {
                  Supplier<T> test() {
                        return () -> (T) this;
                  }
              }
              """
          )
        );
    }

    @Issue("https://github.com/openrewrite/rewrite/issues/1926")
    @Test
    void multipleMethodInvocations() {
        rewriteRun(
          //language=java
          java(
            """
              import java.nio.file.Path;
              import java.nio.file.Paths;
              import java.util.List;
              import java.util.stream.Collectors;
                            
              class Test {
                  Path path = Paths.get("");
                  List<String> method(List<String> l) {
                      return l.stream()
                          .filter(s -> path.getFileName().toString().equals(s))
                          .collect(Collectors.toList());
                  }
              }
              """
          )
        );
    }

    @Issue("https://github.com/openrewrite/rewrite-static-analysis/issues/96")
    @Test
    void ignoreAmbiguousMethodReference() {
        rewriteRun(
          //language=java
          java(
            """
              import java.util.stream.Stream;
                            
              class Test {
                  Stream<String> method() {
                      return Stream.of(1, 32, 12, 15, 23).map(x -> Integer.toString(x));
                  }
              }
              """
          )
        );
    }

    @Test
    void containsMultipleStatements() {
        rewriteRun(
          //language=java
          java(
            """
              import java.util.List;
              import java.util.stream.Collectors;

              class Test {
                  List<Integer> even(List<Integer> l) {
                      return l.stream().map(n -> {
                          if (n % 2 == 0) return n;
                          return n * 2;
                      }).collect(Collectors.toList());
                  }
              }
              """
          )
        );
    }

    @Issue("https://github.com/openrewrite/rewrite/issues/1772")
    @Test
    void typeCastOnMethodInvocationReturnType() {
        rewriteRun(
          //language=java
          java(
            """
              import java.util.List;
              import java.util.stream.Collectors;
              import java.util.stream.Stream;

              class Test {
                  public void foo() {
                      List<String> bar = Stream.of("A", "b")
                              .map(s -> (String) s.toLowerCase())
                              .collect(Collectors.toList());
                  }
              }
              """
          )
        );
    }

    @Test
    @Issue("https://github.com/openrewrite/rewrite-static-analysis/issues/201")
    void typeCastOnConstructorCall() {
        rewriteRun(
          //language=java
          java(
            """
              import java.util.List;
              import java.util.stream.Collectors;
              import java.util.stream.Stream;

              class Test {
                  public void foo() {
                      List<Object> bar = Stream.of("A", "b")
                              .map(s -> (Object) new String(s + ":"))
                              .collect(Collectors.toList());
                  }
              }
              """
          )
        );
    }

    @Test
    void instanceOf() {
        rewriteRun(
          //language=java
          java(
            """
              package org.test;
              public class CheckType {
              }
              """
          ),
          java(
            //language=java
            """
              import java.util.List;
              import java.util.stream.Collectors;

              import org.test.CheckType;

              class Test {
                  List<Object> method(List<Object> input) {
                      return input.stream().filter(n -> n instanceof CheckType).collect(Collectors.toList());
                  }
              }
              """,
            //language=java
            """
              import java.util.List;
              import java.util.stream.Collectors;

              import org.test.CheckType;

              class Test {
                  List<Object> method(List<Object> input) {
                      return input.stream().filter(CheckType.class::isInstance).collect(Collectors.toList());
                  }
              }
              """,
            spec -> spec.afterRecipe(cu -> new JavaIsoVisitor<>() {
                @Override
                public J.MemberReference visitMemberReference(J.MemberReference memberRef, Object o) {
                    assertThat(TypeUtils.isOfClassType(((J.FieldAccess) memberRef.getContaining()).getTarget().getType(),
                      "org.test.CheckType")).isTrue();
                    return memberRef;
                }
            }.visit(cu, 0))
          )
        );
    }

    @Test
    void qualifiedInstanceOf() {
        rewriteRun(
          //language=java
          java(
            """
              package org.test;
              public class CheckType {
              }
              """
          ),
          java(
            //language=java
            """
              import java.util.List;
              import java.util.stream.Collectors;

              class Test {
                  List<Object> method(List<Object> input) {
                      return input.stream().filter(n -> n instanceof org.test.CheckType).collect(Collectors.toList());
                  }
              }
              """,
            //language=java
            """
              import org.test.CheckType;
                            
              import java.util.List;
              import java.util.stream.Collectors;

              class Test {
                  List<Object> method(List<Object> input) {
                      return input.stream().filter(CheckType.class::isInstance).collect(Collectors.toList());
                  }
              }
              """,
            spec -> spec.afterRecipe(cu -> new JavaIsoVisitor<>() {
                @Override
                public J.MemberReference visitMemberReference(J.MemberReference memberRef, Object o) {
                    assertThat(TypeUtils.isOfClassType(((J.FieldAccess) memberRef.getContaining()).getTarget().getType(),
                      "org.test.CheckType")).isTrue();
                    return memberRef;
                }
            }.visit(cu, 0))
          )
        );
    }

    @Test
    void typeFromSourcePath() {
        rewriteRun(
          //language=java
          java(
            """
              package org.test;
              public class CheckType {
                  Integer foo() {
                      return 0;
                  }
              }
              """
          ),
          java(
            //language=java
            """
              import java.util.List;
              import java.util.stream.Stream;
              import org.test.CheckType;

              class Test {
                  Stream<Integer> method(List<CheckType> input) {
                      return input.stream().map(n -> n.foo());
                  }
              }
              """,
            //language=java
            """
              import java.util.List;
              import java.util.stream.Stream;
              import org.test.CheckType;

              class Test {
                  Stream<Integer> method(List<CheckType> input) {
                      return input.stream().map(CheckType::foo);
                  }
              }
              """,
            spec -> spec.afterRecipe(cu -> new JavaIsoVisitor<>() {
                @Override
                public J.MemberReference visitMemberReference(J.MemberReference memberRef, Object o) {
                    assertThat(TypeUtils.isOfClassType(((J.Identifier) memberRef.getContaining()).getType(),
                      "org.test.CheckType")).isTrue();
                    return memberRef;
                }
            }.visit(cu, 0))
          )
        );
    }

    @Issue("https://github.com/openrewrite/rewrite/issues/2875")
    @Test
    void instanceOfLeftHandIsNotLambdaParameter() {
        rewriteRun(
          //language=java
          java(
            """
              package org.test;
              public class CheckType {
              }
              """
          ),
          //language=java
          java(
            """
              import java.util.List;
              import java.util.Optional;
              import java.util.stream.Collectors;

              import org.test.CheckType;

              class Test {
                  List<Optional<Object>> method(List<Optional<Object>> input) {
                      return input.stream().filter(n -> n.get() instanceof CheckType).collect(Collectors.toList());
                  }
              }
              """
          )
        );
    }

    @DocumentExample
    @Test
    void functionMultiParamReference() {
        rewriteRun(
          //language=java
          java(
            """
              import java.util.function.Function;
              class Test {
                            
                  ChangeListener listener = (o, oldVal, newVal) -> {
                      onChange(o, oldVal, newVal);
                  };
                  
                  protected void onChange(ObservableValue<?> o, Object oldVal, Object newVal) {
                      String strVal = newVal.toString();
                      System.out.println(strVal);
                  }

                  interface ObservableValue<T> {
                  }

                  @FunctionalInterface
                  interface ChangeListener<T> {
                      void changed(ObservableValue<? extends T> observable, T oldValue, T newValue);
                  }
              }
              """,
            """
              import java.util.function.Function;
              class Test {
                            
                  ChangeListener listener = this::onChange;
                  
                  protected void onChange(ObservableValue<?> o, Object oldVal, Object newVal) {
                      String strVal = newVal.toString();
                      System.out.println(strVal);
                  }

                  interface ObservableValue<T> {
                  }

                  @FunctionalInterface
                  interface ChangeListener<T> {
                      void changed(ObservableValue<? extends T> observable, T oldValue, T newValue);
                  }
              }
              """
          )
        );
    }

    @Test
    void nonStaticMethods() {
        rewriteRun(
          //language=java
          java(
            """
              import java.util.Collections;
                            
              class Test2 {
                  class Test {
                      Runnable r = () -> run();
                      public void run() {
                          Collections.singletonList(1).forEach(n -> run());
                      }
                  }
                  void foo() {
                      Test t = new Test();
                      Runnable r = () -> t.run();
                  }
              }
              """,
            """
              import java.util.Collections;
                            
              class Test2 {
                  class Test {
                      Runnable r = this::run;
                      public void run() {
                          Collections.singletonList(1).forEach(n -> run());
                      }
                  }
                  void foo() {
                      Test t = new Test();
                      Runnable r = t::run;
                  }
              }
              """
          )
        );
    }

    @Test
    void staticMethods() {
        rewriteRun(
          //language=java
          java(
            """
              import java.util.Collections;
              class Test {
                  Runnable r = () -> run();
                  public static void run() {
                      Collections.singletonList(1).forEach(n -> run());
                  }
                  static class Test2 {
                      Runnable r = () -> Test.run();
                  }
              }
              """,
            """
              import java.util.Collections;
              class Test {
                  Runnable r = Test::run;
                  public static void run() {
                      Collections.singletonList(1).forEach(n -> run());
                  }
                  static class Test2 {
                      Runnable r = Test::run;
                  }
              }
              """
          )
        );
    }

    @Test
    void systemOutPrint() {
        rewriteRun(
          //language=java
          java(
            """
              import java.util.List;
              class Test {
                  void method(List<Integer> input) {
                      input.forEach(x -> System.out.println(x));
                  }
              }
              """,
            """
              import java.util.List;
              class Test {
                  void method(List<Integer> input) {
                      input.forEach(System.out::println);
                  }
              }
              """
          )
        );
    }

    @Test
    void systemOutPrintInBlock() {
        rewriteRun(
          //language=java
          java(
            """
              import java.util.List;
              class Test {
                  void method(List<Integer> input) {
                      input.forEach(x -> { System.out.println(x); });
                  }
              }
              """,
            """
              import java.util.List;
              class Test {
                  void method(List<Integer> input) {
                      input.forEach(System.out::println);
                  }
              }
              """
          )
        );
    }

    @Test
    void castType() {
        rewriteRun(
          java(
            //language=java
            """
              package org.test;
              public class CheckType {
              }
              """
          ),
          java(
            //language=java
            """
              import java.util.List;
              import java.util.stream.Collectors;

              import org.test.CheckType;

              class Test {
                  List<Object> filter(List<Object> l) {
                      return l.stream()
                          .filter(CheckType.class::isInstance)
                          .map(o -> (CheckType) o)
                          .collect(Collectors.toList());
                  }
              }
              """,
            //language=java
            """
              import java.util.List;
              import java.util.stream.Collectors;

              import org.test.CheckType;

              class Test {
                  List<Object> filter(List<Object> l) {
                      return l.stream()
                          .filter(CheckType.class::isInstance)
                          .map(CheckType.class::cast)
                          .collect(Collectors.toList());
                  }
              }
              """,
            spec -> spec.afterRecipe(cu -> new JavaIsoVisitor<>() {
                @Override
                public J.MemberReference visitMemberReference(J.MemberReference memberRef, Object o) {
                    assertThat(TypeUtils.isOfClassType(((J.FieldAccess) memberRef.getContaining()).getTarget().getType(),
                      "org.test.CheckType")).isTrue();
                    return memberRef;
                }
            }.visit(cu, 0))
          )
        );
    }

    @Test
    void qualifiedCastType() {
        rewriteRun(
          //language=java
          java(
            """
              package org.test;
              public class CheckType {
              }
              """
          ),
          java(
            //language=java
            """
              import java.util.List;
              import java.util.stream.Collectors;

              class Test {
                  List<Object> filter(List<Object> l) {
                      return l.stream()
                          .filter(org.test.CheckType.class::isInstance)
                          .map(o -> (org.test.CheckType) o)
                          .collect(Collectors.toList());
                  }
              }
              """,
            //language=java
            """
              import org.test.CheckType;
                            
              import java.util.List;
              import java.util.stream.Collectors;

              class Test {
                  List<Object> filter(List<Object> l) {
                      return l.stream()
                          .filter(org.test.CheckType.class::isInstance)
                          .map(CheckType.class::cast)
                          .collect(Collectors.toList());
                  }
              }
              """,
            spec -> spec.afterRecipe(cu -> new JavaIsoVisitor<>() {
                @Override
                public J.MemberReference visitMemberReference(J.MemberReference memberRef, Object o) {
                    assertThat(TypeUtils.isOfClassType(((J.FieldAccess) memberRef.getContaining()).getTarget().getType(),
                      "org.test.CheckType")).isTrue();
                    return memberRef;
                }
            }.visit(cu, 0))
          )
        );
    }

    @Test
    void methodSelectMatchingSingleLambdaParameter() {
        rewriteRun(
          //language=java
          java(
            """
              import java.util.List;
              import java.util.stream.Collectors;

              class Test {
                  List<String> filter(List<Object> l) {
                      return l.stream()
                          .map(o -> o.toString())
                          .collect(Collectors.toList());
                  }
              }
              """,
            """
              import java.util.List;
              import java.util.stream.Collectors;

              class Test {
                  List<String> filter(List<Object> l) {
                      return l.stream()
                          .map(Object::toString)
                          .collect(Collectors.toList());
                  }
              }
              """
          )
        );
    }

    @Test
    void methodSelectMatchingMultipleLambdaParameters() {
        rewriteRun(
          //language=java
          java(
            """
              import java.util.function.BiFunction;

              class Test {
                  void foo() {
                      BiFunction<Integer, Integer, Integer> f = (i1, i2) -> i1.compareTo(i2);
                  }
              }
              """,
            """
              import java.util.function.BiFunction;
               
              class Test {
                  void foo() {
                      BiFunction<Integer, Integer, Integer> f = Integer::compareTo;
                  }
              }
              """
          )
        );
    }

    @Test
    void notEqualToNull() {
        rewriteRun(
          //language=java
          java(
            """
              import java.util.List;
              import java.util.stream.Collectors;

              class Test {
                  List<Object> filter(List<Object> l) {
                      return l.stream()
                          .filter(o -> o != null)
                          .collect(Collectors.toList());
                  }
              }
              """,
            """
              import java.util.List;
              import java.util.Objects;
              import java.util.stream.Collectors;

              class Test {
                  List<Object> filter(List<Object> l) {
                      return l.stream()
                          .filter(Objects::nonNull)
                          .collect(Collectors.toList());
                  }
              }
              """
          )
        );
    }

    @Issue("https://github.com/openrewrite/rewrite/issues/2897")
    @Test
    void notNullToObjectsNonNullError() {
        rewriteRun(
          //language=java
          java(
            """
              import java.util.Collection;

              public class A {
                  private static Class<?> determineCommonAncestor(Class<?> clazz1, Class<?> clazz2) {
                      return clazz1;
                  }

                  private static String forClass(Class<?> clazz) {
                      return clazz.getName();
                  }

                  private static String deriveElementType(Collection<?> elements, String fallbackType) {

                      if (elements.isEmpty()) {
                          return fallbackType;
                      }

                      return elements.stream()
                          .filter(it -> it != null)
                          .<Class<?>> map(Object::getClass)
                          .reduce(A::determineCommonAncestor)
                          .map(A::forClass)
                          .orElse(fallbackType);
                  }
              }
              """,
            """
              import java.util.Collection;
              import java.util.Objects;

              public class A {
                  private static Class<?> determineCommonAncestor(Class<?> clazz1, Class<?> clazz2) {
                      return clazz1;
                  }

                  private static String forClass(Class<?> clazz) {
                      return clazz.getName();
                  }

                  private static String deriveElementType(Collection<?> elements, String fallbackType) {

                      if (elements.isEmpty()) {
                          return fallbackType;
                      }

                      return elements.stream()
                          .filter(Objects::nonNull)
                          .<Class<?>> map(Object::getClass)
                          .reduce(A::determineCommonAncestor)
                          .map(A::forClass)
                          .orElse(fallbackType);
                  }
              }
              """
          )
        );
    }

    @SuppressWarnings("Convert2MethodRef")
    @Test
    void isEqualToNull() {
        rewriteRun(
          //language=java
          java(
            """
              import java.util.List;
              import java.util.stream.Collectors;

              class Test {
                  boolean containsNull(List<Object> l) {
                      return l.stream()
                          .anyMatch(o -> o == null);
                  }
              }
              """,
            """
              import java.util.List;
              import java.util.Objects;
              import java.util.stream.Collectors;

              class Test {
                  boolean containsNull(List<Object> l) {
                      return l.stream()
                          .anyMatch(Objects::isNull);
                  }
              }
              """
          )
        );
    }

    @Test
    void voidMethodReference() {
        rewriteRun(
          //language=java
          java(
            """
              class Test {
                  Runnable r = () -> {
                      this.execute();
                  };

                  void execute() {}
              }
              """,
            """
              class Test {
                  Runnable r = this::execute;

                  void execute() {}
              }
              """
          )
        );
    }

    @Test
    void functionReference() {
        rewriteRun(
          //language=java
          java(
            """
              import java.util.function.Function;

              class Test {
                  Function<Integer, String> f = (i) -> {
                      return this.execute(i);
                  };
                  
                  String execute(Integer i) {
                      return i.toString();
                  }
              }
              """,
            """
              import java.util.function.Function;

              class Test {
                  Function<Integer, String> f = this::execute;
                  
                  String execute(Integer i) {
                      return i.toString();
                  }
              }
              """
          )
        );
    }

    @Test
    void returnExpressionIsNotAMethodInvocation() {
        rewriteRun(
          spec -> spec.typeValidationOptions(TypeValidation.builder().methodInvocations(false).build()),
          //language=java
          java(
            """
              class T {
                  public void killBatchJob() {
                      return deleteSparkBatchRequest()
                              .map(resp -> {
                                  return this;
                              })
                              .defaultIfEmpty(this);
                  }
              }
              """
          )
        );
    }

    @Test
    void lambdaReturnsFunctionalInterface() {
        rewriteRun(
          //language=java
          java(
            """
              package abc;
              @FunctionalInterface
              public interface MyFunction {
                  String get();
              }
              """
          ),
          //language=java
          java(
            """
              package abc;
                            
              class M {
                  MyFunction getFunction(String fcn) {
                      return () -> fcn;
                  }
              }
              """
          )
        );
    }

    @Issue("https://github.com/openrewrite/rewrite/issues/2178")
    @Test
    void doNotReplaceInvocationWhichAcceptsArgument() {
        rewriteRun(
          //language=java
          java(
            """
              import java.util.*;

              class A {
                  void foo() {
                      new ArrayList<List<Integer>>().stream()
                              .map(it -> it.addAll(List.of(1, 2, 3)));
                  }
              }
              """
          )
        );
    }

    @Test
    void replacedConstructorCalls() {
        rewriteRun(
          //language=java
          java(
            """
              import java.util.ArrayList;
              import java.util.function.Function;
              import java.util.function.Supplier;
                            
              class A {
                  void foo() {
                      Supplier<?> s;
                      s = () -> new Object();
                      s = () -> new java.lang.Object();
                      s = () -> new java.util.ArrayList();
                      s = () -> new java.util.ArrayList<>();
                      s = () -> new java.util.ArrayList<Object>();
                      s = () -> new ArrayList<Object>();
                      s = () -> new java.util.HashSet<Object>();
                      Function<Integer, ?> f;
                      f = i -> new ArrayList(i);
                  }
              }
              """,
            """
              import java.util.ArrayList;
              import java.util.function.Function;
              import java.util.function.Supplier;
                            
              class A {
                  void foo() {
                      Supplier<?> s;
                      s = Object::new;
                      s = java.lang.Object::new;
                      s = java.util.ArrayList::new;
                      s = java.util.ArrayList::new;
                      s = java.util.ArrayList::new;
                      s = ArrayList::new;
                      s = java.util.HashSet::new;
                      Function<Integer, ?> f;
                      f = ArrayList::new;
                  }
              }
              """
          )
        );
    }

    @Test
    void notReplacedConstructorCalls() {
        rewriteRun(
          //language=java
          java(
            """
              import java.util.ArrayList;
              import java.util.function.Function;
              import java.util.function.Supplier;
                            
              class A {
                  void foo() {
                      Supplier<?> s;
                      s = () -> new Object() {};
                      s = () -> new java.util.ArrayList(1);

                      Function<Integer, ?> f;
                      f = i -> new ArrayList();
                      f = i -> new ArrayList(i) {};

                      Object o;
                      o = i -> new ArrayList(1);
                  }
              }
              """
          )
        );
    }

    @Issue("https://github.com/openrewrite/rewrite/issues/2949")
    @Test
    void multipleConstructors() {
        rewriteRun(
          //language=java
          java(
            """
              import java.util.function.Predicate;

              class B {
                  B () {}
                  B (Predicate<String> predicate) {}
              }
              """
          ),
          //language=java
          java(
            """
              import java.util.function.Function;
              import java.util.function.Supplier;

              class A {
                  void method(Supplier<B> supplier) {}
                  void method(Function<B, B> function) {}

                  void test() {
                      method(() -> new B());            // OK
                      method(() -> new B(t -> false));  // OK
                      method((x) -> new B(t -> false)); // OK
                      // method(B::new);                // Error
                  }
              }
              """
          )
        );
    }

    @Issue("https://github.com/openrewrite/rewrite/issues/2949")
    @Test
    void anotherMultipleConstructorsCaseEasyUnderstanding() {
        rewriteRun(
          //language=java
          java(
            """
              class B {
                  B () {}
                  B (String name) {}
              }
              """
          ),
          //language=java
          java(
            """
              import java.util.function.Function;
              import java.util.function.Supplier;

              class A {
                  void method(Supplier<B> supplier) {}
                  void method(Function<String, B> function) {}

                  void test() {
                      method(() -> new B());         // OK
                      method(name -> new B(name));   // OK
                      // method(B::new);             // Error
                  }
              }
              """
          )
        );
    }

    @SuppressWarnings("StringOperationCanBeSimplified")
    @Issue("https://github.com/openrewrite/rewrite/issues/2949")
    @Test
    void anotherSimplerMultipleConstructorsCase() {
        rewriteRun(
          //language=java
          java(
            """
              import java.util.function.Function;
              import java.util.function.Supplier;

              public class A {
                  void method(Supplier<String> visitor) {}
                  void method(Function<String, String> visitor) {}

                  void test() {
                      method(() -> new String());
                      // method(String::new); // Error
                  }
              }
              """
          )
        );
    }

    @Issue("https://github.com/openrewrite/rewrite/issues/2958")
    @Test
    void insideIfConditionAfterInstanceOfPatternVariable() {
        rewriteRun(
          //language=java
          java(
            """
              import java.util.Collection;
              class A {
                  Collection<?> test(Object value) {
                      if (value instanceof Collection<?> values && values.stream().allMatch(it -> it instanceof Class)) {
                          return values;
                      }
                      return null;
                  }
              }
              """,
            """
              import java.util.Collection;
              class A {
                  Collection<?> test(Object value) {
                      if (value instanceof Collection<?> values && values.stream().allMatch(Class.class::isInstance)) {
                          return values;
                      }
                      return null;
                  }
              }
              """
          )
        );
    }

    @SuppressWarnings("EmptyTryBlock")
    @Test
    void tryInAForLoop() {
        rewriteRun(
          //language=java
          java(
            """
              import java.nio.file.DirectoryStream;
              import java.nio.file.Files;
              import java.nio.file.Path;
              import java.util.Set;

              class A {
                  void cleanOldBackups(Set<Path> backupPaths) throws Exception {
                      for (Path backupDirPath : backupPaths)
                          try (DirectoryStream<Path> directoryStream = Files.newDirectoryStream(backupDirPath, path -> Files.isDirectory(path))) {
                          }
                  }
              }
              """,
            """
              import java.nio.file.DirectoryStream;
              import java.nio.file.Files;
              import java.nio.file.Path;
              import java.util.Set;

              class A {
                  void cleanOldBackups(Set<Path> backupPaths) throws Exception {
                      for (Path backupDirPath : backupPaths)
                          try (DirectoryStream<Path> directoryStream = Files.newDirectoryStream(backupDirPath, Files::isDirectory)) {
                          }
                  }
              }
              """
          )
        );
    }


    @SuppressWarnings("OptionalOfNullableMisuse")
    @Test
    @Issue("https://github.com/openrewrite/rewrite/issues/3071")
    void missingImportForDeclaringType() {
        rewriteRun(
          //language=java
          java(
            """
              import java.net.URI;
              import java.nio.file.Paths;
              import java.util.Optional;

              class A {
                  void m() {
                      URI uri = Optional.ofNullable("path")
                            .map(Paths::get)
                            .map(path -> path.toUri())
                            .orElse(null);
                  }
              }
              """,
            """
              import java.net.URI;
              import java.nio.file.Path;
              import java.nio.file.Paths;
              import java.util.Optional;

              class A {
                  void m() {
                      URI uri = Optional.ofNullable("path")
                            .map(Paths::get)
                            .map(Path::toUri)
                            .orElse(null);
                  }
              }
              """
          )
        );
    }

    @SuppressWarnings({"ConstantValue"})
    @Test
    @Issue("https://github.com/openrewrite/rewrite-static-analysis/issues/200")
    void nestedType() {
        rewriteRun(
          //language=java
          java(
            """
              import java.util.HashMap;

              class A {
                  Boolean m() {
                      return new HashMap<String, String>()
                            .entrySet()
                            .stream()
                            .map(e -> e instanceof java.util.Map.Entry)
                            .findFirst()
                            .orElse(null);
                  }
              }
              """,
            """
              import java.util.HashMap;
              import java.util.Map;

              class A {
                  Boolean m() {
                      return new HashMap<String, String>()
                            .entrySet()
                            .stream()
                            .map(Map.Entry.class::isInstance)
                            .findFirst()
                            .orElse(null);
                  }
              }
              """
          )
        );
    }

    @Test
    @Issue("https://github.com/openrewrite/rewrite-static-analysis/pull/132")
    void dontReplaceLambdaSupplierOfMethodReference() {
        rewriteRun(
          //language=java
          java(
            """
              import java.util.Optional;
              import java.util.function.Function;

              class A {
                public void testCase() {
                  Function<String, Integer> function = str -> 1;
                  Optional.of(function).orElseGet(() -> this::foo);
                }

                private Integer foo(String bar) {
                  return 1;
                }
              }
              """
          )
        );
    }

    @Test
    void newClassSelector() {
        //language=java
        rewriteRun(
          java(
            """
              class A {
                  int seen = 0;
                  String lower(String s) {
                      seen++;
                      return s.toLowerCase();
                  }
              }
              """
          ),
          java(
            """
              import java.util.stream.Stream;
              class B {
                  void bar(Stream<String> stream) {
                      stream.map(s -> new A().lower(s));
                  }
              }
              """
          )
        );
    }

    @Test
    @Issue("https://github.com/openrewrite/rewrite-static-analysis/issues/237")
    void groupingByGetClass() {
        rewriteRun(
          //language=java
          java(
            """
              import java.util.*;
              import java.util.stream.*;
                          
              class Animal {}
              class Cat extends Animal {}
              class Dog extends Animal {}

              class Test {
                public void groupOnGetClass() {
                  List<Animal> animals = List.of(new Cat(), new Dog());
                  Map<Class<? extends Animal>, List<Animal>> collect;
                  collect = animals.stream().collect(Collectors.groupingBy(a -> a.getClass()));
                }
              }
              """
          )
        );
    }

    @Test
    void dontReplaceNullableFieldReferences() {
        //language=java
        rewriteRun(
          java(
            """
              import java.util.function.Supplier;
              class A {
                  Object field;
                  void foo() {
                      // Runtime exception when replaced with field::toString
                      Supplier<String> supplier = () -> field.toString();
                      Supplier<String> supplier = () -> this.field.toString();
                  }
              }
              """
          )
        );
    }
}
