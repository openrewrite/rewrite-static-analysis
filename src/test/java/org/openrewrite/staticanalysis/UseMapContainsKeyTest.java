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

class UseMapContainsKeyTest implements RewriteTest {

    @Override
    public void defaults(RecipeSpec spec) {
        spec.recipeFromResources("org.openrewrite.staticanalysis.UseMapContainsKey");
    }

    @DocumentExample
    @Test
    void useContainsKey() {
        rewriteRun(
          //language=java
          java(
            """
              import java.util.Map;

              class Test {
                  boolean test(Map<String, Integer> map, String key) {
                      return map.keySet().contains(key);
                  }
              }
              """,
            """
              import java.util.Map;

              class Test {
                  boolean test(Map<String, Integer> map, String key) {
                      return map.containsKey(key);
                  }
              }
              """
          )
        );
    }

    @Test
    void useContainsKeyThroughJavaApiBestPractices() {
        rewriteRun(
          spec -> spec.recipeFromResources("org.openrewrite.staticanalysis.JavaApiBestPractices"),
          //language=java
          java(
            """
              import java.util.Map;

              class Test {
                  boolean test(Map<String, Integer> map, String key) {
                      return map.keySet().contains(key);
                  }
              }
              """,
            """
              import java.util.Map;

              class Test {
                  boolean test(Map<String, Integer> map, String key) {
                      return map.containsKey(key);
                  }
              }
              """
          )
        );
    }

    @Test
    void doNotChangeOtherSetContains() {
        rewriteRun(
          //language=java
          java(
            """
              import java.util.Map;

              class Test {
                  boolean test(Map<String, Integer> map, Integer value) {
                      return map.values().contains(value);
                  }
              }
              """
          )
        );
    }
}
