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

import static org.openrewrite.groovy.Assertions.groovy;
import static org.openrewrite.java.Assertions.java;

class CovariantEqualsTest implements RewriteTest {

    @Override
    public void defaults(RecipeSpec spec) {
        spec.recipe(new CovariantEquals());
    }

    @DocumentExample
    @Test
    void addEqualsObjectDelegatingToCovariantEquals() {
        rewriteRun(
          //language=java
          java(
            """
              class Test {
                  int n;

                  public boolean equals(Test tee) {
                      return n == tee.n;
                  }

                  @Override
                  public int hashCode() {
                      return n;
                  }
              }
              """,
            """
              class Test {
                  int n;

                  public boolean equals(Test tee) {
                      return n == tee.n;
                  }

                  @Override
                  public boolean equals(Object obj) {
                      return obj instanceof Test && equals((Test) obj);
                  }

                  @Override
                  public int hashCode() {
                      return n;
                  }
              }
              """
          )
        );
    }

    @Test
    void abstractCovariantEquals() {
        rewriteRun(
          //language=java
          java(
            """
              abstract class Shape {
                  public abstract boolean equals(Shape other);

                  @Override
                  public int hashCode() {
                      return 0;
                  }
              }
              """,
            """
              abstract class Shape {
                  public abstract boolean equals(Shape other);

                  @Override
                  public boolean equals(Object obj) {
                      return obj instanceof Shape && equals((Shape) obj);
                  }

                  @Override
                  public int hashCode() {
                      return 0;
                  }
              }
              """
          )
        );
    }

    @Test
    void keepCovariantEqualsForOverridesAndCallers() {
        rewriteRun(
          java(
            """
              class Point {
                  int x;

                  public boolean equals(Point other) {
                      return x == other.x;
                  }

                  @Override
                  public int hashCode() {
                      return x;
                  }
              }
              """,
            """
              class Point {
                  int x;

                  public boolean equals(Point other) {
                      return x == other.x;
                  }

                  @Override
                  public boolean equals(Object obj) {
                      return obj instanceof Point && equals((Point) obj);
                  }

                  @Override
                  public int hashCode() {
                      return x;
                  }
              }
              """
          ),
          java(
            """
              class ColorPoint extends Point {
                  int color;

                  @Override
                  public boolean equals(Point other) {
                      return super.equals(other) && (!(other instanceof ColorPoint) || color == ((ColorPoint) other).color);
                  }
              }
              """
          ),
          java(
            """
              class Caller {
                  boolean same(Point a, Point b) {
                      return a.equals(b);
                  }
              }
              """
          )
        );
    }

    @Test
    void genericClass() {
        rewriteRun(
          //language=java
          java(
            """
              import java.util.Objects;

              class Box<T> {
                  T value;

                  public boolean equals(Box<T> other) {
                      return Objects.equals(value, other.value);
                  }

                  @Override
                  public int hashCode() {
                      return Objects.hashCode(value);
                  }
              }
              """,
            """
              import java.util.Objects;

              class Box<T> {
                  T value;

                  public boolean equals(Box<T> other) {
                      return Objects.equals(value, other.value);
                  }

                  @Override
                  public boolean equals(Object obj) {
                      return obj instanceof Box && equals((Box) obj);
                  }

                  @Override
                  public int hashCode() {
                      return Objects.hashCode(value);
                  }
              }
              """
          )
        );
    }

    @Test
    void onlyMatchCovariantEqualsSignature() {
        rewriteRun(
          //language=java
          java(
            """
              class Test {
                  int n;

                  public boolean equals(Number test) {
                      return false;
                  }

                  public boolean equals(Test test) {
                      return n == test.n;
                  }

                  public boolean equals() {
                      return false;
                  }

                  @Override
                  public int hashCode() {
                      return n;
                  }
              }
              """,
            """
              class Test {
                  int n;

                  public boolean equals(Number test) {
                      return false;
                  }

                  public boolean equals(Test test) {
                      return n == test.n;
                  }

                  @Override
                  public boolean equals(Object obj) {
                      return obj instanceof Test && equals((Test) obj);
                  }

                  public boolean equals() {
                      return false;
                  }

                  @Override
                  public int hashCode() {
                      return n;
                  }
              }
              """
          )
        );
    }

    @Issue("https://github.com/openrewrite/rewrite/issues/653")
    @Test
    void nestedClass() {
        rewriteRun(
          //language=java
          java(
            """
              class A {
                  class B {
                      int n;

                      public boolean equals(B bee) {
                          return n == bee.n;
                      }

                      @Override
                      public int hashCode() {
                          return n;
                      }
                  }
              }
              """,
            """
              class A {
                  class B {
                      int n;

                      public boolean equals(B bee) {
                          return n == bee.n;
                      }

                      @Override
                      public boolean equals(Object obj) {
                          return obj instanceof B && equals((B) obj);
                      }

                      @Override
                      public int hashCode() {
                          return n;
                      }
                  }
              }
              """
          )
        );
    }

    @Test
    void groovyClass() {
        rewriteRun(
          groovy(
            """
              class Test {
                  int n

                  public boolean equals(Test tee) {
                      return n == tee.n
                  }

                  @Override
                  public int hashCode() {
                      return n
                  }
              }
              """,
            """
              class Test {
                  int n

                  public boolean equals(Test tee) {
                      return n == tee.n
                  }

                  @Override
                  public boolean equals(Object obj) {
                      return obj instanceof Test && equals((Test) obj)
                  }

                  @Override
                  public int hashCode() {
                      return n
                  }
              }
              """
          )
        );
    }

    @Test
    void doNotChangeWithoutHashCode() {
        rewriteRun(
          //language=java
          java(
            """
              class Test {
                  int n;

                  public boolean equals(Test tee) {
                      return n == tee.n;
                  }
              }
              """
          )
        );
    }

    @Test
    void doNotChangeWithExistingEqualsObject() {
        rewriteRun(
          //language=java
          java(
            """
              class Test {
                  public boolean equals(Test t) {
                      return false;
                  }

                  public boolean equals(Object i) {
                      return false;
                  }

                  @Override
                  public int hashCode() {
                      return 0;
                  }
              }
              """
          )
        );
    }

    @Test
    void doNotChangeEnum() {
        rewriteRun(
          //language=java
          java(
            """
              enum Color {
                  RED;

                  public boolean equals(Color other) {
                      return this == other;
                  }
              }
              """
          )
        );
    }

    @Issue("https://github.com/openrewrite/rewrite/issues/2775")
    @Test
    void doNotChangeInterface() {
        rewriteRun(
          //language=java
          java(
            """
              public interface Test {
                  boolean equals(final Test other);

                  int hashCode();
              }
              """
          )
        );
    }
}
