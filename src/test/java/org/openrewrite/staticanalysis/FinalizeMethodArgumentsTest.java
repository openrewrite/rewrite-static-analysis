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
import org.openrewrite.Issue;
import org.openrewrite.test.RecipeSpec;
import org.openrewrite.test.RewriteTest;

import static org.openrewrite.golang.Assertions.go;
import static org.openrewrite.groovy.Assertions.groovy;
import static org.openrewrite.java.Assertions.java;

class FinalizeMethodArgumentsTest implements RewriteTest {

    @Override
    public void defaults(RecipeSpec spec) {
        spec.recipe(new FinalizeMethodArguments());
    }

    @DocumentExample
    @Test
    void replaceWithFinalModifier() {
        rewriteRun(
          //language=java
          java(
            """
              class TestClass {
                  private void getAccaCouponData(String responsiveRequestConfig, String card) {
                  }
              }
              """,
            """
              class TestClass {
                  private void getAccaCouponData(final String responsiveRequestConfig, final String card) {
                  }
              }
              """
          )
        );
    }

    @Issue("https://github.com/openrewrite/rewrite/issues/3133")
    @Test
    void twoParameters() {
        rewriteRun(
          //language=java
          java(
            """
              class Foo {
                int test(final int a, int b) {
                    return a + b;
                }
              }
              """,
            """
              class Foo {
                int test(final int a, final int b) {
                    return a + b;
                }
              }
              """
          )
        );
    }

    @Issue("https://github.com/openrewrite/rewrite/issues/3135")
    @Test
    void ignoreAbstractMethod() {
        rewriteRun(
          //language=java
          java(
            """
              class Foo {
              abstract void bar(String a);
              }
              """
          )
        );
    }

    @Test
    void doNotAddFinalIfAssigned() {
        rewriteRun(
          //language=java
          java(
            """
              class A {
                  void SubeventUtils(String a) {
                      a = "abc";
                  }
              }
              """
          )
        );
    }

    @Test
    void doNotAddFinalIfInterface() {
        rewriteRun(
          //language=java
          java(
            """
              public interface MarketDeleteService {
                   void deleteMarket(Long marketId, String deletionTimestamp);
              }
              """
          )
        );
    }

    @Test
    void replaceWithFinalModifierWhenAnnotated() {
        rewriteRun(
          //language=java
          java(
            """
              class Test {
                  public void test(@Override Integer test) {}
              }
              """,
            """
              class Test {
                  public void test(@Override final Integer test) {}
              }
              """
          )
        );
    }

    @Test
    void doNotReplaceWithFinalModifier() {
        rewriteRun(
          //language=java
          java(
            """
              import java.util.List;

              class Test {
                  private final List<Integer> ids;

                  Test(@SuppressWarnings("unused") final List<Integer> ids) {
                      this.ids = ids;
                  }

                  static boolean anyMatch(final List<String> names, final String name) {
                      return names.stream().anyMatch(n -> n.equalsIgnoreCase(name));
                  }
              }
              """
          )
        );
    }

    @Issue("https://github.com/openrewrite/rewrite-static-analysis/issues/176")
    @Test
    void doNotReplaceIfAssignedThroughUnaryOrAccumulator() {
        rewriteRun(
          //language=java
          java(
            """
              class Test {
                protected int addFinalToThisVar(int unalteredVariable) {
                  return unalteredVariable;
                }

                protected int increment(int variableToIncrement) {
                  variableToIncrement++;
                  return variableToIncrement;
                }

                protected int preIncrement(int variableToPreIncrement) {
                  return ++variableToPreIncrement;
                }

                protected int decrement(int variableToDecrement) {
                  variableToDecrement--;
                  return variableToDecrement;
                }

                protected int preDecrement(int variableToPreDecrement) {
                  return --variableToPreDecrement;
                }

                protected int accumulate(int add, int accumulator) {
                  accumulator += add;
                  return accumulator;
                }
              }
              """,
            """
              class Test {
                protected int addFinalToThisVar(final int unalteredVariable) {
                  return unalteredVariable;
                }

                protected int increment(int variableToIncrement) {
                  variableToIncrement++;
                  return variableToIncrement;
                }

                protected int preIncrement(int variableToPreIncrement) {
                  return ++variableToPreIncrement;
                }

                protected int decrement(int variableToDecrement) {
                  variableToDecrement--;
                  return variableToDecrement;
                }

                protected int preDecrement(int variableToPreDecrement) {
                  return --variableToPreDecrement;
                }

                protected int accumulate(int add, int accumulator) {
                  accumulator += add;
                  return accumulator;
                }
              }
              """
          )
        );
    }

    @Test
    void finalizeGroovyParameters() {
        rewriteRun(
          //language=groovy
          groovy(
            """
              class Test {
                  void greet(String name) {
                  }
              }
              """,
            """
              class Test {
                  void greet(final String name) {
                  }
              }
              """
          )
        );
    }

    @Test
    void doNotChangeGo() {
        rewriteRun(
          //language=go
          go(
            """
              package main

              func RandomString(n int) string {
                  return ""
              }
              """
          )
        );
    }
}
