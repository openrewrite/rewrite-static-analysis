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

class FindNullReturnedFromBooleanMethodTest implements RewriteTest {

    @Override
    public void defaults(RecipeSpec spec) {
        spec.recipe(new FindNullReturnedFromBooleanMethod());
    }

    @DocumentExample
    @Test
    void returnsNull() {
        rewriteRun(
          //language=java
          java(
            """
              class A {
                  public Boolean isUsable(String value) {
                      if (value == null) {
                          return null;
                      }
                      return !value.isEmpty();
                  }
              }
              """,
            """
              class A {
                  public Boolean isUsable(String value) {
                      if (value == null) {
                          /*~~(A `Boolean` returning `null` forces every caller to handle three outcomes, and unboxing the result in a condition throws a `NullPointerException`. Return `false`, or declare the method as returning `boolean`.)~~>*/return null;
                      }
                      return !value.isEmpty();
                  }
              }
              """
          )
        );
    }

    @Test
    void castNull() {
        rewriteRun(
          //language=java
          java(
            """
              class A {
                  Boolean m() {
                      return (Boolean) null;
                  }
              }
              """,
            """
              class A {
                  Boolean m() {
                      /*~~(A `Boolean` returning `null` forces every caller to handle three outcomes, and unboxing the result in a condition throws a `NullPointerException`. Return `false`, or declare the method as returning `boolean`.)~~>*/return (Boolean) null;
                  }
              }
              """
          )
        );
    }

    @Test
    void primitiveBooleanCannotReturnNull() {
        rewriteRun(
          //language=java
          java(
            """
              class A {
                  boolean m(String value) {
                      return value != null;
                  }
              }
              """
          )
        );
    }

    @Test
    void otherReferenceTypesMayReturnNull() {
        rewriteRun(
          //language=java
          java(
            """
              class A {
                  String m() {
                      return null;
                  }

                  Integer n() {
                      return null;
                  }
              }
              """
          )
        );
    }

    @Test
    void returnNullFromALambdaInsideABooleanMethod() {
        rewriteRun(
          //language=java
          java(
            """
              import java.util.function.Supplier;

              class A {
                  Boolean m() {
                      Supplier<String> s = () -> {
                          return null;
                      };
                      return true;
                  }
              }
              """
          )
        );
    }

    @Test
    void nonNullReturns() {
        rewriteRun(
          //language=java
          java(
            """
              class A {
                  Boolean m(String value) {
                      if (value == null) {
                          return Boolean.FALSE;
                      }
                      return !value.isEmpty();
                  }
              }
              """
          )
        );
    }

    @Test
    void doNotChangeNullableAnnotatedMethod() {
        rewriteRun(
          //language=java
          java(
            """
              import org.jspecify.annotations.Nullable;

              class A {
                  @Nullable
                  Boolean annotationPolicy(String name) {
                      if ("CheckReturnValue".equals(name)) {
                          return true;
                      }
                      if ("CanIgnoreReturnValue".equals(name)) {
                          return false;
                      }
                      return null;
                  }
              }
              """
          )
        );
    }
}
