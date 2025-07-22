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

@SuppressWarnings({"SimplifiableForEach", "ForLoopReplaceableByForEach"})
class UseForEachLoopTest implements RewriteTest {
    @Override
    public void defaults(RecipeSpec spec) {
        spec.recipe(new UseForEachLoop());
    }

    @DocumentExample
    @Test
    void transformListIteration() {
        rewriteRun(
          //language=java
          java(
              """
              import java.util.List;

              class Test {
                  void test(List<String> names) {
                      for (int i = 0; i < names.size(); i++) {
                          System.out.println(names.get(i));
                      }
                  }
              }
            """,
            """
            import java.util.List;

            class Test {
                void test(List<String> names) {
                    for (String name : names) {
                        System.out.println(name);
                    }
                }
            }
            """
          )
        );
    }

    @Test
    void transformArrayIteration() {
        rewriteRun(
          //language=java
          java(
            """
              class Test {
                  void test(String[] names) {
                      for (int i = 0; i < names.length; i++) {
                          System.out.println(names[i]);
                      }
                  }
              }
            """,
            """
            class Test {
                void test(String[] names) {
                    for (String name : names) {
                        System.out.println(name);
                    }
                }
            }
            """
          )
        );
    }

    @Test
    void preIncrementLoop() {
        rewriteRun(
          //language=java
          java(
            """
              import java.util.List;

              class Test {
                  void test(List<Integer> numbers) {
                      for (int i = 0; i < numbers.size(); ++i) {
                          System.out.println(numbers.get(i));
                      }
                  }
              }
            """,
            """
            import java.util.List;

            class Test {
                void test(List<String> names) {
                    for (String name : names) {
                        System.out.println(name);
                    }
                }
            }
            """
          )
        );
    }

    @Test
    void noChangeWhenIndexUsedForOtherPurposes() {
        rewriteRun(
          //language=java
          java(
            """
              import java.util.List;

              class Test {
                  void test(List<String> names) {
                      for (int i = 0; i < names.size(); i++) {
                          System.out.println(i + ": " + names.get(i));
                      }
                  }
              }
              """
          )
        );
    }

    @Test
    void noChangeWhenNotStartingFromZero() {
        rewriteRun(
          //language=java
          java(
            """
              import java.util.List;

              class Test {
                  void test(List<String> names) {
                      for (int i = 1; i < names.size(); i++) {
                          System.out.println(names.get(i));
                      }
                  }
              }
              """
          )
        );
    }

    @Test
    void noChangeWhenNotSimpleIncrement() {
        rewriteRun(
          //language=java
          java(
            """
              import java.util.List;

              class Test {
                  void test(List<String> names) {
                      for (int i = 0; i < names.size(); i += 2) {
                          System.out.println(names.get(i));
                      }
                  }
              }
              """
          )
        );
    }

    @Test
    void noChangeWhenAccessingDifferentCollection() {
        rewriteRun(
          //language=java
          java(
            """
              import java.util.List;

              class Test {
                  void test(List<String> names, List<String> other) {
                      for (int i = 0; i < names.size(); i++) {
                          System.out.println(other.get(i));
                      }
                  }
              }
              """
          )
        );
    }

    @Test
    void noChangeWhenComplexCondition() {
        rewriteRun(
          //language=java
          java(
            """
              import java.util.List;

              class Test {
                  void test(List<String> names) {
                      for (int i = 0; i < names.size() && i < 10; i++) {
                          System.out.println(names.get(i));
                      }
                  }
              }
              """
          )
        );
    }
}
