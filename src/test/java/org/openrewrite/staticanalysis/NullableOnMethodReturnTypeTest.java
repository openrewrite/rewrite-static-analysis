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

class NullableOnMethodReturnTypeTest implements RewriteTest {

    @Override
    public void defaults(RecipeSpec spec) {
        spec.recipe(new NullableOnMethodReturnType());
    }

    @DocumentExample
    @Test
    void nullableOnMethodReturnType() {
        rewriteRun(
          //language=java
          java(
            """
              import org.openrewrite.internal.lang.Nullable;
              class Test {
                  @Nullable
                  public String test() {
                  }
              }
              """,
            """
              import org.openrewrite.internal.lang.Nullable;
              class Test {

                  public @Nullable String test() {
                  }
              }
              """
          )
        );
    }

    @Test
    void dontTouchArguments() {
        rewriteRun(
          java(
            //language=java
            """
              import org.openrewrite.internal.lang.Nullable;
              class Test {
                  void test(@Nullable String s) {
                  }
              }
              """
          )
        );
    }

    @Test
    void noChangeForPackagePrivate() {
        rewriteRun(
          //language=java
          java(
            """
              import org.jspecify.annotations.Nullable;

              class Test {
                  @Nullable
                  String test() {
                      return null;
                  }
              }
              """
          )
        );
    }
}
