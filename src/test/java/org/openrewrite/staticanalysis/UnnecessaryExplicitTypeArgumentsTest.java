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
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junitpioneer.jupiter.ExpectedToFail;
import org.openrewrite.DocumentExample;
import org.openrewrite.Issue;
import org.openrewrite.test.RecipeSpec;
import org.openrewrite.test.RewriteTest;

import static org.openrewrite.java.Assertions.java;
import static org.openrewrite.kotlin.Assertions.kotlin;

@SuppressWarnings({"RedundantTypeArguments", "InfiniteRecursion", "CodeBlock2Expr"})
class UnnecessaryExplicitTypeArgumentsTest implements RewriteTest {
    @Override
    public void defaults(RecipeSpec spec) {
        spec.recipe(new UnnecessaryExplicitTypeArguments());
    }

    @DocumentExample
    @Test
    void unnecessaryExplicitTypeArguments() {
        rewriteRun(
          //language=java
          java(
            """
              class Test {
                  <T> T test() {
                      String s = this.<String>test();
                      Object o = this.<String>test();
                      return this.<T>test();
                  }

                  Object o() {
                      return this.<String>test();
                  }
              }
              """,
            """
              class Test {
                  <T> T test() {
                      String s = this.test();
                      Object o = this.<String>test();
                      return this.test();
                  }

                  Object o() {
                      return this.<String>test();
                  }
              }
              """
          )
        );
    }

    @Test
    @Disabled
    void withinLambda() {
        rewriteRun(
          //language=java
          java(
            """
              import java.util.function.Function;

              class Test {
                  Function<Object, Object> f = (d1) -> {
                      return this.<Object>test();
                  };

                  <T> T test() {
                      return this.test();
                  }
              }
              """,
            """
              import java.util.function.Function;

              class Test {
                  Function<Object, Object> f = (d1) -> {
                      return this.test();
                  };

                  <T> T test() {
                      return this.test();
                  }
              }
              """
          )
        );
    }

    @Issue("https://github.com/openrewrite/rewrite/issues/1211")
    @Test
    void doesNotIntroduceAmbiguity() {
        rewriteRun(
          //language=java
          java(
            """
              import java.util.Collection;

              public class Test {

                  <G> G foo() {
                      return null;
                  }

                  <E> E fetch(E entity) {
                      return null;
                  }

                  <E> Collection<E> fetch(Collection<E> entity) {
                      return null;
                  }

                  void test() {
                      Integer bar = fetch(this.<Integer>foo());
                  }
              }
              """
          )
        );
    }

    @ExpectedToFail
    @Issue("https://github.com/openrewrite/rewrite-static-analysis/issues/164")
    @Test
    void doesNotRemoveNecessaryTypeArguments() {
        rewriteRun(
          //language=java
          java(
            """
              import java.util.Optional;
              import java.util.stream.Stream;
              public class Test {
                  void test() {
                      Stream.of("hi")
                              .map(it -> it == null ? Optional.<String>empty() : Optional.of(it))
                              .flatMap(Optional::stream)
                              .map(this::mapper); //this requires the type information
                  }
                  Optional<String> mapper(String value) {
                      return Optional.ofNullable(value)
                              .filter("hi"::equals);
                  }
              }
              """
          )
        );
    }



    @SuppressWarnings("UnnecessaryLocalVariable")
    @Issue("https://github.com/openrewrite/rewrite/issues/2818")
    @Test
    void assignedToVar() {
        rewriteRun(
          //language=java
          java(
            """
              import java.util.List;

              public class Test {

                  List<String> test() {
                      var l = List.<String> of("x");
                      return l;
                  }
              }
              """
          )
        );
    }

    @Test
    void containerInitialization() {
        rewriteRun(
          java(
            """
              import java.util.List;

              public class Test {
                  List<String> test() {
                      List<String> l = List.<String> of("x");
                      return l;
                  }
              }
              """,
            """
              import java.util.List;

              public class Test {
                  List<String> test() {
                      List<String> l = List. of("x");
                      return l;
                  }
              }
              """
          )
        );
    }

    @Nested
    class kotlinTest {
        @Test
        void doNotChangeIfHasNotTypeInference() {
            rewriteRun(
              kotlin(
                """
                  val foo = listOf<String>()
                  var bar = mutableMapOf<String, String>()
                  """
              )
            );
        }

        @Disabled
        @Test
        void changeIfHasTypeInference() {
            rewriteRun(
              kotlin(
                """
                  val foo = listOf<String>("a", "b")
                  var bar = mutableMapOf<String, Int>("a" to 1)
                  """,
                """
                  val foo = listOf("a", "b")
                  var bar = mutableMapOf("a" to 1)
                  """
              )
            );
        }

        @Test
        void doNotChangeSinceCompilerHasNoEnoughInformationToInferType() {
            rewriteRun(
              kotlin(
                """
                  fun <TClass, TValue> default(arg: String): TValue? {
                      return null
                  }

                  fun method() {
                      val email = default<Int, String?>("email")
                  }
                  """
              )
            );
        }
    }
}
