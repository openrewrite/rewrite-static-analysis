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

@SuppressWarnings({"ConstantConditions"})
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


    @Test
    void removeRedundantNullCheckWithMethodInvocation() {
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
              """,
            """
              class A {
                  void foo() {
                      if (getValue() instanceof String) {
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
        // Recipe processes one null check at a time, so only the first redundant check is removed
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
                      if (s instanceof String && obj != null && obj instanceof String) {
                          System.out.println("Both are strings");
                      }
                  }
              }
              """
          )
        );
    }

}
