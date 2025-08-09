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

@SuppressWarnings({"ConstantConditions", "ConditionCoveredByFurtherCondition"})
class RemoveRedundantNullCheckBeforeLiteralEqualsTest implements RewriteTest {

    @Override
    public void defaults(RecipeSpec spec) {
        spec.recipe(new RemoveRedundantNullCheckBeforeLiteralEquals());
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
                      if (s != null && "test".equals(s)) {
                          System.out.println("String matches");
                      }
                  }
              }
              """,
            """
              class A {
                  void foo(String s) {
                      if ("test".equals(s)) {
                          System.out.println("String matches");
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
                  void foo(String value) {
                      if (null != value && "hello".equals(value)) {
                          System.out.println("Hello!");
                      }
                  }
              }
              """,
            """
              class A {
                  void foo(String value) {
                      if ("hello".equals(value)) {
                          System.out.println("Hello!");
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
                      if (getValue() != null && "expected".equals(getValue())) {
                          System.out.println("Match");
                      }
                  }

                  String getValue() {
                      return "expected";
                  }
              }
              """,
            """
              class A {
                  void foo() {
                      if ("expected".equals(getValue())) {
                          System.out.println("Match");
                      }
                  }

                  String getValue() {
                      return "expected";
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
                      if (this.field != null && "constant".equals(this.field)) {
                          System.out.println("Field matches");
                      }
                  }
              }
              """,
            """
              class A {
                  String field;

                  void foo() {
                      if ("constant".equals(this.field)) {
                          System.out.println("Field matches");
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
                  void foo(String s, String other) {
                      if (s != null && "test".equals(other)) {
                          System.out.println("Different variables");
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
                      if (s == null && "test".equals(s)) {
                          System.out.println("Won't execute");
                      }
                  }
              }
              """
          )
        );
    }

    @Test
    void doNotChangeWhenNotLiteralEquals() {
        rewriteRun(
          //language=java
          java(
            """
              class A {
                  void foo(String s, String other) {
                      if (s != null && s.equals(other)) {
                          System.out.println("Not a literal");
                      }
                  }
              }
              """
          )
        );
    }

    @Test
    void doNotChangeWhenNotEqualsMethod() {
        rewriteRun(
          //language=java
          java(
            """
              class A {
                  void foo(String s) {
                      if (s != null && s.length() > 0) {
                          System.out.println("Not equals method");
                      }
                  }
              }
              """
          )
        );
    }

    @Test
    void doNotChangeWhenOnlyEqualsCheck() {
        rewriteRun(
          //language=java
          java(
            """
              class A {
                  void foo(String s) {
                      if ("test".equals(s)) {
                          System.out.println("Already optimal");
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
                      if (s != null || "test".equals(s)) {
                          System.out.println("Or operator");
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
                      if ("test".equals(s) && s != null) {
                          System.out.println("Wrong order");
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
                  void foo(String s, String t) {
                      if (s != null && "foo".equals(s) && t != null && "bar".equals(t)) {
                          System.out.println("Both match");
                      }
                  }
              }
              """,
            """
              class A {
                  void foo(String s, String t) {
                      if ("foo".equals(s) && "bar".equals(t)) {
                          System.out.println("Both match");
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
                  void foo(String a, String b, String c) {
                      if (a != null && "hello".equals(a) && b != null && c.startsWith("test")) {
                          System.out.println("Only first null check is redundant");
                      }
                  }
              }
              """,
            """
              class A {
                  void foo(String a, String b, String c) {
                      if ("hello".equals(a) && b != null && c.startsWith("test")) {
                          System.out.println("Only first null check is redundant");
                      }
                  }
              }
              """
          )
        );
    }

    @Test
    void removeNullCheckInChainedConditions() {
        rewriteRun(
          //language=java
          java(
            """
              class A {
                  void foo(String s, int x) {
                      if (x > 0 && s != null && "value".equals(s)) {
                          System.out.println("Chained conditions");
                      }
                  }
              }
              """,
            """
              class A {
                  void foo(String s, int x) {
                      if (x > 0 && "value".equals(s)) {
                          System.out.println("Chained conditions");
                      }
                  }
              }
              """
          )
        );
    }

    @Test
    void doNotChangeWithNonStringLiteral() {
        rewriteRun(
          //language=java
          java(
            """
              class A {
                  void foo(Integer i) {
                      if (i != null && Integer.valueOf(5).equals(i)) {
                          System.out.println("Not a string literal");
                      }
                  }
              }
              """
          )
        );
    }

    @Test
    void doNotChangeWhenVariableReceiverEquals() {
        rewriteRun(
          //language=java
          java(
            """
              class A {
                  void foo(String s, String other) {
                      if (s != null && s.equals("literal")) {
                          System.out.println("Variable receiver, not literal");
                      }
                  }
              }
              """
          )
        );
    }
}
