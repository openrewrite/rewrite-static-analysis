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

class CovariantEqualsTest implements RewriteTest {

    @Override
    public void defaults(RecipeSpec spec) {
        spec.recipe(new CovariantEquals());
    }

    @Test
    @DocumentExample
    void replaceWithNonCovariantEquals() {
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
              """,
            """
              class Test {
                  int n;

                  @Override
                  public boolean equals(Object obj) {
                      if (obj == this) return true;
                      if (obj == null || getClass() != obj.getClass()) return false;
                      Test tee = (Test) obj;
                      return n == tee.n;
                  }
              }
              """
          )
        );
    }

    @Test
    @SuppressWarnings("UnnecessaryLocalVariable")
    void replaceMultiStatementReturnBody() {
        rewriteRun(
          //language=java
          java(
            """
              class A {
                  String id;

                  public boolean equals(A other) {
                      boolean isEqual = id.equals(other.id);
                      return isEqual;
                  }
              }
              """,
            """
              class A {
                  String id;

                  @Override
                  public boolean equals(Object obj) {
                      if (obj == this) return true;
                      if (obj == null || getClass() != obj.getClass()) return false;
                      A other = (A) obj;
                      boolean isEqual = id.equals(other.id);
                      return isEqual;
                  }
              }
              """
          )
        );
    }

    @Test
    void replaceEqualsBasedOnTypeSignature() {
        rewriteRun(
          //language=java
          java(
            """
              class Test {
                  int n;
                  public void placeholder(Test test) {}
                  public void placeholder(Object test) {}

                  public boolean equals(Number test) {
                      return false;
                  }

                  public boolean equals(Test test) {
                      return n == test.n;
                  }

                  public boolean equals() {
                      return false;
                  }

                  public boolean equals(String test) {
                      return false;
                  }
              }
              """,
            """
              class Test {
                  int n;
                  public void placeholder(Test test) {}
                  public void placeholder(Object test) {}

                  public boolean equals(Number test) {
                      return false;
                  }

                  @Override
                  public boolean equals(Object obj) {
                      if (obj == this) return true;
                      if (obj == null || getClass() != obj.getClass()) return false;
                      Test test = (Test) obj;
                      return n == test.n;
                  }

                  public boolean equals() {
                      return false;
                  }

                  public boolean equals(String test) {
                      return false;
                  }
              }
              """
          )
        );
    }

    @Test
    @SuppressWarnings("UnnecessaryLocalVariable")
    void replaceEqualsMaintainsExistingAnnotations() {
        rewriteRun(
          //language=java
          java(
            """
              class A {
                  String id;

                  public boolean equals(A other) {
                      boolean isEqual = id.equals(other.id);
                      return isEqual;
                  }
              }
              """,
            """
              class A {
                  String id;

                  @Override
                  public boolean equals(Object obj) {
                      if (obj == this) return true;
                      if (obj == null || getClass() != obj.getClass()) return false;
                      A other = (A) obj;
                      boolean isEqual = id.equals(other.id);
                      return isEqual;
                  }
              }
              """
          )
        );
    }

    @Test
    @Issue("https://github.com/openrewrite/rewrite/issues/653")
    void replaceWithNonCovariantEqualsWhenNested() {
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
                  }
              }
              """,
            """
              class A {
                  class B {
                      int n;

                      @Override
                      public boolean equals(Object obj) {
                          if (obj == this) return true;
                          if (obj == null || getClass() != obj.getClass()) return false;
                          B bee = (B) obj;
                          return n == bee.n;
                      }
                  }
              }
              """
          )
        );
    }

    @Test
    void renameExistingParameterNameWhenParameterNameIsDefaultTemplateName() {
        rewriteRun(
          //language=java
          java(
            """
              class Test {
                  int n;

                  public boolean equals(Test obj) {
                      return n == obj.n;
                  }
              }
              """,
            """
              class Test {
                  int n;

                  @Override
                  public boolean equals(Object other) {
                      if (other == this) return true;
                      if (other == null || getClass() != other.getClass()) return false;
                      Test obj = (Test) other;
                      return n == obj.n;
                  }
              }
              """
          )
        );
    }

    @Test
    void ignoreIfAtLeastOneExistingNonCovariantEqualsMethod() {
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

                  public boolean equals(Object i, Test t) {
                      return false;
                  }
              }
              """
          )
        );
    }

    @Test
    void ignoreIfNoExistingEqualsMethod() {
        rewriteRun(
          //language=java
          java(
            """
              class A {}

              class B {
                  B() {}
                  public void placeholder(B t) {}
                  public void placeholder(Object t) {}
              }
              """
          )
        );
    }

    @Test
    @Issue("https://github.com/openrewrite/rewrite/issues/2775")
    void equalsInInterface() {
        rewriteRun(
          //language=java
          java(
            """
              public interface Test {
                  String id;

                  boolean equals(final Test other);
              }
              """
          )
        );
    }
}
