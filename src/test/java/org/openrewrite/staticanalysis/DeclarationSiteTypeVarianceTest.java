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
import org.openrewrite.test.RecipeSpec;
import org.openrewrite.test.RewriteTest;
import org.openrewrite.test.SourceSpec;
import org.openrewrite.test.TypeValidation;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

import static org.openrewrite.java.Assertions.java;
import static org.openrewrite.kotlin.Assertions.kotlin;

class DeclarationSiteTypeVarianceTest implements RewriteTest {

    @Override
    public void defaults(RecipeSpec spec) {
        spec.recipe(new DeclarationSiteTypeVariance(
          List.of("java.util.function.Function<IN, OUT>"),
          List.of("java.lang.*"),
          true
        ));
    }

    @DocumentExample
    @Test
    void inOutVariance() {
        rewriteRun(
          //language=java
          java(
            """
              interface In {}
              interface Out {}
              """
          ),
          //language=java
          java(
            """
              import java.util.function.Function;
              class Test {
                  void test(Function<In, Out> f) {
                  }
              }
              """,
            """
              import java.util.function.Function;
              class Test {
                  void test(Function<? super In, ? extends Out> f) {
                  }
              }
              """
          )
        );
    }

    @Test
    void validation() {
        assertThat(new DeclarationSiteTypeVariance(
          List.of("java.util.function.Function<INVALID, OUT>"),
          List.of("java.lang.*"),
          null
        ).validate().isInvalid()).isTrue();
    }

    @Test
    void validationWhenNull() {
        assertThat(new DeclarationSiteTypeVariance(
          null,
          null,
          null
        ).validate().isInvalid()).isTrue();
    }

    @Test
    void commonVariances() {
        rewriteRun(
          spec -> spec.recipeFromResources("org.openrewrite.staticanalysis.CommonDeclarationSiteTypeVariances"),
          //language=java
          java(
            """
              interface In {}
              interface Out {}
              """
          ),
          //language=java
          java(
            """
              import java.util.function.Function;
              class Test {
                  void test(Function<In, Out> f) {
                  }
              }
              """,
            """
              import java.util.function.Function;
              class Test {
                  void test(Function<? super In, ? extends Out> f) {
                  }
              }
              """
          )
        );
    }

    @Test
    void invariance() {
        rewriteRun(
          spec -> spec.recipe(new DeclarationSiteTypeVariance(
            List.of("java.util.function.Function<INVARIANT, OUT>"),
            List.of("java.lang.*"),
            null
          )),
          //language=java
          java(
            """
              interface In {}
              interface Out {}
              """
          ),
          //language=java
          java(
            """
              import java.util.function.Function;
              class Test {
                  void test(Function<In, Out> f) {
                  }
              }
              """,
            """
              import java.util.function.Function;
              class Test {
                  void test(Function<In, ? extends Out> f) {
                  }
              }
              """
          )
        );
    }

    @Test
    void excludedBounds() {
        rewriteRun(
          //language=java
          java(
            """
              import java.util.function.Function;
              class Test {
                  void test(Function<String, Integer> f) {
                  }
              }
              """
          )
        );
    }

    @Test
    void finalClasses() {
        rewriteRun(
          //language=java
          java(
            """
              interface In {}
              final class Out {}
              """
          ),
          //language=java
          java(
            """
              import java.util.function.Function;
              class Test {
                  void test(Function<In, Out> f) {
                  }
              }
              """,
            """
              import java.util.function.Function;
              class Test {
                  void test(Function<? super In, Out> f) {
                  }
              }
              """
          )
        );
    }

    @Issue("https://github.com/openrewrite/rewrite-static-analysis/issues/277")
    @Test
    void overriddenMethodOfUnresolvedSuperclass() {
        // The supertype is outside the source set, so `isOverride()` cannot see the declaration being
        // implemented; the `@Override` annotation is the only remaining signal.
        rewriteRun(
          spec -> spec.typeValidationOptions(TypeValidation.none()),
          //language=java
          java(
            """
              import java.util.function.Function;

              interface In {}
              interface Out {}

              class A extends SomeExternalClassThatCannotBeChanged {
                  @Override
                  public void test(Function<In, Out> f) {
                  }
              }
              """
          )
        );
    }

    @Test
    void overriddenMethods() {
        rewriteRun(
          //language=java
          java(
            """
              interface In {}
              interface Out {}
              """
          ),
          //language=java
          java(
            """
              import java.util.function.Function;
              interface TestInterface {
                  void test(Function<In, Out> f);
              }
              """,
            SourceSpec::skip
          ),
          //language=java
          java(
            """
              import java.util.function.Function;
              class TestImpl implements TestInterface {
                  @Override
                  public void test(Function<In, Out> f) {
                  }
              }
              """
          )
        );
    }
    @Test
    void doesNotAddVarianceToParameterStoredInvariantly() {
        rewriteRun(
          //language=java
          java(
            """
              import java.util.function.Function;

              class FieldTest<Input, Output> {
                  private final Function<Input, Output> mapper;

                  FieldTest(Function<Input, Output> mapper) {
                      this.mapper = mapper;
                  }
              }
              """
          ),
          //language=java
          java(
            """
              import java.util.function.Function;

              class LocalTest<Input, Output> {
                  void test(Function<Input, Output> mapper) {
                      Function<Input, Output> stored = mapper;
                  }
              }
              """
          )
        );
    }

    @Test
    void addsVarianceWhenFieldAcceptsIt() {
        rewriteRun(
          //language=java
          java(
            """
              import java.util.function.Function;

              class Test<Input, Output> {
                  private final Function<? super Input, ? extends Output> mapper;

                  Test(Function<Input, Output> mapper) {
                      this.mapper = mapper;
                  }
              }
              """,
            """
              import java.util.function.Function;

              class Test<Input, Output> {
                  private final Function<? super Input, ? extends Output> mapper;

                  Test(Function<? super Input, ? extends Output> mapper) {
                      this.mapper = mapper;
                  }
              }
              """
          )
        );
    }

    @Test
    void addsVarianceWhenInvariantFieldStoresAdapter() {
        rewriteRun(
          //language=java
          java(
            """
              import java.util.function.Function;

              class Test<Input, Output> {
                  private Function<Input, Output> mapper;

                  void set(Function<Input, Output> mapper) {
                      this.mapper = input -> mapper.apply(input);
                  }

                  void forward(Function<Input, Output> mapper) {
                      set(mapper);
                  }
              }
              """,
            """
              import java.util.function.Function;

              class Test<Input, Output> {
                  private Function<Input, Output> mapper;

                  void set(Function<? super Input, ? extends Output> mapper) {
                      this.mapper = input -> mapper.apply(input);
                  }

                  void forward(Function<? super Input, ? extends Output> mapper) {
                      set(mapper);
                  }
              }
              """
          )
        );
    }

    @Test
    void addsVarianceWhenParameterIsNormalizedInPlace() {
        rewriteRun(
          //language=java
          java(
            """
              import java.util.Objects;
              import java.util.function.Function;

              class Test<Input, Output> {
                  void use(Function<Input, Output> mapper) {
                      mapper = Objects.requireNonNull(mapper);
                  }

                  void forward(Function<Input, Output> mapper) {
                      use(mapper);
                  }
              }
              """,
            """
              import java.util.Objects;
              import java.util.function.Function;

              class Test<Input, Output> {
                  void use(Function<? super Input, ? extends Output> mapper) {
                      mapper = Objects.requireNonNull(mapper);
                  }

                  void forward(Function<? super Input, ? extends Output> mapper) {
                      use(mapper);
                  }
              }
              """
          )
        );
    }

    @Test
    void addsVarianceWhenParameterIsStoredInVar() {
        rewriteRun(
          //language=java
          java(
            """
              import java.util.function.Function;

              class Test<Input, Output> {
                  void use(Function<Input, Output> mapper) {
                      var stored = mapper;
                  }

                  void forward(Function<Input, Output> mapper) {
                      use(mapper);
                  }
              }
              """,
            """
              import java.util.function.Function;

              class Test<Input, Output> {
                  void use(Function<? super Input, ? extends Output> mapper) {
                      var stored = mapper;
                  }

                  void forward(Function<? super Input, ? extends Output> mapper) {
                      use(mapper);
                  }
              }
              """
          )
        );
    }

    @Test
    void addsVarianceWhenInvariantLocalStoresAdapterResult() {
        rewriteRun(
          //language=java
          java(
            """
              import java.util.function.Function;

              class Test<Input, Output> {
                  Function<Input, Output> adapt(Function<? super Input, ? extends Output> mapper) {
                      return input -> mapper.apply(input);
                  }

                  void use(Function<Input, Output> mapper) {
                      Function<Input, Output> stored = adapt(mapper);
                  }

                  void forward(Function<Input, Output> mapper) {
                      use(mapper);
                  }
              }
              """,
            """
              import java.util.function.Function;

              class Test<Input, Output> {
                  Function<Input, Output> adapt(Function<? super Input, ? extends Output> mapper) {
                      return input -> mapper.apply(input);
                  }

                  void use(Function<? super Input, ? extends Output> mapper) {
                      Function<Input, Output> stored = adapt(mapper);
                  }

                  void forward(Function<? super Input, ? extends Output> mapper) {
                      use(mapper);
                  }
              }
              """
          )
        );
    }

    @Test
    void doNotChangeParameterPassedToInvariantParameter() {
        rewriteRun(
          //language=java
          java(
            """
              import java.util.function.Function;

              class Test<Input, Output> {
                  private final Function<Input, Output> mapper;

                  Test(Function<Input, Output> mapper) {
                      this(mapper, 0);
                  }

                  Test(Function<Input, Output> mapper, int ignored) {
                      this.mapper = mapper;
                  }

                  void register(Function<Input, Output> mapper) {
                      store(mapper);
                  }

                  private void store(Function<Input, Output> mapper) {
                      Function<Input, Output> copy = mapper;
                  }
              }
              """
          )
        );
    }

    @Test
    void doNotChangeWhenStoredInPartiallyWildcardedField() {
        rewriteRun(
          //language=java
          java(
            """
              import java.util.function.Function;

              class Test<Input> {
                  private final Function<Input, ?> extractor;

                  <T> Test(Function<Input, T> extractor) {
                      this.extractor = extractor;
                  }
              }
              """
          )
        );
    }

    @Test
    void widensWhenStoredInFieldWithMatchingWildcards() {
        rewriteRun(
          //language=java
          java(
            """
              import java.util.function.Function;

              class Test<Input> {
                  private final Function<? super Input, ?> extractor;

                  <T> Test(Function<Input, T> extractor) {
                      this.extractor = extractor;
                  }
              }
              """,
            """
              import java.util.function.Function;

              class Test<Input> {
                  private final Function<? super Input, ?> extractor;

                  <T> Test(Function<? super Input, ? extends T> extractor) {
                      this.extractor = extractor;
                  }
              }
              """
          )
        );
    }

    @Test
    void doNotChangeInterfaceMethodImplementedInSourceSet() {
        rewriteRun(
          //language=java
          java(
            """
              import java.util.function.Function;
              interface Buffer<In, Out> {
                  void compact(Function<In, Out> f);
              }
              """
          ),
          //language=java
          java(
            """
              import java.util.function.Function;
              abstract class AbstractBuffer<In, Out> implements Buffer<In, Out> {
                  public void compact(Function<In, Out> f) {
                  }
              }
              """
          ),
          //language=java
          java(
            """
              import java.util.function.Function;
              class Node {
                  void visit(Function<Node, Node> f) {
                  }
              }
              class Leaf extends Node {
                  @Override
                  void visit(Function<Node, Node> f) {
                  }
              }
              """
          ),
          //language=java
          java(
            """
              import java.util.function.Function;
              interface Visitable {
                  void accept(Function<Visitable, String> f);
              }
              class Anonymous {
                  Visitable visitable = new Visitable() {
                      @Override
                      public void accept(Function<Visitable, String> f) {
                      }
                  };
              }
              """
          )
        );
    }

    @Test
    void doNotChangeWhenDefaultMethodResultIsUsed() {
        rewriteRun(
          //language=java
          java(
            """
              import java.util.function.Function;

              class Test<Input, Output> {
                  Function<Input, Output> composed;

                  void set(Function<Input, Output> mapper) {
                      composed = mapper.andThen(o -> o);
                  }
              }
              """
          )
        );
    }

    @Test
    void widensWhenFunctionalResultIsStoredInVar() {
        rewriteRun(
          spec -> spec.recipe(new DeclarationSiteTypeVariance(
            List.of("java.util.function.Supplier<OUT>"),
            List.of("java.lang.*"),
            true
          )),
          //language=java
          java(
            """
              import java.util.function.Supplier;

              class Test<Output> {
                  void use(Supplier<Output> supplier, Output fallback) {
                      var value = supplier.get();
                      value = fallback;
                  }
              }
              """,
            """
              import java.util.function.Supplier;

              class Test<Output> {
                  void use(Supplier<? extends Output> supplier, Output fallback) {
                      var value = supplier.get();
                      value = fallback;
                  }
              }
              """
          )
        );
    }

    @Test
    void widensWhenUsesAcceptWideningType() {
        rewriteRun(
          //language=java
          java(
            """
              import java.util.List;
              import java.util.Objects;
              import java.util.function.Function;
              import java.util.stream.Collectors;

              class Test<Input, Output> {
                  List<Output> map(List<Input> inputs, Function<Input, Output> mapper) {
                      Objects.requireNonNull(mapper, "mapper");
                      check(mapper);
                      if (mapper == null) {
                          return null;
                      }
                      Output first = mapper.apply(inputs.get(0));
                      inputs.stream().map(mapper::apply).count();
                      return inputs.stream().map(mapper).collect(Collectors.toList());
                  }

                  void check(Object o) {
                  }
              }
              """,
            """
              import java.util.List;
              import java.util.Objects;
              import java.util.function.Function;
              import java.util.stream.Collectors;

              class Test<Input, Output> {
                  List<Output> map(List<Input> inputs, Function<? super Input, ? extends Output> mapper) {
                      Objects.requireNonNull(mapper, "mapper");
                      check(mapper);
                      if (mapper == null) {
                          return null;
                      }
                      Output first = mapper.apply(inputs.get(0));
                      inputs.stream().map(mapper::apply).count();
                      return inputs.stream().map(mapper).collect(Collectors.toList());
                  }

                  void check(Object o) {
                  }
              }
              """
          )
        );
    }

    @Test
    void doNotChangeKotlinSources() {
        rewriteRun(
          //language=kotlin
          kotlin(
            """
              import java.util.function.Function

              class Test {
                  fun test(f: Function<String, Int>) {
                  }
              }
              """
          )
        );
    }

    @Test
    void doNotChangeWhenInferenceWouldCaptureTheWildcard() {
        rewriteRun(
          //language=java
          java(
            """
              import java.util.Optional;
              import java.util.function.Function;

              class Test<In, Out> {
                  Out viaMap(Optional<In> o, Function<In, Out> f, Out def) {
                      return o.map(f).orElse(def);
                  }

                  Out viaLambda(Optional<In> o, Function<In, Out> f, Out def) {
                      return o.map(x -> f.apply(x)).orElse(def);
                  }

                  Out viaMethodReference(Optional<In> o, Function<In, Out> f, Out def) {
                      return o.map(f::apply).orElse(def);
                  }
              }
              """
          )
        );
    }

    @Test
    void widensWhenTargetTypeFixesInference() {
        rewriteRun(
          //language=java
          java(
            """
              import java.util.List;
              import java.util.Optional;
              import java.util.function.Function;
              import java.util.stream.Collectors;

              class Test<In, Out> {
                  Function<In, Out> field;

                  List<Out> collect(List<In> l, Function<In, Out> f) {
                      return l.stream().map(f).collect(Collectors.toList());
                  }

                  long count(List<In> l, Function<In, Out> f) {
                      return l.stream().map(f::apply).count();
                  }

                  void adapt(Function<In, Out> f) {
                      field = x -> f.apply(x);
                  }

                  Optional<Out> map(Optional<In> o, Function<In, Out> f) {
                      return o.map(f);
                  }

                  void wrap(Function<In, Out> f, In in) {
                      Optional<Out> r = Optional.of(f.apply(in));
                  }
              }
              """,
            """
              import java.util.List;
              import java.util.Optional;
              import java.util.function.Function;
              import java.util.stream.Collectors;

              class Test<In, Out> {
                  Function<In, Out> field;

                  List<Out> collect(List<In> l, Function<? super In, ? extends Out> f) {
                      return l.stream().map(f).collect(Collectors.toList());
                  }

                  long count(List<In> l, Function<? super In, ? extends Out> f) {
                      return l.stream().map(f::apply).count();
                  }

                  void adapt(Function<? super In, ? extends Out> f) {
                      field = x -> f.apply(x);
                  }

                  Optional<Out> map(Optional<In> o, Function<? super In, ? extends Out> f) {
                      return o.map(f);
                  }

                  void wrap(Function<? super In, ? extends Out> f, In in) {
                      Optional<Out> r = Optional.of(f.apply(in));
                  }
              }
              """
          )
        );
    }

    @Test
    void doNotChangeWhenLambdaIsPassedToFunctionalMethod() {
        rewriteRun(
          spec -> spec.recipe(new DeclarationSiteTypeVariance(
            List.of("java.util.function.Consumer<IN>"),
            null,
            null
          )),
          //language=java
          java(
            """
              import java.util.function.Consumer;

              class Test {
                  interface Task {
                      void run();
                  }

                  void m(Consumer<Task> c) {
                      c.accept(() -> {
                      });
                  }

                  void ternary(Consumer<Task> c, Task t, boolean b) {
                      c.accept(b ? () -> {
                      } : t);
                  }
              }
              """
          )
        );
    }

    @Test
    void doNotChangeInterfaceImplementedByLambda() {
        rewriteRun(
          //language=java
          java(
            """
              import java.util.function.Function;

              interface Transformer<In, Out> {
                  Out transform(Function<In, Out> f);
              }
              """
          ),
          //language=java
          java(
            """
              import java.util.function.Function;

              class Usage<In, Out> {
                  static <A, B> B helper(Function<A, B> f) {
                      return null;
                  }

                  Transformer<In, Out> lambda = f -> helper(f);
              }
              """,
            """
              import java.util.function.Function;

              class Usage<In, Out> {
                  static <A, B> B helper(Function<? super A, ? extends B> f) {
                      return null;
                  }

                  Transformer<In, Out> lambda = f -> helper(f);
              }
              """
          )
        );
    }

    @Test
    void doNotChangeMethodsRelatedThroughInheritance() {
        rewriteRun(
          //language=java
          java(
            """
              import java.util.function.Function;

              interface I<In, Out> {
                  void m(Function<In, Out> f);
              }
              """
          ),
          //language=java
          java(
            """
              import java.util.function.Function;

              class Base<In, Out> {
                  public void m(Function<In, Out> f) {
                      f.apply(null);
                  }
              }
              """
          ),
          //language=java
          java(
            """
              class Sub<In, Out> extends Base<In, Out> implements I<In, Out> {
              }
              """
          )
        );
    }

    @Test
    void doNotChangeOverloadedMethod() {
        rewriteRun(
          spec -> spec.recipe(new DeclarationSiteTypeVariance(
            List.of("java.util.function.Supplier<OUT>"),
            null,
            null
          )),
          //language=java
          java(
            """
              import java.util.function.Supplier;

              class Test<Out> {
                  static String m(Supplier<Object> s) {
                      s.get();
                      return "supplier";
                  }

                  static String m(Object o) {
                      return "object";
                  }
              }
              """
          )
        );
    }

    @Test
    void privateMethodWithSameNameAsSuperclassMethodIsNotAnOverride() {
        rewriteRun(
          spec -> spec.recipe(new DeclarationSiteTypeVariance(
            List.of("java.util.function.Consumer<IN>"),
            null,
            null
          )),
          //language=java
          java(
            """
              import java.util.ArrayList;
              import java.util.List;
              import java.util.Objects;
              import java.util.function.Consumer;

              class Base {
                  private void acceptValues(String name, Consumer<List<String>> consumer) {
                  }
              }

              class Builder extends Base {
                  Builder values(Consumer<List<String>> consumer) {
                      acceptValues("values", consumer);
                      return this;
                  }

                  private void acceptValues(String name, Consumer<List<String>> consumer) {
                      Objects.requireNonNull(consumer, "consumer");
                      consumer.accept(new ArrayList<>());
                  }
              }
              """,
            """
              import java.util.ArrayList;
              import java.util.List;
              import java.util.Objects;
              import java.util.function.Consumer;

              class Base {
                  private void acceptValues(String name, Consumer<? super List<String>> consumer) {
                  }
              }

              class Builder extends Base {
                  Builder values(Consumer<? super List<String>> consumer) {
                      acceptValues("values", consumer);
                      return this;
                  }

                  private void acceptValues(String name, Consumer<? super List<String>> consumer) {
                      Objects.requireNonNull(consumer, "consumer");
                      consumer.accept(new ArrayList<>());
                  }
              }
              """
          )
        );
    }

    @Test
    void widensOverloadsNoArgumentCanFitTogether() {
        rewriteRun(
          spec -> spec.recipe(new DeclarationSiteTypeVariance(
            List.of("java.util.function.Supplier<OUT>", "java.util.function.Function<IN, OUT>"),
            null,
            null
          )),
          //language=java
          java(
            """
              import java.util.function.Function;
              import java.util.function.Supplier;

              class Test<In, Out> {
                  void define(String key, Supplier<Out> supplier) {
                      supplier.get();
                  }

                  void define(String key, Function<In, Out> function) {
                      function.apply(null);
                  }

                  void map(String key, Function<In, Out> function) {
                      function.apply(null);
                  }

                  void map(String[] keys, Function<In, Out> function) {
                      function.apply(null);
                  }
              }
              """,
            """
              import java.util.function.Function;
              import java.util.function.Supplier;

              class Test<In, Out> {
                  void define(String key, Supplier<? extends Out> supplier) {
                      supplier.get();
                  }

                  void define(String key, Function<? super In, ? extends Out> function) {
                      function.apply(null);
                  }

                  void map(String key, Function<? super In, ? extends Out> function) {
                      function.apply(null);
                  }

                  void map(String[] keys, Function<? super In, ? extends Out> function) {
                      function.apply(null);
                  }
              }
              """
          )
        );
    }

    @Test
    void widensOverloadsTakingUnrelatedFunctionalInterfaces() {
        rewriteRun(
          spec -> spec.recipe(new DeclarationSiteTypeVariance(
            List.of("java.util.function.Supplier<OUT>", "java.util.function.Function<IN, OUT>"),
            null,
            null
          )),
          //language=java
          java(
            """
              import java.util.function.BiFunction;
              import java.util.function.Function;
              import java.util.function.Supplier;

              class ParamDefine<T> {
                  <U> ParamDefine(String key, BiFunction<String, U, T> loader) {
                  }

                  ParamDefine(String key, Function<ParamSet, T> loader) {
                      loader.apply(null);
                  }

                  static <T, U> ParamDefine<T> define(String key, BiFunction<String, U, T> loader) {
                      return new ParamDefine<>(key, loader);
                  }

                  static <T> ParamDefine<T> define(String key, Function<ParamSet, T> loader) {
                      return new ParamDefine<>(key, loader);
                  }

                  static <T> ParamDefine<T> define(String key, Supplier<T> operator) {
                      return new ParamDefine<T>(key, (String k, Object source) -> operator.get());
                  }
              }

              class ParamSet {
              }
              """,
            """
              import java.util.function.BiFunction;
              import java.util.function.Function;
              import java.util.function.Supplier;

              class ParamDefine<T> {
                  <U> ParamDefine(String key, BiFunction<String, U, T> loader) {
                  }

                  ParamDefine(String key, Function<? super ParamSet, ? extends T> loader) {
                      loader.apply(null);
                  }

                  static <T, U> ParamDefine<T> define(String key, BiFunction<String, U, T> loader) {
                      return new ParamDefine<>(key, loader);
                  }

                  static <T> ParamDefine<T> define(String key, Function<? super ParamSet, ? extends T> loader) {
                      return new ParamDefine<>(key, loader);
                  }

                  static <T> ParamDefine<T> define(String key, Supplier<? extends T> operator) {
                      return new ParamDefine<T>(key, (String k, Object source) -> operator.get());
                  }
              }

              class ParamSet {
              }
              """
          )
        );
    }

    @Test
    void doNotChangeOverloadTakingSubtypeOfParameterType() {
        rewriteRun(
          //language=java
          java(
            """
              import java.util.function.Function;
              import java.util.function.UnaryOperator;

              class Test<In, Out> {
                  void apply(String key, Function<In, Out> f) {
                      f.apply(null);
                  }

                  void apply(String key, UnaryOperator<In> op) {
                      op.apply(null);
                  }
              }
              """
          )
        );
    }

    @Test
    void doNotChangeWhenSelfTypeWitnessOrBoundKeepsTheOldType() {
        rewriteRun(
          //language=java
          java(
            """
              import java.util.List;
              import java.util.Objects;
              import java.util.Optional;
              import java.util.function.Function;
              import java.util.stream.Stream;

              class Box<X> {
                  static <X> Box<X> of(Optional<X> o) {
                      return new Box<>();
                  }

                  <U extends X> Box<X> with(U u) {
                      return this;
                  }
              }

              class Test<In, Out> {
                  Stream<Out> parallel(List<In> in, Function<In, Out> f) {
                      return in.stream().map(f).parallel();
                  }

                  void witness(Function<In, Out> f, In in) {
                      Objects.<Function<In, Out>>requireNonNull(f).apply(in);
                  }

                  void bound(Function<In, Out> f, Optional<In> o, Out out) {
                      Box.of(o.map(f)).with(out);
                  }
              }
              """
          )
        );
    }

    @Test
    void javadocMentionDoesNotCount() {
        rewriteRun(
          //language=java
          java(
            """
              import java.util.function.Function;

              class Test<In, Out> {
                  /**
                   * @param mapper the {@code mapper}
                   */
                  void use(Function<In, Out> mapper) {
                      mapper.apply(null);
                  }
              }
              """,
            """
              import java.util.function.Function;

              class Test<In, Out> {
                  /**
                   * @param mapper the {@code mapper}
                   */
                  void use(Function<? super In, ? extends Out> mapper) {
                      mapper.apply(null);
                  }
              }
              """
          )
        );
    }

    @Test
    void doNotChangeWhenCaptureMeetsSwitchOrArray() {
        rewriteRun(
          spec -> spec.recipe(new DeclarationSiteTypeVariance(
            List.of("java.util.function.Supplier<OUT>"),
            null,
            null
          )),
          //language=java
          java(
            """
              import java.util.function.Supplier;

              class Test {
                  enum Color { RED, GREEN }

                  int selector(Supplier<Color> s) {
                      switch (s.get()) {
                          case RED:
                              return 1;
                          default:
                              return 0;
                      }
                  }

                  Color element(Supplier<Color[]> s) {
                      return s.get()[0];
                  }
              }
              """
          )
        );
    }

    @Test
    void doNotChangeInterfaceInheritedByLambdaTarget() {
        rewriteRun(
          //language=java
          java(
            """
              import java.util.function.Function;

              interface A<In, Out> {
                  void m(Function<In, Out> f);
              }
              """
          ),
          //language=java
          java(
            """
              interface B<In, Out> extends A<In, Out> {
              }
              """
          ),
          //language=java
          java(
            """
              class Usage<In, Out> {
                  B<In, Out> b = f -> {
                  };
              }
              """
          )
        );
    }

    @Test
    void doNotChangeWhenVarargsOverloadExists() {
        rewriteRun(
          //language=java
          java(
            """
              import java.util.function.Function;

              class Test<In, Out> {
                  String m(Function<In, Out> f) {
                      f.apply(null);
                      return "fn";
                  }

                  String m(Object o, Object... rest) {
                      return "varargs";
                  }
              }
              """
          )
        );
    }

    @Test
    void doNotChangeWhenReceiverMethodTakesTheCapture() {
        rewriteRun(
          //language=java
          java(
            """
              import java.util.Optional;
              import java.util.function.Function;

              class Test<In, Out> {
                  Out orElse(Optional<In> o, Function<In, Out> f, Out def) {
                      return o.map(f).orElseGet(() -> def);
                  }
              }
              """
          )
        );
    }

    @Test
    void widensWhenCaptureFlowsSafelyThroughReceivers() {
        rewriteRun(
          //language=java
          java(
            """
              import java.util.List;
              import java.util.Objects;
              import java.util.Optional;
              import java.util.function.Function;
              import java.util.stream.Collectors;

              class Test<In, Out> {
                  Out orNull(Optional<In> o, Function<In, Out> f) {
                      return o.map(f).orElse(null);
                  }

                  List<Out> nonNull(List<In> l, Function<In, Out> f) {
                      return l.stream().map(f).filter(Objects::nonNull).distinct().collect(Collectors.toList());
                  }
              }
              """,
            """
              import java.util.List;
              import java.util.Objects;
              import java.util.Optional;
              import java.util.function.Function;
              import java.util.stream.Collectors;

              class Test<In, Out> {
                  Out orNull(Optional<In> o, Function<? super In, ? extends Out> f) {
                      return o.map(f).orElse(null);
                  }

                  List<Out> nonNull(List<In> l, Function<? super In, ? extends Out> f) {
                      return l.stream().map(f).filter(Objects::nonNull).distinct().collect(Collectors.toList());
                  }
              }
              """
          )
        );
    }

    @Test
    void doNotChangeWhenPassedOnToSameArityOverloadThatStaysUnchanged() {
        rewriteRun(
          //language=java
          java(
            """
              import java.util.function.Function;

              class Test<In, Out> {
                  Function<In, Out> field;

                  void a(Function<In, Out> f) {
                      store(f, 1);
                  }

                  private void store(Function<In, Out> g, Integer i) {
                      field = g;
                  }

                  private void store(Function<In, Out> g, String s) {
                  }

                  void b(Function<In, Out> f, Integer[] xs) {
                      store(f, xs);
                  }

                  @Marker
                  private void store(Function<In, Out> g, Integer[] i) {
                      field = g;
                  }

                  private void store(Function<In, Out> g, String[] s) {
                  }
              }

              @interface Marker {}
              """,
            """
              import java.util.function.Function;

              class Test<In, Out> {
                  Function<In, Out> field;

                  void a(Function<In, Out> f) {
                      store(f, 1);
                  }

                  private void store(Function<In, Out> g, Integer i) {
                      field = g;
                  }

                  private void store(Function<? super In, ? extends Out> g, String s) {
                  }

                  void b(Function<In, Out> f, Integer[] xs) {
                      store(f, xs);
                  }

                  @Marker
                  private void store(Function<In, Out> g, Integer[] i) {
                      field = g;
                  }

                  private void store(Function<? super In, ? extends Out> g, String[] s) {
                  }
              }

              @interface Marker {}
              """
          )
        );
    }

    @Test
    void doNotChangeAnnotatedMethodsOrParameters() {
        rewriteRun(
          spec -> spec.recipe(new DeclarationSiteTypeVariance(
            List.of("java.util.function.Supplier<OUT>"),
            null,
            null
          )),
          //language=java
          java(
            """
              import java.lang.annotation.Retention;
              import java.lang.annotation.RetentionPolicy;
              import java.util.function.Supplier;

              @Retention(RetentionPolicy.RUNTIME)
              @interface Provides {}

              @Retention(RetentionPolicy.RUNTIME)
              @interface Named {
                  String value();
              }

              class Module<Foo> {
                  @Provides
                  Foo provide(Supplier<Foo> s) {
                      return s.get();
                  }

                  Foo qualified(@Named("x") Supplier<Foo> s) {
                      return s.get();
                  }

                  @Deprecated
                  Foo plain(@SuppressWarnings("unused") Supplier<Foo> s) {
                      return s.get();
                  }

                  @Nullable
                  Foo nullness(@Nonnull Supplier<Foo> s) {
                      return s.get();
                  }
              }

              @interface Nullable {}
              @interface Nonnull {}
              """,
            """
              import java.lang.annotation.Retention;
              import java.lang.annotation.RetentionPolicy;
              import java.util.function.Supplier;

              @Retention(RetentionPolicy.RUNTIME)
              @interface Provides {}

              @Retention(RetentionPolicy.RUNTIME)
              @interface Named {
                  String value();
              }

              class Module<Foo> {
                  @Provides
                  Foo provide(Supplier<Foo> s) {
                      return s.get();
                  }

                  Foo qualified(@Named("x") Supplier<Foo> s) {
                      return s.get();
                  }

                  @Deprecated
                  Foo plain(@SuppressWarnings("unused") Supplier<? extends Foo> s) {
                      return s.get();
                  }

                  @Nullable
                  Foo nullness(@Nonnull Supplier<? extends Foo> s) {
                      return s.get();
                  }
              }

              @interface Nullable {}
              @interface Nonnull {}
              """
          )
        );
    }

    @Test
    void doNotChangeWhenSubtypeOverloadsTheMethod() {
        rewriteRun(
          //language=java
          java(
            """
              import java.util.function.Function;

              class A<In, Out> {
                  String m(Function<In, Out> f) {
                      return "A";
                  }
              }
              """
          ),
          //language=java
          java(
            """
              class B<In, Out> extends A<In, Out> {
                  String m(Object o) {
                      return "B";
                  }
              }
              """
          )
        );
    }

    @Test
    void widensWhenOnlyInputSideChangesThroughGenericCall() {
        rewriteRun(
          //language=java
          java(
            """
              import java.util.Optional;
              import java.util.function.Function;

              class Test<In> {
                  String name(Optional<In> o, Function<In, String> f) {
                      return o.map(f).orElse("");
                  }
              }
              """,
            """
              import java.util.Optional;
              import java.util.function.Function;

              class Test<In> {
                  String name(Optional<In> o, Function<? super In, String> f) {
                      return o.map(f).orElse("");
                  }
              }
              """
          )
        );
    }

    @Test
    void doNotChangeMethodsOfClassWithUnresolvedSupertype() {
        rewriteRun(
          spec -> spec.typeValidationOptions(TypeValidation.none()),
          //language=java
          java(
            """
              interface In {}
              interface Out {}
              """
          ),
          //language=java
          java(
            """
              import java.util.function.Function;

              class Test extends com.example.Missing {
                  void m(Function<In, Out> f) {
                      f.apply(null);
                  }
              }

              class Anonymous {
                  Object o = new com.example.Other() {
                      void m(Function<In, Out> f) {
                          f.apply(null);
                      }
                  };
              }
              """
          )
        );
    }

    @Test
    void widensParameterNamedLikeItsMethod() {
        rewriteRun(
          //language=java
          java(
            """
              interface In {}
              interface Out {}
              """
          ),
          //language=java
          java(
            """
              import java.util.function.Function;

              class Test {
                  void mapper(Function<In, Out> mapper) {
                      mapper.apply(null);
                  }
              }
              """,
            """
              import java.util.function.Function;

              class Test {
                  void mapper(Function<? super In, ? extends Out> mapper) {
                      mapper.apply(null);
                  }
              }
              """
          )
        );
    }

    @Test
    void widensReturnedParameterOnlyWhenReturnTypeAcceptsIt() {
        rewriteRun(
          //language=java
          java(
            """
              interface In {}
              interface Out {}
              """
          ),
          //language=java
          java(
            """
              import java.util.function.Function;

              class Test {
                  Function<In, Out> same(Function<In, Out> f) {
                      return f;
                  }

                  Function<? super In, ? extends Out> wide(Function<In, Out> f) {
                      return f;
                  }

                  Object object(Function<In, Out> f) {
                      return f;
                  }
              }
              """,
            """
              import java.util.function.Function;

              class Test {
                  Function<In, Out> same(Function<In, Out> f) {
                      return f;
                  }

                  Function<? super In, ? extends Out> wide(Function<? super In, ? extends Out> f) {
                      return f;
                  }

                  Object object(Function<? super In, ? extends Out> f) {
                      return f;
                  }
              }
              """
          )
        );
    }

    @Test
    void widensParameterPassedToConstructorThatAcceptsIt() {
        rewriteRun(
          //language=java
          java(
            """
              interface In {}
              interface Out {}
              """
          ),
          //language=java
          java(
            """
              import java.util.function.Function;

              class Holder {
                  Holder(Function<In, Out> f) {
                      f.apply(null);
                  }
              }

              class Wrap<T> {
                  Wrap(Function<T, Out> f) {
                  }
              }

              class Invariant {
                  Function<In, Out> f;

                  Invariant(Function<In, Out> f) {
                      this.f = f;
                  }
              }

              class Test {
                  Object holder(Function<In, Out> f) {
                      return new Holder(f);
                  }

                  Object diamond(Function<In, Out> f) {
                      return new Wrap<>(f);
                  }

                  Object invariant(Function<In, Out> f) {
                      return new Invariant(f);
                  }
              }
              """,
            """
              import java.util.function.Function;

              class Holder {
                  Holder(Function<? super In, ? extends Out> f) {
                      f.apply(null);
                  }
              }

              class Wrap<T> {
                  Wrap(Function<? super T, ? extends Out> f) {
                  }
              }

              class Invariant {
                  Function<In, Out> f;

                  Invariant(Function<In, Out> f) {
                      this.f = f;
                  }
              }

              class Test {
                  Object holder(Function<? super In, ? extends Out> f) {
                      return new Holder(f);
                  }

                  Object diamond(Function<? super In, ? extends Out> f) {
                      return new Wrap<>(f);
                  }

                  Object invariant(Function<In, Out> f) {
                      return new Invariant(f);
                  }
              }
              """
          )
        );
    }

    @Test
    void widensParameterPassedToStreamAndOptional() {
        rewriteRun(
          //language=java
          java(
            """
              interface In {}
              interface Out {}
              """
          ),
          //language=java
          java(
            """
              import java.util.List;
              import java.util.Optional;
              import java.util.function.Function;
              import java.util.stream.Collectors;

              class Test {
                  List<Out> collect(List<In> in, Function<In, Out> f) {
                      return in.stream().map(f).collect(Collectors.toList());
                  }

                  Out orElse(Optional<In> in, Function<In, Out> f) {
                      return in.map(f).orElse(null);
                  }

                  Optional<Out> lambda(Optional<In> in, Function<In, Out> f) {
                      return in.map(x -> {
                          return f.apply(x);
                      });
                  }
              }
              """,
            """
              import java.util.List;
              import java.util.Optional;
              import java.util.function.Function;
              import java.util.stream.Collectors;

              class Test {
                  List<Out> collect(List<In> in, Function<? super In, ? extends Out> f) {
                      return in.stream().map(f).collect(Collectors.toList());
                  }

                  Out orElse(Optional<In> in, Function<? super In, ? extends Out> f) {
                      return in.map(f).orElse(null);
                  }

                  Optional<Out> lambda(Optional<In> in, Function<? super In, ? extends Out> f) {
                      return in.map(x -> {
                          return f.apply(x);
                      });
                  }
              }
              """
          )
        );
    }
}
