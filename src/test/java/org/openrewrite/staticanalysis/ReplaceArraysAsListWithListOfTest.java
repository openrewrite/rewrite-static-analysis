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

@SuppressWarnings("java:S2699")
class ReplaceArraysAsListWithListOfTest implements RewriteTest {

    @Override
    public void defaults(RecipeSpec spec) {
        spec.recipe(new ReplaceArraysAsListWithListOf());
    }

    @DocumentExample
    @Test
    void replaceWithSingleArgument() {
        rewriteRun(
          //language=java
          java(
            """
              import java.util.Arrays;

              class A {
                  void foo() {
                      Arrays.asList("A");
                  }
              }
              """,
            """
              import java.util.List;

              class A {
                  void foo() {
                      List.of("A");
                  }
              }
              """
          )
        );
    }

    @Test
    void replaceWith10Arguments() {
        rewriteRun(
          //language=java
          java(
            """
              import java.util.Arrays;

              class A {
                  void foo() {
                      Arrays.asList(1, 2, 3, 4, 5, 6, 7, 8, 9, 10);
                  }
              }
              """,
            """
              import java.util.List;

              class A {
                  void foo() {
                      List.of(1, 2, 3, 4, 5, 6, 7, 8, 9, 10);
                  }
              }
              """
          )
        );
    }

    @Test
    void doNotReplaceWith11Arguments() {
        rewriteRun(
          //language=java
          java(
            """
              import java.util.Arrays;

              class A {
                  void foo() {
                      Arrays.asList(1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11);
                  }
              }
              """
          )
        );
    }
}
