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

@SuppressWarnings({
  "SizeReplaceableByIsEmpty",
  "DuplicateCondition",
  "ConstantConditions",
  "ExcessiveRangeCheck",
  "ConstantOnWrongSideOfComparison",
  "StatementWithEmptyBody",
  "BooleanMethodNameMustStartWithQuestion",
  "PointlessBooleanExpression",
  "Convert2MethodRef"
})
class IsEmptyCallOnMapsTest implements RewriteTest {
    @Override
    public void defaults(RecipeSpec spec) {
        spec.recipe(new IsEmptyCallOnMaps());
    }

    @Test
    void comparisonWithZero() {
        rewriteRun(
          //language=java
          java(
            """
              import java.util.Map;

              class Test {
                  static void method(Map<String, String> map) {
                      if (map.size() == 0 || 0 == map.size()) {
                          // empty body
                      } else if (map.size() != 0 || 0 != map.size()) {
                          // empty body
                      } else if (map.size() > 0 || map.size() < 0) {
                          // empty body
                      } else if (0 < map.size() || 0 > map.size()) {
                          // empty body
                      }
                  }
              }
              """,
            """
              import java.util.Map;

              class Test {
                  static void method(Map<String, String> map) {
                      if (map.isEmpty() || map.isEmpty()) {
                          // empty body
                      } else if (!map.isEmpty() || !map.isEmpty()) {
                          // empty body
                      } else if (!map.isEmpty() || map.size() < 0) {
                          // empty body
                      } else if (!map.isEmpty() || 0 > map.size()) {
                          // empty body
                      }
                  }
              }
              """
          )
        );
    }

    @Test
    void comparisonWithOne() {
        rewriteRun(
          //language=java
          java(
            """
              import java.util.Map;

              class Test {
                  static void method(Map<String, String> map) {
                      if (map.size() < 1 || 1 > map.size()) {
                          // empty body
                      } else if (map.size() > 1 || 1 < map.size()) {
                          // empty body
                      } else if (map.size() >= 1 || 1 <= map.size()) {
                          // empty body
                      } else if (map.size() <= 1 || 1 >= map.size()) {
                          // empty body
                      }
                  }
              }
              """,
            """
              import java.util.Map;

              class Test {
                  static void method(Map<String, String> map) {
                      if (map.isEmpty() || map.isEmpty()) {
                          // empty body
                      } else if (map.size() > 1 || 1 < map.size()) {
                          // empty body
                      } else if (!map.isEmpty() || !map.isEmpty()) {
                          // empty body
                      } else if (map.size() <= 1 || 1 >= map.size()) {
                          // empty body
                      }
                  }
              }
              """
          )
        );
    }

    @DocumentExample
    @Test
    void mapWithNullGuard() {
        rewriteRun(
          //language=java
          java(
            """
              import java.util.Map;

              class Test {
                  boolean empty(Map<String, String> map) {
                      return map == null || map.size() == 0;
                  }
              }
              """,
            """
              import java.util.Map;

              class Test {
                  boolean empty(Map<String, String> map) {
                      return map == null || map.isEmpty();
                  }
              }
              """
          )
        );
    }

    @Test
    void sizeOnMapImplementation() {
        rewriteRun(
          //language=java
          java(
            """
              import java.util.HashMap;

              class Test extends HashMap<String, String> {
                  boolean empty() {
                      return size() == 0 && this.size() == 0;
                  }

                  boolean nonEmpty() {
                      return size() != 0;
                  }
              }
              """,
            """
              import java.util.HashMap;

              class Test extends HashMap<String, String> {
                  boolean empty() {
                      return isEmpty() && this.isEmpty();
                  }

                  boolean nonEmpty() {
                      return !isEmpty();
                  }
              }
              """
          )
        );
    }

    @Test
    void doNotChangeUnrelatedSizeOrExactMapSize() {
        rewriteRun(
          //language=java
          java(
            """
              import java.util.List;
              import java.util.Map;

              class Test {
                  boolean test(Map<String, String> map, List<String> list, Other other) {
                      return map.size() == 1 || map.size() == 2 || other.size() == 0 || list.size() == 0;
                  }
              }

              class Other {
                  int size() {
                      return 0;
                  }
              }
              """
          )
        );
    }
}
