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
import org.openrewrite.test.RecipeSpec;
import org.openrewrite.test.RewriteTest;

import static org.openrewrite.java.Assertions.java;

class RemoveRedundantTypeCastConditionalControlsTest implements RewriteTest {
    @Override
    public void defaults(RecipeSpec spec) {
        spec.recipe(new RemoveRedundantTypeCast());
    }

    @Test
    void retainNumberCastOnFalseBranch() {
        rewriteRun(
          //language=java
          java(
            """
              class A {
                  Number choose(boolean flag, Integer value, Double fallback) {
                      Number selected = flag ? fallback : (Number) value;
                      return selected;
                  }
              }
              """
          )
        );
    }

    @Test
    void retainParenthesizedCastsAndOperandsOnBothBranches() {
        rewriteRun(
          //language=java
          java(
            """
              class A {
                  Number choose(boolean flag, Integer value, Double fallback) {
                      Number selected = flag ? ((Number) (value)) : ((Number) (fallback));
                      return selected;
                  }
              }
              """
          )
        );
    }

    @Test
    void retainNumberCastInNestedParenthesizedInitializer() {
        rewriteRun(
          //language=java
          java(
            """
              class A {
                  Number choose(boolean outer, boolean inner, Integer value, Double fallback) {
                      Number selected = (outer ? (inner ? (Number) value : fallback) : fallback);
                      return selected;
                  }
              }
              """
          )
        );
    }

    @Test
    void removeSameTypeCastsFromConditionalBranches() {
        rewriteRun(
          //language=java
          java(
            """
              class A {
                  Number choose(boolean flag, Integer value, Integer fallback) {
                      Number selected = flag ? (Integer) value : (Integer) fallback;
                      return selected;
                  }
              }
              """,
            """
              class A {
                  Number choose(boolean flag, Integer value, Integer fallback) {
                      Number selected = flag ? value : fallback;
                      return selected;
                  }
              }
              """
          )
        );
    }

    @Test
    void removeWideningCastInParenthesizedDirectInitializer() {
        rewriteRun(
          //language=java
          java(
            """
              class A {
                  Number widen(Integer value) {
                      Number selected = ((Number) value);
                      return selected;
                  }
              }
              """,
            """
              class A {
                  Number widen(Integer value) {
                      Number selected = value;
                      return selected;
                  }
              }
              """
          )
        );
    }
}
