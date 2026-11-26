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
import org.openrewrite.java.JavaParser;
import org.openrewrite.test.RecipeSpec;
import org.openrewrite.test.RewriteTest;
import org.openrewrite.test.TypeValidation;

import java.util.List;

import static org.openrewrite.groovy.Assertions.groovy;
import static org.openrewrite.java.Assertions.addTypesToSourceSet;
import static org.openrewrite.java.Assertions.java;
import static org.openrewrite.java.Assertions.sourceSet;
import static org.openrewrite.javascript.Assertions.typescript;

@SuppressWarnings({
  "ComparatorCombinators",
  "Convert2MethodRef",
  "ResultOfMethodCallIgnored",
  "CodeBlock2Expr"
})
class ExplicitLambdaArgumentTypesTest implements RewriteTest {
    @Override
    public void defaults(RecipeSpec spec) {
        spec.recipe(new ExplicitLambdaArgumentTypes());
    }

    @DocumentExample
    @Test
    void twoArgumentsWithBlock() {
        rewriteRun(
          //language=java
          java(
            """
              import java.util.function.BiPredicate;

              class Test {
                  static void run(BiPredicate<String, Object> bc) {
                  }

                  static void method() {
                      run((a, b) -> {
                          return a.isEmpty();
                      });
                  }
              }
              """,
            """
              import java.util.function.BiPredicate;

              class Test {
                  static void run(BiPredicate<String, Object> bc) {
                  }

                  static void method() {
                      run((String a, Object b) -> {
                          return a.isEmpty();
                      });
                  }
              }
              """
          )
        );
    }

    @Issue("https://github.com/openrewrite/rewrite/issues/1459")
    @Test
    void unknownArgumentType() {
        rewriteRun(
          spec -> spec.typeValidationOptions(TypeValidation.builder().identifiers(false).methodDeclarations(false).build()),
          //language=java
          java(
            """
              import java.util.function.Predicate;

              class Test {
                  static void run(Predicate<WillyWonka> c) {
                  }

                  static void method() {
                      run(a -> {
                          return a.isEmpty();
                      });
                  }
              }
              """
          )
        );
    }

    @Test
    void oneArgumentExistingExplicitType() {
        rewriteRun(
          //language=java
          java(
            """
              import java.util.List;
              import java.util.function.Consumer;

              class Test {
                  static void run(Consumer<List<String>> c) {
                  }

                  static void method() {
                      run(a -> a.size());
                  }
              }
              """
          )
        );
    }

    @Test
    void oneArgumentNoBlock() {
        rewriteRun(
          //language=java
          java(
            """
              import java.util.function.Consumer;

              class Test {
                  static void run(Consumer<String> c) {
                  }

                  static void method() {
                      run(q -> q.length());
                  }
              }
              """
          )
        );
    }

    @Test
    void twoArgumentsExistingExplicitType() {
        rewriteRun(
          //language=java
          java(
            """
              import java.util.function.BiConsumer;

              class Test {
                  static void run(BiConsumer<String, Object> bc) {
                  }

                  static void method() {
                      run((String a, Object b) -> a.length());
                  }
              }
              """
          )
        );
    }

    @Test
    void twoArgumentsNoBlock() {
        rewriteRun(
          //language=java
          java(
            """
              import java.util.function.BiConsumer;

              class Test {
                  static void run(BiConsumer<String, Object> bc) {
                  }

                  static void method() {
                      run((a, b) -> a.length());
                  }
              }
              """
          )
        );
    }

    @Test
    void handlePrimitiveArrays() {
        rewriteRun(
          //language=java
          java(
            """
              import java.util.function.BiFunction;

              class Test {
                  private final BiFunction<Integer, byte[], byte[]> func = (a, b) -> {
                      return null;
                  };
              }
              """,
            """
              import java.util.function.BiFunction;

              class Test {
                  private final BiFunction<Integer, byte[], byte[]> func = (Integer a, byte[] b) -> {
                      return null;
                  };
              }
              """
          )
        );
    }

    @Test
    void handleMultiDimensionalPrimitiveArrays() {
        rewriteRun(
          //language=java
          java(
            """
              import java.util.function.BiFunction;

              class Test {
                  private final BiFunction<Integer, byte[][], byte[][]> func = (a, b) -> {
                      return null;
                  };
              }
              """,
            """
              import java.util.function.BiFunction;

              class Test {
                  private final BiFunction<Integer, byte[][], byte[][]> func = (Integer a, byte[][] b) -> {
                      return null;
                  };
              }
              """
          )
        );
    }

    @Test
    void handleMultiDimensionalFullyQualifiedArrays() {
        rewriteRun(
          //language=java
          java(
            """
              import java.util.function.BiFunction;

              class Test {
                  private final BiFunction<Integer, Integer[][], Integer[][]> func = (a, b) -> {
                      return null;
                  };
              }
              """,
            """
              import java.util.function.BiFunction;

              class Test {
                  private final BiFunction<Integer, Integer[][], Integer[][]> func = (Integer a, Integer[][] b) -> {
                      return null;
                  };
              }
              """
          )
        );
    }

    @Test
    void oneArgumentWithBlock() {
        rewriteRun(
          //language=java
          java(
            """
              import java.util.function.Predicate;

              class Test {
                  static void run(Predicate<String> c) {
                  }

                  static void method() {
                      run(a -> {
                          return a.isEmpty();
                      });
                  }
              }
              """,
            """
              import java.util.function.Predicate;

              class Test {
                  static void run(Predicate<String> c) {
                  }

                  static void method() {
                      run((String a) -> {
                          return a.isEmpty();
                      });
                  }
              }
              """
          )
        );
    }

    @Test
    void threeArgumentsNoBlock() {
        rewriteRun(
          //language=java
          java(
            """
              class Test {
                  static void run(TriConsumer tc) {
                  }

                  static void method() {
                      run((a, b, c) -> a.toUpperCase());
                  }

                  private interface TriConsumer {
                      String method(String a, String b, String c);
                  }
              }
              """,
            """
              class Test {
                  static void run(TriConsumer tc) {
                  }

                  static void method() {
                      run((String a, String b, String c) -> a.toUpperCase());
                  }

                  private interface TriConsumer {
                      String method(String a, String b, String c);
                  }
              }
              """
          )
        );
    }

    @Test
    void threeArgumentsWithBlock() {
        rewriteRun(
          //language=java
          java(
            """
              class Test {
                  static void run(TriConsumer tc) {
                  }

                  static void method() {
                      run((a, b, c) -> {
                          return a.toUpperCase();
                      });
                  }

                  private interface TriConsumer {
                      String method(String a, String b, String c);
                  }
              }
              """,
            """
              class Test {
                  static void run(TriConsumer tc) {
                  }

                  static void method() {
                      run((String a, String b, String c) -> {
                          return a.toUpperCase();
                      });
                  }

                  private interface TriConsumer {
                      String method(String a, String b, String c);
                  }
              }
              """
          )
        );
    }

    @Test
    void threeArgumentsWithBlockPrimitive() {
        rewriteRun(
          //language=java
          java(
            """
              class Test {
                  static void run(TriConsumer tc) {
                  }

                  static void method() {
                      run((a, b, c) -> {
                          return a + b - c;
                      });
                  }

                  private interface TriConsumer {
                      int method(int a, int b, int c);
                  }
              }
              """,
            """
              class Test {
                  static void run(TriConsumer tc) {
                  }

                  static void method() {
                      run((int a, int b, int c) -> {
                          return a + b - c;
                      });
                  }

                  private interface TriConsumer {
                      int method(int a, int b, int c);
                  }
              }
              """
          )
        );
    }

    @Test
    void threeArgumentsWithBlockGeneric() {
        rewriteRun(
          //language=java
          java(
            """
              class Test {
                  static <T> void run(TriConsumer<T> tc) {
                  }

                  static void method() {
                      run((a, b, c) -> {
                          return a.toString();
                      });
                  }

                  private interface TriConsumer<T> {
                      T method(T a, T b, T c);
                  }
              }
              """,
            """
              class Test {
                  static <T> void run(TriConsumer<T> tc) {
                  }

                  static void method() {
                      run((Object a, Object b, Object c) -> {
                          return a.toString();
                      });
                  }

                  private interface TriConsumer<T> {
                      T method(T a, T b, T c);
                  }
              }
              """
          )
        );
    }

    @Test
    void noArguments() {
        rewriteRun(
          //language=java
          java(
            """
              import java.util.function.Supplier;

              class Test {
                  static void run(Supplier<String> s) {
                  }

                  static void method() {
                      run(() -> {
                          return "example";
                      });
                  }
              }
              """
          )
        );
    }

    @Test
    void arraysSortExample() {
        rewriteRun(
          //language=java
          java(
            """
              import java.util.Arrays;

              class Test {
                  static void method(String[] arr) {
                      Arrays.sort(arr, (a, b) -> {
                          return a.length() - b.length();
                      });
                  }
              }
              """,
            """
              import java.util.Arrays;

              class Test {
                  static void method(String[] arr) {
                      Arrays.sort(arr, (String a, String b) -> {
                          return a.length() - b.length();
                      });
                  }
              }
              """
          )
        );
    }

    @Test
    void arraysSortExampleWithGeneric() {
        rewriteRun(
          //language=java
          java(
            """
              import java.util.Arrays;

              class Test {
                  static <T> void method(T[] arr) {
                      Arrays.sort(arr, (a, b) -> {
                          return a.toString().length() - b.toString().length();
                      });
                  }
              }
              """,
            """
              import java.util.Arrays;

              class Test {
                  static <T> void method(T[] arr) {
                      Arrays.sort(arr, (T a, T b) -> {
                          return a.toString().length() - b.toString().length();
                      });
                  }
              }
              """
          )
        );
    }

    @Test
    void parameterizedTypes() {
        rewriteRun(
          //language=java
          java(
            """
              package javafx.beans.value;

              public interface ObservableValue<T> {
                  void addListener(ChangeListener<? super T> listener);
              }
              """
          ),
          //language=java
          java(
            """
              package javafx.beans.value;

              @FunctionalInterface
              public interface ChangeListener<T> {
                  void changed(ObservableValue<? extends T> observable, T oldValue, T newValue);
              }
              """
          ),
          //language=java
          java(
            """
              package example;

              import javafx.beans.value.ObservableValue;
              import javafx.beans.value.ChangeListener;

              public class Fred implements ObservableValue<String>{
                  public void addListener(ChangeListener<? super String> listener) {
                  }
              }
              """
          ),
          //language=java
          java(
            """
              import javafx.beans.value.ObservableValue;
              import example.Fred;

              class Test {
                  void foo() {
                      Fred fred = new Fred();
                      fred.addListener((ov, oldState, newState) -> {
                      });
                  }
              }
              """,
            """
              import javafx.beans.value.ObservableValue;
              import example.Fred;

              class Test {
                  void foo() {
                      Fred fred = new Fred();
                      fred.addListener((ObservableValue<? extends String> ov, String oldState, String newState) -> {
                      });
                  }
              }
              """
          )
        );
    }

    @Issue("https://github.com/openrewrite/rewrite-static-analysis/issues/323")
    @Test
    void doesNotImportConflictingClassName() {
        rewriteRun(
          java(
            """
              package foo;
              public class A { }
              """
          ),
          java(
            """
              package buzz;
              public class A { }
              """
          ),
          //language=java
          java(
            """
              import foo.A;
              import java.util.List;

              class Test {
                  A field;
                  void method(List<List<buzz.A>> outer) {
                      outer.forEach((inner) -> {
                          inner.forEach((a) -> {
                              System.out.println(a);
                          });
                      });
                  }
              }
              """,
            """
              import foo.A;
              import java.util.List;

              class Test {
                  A field;
                  void method(List<List<buzz.A>> outer) {
                      outer.forEach((List<buzz.A> inner) -> {
                          inner.forEach((buzz.A a) -> {
                              System.out.println(a);
                          });
                      });
                  }
              }
              """
          )
        );
    }

    @Issue("https://github.com/openrewrite/rewrite/issues/2177")
    @Test
    void extendsConstraint() {
        rewriteRun(
          //language=java
          java(
            """
              package com.test;

              import java.util.List;

              class A {
                  void foo(List<? extends A> a) {
                      a.forEach(it -> { });
                  }
              }
              """,
            """
              package com.test;

              import java.util.List;

              class A {
                  void foo(List<? extends A> a) {
                      a.forEach((A it) -> { });
                  }
              }
              """
          )
        );
    }

    @Test
    void doNotStackOverflowOnRecursiveGenericType() {
        rewriteRun(
          spec -> spec.parser(JavaParser.fromJavaVersion().classpath("assertj-core")),
          //language=java
          java(
            """
              import org.assertj.core.api.AbstractStringAssert;

              class Test {
                  static <C> void inject(C carrier, Setter<C> setter) {
                  }

                  interface Setter<C> {
                      void set(C carrier);
                  }

                  static void method(AbstractStringAssert<?> assertion) {
                      inject(assertion, a -> {
                          a.isNotNull();
                      });
                  }
              }
              """
          )
        );
    }

    @Test
    void importsRawTypeOfParameterizedType() {
        rewriteRun(
          //language=java
          java(
            """
              package lib;

              import java.util.List;
              import java.util.function.Consumer;

              public class Lib {
                  public static void run(Consumer<List<String>> c) {
                  }
              }
              """
          ),
          //language=java
          java(
            """
              import lib.Lib;

              class Test {
                  void method() {
                      Lib.run(scopes -> {
                          scopes.clear();
                      });
                  }
              }
              """,
            """
              import lib.Lib;

              import java.util.List;

              class Test {
                  void method() {
                      Lib.run((List<String> scopes) -> {
                          scopes.clear();
                      });
                  }
              }
              """
          )
        );
    }

    @Test
    void nestedTypeIsQualifiedByItsOuterClassAndOtherReferencesAreLeftAlone() {
        rewriteRun(
          //language=java
          java(
            """
              package lib;

              import java.util.function.Consumer;

              public class Writer {
                  public static class Job {
                  }

                  public static void run(Consumer<Job> c) {
                  }
              }
              """
          ),
          //language=java
          java(
            """
              import lib.Writer;

              class Test {
                  Writer.Job current;

                  void method() {
                      Writer.run(job -> {
                          current = job;
                      });
                  }
              }
              """,
            """
              import lib.Writer;

              class Test {
                  Writer.Job current;

                  void method() {
                      Writer.run((Writer.Job job) -> {
                          current = job;
                      });
                  }
              }
              """
          )
        );
    }

    @Test
    void nestedTypesWithTheSameSimpleNameDoNotClash() {
        rewriteRun(
          //language=java
          java(
            """
              package lib;

              public class Application {
                  public static class Name {
                  }
              }
              """
          ),
          //language=java
          java(
            """
              package lib;

              public class Page {
                  public static class Name {
                  }
              }
              """
          ),
          //language=java
          java(
            """
              package lib;

              import java.util.function.BiConsumer;

              public class Repo {
                  public static void run(BiConsumer<Application.Name, Page.Name> c) {
                  }
              }
              """
          ),
          //language=java
          java(
            """
              import lib.Repo;

              class Test {
                  void method() {
                      Repo.run((app, page) -> {
                          System.out.println(app + " " + page);
                      });
                  }
              }
              """,
            """
              import lib.Application;
              import lib.Page;
              import lib.Repo;

              class Test {
                  void method() {
                      Repo.run((Application.Name app, Page.Name page) -> {
                          System.out.println(app + " " + page);
                      });
                  }
              }
              """
          )
        );
    }

    @Test
    void explicitlyImportedNestedTypeUsesSimpleName() {
        rewriteRun(
          //language=java
          java(
            """
              package lib;

              import java.util.function.Consumer;

              public class Writer {
                  public static class Job {
                  }

                  public static void run(Consumer<Job> c) {
                  }
              }
              """
          ),
          //language=java
          java(
            """
              import lib.Writer;
              import lib.Writer.Job;

              class Test {
                  Job current;

                  void method() {
                      Writer.run(job -> {
                          current = job;
                      });
                  }
              }
              """,
            """
              import lib.Writer;
              import lib.Writer.Job;

              class Test {
                  Job current;

                  void method() {
                      Writer.run((Job job) -> {
                          current = job;
                      });
                  }
              }
              """
          )
        );
    }

    @Test
    void doesNotImportTypeShadowingSamePackageType() {
        rewriteRun(
          //language=java
          java(
            """
              package other;

              import java.util.function.Consumer;

              public class ParseException extends Exception {
                  public static void run(Consumer<ParseException> c) {
                  }
              }
              """
          ),
          //language=java
          java(
            """
              package app;

              public class ParseException extends Exception {
              }
              """
          ),
          //language=java
          java(
            """
              package app;

              class Test {
                  ParseException last;

                  void method() {
                      other.ParseException.run(e -> {
                          System.out.println(e);
                      });
                  }
              }
              """,
            """
              package app;

              class Test {
                  ParseException last;

                  void method() {
                      other.ParseException.run((other.ParseException e) -> {
                          System.out.println(e);
                      });
                  }
              }
              """
          )
        );
    }

    @Test
    void doNotChangeWhenAnyParameterTypeCannotBeNamed() {
        rewriteRun(
          //language=java
          java(
            """
              package lib;

              class Hidden {
              }
              """
          ),
          //language=java
          java(
            """
              package lib;

              public class Api {
                  public interface TriConsumer<A, B, C> {
                      void accept(A a, B b, C c);
                  }

                  public static void run(TriConsumer<String, Hidden, String> c) {
                  }
              }
              """
          ),
          //language=java
          java(
            """
              import lib.Api;

              class Test {
                  void method() {
                      Api.run((a, h, b) -> System.out.println(a + b));
                  }
              }
              """
          )
        );
    }

    @Test
    void rawTypeStaysRaw() {
        rewriteRun(
          //language=java
          java(
            """
              import java.util.Map;
              import java.util.concurrent.ConcurrentHashMap;

              class Test {
                  interface Chain<T extends Number> {
                      void clean();
                  }

                  private Map<String, Chain> chains = new ConcurrentHashMap<>();

                  void reset(String key) {
                      chains.compute(key, (k, value) -> {
                          value.clean();
                          return value;
                      });
                  }
              }
              """,
            """
              import java.util.Map;
              import java.util.concurrent.ConcurrentHashMap;

              class Test {
                  interface Chain<T extends Number> {
                      void clean();
                  }

                  private Map<String, Chain> chains = new ConcurrentHashMap<>();

                  void reset(String key) {
                      chains.compute(key, (String k, Chain value) -> {
                          value.clean();
                          return value;
                      });
                  }
              }
              """
          )
        );
    }

    @Test
    void doNotChangeInnerClassOfGenericClass() {
        rewriteRun(
          //language=java
          java(
            """
              package lib;

              public class Outer<T> {
                  public class Inner {
                  }

                  public interface Tri<A, B, C> {
                      void accept(A a, B b, C c);
                  }

                  public void run(Tri<Inner, String, String> c) {
                  }

                  void other(Outer<String> o) {
                      o.run((i, a, b) -> System.out.println(i));
                  }
              }
              """
          ),
          //language=java
          java(
            """
              import lib.Outer;

              class Test {
                  void method(Outer<String> outer) {
                      outer.run((i, a, b) -> System.out.println(i));
                  }
              }
              """
          )
        );
    }

    @Test
    void doNotChangeLowerBoundedOrUnboundedCapture() {
        rewriteRun(
          //language=java
          java(
            """
              import java.util.List;

              class Test {
                  void lower(List<? super Integer> l) {
                      l.forEach(x -> {
                          System.out.println(x);
                      });
                  }

                  void unbounded(List<?> l) {
                      l.forEach(x -> {
                          System.out.println(x);
                      });
                  }
              }
              """
          )
        );
    }

    @Test
    void doNotChangeTypeVariableHiddenByInnerDeclaration() {
        rewriteRun(
          //language=java
          java(
            """
              import java.util.List;

              class Test<T> {
                  List<T> items;

                  <T> void m() {
                      items.forEach(a -> {
                          System.out.println(a);
                      });
                  }
              }
              """
          )
        );
    }

    @Test
    void memberTypeOfOuterClassIsQualifiedInsideAnonymousClass() {
        rewriteRun(
          //language=java
          java(
            """
              package base;

              public abstract class Base {
                  public static class Job {
                  }

                  public abstract void go();
              }
              """
          ),
          //language=java
          java(
            """
              import base.Base;
              import java.util.List;

              class Test {
                  static class Job {
                  }

                  void m(List<Job> jobs) {
                      new Base() {
                          public void go() {
                              jobs.forEach(j -> {
                                  System.out.println(j);
                              });
                          }
                      };
                  }
              }
              """,
            """
              import base.Base;
              import java.util.List;

              class Test {
                  static class Job {
                  }

                  void m(List<Job> jobs) {
                      new Base() {
                          public void go() {
                              jobs.forEach((Test.Job j) -> {
                                  System.out.println(j);
                              });
                          }
                      };
                  }
              }
              """
          )
        );
    }

    @Test
    void protectedMemberTypeFromSubclass() {
        rewriteRun(
          //language=java
          java(
            """
              package base;

              import java.util.function.Consumer;

              public class Base {
                  protected static class Job {
                  }

                  protected void each(Consumer<Job> c) {
                  }
              }
              """
          ),
          //language=java
          java(
            """
              import base.Base;

              class Test extends Base {
                  void m() {
                      each(j -> {
                          System.out.println(j);
                      });
                  }
              }
              """,
            """
              import base.Base;

              class Test extends Base {
                  void m() {
                      each((Base.Job j) -> {
                          System.out.println(j);
                      });
                  }
              }
              """
          )
        );
    }

    @Test
    void memberTypeInheritedFromSupertypeInAnotherFileHidesImport() {
        rewriteRun(
          //language=java
          java(
            """
              package lib;

              public class Base {
                  public static class Result {
                  }
              }
              """
          ),
          //language=java
          java(
            """
              package lib2;

              public class Result {
              }
              """
          ),
          //language=java
          java(
            """
              package lib2;

              public class Api {
                  public interface Tri<A, B, C> {
                      void accept(A a, B b, C c);
                  }

                  public static void run(Tri<Result, String, String> c) {
                  }
              }
              """
          ),
          //language=java
          java(
            """
              package app;

              import lib2.Api;

              class Test extends lib.Base {
                  void m() {
                      Api.run((r, a, b) -> System.out.println(r));
                  }
              }
              """,
            """
              package app;

              import lib2.Api;

              class Test extends lib.Base {
                  void m() {
                      Api.run((lib2.Result r, String a, String b) -> System.out.println(r));
                  }
              }
              """
          )
        );
    }

    @Test
    void samePackageClassInAnotherFileHidesJavaLang() {
        rewriteRun(
          //language=java
          java(
            """
              package lib;

              public class Api {
                  public interface Tri<A, B, C> {
                      void accept(A a, B b, C c);
                  }

                  public static void run(Tri<Process, String, String> c) {
                  }
              }
              """
          ),
          //language=java
          java(
            """
              package app;

              public class Process {
              }
              """
          ),
          //language=java
          java(
            """
              package app;

              import lib.Api;

              class Test {
                  void m() {
                      Api.run((p, a, b) -> System.out.println(p));
                  }
              }
              """,
            """
              package app;

              import lib.Api;

              class Test {
                  void m() {
                      Api.run((java.lang.Process p, String a, String b) -> System.out.println(p));
                  }
              }
              """
          )
        );
    }

    @Test
    void doNotChangeGroovyClosures() {
        rewriteRun(
          groovy(
            """
              def run(Closure c) {}
              run { a, b, c ->
                  println a
              }
              """
          )
        );
    }

    @Test
    void ambiguousOnDemandImportsUseFullyQualifiedName() {
        rewriteRun(
          spec -> spec.beforeRecipe(addTypesToSourceSet("main", List.of(), List.of())),
          //language=java
          java(
            """
              import java.awt.*;
              import java.util.*;
              import java.util.function.Consumer;

              class Test {
                  static void run(Consumer<java.util.List<String>> c) {
                  }

                  void m() {
                      run(x -> {
                          System.out.println(x);
                      });
                  }
              }
              """,
            """
              import java.awt.*;
              import java.util.*;
              import java.util.function.Consumer;

              class Test {
                  static void run(Consumer<java.util.List<String>> c) {
                  }

                  void m() {
                      run((java.util.List<String> x) -> {
                          System.out.println(x);
                      });
                  }
              }
              """,
            spec -> sourceSet(spec, "main")
          )
        );
    }

    @Test
    void memberClassImportedOnDemandByStaticImportHidesJavaLang() {
        rewriteRun(
          //language=java
          java(
            """
              package lib;

              public class Consts {
                  public static class Process {
                  }
              }
              """
          ),
          //language=java
          java(
            """
              package lib;

              public class Api {
                  public interface Tri<A, B, C> {
                      void accept(A a, B b, C c);
                  }

                  public static void run(Tri<java.lang.Process, String, String> c) {
                  }
              }
              """
          ),
          //language=java
          java(
            """
              import lib.Api;

              import static lib.Consts.*;

              class Test {
                  void m() {
                      Api.run((p, a, b) -> System.out.println(p));
                  }
              }
              """,
            """
              import lib.Api;

              import static lib.Consts.*;

              class Test {
                  void m() {
                      Api.run((java.lang.Process p, String a, String b) -> System.out.println(p));
                  }
              }
              """
          )
        );
    }

    @Test
    void methodTypeVariableInStaticMethodOfGenericClass() {
        rewriteRun(
          //language=java
          java(
            """
              class Box<T> {
                  interface Tri<X, Y, Z> {
                      void accept(X x, Y y, Z z);
                  }

                  static <X> void run(Tri<X, X, X> t) {
                  }

                  static <T> void s() {
                      Box.<T>run((x, y, z) -> System.out.println(x));
                  }
              }
              """,
            """
              class Box<T> {
                  interface Tri<X, Y, Z> {
                      void accept(X x, Y y, Z z);
                  }

                  static <X> void run(Tri<X, X, X> t) {
                  }

                  static <T> void s() {
                      Box.<T>run((T x, T y, T z) -> System.out.println(x));
                  }
              }
              """
          )
        );
    }

    @Test
    void doNotChangeTypeVariableHiddenByInheritedMemberClass() {
        rewriteRun(
          //language=java
          java(
            """
              package lib;

              public class Base {
                  public static class T {
                  }
              }
              """
          ),
          //language=java
          java(
            """
              import lib.Base;

              class A<T> {
                  interface Tri<X, Y, Z> {
                      void accept(X x, Y y, Z z);
                  }

                  void each(Tri<T, T, T> t) {
                  }

                  class B extends Base {
                      void m() {
                          each((x, y, z) -> System.out.println(x));
                      }
                  }
              }
              """
          )
        );
    }

    @Test
    void simpleNameResolvedWithoutImportIsNotClaimedByLaterImport() {
        rewriteRun(
          //language=java
          java(
            """
              package a;

              public class Foo {
              }
              """
          ),
          //language=java
          java(
            """
              package b;

              public class Foo {
              }
              """
          ),
          //language=java
          java(
            """
              package lib;

              public class Api {
                  public interface Tri<A, B, C> {
                      void accept(A a, B b, C c);
                  }

                  public static void run(Tri<a.Foo, b.Foo, String> c) {
                  }

                  public static void runB(Tri<b.Foo, String, String> c) {
                  }
              }
              """
          ),
          //language=java
          java(
            """
              package a;

              import lib.Api;

              class Test {
                  void m() {
                      Api.run((x, y, z) -> {
                      });
                      Api.runB((x, y, z) -> {
                      });
                  }
              }
              """,
            """
              package a;

              import lib.Api;

              class Test {
                  void m() {
                      Api.run((Foo x, b.Foo y, String z) -> {
                      });
                      Api.runB((b.Foo x, String y, String z) -> {
                      });
                  }
              }
              """
          )
        );
    }

    @Test
    void memberClassInheritedFromClasspathSupertypeHidesImport() {
        rewriteRun(
          spec -> spec.beforeRecipe(addTypesToSourceSet("main", List.of(), List.of())),
          //language=java
          java(
            """
              package p;

              public class SimpleEntry {
              }
              """,
            spec -> sourceSet(spec, "main")
          ),
          //language=java
          java(
            """
              package p;

              import java.util.function.Consumer;

              public class Api {
                  public static void run(Consumer<SimpleEntry> c) {
                  }
              }
              """,
            spec -> sourceSet(spec, "main")
          ),
          //language=java
          java(
            """
              package q;

              import java.util.AbstractMap;
              import java.util.Map;
              import java.util.Set;

              abstract class Test extends AbstractMap<String, String> {
                  void m() {
                      p.Api.run(e -> {
                          System.out.println(e);
                      });
                  }
              }
              """,
            """
              package q;

              import java.util.AbstractMap;
              import java.util.Map;
              import java.util.Set;

              abstract class Test extends AbstractMap<String, String> {
                  void m() {
                      p.Api.run((p.SimpleEntry e) -> {
                          System.out.println(e);
                      });
                  }
              }
              """,
            spec -> sourceSet(spec, "main")
          )
        );
    }

    @Test
    void doNotChangeCaptureAsArrayElementOrWildcardBound() {
        rewriteRun(
          //language=java
          java(
            """
              import java.util.List;

              class Test {
                  interface Tri<A, B, C> {
                      void accept(A a, B b, C c);
                  }

                  static <T> void array(List<T> l, Tri<T[], String, String> f) {
                  }

                  static <T> void bound(List<T> l, Tri<List<? extends T>, String, String> f) {
                  }

                  void m(List<? extends Number> nums) {
                      array(nums, (a, b, c) -> {
                      });
                      bound(nums, (a, b, c) -> {
                      });
                  }
              }
              """
          )
        );
    }

    @Test
    void doNotChangeCaptureUsedInvariantlyByTarget() {
        rewriteRun(
          //language=java
          java(
            """
              import java.util.List;

              class Test {
                  interface Tri<A, B, C> {
                      void accept(A a, B b, C c);
                  }

                  static <T> void each(List<T> l, Tri<T, String, String> f) {
                  }

                  static <T> void eachSuper(List<T> l, Tri<? super T, String, String> f) {
                  }

                  void m(List<? extends Number> nums) {
                      each(nums, (a, b, c) -> {
                      });
                      eachSuper(nums, (a, b, c) -> {
                      });
                  }
              }
              """,
            """
              import java.util.List;

              class Test {
                  interface Tri<A, B, C> {
                      void accept(A a, B b, C c);
                  }

                  static <T> void each(List<T> l, Tri<T, String, String> f) {
                  }

                  static <T> void eachSuper(List<T> l, Tri<? super T, String, String> f) {
                  }

                  void m(List<? extends Number> nums) {
                      each(nums, (a, b, c) -> {
                      });
                      eachSuper(nums, (Number a, String b, String c) -> {
                      });
                  }
              }
              """
          )
        );
    }

    @Test
    void doNotChangeFBoundedCapture() {
        rewriteRun(
          //language=java
          java(
            """
              import java.util.EnumSet;

              class Test {
                  void m(EnumSet<?> s) {
                      s.forEach(x -> {
                          x.compareTo(x);
                      });
                  }
              }
              """
          )
        );
    }

    @Test
    void captureInJdkBiConsumer() {
        rewriteRun(
          //language=java
          java(
            """
              import java.util.Map;

              class Test {
                  void m(Map<String, ? extends Number> m) {
                      m.forEach((k, v) -> {
                          System.out.println(k + v);
                      });
                  }
              }
              """,
            """
              import java.util.Map;

              class Test {
                  void m(Map<String, ? extends Number> m) {
                      m.forEach((String k, Number v) -> {
                          System.out.println(k + v);
                      });
                  }
              }
              """
          )
        );
    }

    @Test
    void explicitlyImportedClassKeepsSimpleNameWhenAnotherIsUsedQualified() {
        rewriteRun(
          //language=java
          java(
            """
              package a;

              public class Item {
              }
              """
          ),
          //language=java
          java(
            """
              package b;

              public class Item {
              }
              """
          ),
          //language=java
          java(
            """
              import a.Item;

              import java.util.List;

              class Test {
                  b.Item other;

                  void m(List<Item> items) {
                      items.forEach(i -> {
                          System.out.println(i);
                      });
                  }
              }
              """,
            """
              import a.Item;

              import java.util.List;

              class Test {
                  b.Item other;

                  void m(List<Item> items) {
                      items.forEach((Item i) -> {
                          System.out.println(i);
                      });
                  }
              }
              """
          )
        );
    }

    @Test
    void captureUnderSuperInConstructorAndVarargs() {
        rewriteRun(
          //language=java
          java(
            """
              import java.util.List;
              import java.util.function.Consumer;

              class Test {
                  static class Holder<E> {
                      Holder(List<E> l, Consumer<? super E> c) {
                      }
                  }

                  @SafeVarargs
                  static <E> void all(List<E> l, Consumer<? super E>... cs) {
                  }

                  void m(List<? extends Number> nums) {
                      new Holder<>(nums, x -> {
                          System.out.println(x);
                      });
                      all(nums, x -> {
                          System.out.println(x);
                      }, y -> {
                          System.out.println(y);
                      });
                  }
              }
              """,
            """
              import java.util.List;
              import java.util.function.Consumer;

              class Test {
                  static class Holder<E> {
                      Holder(List<E> l, Consumer<? super E> c) {
                      }
                  }

                  @SafeVarargs
                  static <E> void all(List<E> l, Consumer<? super E>... cs) {
                  }

                  void m(List<? extends Number> nums) {
                      new Holder<>(nums, (Number x) -> {
                          System.out.println(x);
                      });
                      all(nums, (Number x) -> {
                          System.out.println(x);
                      }, (Number y) -> {
                          System.out.println(y);
                      });
                  }
              }
              """
          )
        );
    }

    @Test
    void doNotFailOnTypeScriptArrowFunction() {
        rewriteRun(
          typescript(
            """
              namespace com.example {
                export class Foo {}
              }
              function run(cb: (x: com.example.Foo) => void) {}
              run(x => {
                console.log(x);
              });
              """
          )
        );
    }
}
