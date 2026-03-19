/*
 * Copyright 2025 the original author or authors.
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

@SuppressWarnings({"EqualsBetweenUnconvertibleTypes", "ArrayEquals", "ConstantConditions"})
class SillyEqualsCheckTest implements RewriteTest {
    @Override
    public void defaults(RecipeSpec spec) {
        spec.recipe(new SillyEqualsCheck());
    }

    @DocumentExample
    @Test
    void replaceEqualsNullWithEqualityCheck() {
        rewriteRun(
          //language=java
          java(
            """
              class A {
                  boolean foo(String s) {
                      return s.equals(null);
                  }
              }
              """,
            """
              class A {
                  boolean foo(String s) {
                      return s == null;
                  }
              }
              """
          )
        );
    }

    @Test
    void replaceNegatedEqualsNull() {
        rewriteRun(
          //language=java
          java(
            """
              class A {
                  boolean foo(String s) {
                      return !s.equals(null);
                  }
              }
              """,
            """
              class A {
                  boolean foo(String s) {
                      return s != null;
                  }
              }
              """
          )
        );
    }

    @Test
    void replaceArrayEqualsWithArraysEquals() {
        rewriteRun(
          //language=java
          java(
            """
              class A {
                  boolean foo(String[] a, String[] b) {
                      return a.equals(b);
                  }
              }
              """,
            """
              import java.util.Arrays;

              class A {
                  boolean foo(String[] a, String[] b) {
                      return Arrays.equals(a, b);
                  }
              }
              """
          )
        );
    }

    @Test
    void replacePrimitiveArrayEquals() {
        rewriteRun(
          //language=java
          java(
            """
              class A {
                  boolean foo(int[] a, int[] b) {
                      return a.equals(b);
                  }
              }
              """,
            """
              import java.util.Arrays;

              class A {
                  boolean foo(int[] a, int[] b) {
                      return Arrays.equals(a, b);
                  }
              }
              """
          )
        );
    }

    @Test
    void replaceArrayEqualsWithMethodSelect() {
        rewriteRun(
          //language=java
          java(
            """
              class A {
                  boolean foo(int[] b) {
                      return getArr().equals(b);
                  }

                  int[] getArr() {
                      return new int[]{1, 2, 3};
                  }
              }
              """,
            """
              import java.util.Arrays;

              class A {
                  boolean foo(int[] b) {
                      return Arrays.equals(getArr(), b);
                  }

                  int[] getArr() {
                      return new int[]{1, 2, 3};
                  }
              }
              """
          )
        );
    }

    @Test
    void detectArrayVsNonArray() {
        rewriteRun(
          //language=java
          java(
            """
              class A {
                  boolean foo(String[] arr, String s) {
                      return arr.equals(s);
                  }
              }
              """,
            """
              class A {
                  boolean foo(String[] arr, String s) {
                      return /*~~(Comparing array with non-array always returns false)~~>*/arr.equals(s);
                  }
              }
              """
          )
        );
    }

    @Test
    void detectUnrelatedTypes() {
        rewriteRun(
          //language=java
          java(
            """
              class A {
                  boolean foo(Integer i, String s) {
                      return i.equals(s);
                  }
              }
              """,
            """
              class A {
                  boolean foo(Integer i, String s) {
                      return /*~~(Comparing unrelated types java.lang.Integer and java.lang.String always returns false)~~>*/i.equals(s);
                  }
              }
              """
          )
        );
    }

    @Test
    void unchangedWhenSameType() {
        rewriteRun(
          //language=java
          java(
            """
              class A {
                  boolean foo(String a, String b) {
                      return a.equals(b);
                  }
              }
              """
          )
        );
    }

    @Test
    void unchangedWhenRelatedTypes() {
        rewriteRun(
          //language=java
          java(
            """
              class A {
                  boolean foo(Object obj, String s) {
                      return s.equals(obj);
                  }
              }
              """
          )
        );
    }

    @Test
    void unchangedWhenComparedToObject() {
        rewriteRun(
          //language=java
          java(
            """
              class A {
                  boolean foo(String s, Object obj) {
                      return s.equals(obj);
                  }
              }
              """
          )
        );
    }

    @Test
    void unchangedWhenInterfaceRelated() {
        rewriteRun(
          //language=java
          java(
            """
              class A implements Comparable<A> {
                  boolean foo(Comparable<?> c) {
                      return this.equals(c);
                  }

                  @Override
                  public int compareTo(A o) {
                      return 0;
                  }
              }
              """
          )
        );
    }
}
