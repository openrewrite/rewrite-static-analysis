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
import org.openrewrite.Issue;
import org.openrewrite.test.RecipeSpec;
import org.openrewrite.test.RewriteTest;

import static org.openrewrite.java.Assertions.java;

class RemoveRedundantTypeCastConditionalTest implements RewriteTest {
    @Override
    public void defaults(RecipeSpec spec) {
        spec.recipe(new RemoveRedundantTypeCast());
    }

    @Issue("https://github.com/openrewrite/rewrite-static-analysis/issues/1116")
    @Test
    void retainNumberCastInConditionalInitializer() {
        rewriteRun(
          //language=java
          java(
            """
              class A {
                  Number choose(boolean flag, Integer value, Double fallback) {
                      Number selected = flag ? (Number) value : fallback;
                      return selected;
                  }

                  Number[] examples() {
                      return new Number[] {
                          choose(true, 7, 3.5),
                          choose(false, 7, 3.5),
                          choose(true, null, 3.5)
                      };
                  }
              }
              """
          )
        );
    }

    @Test
    void removeWideningCastInOrdinaryInitializer() {
        rewriteRun(
          //language=java
          java(
            """
              class A {
                  Number widen(Integer integer) {
                      Number number = (Number) integer;
                      return number;
                  }
              }
              """,
            """
              class A {
                  Number widen(Integer integer) {
                      Number number = integer;
                      return number;
                  }
              }
              """
          )
        );
    }
}
