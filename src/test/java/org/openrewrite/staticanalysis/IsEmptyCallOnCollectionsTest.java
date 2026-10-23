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
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.openrewrite.DocumentExample;
import org.openrewrite.Issue;
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
class IsEmptyCallOnCollectionsTest implements RewriteTest {
    @Override
    public void defaults(RecipeSpec spec) {
        spec.recipe(new IsEmptyCallOnCollections());
    }

    @DocumentExample
    @Test
    void sizeOnClassImplementationCollection() {
        rewriteRun(
          //language=java
          java(
            """
              import java.util.ArrayList;
              class Test {
                  public boolean isZeroSize() {
                      return new ArrayList<String>() {
                          boolean test() {
                              return size() == 0;
                          }
                      }.test();
                  }
              }
              """,
            """
              import java.util.ArrayList;
              class Test {
                  public boolean isZeroSize() {
                      return new ArrayList<String>() {
                          boolean test() {
                              return isEmpty();
                          }
                      }.test();
                  }
              }
              """
          )
        );
    }

    @ParameterizedTest
    @ValueSource(strings = {"java.util.List<String>", "java.util.Map<String, String>"})
    void comparisonWithZero(String type) {
        rewriteRun(
          //language=java
          java(
            """
              class Test {
                  static void method(%s l) {
                      if (l.size() == 0 || 0 == l.size()) {
                          // empty body
                      } else if (l.size() != 0 || 0 != l.size()) {
                          // empty body
                      } else if (l.size() > 0 || l.size() < 0) {
                          // empty body
                      } else if (0 < l.size() || 0 > l.size()) {
                          // empty body
                      }
                  }
              }
              """.formatted(type),
            """
              class Test {
                  static void method(%s l) {
                      if (l.isEmpty() || l.isEmpty()) {
                          // empty body
                      } else if (!l.isEmpty() || !l.isEmpty()) {
                          // empty body
                      } else if (!l.isEmpty() || l.size() < 0) {
                          // empty body
                      } else if (!l.isEmpty() || 0 > l.size()) {
                          // empty body
                      }
                  }
              }
              """.formatted(type)
          )
        );
    }

    @ParameterizedTest
    @ValueSource(strings = {"java.util.List<String>", "java.util.Map<String, String>"})
    void comparisonWithOne(String type) {
        rewriteRun(
          //language=java
          java(
            """
              class Test {
                  static void method(%s l) {
                      if (l.size() < 1 || 1 > l.size()) {
                          // empty body
                      } else if (l.size() > 1 || 1 < l.size()) {
                          // empty body
                      } else if (l.size() >= 1 || 1 <= l.size()) {
                          // empty body
                      } else if (l.size() <= 1 || 1 >= l.size()) {
                          // empty body
                      }
                  }
              }
              """.formatted(type),
            """
              class Test {
                  static void method(%s l) {
                      if (l.isEmpty() || l.isEmpty()) {
                          // empty body
                      } else if (l.size() > 1 || 1 < l.size()) {
                          // empty body
                      } else if (!l.isEmpty() || !l.isEmpty()) {
                          // empty body
                      } else if (l.size() <= 1 || 1 >= l.size()) {
                          // empty body
                      }
                  }
              }
              """.formatted(type)
          )
        );
    }

    @Issue("https://github.com/openrewrite/rewrite/issues/1112")
    @Test
    void formatting() {
        rewriteRun(
          //language=java
          java(
            """
              import java.util.List;

              class Test {
                  static boolean method(List<String> l) {
                      if (true || l.isEmpty()) {
                          // empty body
                      }
                      return l.isEmpty();
                  }
              }
              """
          )
        );
    }

    @Issue("https://github.com/openrewrite/rewrite/issues/1120")
    @Test
    void lambda() {
        rewriteRun(
          //language=java
          java(
            """
              import java.util.List;
              import java.util.stream.Stream;

              class Test {
                  static <T> Stream<List<T>> method(Stream<List<T>> stream) {
                      return stream.filter(s -> s.isEmpty());
                  }
              }
              """
          )
        );
    }

    @Issue("https://github.com/openrewrite/rewrite/issues/2813")
    @Test
    void forLoop() {
        rewriteRun(
          //language=java
          java(
            """
              import java.util.ArrayList;
              import java.util.List;

              class Test extends ArrayList<String> {
                void method() {
                    List<String> lines = new ArrayList<>();
                    for (int i = lines.size() - 1; lines.size() > 0 && size() > 0; i--) {
                        lines.remove(0);
                    }
                }
              }
              """,
            """
              import java.util.ArrayList;
              import java.util.List;

              class Test extends ArrayList<String> {
                void method() {
                    List<String> lines = new ArrayList<>();
                    for (int i = lines.size() - 1; !lines.isEmpty() && !isEmpty(); i--) {
                        lines.remove(0);
                    }
                }
              }
              """
          )
        );
    }

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
              import java.util.Map;

              class Test {
                  boolean test(Map<String, String> map, Other other) {
                      return map.size() == 1 || map.size() == 2 || other.size() == 0;
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
