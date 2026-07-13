/*
 * Copyright 2026 the original author or authors.
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
import org.openrewrite.test.RecipeSpec;
import org.openrewrite.test.RewriteTest;

import static org.openrewrite.java.Assertions.java;

class UseStringCaseInsensitiveOrderTest implements RewriteTest {

    @Override
    public void defaults(RecipeSpec spec) {
        spec.recipe(new UseStringCaseInsensitiveOrderRecipe());
    }

    @DocumentExample
    @Test
    void lambdaRewrites() {
        rewriteRun(
          //language=java
          java(
            """
              import java.util.List;

              class Test {
                  void m(List<String> names) {
                      names.sort((a, b) -> a.compareToIgnoreCase(b));
                  }
              }
              """,
            """
              import java.util.List;

              class Test {
                  void m(List<String> names) {
                      names.sort(String.CASE_INSENSITIVE_ORDER);
                  }
              }
              """
          )
        );
    }

    @Test
    void methodReferenceRewrites() {
        rewriteRun(
          //language=java
          java(
            """
              import java.util.List;

              class Test {
                  void m(List<String> names) {
                      names.sort(String::compareToIgnoreCase);
                  }
              }
              """,
            """
              import java.util.List;

              class Test {
                  void m(List<String> names) {
                      names.sort(String.CASE_INSENSITIVE_ORDER);
                  }
              }
              """
          )
        );
    }

    @Test
    void reverseOrderUnchanged() {
        // Argument order is reversed → semantically the inverse comparator. Must NOT rewrite.
        rewriteRun(
          //language=java
          java(
            """
              import java.util.List;

              class Test {
                  void m(List<String> names) {
                      names.sort((a, b) -> b.compareToIgnoreCase(a));
                  }
              }
              """
          )
        );
    }

    @Test
    void toLowerCaseCompareUnchanged() {
        // Locale-aware case folding differs from String.CASE_INSENSITIVE_ORDER
        // (which is internally locale-insensitive). Must NOT rewrite.
        rewriteRun(
          //language=java
          java(
            """
              import java.util.List;

              class Test {
                  void m(List<String> names) {
                      names.sort((a, b) -> a.toLowerCase().compareTo(b.toLowerCase()));
                  }
              }
              """
          )
        );
    }

    @Test
    void nonComparatorContextUnchanged() {
        // Same lambda shape, but the target type is BiFunction — the rewrite to a
        // Comparator constant would be a type error here. Must NOT rewrite.
        rewriteRun(
          //language=java
          java(
            """
              import java.util.function.BiFunction;

              class Test {
                  BiFunction<String, String, Integer> cmp = (a, b) -> a.compareToIgnoreCase(b);
              }
              """
          )
        );
    }

    @Test
    void wrappedInNullsFirst() {
        // The inner lambda should still be recognised inside a wrapping comparator factory.
        rewriteRun(
          //language=java
          java(
            """
              import java.util.Comparator;
              import java.util.List;

              class Test {
                  void m(List<String> names) {
                      names.sort(Comparator.nullsFirst((a, b) -> a.compareToIgnoreCase(b)));
                  }
              }
              """,
            """
              import java.util.Comparator;
              import java.util.List;

              class Test {
                  void m(List<String> names) {
                      names.sort(Comparator.nullsFirst(String.CASE_INSENSITIVE_ORDER));
                  }
              }
              """
          )
        );
    }
}
