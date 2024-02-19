/*
 * Copyright 2023 the original author or authors.
 * <p>
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * <p>
 * https://www.apache.org/licenses/LICENSE-2.0
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

    @Test
    @Issue("https://github.com/openrewrite/rewrite-static-analysis/issues/143")
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
            """)
        );
    }

    @SuppressWarnings("StatementWithEmptyBody")
    @Issue("https://github.com/moderneinc/customer-requests/issues/190")
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
}
