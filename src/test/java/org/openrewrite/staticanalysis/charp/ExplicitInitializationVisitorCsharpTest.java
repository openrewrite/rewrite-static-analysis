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
package org.openrewrite.staticanalysis.charp;

import org.junit.jupiter.api.Test;
import org.openrewrite.DocumentExample;
import org.openrewrite.java.JavaParser;
import org.openrewrite.staticanalysis.ExplicitInitialization;
import org.openrewrite.test.RecipeSpec;
import org.openrewrite.test.RewriteTest;

import static org.openrewrite.java.Assertions.java;
import static org.openrewrite.staticanalysis.charp.JavaToCsharp.toCsRecipe;

class ExplicitInitializationVisitorCsharpTest implements RewriteTest {

    @Override
    public void defaults(RecipeSpec spec) {
        spec.recipe(toCsRecipe(new ExplicitInitialization()))
          .parser(JavaParser.fromJavaVersion()
            .dependsOn(
              // C# defines nullable primitive. These are actually in the CLR wrapper objects.
              //language=java
              """
                class Nullable<T> {
                    Integer a;

                    Nullable(Integer a) {
                        this.a = a;
                    }
                }
                """
            )
          );
    }

    @DocumentExample
    @Test
    void removeExplicitInitialization() {
        rewriteRun(
          //language=java
          java(
            """
              class A {
                  // C#: int? a
                  Nullable<Integer> a = null;
                  // C#: int? a = 0
                  Nullable<Integer> a = new Nullable<>(0);
              }
              """,
            """
              class A {
                  // C#: int? a
                  Nullable<Integer> a;
                  // C#: int? a = 0
                  Nullable<Integer> a = new Nullable<>(0);
              }
              """
          )
        );
    }
}
