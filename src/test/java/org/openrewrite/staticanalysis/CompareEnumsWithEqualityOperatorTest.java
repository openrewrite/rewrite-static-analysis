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
import org.openrewrite.Issue;
import org.openrewrite.test.RecipeSpec;
import org.openrewrite.test.RewriteTest;
import org.openrewrite.test.SourceSpecs;

import static org.openrewrite.java.Assertions.java;

class CompareEnumsWithEqualityOperatorTest implements RewriteTest {
    @Override
    public void defaults(RecipeSpec spec) {
        spec.recipe(new CompareEnumsWithEqualityOperator());
    }

    //language=java
    SourceSpecs enumA = java(
      """
        package a;
        public enum A {
            FOO, BAR, BUZ
        }
        """
    );

    @SuppressWarnings("StatementWithEmptyBody")
    @Test
    void changeEnumEquals() {
        rewriteRun(
          enumA,
          //language=java
          java(
            """
              import a.A;
              class Test {
                  void method(A arg0) {
                      if (A.FOO.equals(arg0)) {
                      }
                      if (arg0.equals(A.FOO)) {
                      }
                  }
              }
              """,
            """
              import a.A;
              class Test {
                  void method(A arg0) {
                      if (A.FOO == arg0) {
                      }
                      if (arg0 == A.FOO) {
                      }
                  }
              }
              """
          )
        );
    }

    @SuppressWarnings("StatementWithEmptyBody")
    @Test
    void changeEnumNotEquals() {
        rewriteRun(
          enumA,
          //language=java
          java(
            """
              import a.A;
              class Test {
                  void method(A arg0) {
                      if (!A.FOO.equals(arg0)) {
                      }
                      if (!arg0.equals(A.FOO)) {
                      }
                  }
              }
              """,
            """
              import a.A;
              class Test {
                  void method(A arg0) {
                      if (A.FOO != arg0) {
                      }
                      if (arg0 != A.FOO) {
                      }
                  }
              }
              """
          )
        );
    }

    @SuppressWarnings("StatementWithEmptyBody")
    @Test
    void changeEnumNotEqualsWithParentheses() {
        rewriteRun(
          enumA,
          //language=java
          java(
            """
              import a.A;
              class Test {
                  void method(A arg0) {
                      if (!(A.FOO.equals(arg0))) {
                      }
                  }
              }
              """,
            """
              import a.A;
              class Test {
                  void method(A arg0) {
                      if (A.FOO != arg0) {
                      }
                  }
              }
              """
          )
        );
    }

    @Issue("https://github.com/moderneinc/support-public/issues/28")
    @Test
    void equals() {
        rewriteRun(
          //language=java
          java(
            """
              class T {
                  enum Type {
                      SIMPLE_PROPERTY
                  }
                  void m(Object parameterValue, Type partType) {
                      if (parameterValue == null && Type.SIMPLE_PROPERTY.equals(partType)) {
                          throw new IllegalArgumentException();
                      }
                  }
              }
              """,
            """
              class T {
                  enum Type {
                      SIMPLE_PROPERTY
                  }
                  void m(Object parameterValue, Type partType) {
                      if (parameterValue == null && Type.SIMPLE_PROPERTY == partType) {
                          throw new IllegalArgumentException();
                      }
                  }
              }
              """
          ));
    }

    @Issue("https://github.com/moderneinc/support-public/issues/28")
    @Test
    void notEquals() {
        rewriteRun(
          //language=java
          java(
            """
              class T {
                  enum Type {
                      SIMPLE_PROPERTY
                  }
                  void m(Object parameterValue, Type partType) {
                      if (parameterValue == null && !Type.SIMPLE_PROPERTY.equals(partType)) {
                          throw new IllegalArgumentException();
                      }
                  }
              }
              """,
            """
              class T {
                  enum Type {
                      SIMPLE_PROPERTY
                  }
                  void m(Object parameterValue, Type partType) {
                      if (parameterValue == null && Type.SIMPLE_PROPERTY != partType) {
                          throw new IllegalArgumentException();
                      }
                  }
              }
              """
          ));
    }

    @Issue("https://github.com/openrewrite/rewrite-static-analysis/issues/143")
    @Test
    void noSelect() {
        rewriteRun(
          //language=java
          java(
                """
            package a;
            public enum A {
                FOO, BAR, BUZ;
                boolean isFoo() {
                    return equals(FOO);
                }
            }
            """
          )
        );
    }

    @Issue("https://github.com/moderneinc/customer-requests/issues/190")
    @SuppressWarnings("StatementWithEmptyBody")
    @Test
    void changeEnumInsideBooleanExpression() {
        rewriteRun(
          enumA,
          //language=java
          java(
            """
              import a.A;
              class Test {
                  void method(A arg0) {
                      if (!(A.FOO.equals(arg0) || A.BAR.equals(arg0))) {
                      }
                  }
              }
              """,
            """
              import a.A;
              class Test {
                  void method(A arg0) {
                      if (!(A.FOO == arg0 || A.BAR == arg0)) {
                      }
                  }
              }
              """
          )
        );
    }

    @Issue("https://github.com/openrewrite/rewrite-static-analysis/issues/513")
    @Test
    void lambda() {
        rewriteRun(
          enumA,
          //language=java
          java(
            """
              import a.A;
              class ObjectA {
                  private A enumA;
                  public A getEnumA() {
                      return enumA;
                  }
              }
              """
          ),
          //language=java
          java(
            """
              import a.A;
              import java.util.List;
              class Test {
                  void method(List<ObjectA> list) {
                      if (list.stream().filter(e -> e.getEnumA().equals(A.FOO)).toList().isEmpty()) {}
                      if (list.stream().filter(e -> !(e.getEnumA().equals(A.FOO))).toList().isEmpty()) {}
                  }
              }
              """,
            """
              import a.A;
              import java.util.List;
              class Test {
                  void method(List<ObjectA> list) {
                      if (list.stream().filter(e -> e.getEnumA() == A.FOO).toList().isEmpty()) {}
                      if (list.stream().filter(e -> e.getEnumA() != A.FOO).toList().isEmpty()) {}
                  }
              }
              """
          )
        );
    }

    @Issue("https://github.com/openrewrite/rewrite-static-analysis/issues/513")
    @Test
    void notLambda() {
        rewriteRun(
          enumA,
          //language=java
          java(
            """
              import a.A;
              class ObjectA {
                  private A enumA;
                  public A getEnumA() {
                      return enumA;
                  }
              }
              """
          ),
          //language=java
          java(
            """
              import a.A;
              import java.util.List;
              class Test {
                  void method(List<ObjectA> list) {
                      if (!list.stream().filter(e -> e.getEnumA().equals(A.FOO)).toList().isEmpty()) {}
                      if (!list.stream().filter(e -> !(e.getEnumA().equals(A.FOO))).toList().isEmpty()) {}
                  }
              }
              """,
            """
              import a.A;
              import java.util.List;
              class Test {
                  void method(List<ObjectA> list) {
                      if (!list.stream().filter(e -> e.getEnumA() == A.FOO).toList().isEmpty()) {}
                      if (!list.stream().filter(e -> e.getEnumA() != A.FOO).toList().isEmpty()) {}
                  }
              }
              """
          )
        );
    }

    @Issue("https://github.com/openrewrite/rewrite-static-analysis/issues/513")
    @Test
    void ternaryExpression() {
        rewriteRun(
          enumA,
          //language=java
          java(
            """
              import a.A;
              class Test {
                  void method(A value1, A value2, A value3, A value4) {
                      boolean hasMultipleValues = value1 != null ? value1.equals(value2) : value2 == null ||
                                                  value3 != null ? value3.equals(value4) : value4 == null;
                      boolean hasMultipleValues2 = value1 != null ? !value1.equals(value2) : value2 == null ||
                                                   value3 != null ? !value3.equals(value4) : value4 == null;
                  }
              }
              """,
            """
              import a.A;
              class Test {
                  void method(A value1, A value2, A value3, A value4) {
                      boolean hasMultipleValues = value1 != null ? value1 == value2 : value2 == null ||
                                                  value3 != null ? value3 == value4 : value4 == null;
                      boolean hasMultipleValues2 = value1 != null ? value1 != value2 : value2 == null ||
                                                   value3 != null ? value3 != value4 : value4 == null;
                  }
              }
              """
          )
        );
    }

    @Issue("https://github.com/openrewrite/rewrite-static-analysis/issues/513")
    @Test
    void notTernaryExpression() {
        rewriteRun(
          enumA,
          //language=java
          java(
            """
              import a.A;
              class Test {
                  void method(A value1, A value2, A value3, A value4) {
                      boolean hasMultipleValues = !(value1 != null ? value1.equals(value2) : value2 == null) ||
                                                  !(value3 != null ? value3.equals(value4) : value4 == null);
                      boolean hasMultipleValues2 = !(value1 != null ? !value1.equals(value2) : value2 == null) ||
                                                   !(value3 != null ? !value3.equals(value4) : value4 == null);
                  }
              }
              """,
            """
              import a.A;
              class Test {
                  void method(A value1, A value2, A value3, A value4) {
                      boolean hasMultipleValues = !(value1 != null ? value1 == value2 : value2 == null) ||
                                                  !(value3 != null ? value3 == value4 : value4 == null);
                      boolean hasMultipleValues2 = !(value1 != null ? value1 != value2 : value2 == null) ||
                                                   !(value3 != null ? value3 != value4 : value4 == null);
                  }
              }
              """
          )
        );
    }

    @Issue("https://github.com/openrewrite/rewrite-static-analysis/issues/657")
    @Test
    void parenthesesRequiredInBinaryExpression() {
        rewriteRun(
          //language=java
          java(
            """
              import java.time.DayOfWeek;
              class Test {
                  void method() {
                      boolean foo = true == DayOfWeek.MONDAY.equals(DayOfWeek.TUESDAY);
                  }
              }
              """,
            """
              import java.time.DayOfWeek;
              class Test {
                  void method() {
                      boolean foo = true == (DayOfWeek.MONDAY == DayOfWeek.TUESDAY);
                  }
              }
              """
          )
        );
    }

    @Issue("https://github.com/openrewrite/rewrite-static-analysis/issues/3")
    @Test
    void negatedEnumComparisonInComplexBooleanExpression() {
        rewriteRun(
          enumA,
          //language=java
          java(
            """
              import a.A;
              class Test {
                  boolean hasField(String field) {
                      return true;
                  }
                  void method(A field, Object entry) {
                      if ((!A.FOO.equals(field) && !A.BAR.equals(field)) || !hasField("test")) {
                      }
                  }
              }
              """,
            """
              import a.A;
              class Test {
                  boolean hasField(String field) {
                      return true;
                  }
                  void method(A field, Object entry) {
                      if ((A.FOO != field && A.BAR != field) || !hasField("test")) {
                      }
                  }
              }
              """
          )
        );
    }
}
