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

import static org.openrewrite.java.Assertions.java;
import static org.openrewrite.kotlin.Assertions.kotlin;

@SuppressWarnings({"ConstantConditions", "ConditionCoveredByFurtherCondition"})
class RemoveRedundantNullCheckBeforeInstanceofTest implements RewriteTest {

    @Override
    public void defaults(RecipeSpec spec) {
        spec.recipe(new RemoveRedundantNullCheckBeforeInstanceof());
    }

    @DocumentExample
    @Test
    void removeRedundantNullCheck() {
        rewriteRun(
          //language=java
          java(
            """
              class A {
                  void foo(String s) {
                      if (s != null && s instanceof String) {
                          System.out.println("String value: " + s);
                      }
                  }
              }
              """,
            """
              class A {
                  void foo(String s) {
                      if (s instanceof String) {
                          System.out.println("String value: " + s);
                      }
                  }
              }
              """
          )
        );
    }

    @Test
    void removeRedundantNullCheckWithNullOnLeft() {
        rewriteRun(
          //language=java
          java(
            """
              class A {
                  void foo(Object obj) {
                      if (null != obj && obj instanceof String) {
                          System.out.println("String value: " + obj);
                      }
                  }
              }
              """,
            """
              class A {
                  void foo(Object obj) {
                      if (obj instanceof String) {
                          System.out.println("String value: " + obj);
                      }
                  }
              }
              """
          )
        );
    }


    @Issue("https://github.com/openrewrite/rewrite-static-analysis/issues/953")
    @Test
    void doNotChangeWhenMethodInvocation() {
        rewriteRun(
          //language=java
          java(
            """
              class A {
                  void foo() {
                      if (getValue() != null && getValue() instanceof String) {
                          System.out.println("String value");
                      }
                  }

                  String getValue() {
                      return "test";
                  }
              }
              """
          )
        );
    }

    @Issue("https://github.com/openrewrite/rewrite-static-analysis/issues/953")
    @Test
    void doNotChangeWhenMethodInvocationWithNullOnLeft() {
        rewriteRun(
          //language=java
          java(
            """
              class A {
                  void foo() {
                      if (null != getValue() && getValue() instanceof String) {
                          System.out.println("String value");
                      }
                  }

                  String getValue() {
                      return "test";
                  }
              }
              """
          )
        );
    }

    @Test
    void removeRedundantNullCheckInChainedCondition() {
        rewriteRun(
          //language=java
          java(
            """
              class A {
                  void foo(boolean enabled, Object obj) {
                      if (enabled && obj != null && obj instanceof String) {
                          System.out.println("String value");
                      }
                  }
              }
              """,
            """
              class A {
                  void foo(boolean enabled, Object obj) {
                      if (enabled && obj instanceof String) {
                          System.out.println("String value");
                      }
                  }
              }
              """
          )
        );
    }

    @Issue("https://github.com/openrewrite/rewrite-static-analysis/issues/953")
    @Test
    void doNotChangeWhenChainedConditionHasMethodInvocation() {
        rewriteRun(
          //language=java
          java(
            """
              class A {
                  void foo(boolean enabled) {
                      if (enabled && getValue() != null && getValue() instanceof String) {
                          System.out.println("String value");
                      }
                  }

                  String getValue() {
                      return "test";
                  }
              }
              """
          )
        );
    }

    @Issue("https://github.com/openrewrite/rewrite-static-analysis/issues/953")
    @Test
    void doNotChangeWhenConstructorCall() {
        rewriteRun(
          //language=java
          java(
            """
              class A {
                  void foo() {
                      if (new StringBuilder() != null && new StringBuilder() instanceof CharSequence) {
                          System.out.println("CharSequence value");
                      }
                  }
              }
              """
          )
        );
    }

    @Issue("https://github.com/openrewrite/rewrite-static-analysis/issues/953")
    @Test
    void doNotChangeWhenArrayIndexHasSideEffect() {
        rewriteRun(
          //language=java
          java(
            """
              class A {
                  Object[] values = new Object[2];
                  int i;

                  void foo() {
                      if (values[i++] != null && values[i++] instanceof String) {
                          System.out.println("String value");
                      }
                  }
              }
              """
          )
        );
    }

    @Test
    void removeRedundantNullCheckWithFieldAccess() {
        rewriteRun(
          //language=java
          java(
            """
              class A {
                  String field;

                  void foo() {
                      if (this.field != null && this.field instanceof String) {
                          System.out.println("String value: " + this.field);
                      }
                  }
              }
              """,
            """
              class A {
                  String field;

                  void foo() {
                      if (this.field instanceof String) {
                          System.out.println("String value: " + this.field);
                      }
                  }
              }
              """
          )
        );
    }

    @Test
    void doNotChangeWhenDifferentVariables() {
        rewriteRun(
          //language=java
          java(
            """
              class A {
                  void foo(String s, Object obj) {
                      if (s != null && obj instanceof String) {
                          System.out.println("String value");
                      }
                  }
              }
              """
          )
        );
    }

    @Test
    void doNotChangeWhenNotNullCheck() {
        rewriteRun(
          //language=java
          java(
            """
              class A {
                  void foo(String s) {
                      if (s == null && s instanceof String) {
                          System.out.println("String value");
                      }
                  }
              }
              """
          )
        );
    }

    @Test
    void doNotChangeWhenNotInstanceofCheck() {
        rewriteRun(
          //language=java
          java(
            """
              class A {
                  void foo(String s) {
                      if (s != null && s.length() > 0) {
                          System.out.println("String value");
                      }
                  }
              }
              """
          )
        );
    }

    @Test
    void doNotChangeWhenOnlyInstanceofCheck() {
        rewriteRun(
          //language=java
          java(
            """
              class A {
                  void foo(String s) {
                      if (s instanceof String) {
                          System.out.println("String value");
                      }
                  }
              }
              """
          )
        );
    }

    @Test
    void doNotChangeWhenOrOperator() {
        rewriteRun(
          //language=java
          java(
            """
              class A {
                  void foo(String s) {
                      if (s != null || s instanceof String) {
                          System.out.println("String value");
                      }
                  }
              }
              """
          )
        );
    }

    @Test
    void doNotChangeWhenWrongOrder() {
        rewriteRun(
          //language=java
          java(
            """
              class A {
                  void foo(String s) {
                      if (s instanceof String && s != null) {
                          System.out.println("String value");
                      }
                  }
              }
              """
          )
        );
    }

    @Test
    void removeRedundantNullCheckWithTwoVariables() {
        rewriteRun(
          //language=java
          java(
            """
              class A {
                  void foo(String s, Object obj) {
                      if (s != null && s instanceof String && obj != null && obj instanceof String) {
                          System.out.println("Both are strings");
                      }
                  }
              }
              """,
            """
              class A {
                  void foo(String s, Object obj) {
                      if (s instanceof String && obj instanceof String) {
                          System.out.println("Both are strings");
                      }
                  }
              }
              """
          )
        );
    }

    @Test
    void doNotChangeWhenNullCheckForDifferentVariable() {
        rewriteRun(
          //language=java
          java(
            """
              class A {
                  void foo(String s, Object obj) {
                      if (s != null && obj instanceof String) {
                          System.out.println("Mixed check");
                      }
                  }
              }
              """
          )
        );
    }

    @Test
    void doNotChangeWhenMultipleInstanceofWithoutMatchingNullChecks() {
        rewriteRun(
          //language=java
          java(
            """
              class A {
                  void foo(Object a, Object b, Object c) {
                      if (a instanceof String && b instanceof Integer && c != null) {
                          System.out.println("Mixed types");
                      }
                  }
              }
              """
          )
        );
    }

    @Test
    void doNotChangeWhenNullCheckBetweenUnrelatedInstanceof() {
        rewriteRun(
          //language=java
          java(
            """
              class A {
                  void foo(Object a, Object b, Object c) {
                      if (a instanceof String && b != null && c instanceof Integer) {
                          System.out.println("Null check for different variable");
                      }
                  }
              }
              """
          )
        );
    }

    @Test
    void doNotChangeWhenComplexMixedConditions() {
        rewriteRun(
          //language=java
          java(
            """
              class A {
                  void foo(String s, Object obj, Integer num) {
                      if (s instanceof String && obj != null && num instanceof Integer && obj.hashCode() > 0) {
                          System.out.println("Complex condition");
                      }
                  }
              }
              """
          )
        );
    }

    @Test
    void doNotChangeWhenNullCheckAfterInstanceof() {
        rewriteRun(
          //language=java
          java(
            """
              class A {
                  void foo(Object a, Object b) {
                      if (a instanceof String && b instanceof Integer && a != null && b != null) {
                          System.out.println("Null checks after instanceof");
                      }
                  }
              }
              """
          )
        );
    }

    @Test
    void partialRemovalWithMixedConditions() {
        rewriteRun(
          //language=java
          java(
            """
              class A {
                  void foo(Object a, Object b, Object c) {
                      if (a != null && a instanceof String && b != null && c instanceof Integer) {
                          System.out.println("Only first null check is redundant");
                      }
                  }
              }
              """,
            """
              class A {
                  void foo(Object a, Object b, Object c) {
                      if (a instanceof String && b != null && c instanceof Integer) {
                          System.out.println("Only first null check is redundant");
                      }
                  }
              }
              """
          )
        );
    }

    @Test
    void removeOnlySequentialNullChecks() {
        rewriteRun(
          //language=java
          java(
            """
              class A {
                  void foo(Object a, Object b) {
                      if (a != null && b != null && a instanceof String && b instanceof Integer) {
                          System.out.println("Both null checks are redundant");
                      }
                  }
              }
              """
          )
        );
    }

    @Issue("https://github.com/openrewrite/rewrite/pull/5736")
    @Test
    void kotlinIsNotInstanceOf() {
        rewriteRun(
          //language=kotlin
          kotlin(
            """
              class A {
                fun foo(s: Object?) {
                  if (s != null && s !is String) {
                    println("Not null nor String")
                  }
                }
              }
              """
          )
        );
    }
}
