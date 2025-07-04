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
import org.openrewrite.test.RecipeSpec;
import org.openrewrite.test.RewriteTest;

import static org.openrewrite.java.Assertions.java;

class SortedSetStreamToLinkedHashSetTest implements RewriteTest {

    @Override
    public void defaults(RecipeSpec spec) {
        spec.recipe(new SortedSetStreamToLinkedHashSet());
    }

    @Test
    @DocumentExample
    void changeSortedSetStreamToLinkedHashSet() {
        rewriteRun(
          //language=java
          java(
                """
            import java.util.Set;
            import java.util.stream.Collectors;

            class A {
              void method(Set<Integer> set) {
                  Set<Integer> sorted = set.stream().sorted().collect(Collectors.toSet());
              }
            }
            """,
                """
            import java.util.LinkedHashSet;
            import java.util.Set;
            import java.util.stream.Collectors;

            class A {
              void method(Set<Integer> set) {
                  Set<Integer> sorted = set.stream().sorted().collect(Collectors.toCollection(LinkedHashSet::new));
              }
            }
            """
          ));
    }

    @Test
    void changeSortedSetStreamToLinkedHashSetStaticImport() {
        rewriteRun(
          //language=java
          java(
                """
            import java.util.Set;
            import static java.util.stream.Collectors.toSet;

            class A {
              void method(Set<Integer> set) {
                  Set<Integer> sorted = set.stream().sorted().collect(toSet());
              }
            }
            """,
                """
            import java.util.LinkedHashSet;
            import java.util.Set;
            import java.util.stream.Collectors;

            class A {
              void method(Set<Integer> set) {
                  Set<Integer> sorted = set.stream().sorted().collect(Collectors.toCollection(LinkedHashSet::new));
              }
            }
            """
          ));
    }

    @Test
    void ignoreCollectToLinkedHashSet() {
        rewriteRun(
          //language=java
          java(
                """
            import java.util.Set;
            import java.util.LinkedHashSet;
            import java.util.stream.Collectors;

            class A {
              void method(Set<Integer> set) {
                  Set<Integer> sorted = set.stream().sorted().collect(Collectors.toCollection(LinkedHashSet::new));
              }
            }
            """
          ));
    }

    @Test
    void ignoreCollectToList() {
        rewriteRun(
          //language=java
          java(
                """
            import java.util.List;
            import java.util.Set;
            import java.util.stream.Collectors;

            class A {
              void method(Set<Integer> set) {
                  List<Integer> sorted = set.stream().sorted().collect(Collectors.toList());
              }
            }
            """
          ));
    }
}
