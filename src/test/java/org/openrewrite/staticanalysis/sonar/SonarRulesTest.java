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
package org.openrewrite.staticanalysis.sonar;

import org.junit.jupiter.api.Test;
import org.openrewrite.DocumentExample;
import org.openrewrite.Validated;
import org.openrewrite.config.Environment;
import org.openrewrite.test.RecipeSpec;
import org.openrewrite.test.RewriteTest;

import static org.assertj.core.api.Assertions.assertThat;
import static org.openrewrite.java.Assertions.java;

class SonarRulesTest implements RewriteTest {

    @Override
    public void defaults(RecipeSpec spec) {
        spec.recipeFromResources("org.openrewrite.staticanalysis.sonar.SonarRules");
    }

    @Test
    void compositesResolve() {
        assertThat(Environment.builder().scanRuntimeClasspath().build()
          .activateRecipes("org.openrewrite.staticanalysis.sonar.SonarRules")
          .validateAll())
          .filteredOn(Validated::isInvalid)
          .isEmpty();
    }

    @DocumentExample
    @Test
    void runsSonarRules() {
        rewriteRun(
          //language=java
          java(
            """
              class A {
                  int count;

                  int sum(int[] values) {
                      int count = 0;
                      for (int value : values) {
                          count += value;
                      }
                      return count;
                  }
              }
              """,
            """
              class A {
                  int count;

                  int sum(int[] values) {
                      int count1 = 0;
                      for (int value : values) {
                          count1 += value;
                      }
                      return count1;
                  }
              }
              """,
            // keep the trailing newline, which EmptyNewlineAtEndOfFile would otherwise add
            spec -> spec.noTrim()
          )
        );
    }
}
