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
import org.openrewrite.config.Environment;
import org.openrewrite.test.RecipeSpec;
import org.openrewrite.test.RewriteTest;
import org.openrewrite.test.SourceSpec;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.openrewrite.java.Assertions.java;

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
          spec -> spec.recipe(Environment.builder()
            .scanRuntimeClasspath("org.openrewrite.java")
            .build()
            .activateRecipes("org.openrewrite.staticanalysis.CommonDeclarationSiteTypeVariances")),
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
}
