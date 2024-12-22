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

@SuppressWarnings({"MethodMayBeStatic", "MisspelledEquals", "BooleanMethodNameMustStartWithQuestion"})
class RenameMethodsNamedHashcodeEqualOrToStringTest implements RewriteTest {

    @Override
    public void defaults(RecipeSpec spec) {
        spec.recipe(new RenameMethodsNamedHashcodeEqualOrToString());
    }

    @DocumentExample
    @Test
    void noncompliantMethodNames() {
        rewriteRun(
          //language=java
          java(
            """
              interface ITest {
                  int HASHcoDE();

                  boolean equal(Object obj);

                  String tostring();
              }

              class Test {
                  public int hashcode() {
                      return 0;
                  }

                  public boolean equal(Object obj) {
                      return false;
                  }

                  public String tostring() {
                      return "";
                  }
              }
              """,
            """
              interface ITest {
                  int hashCode();

                  boolean equals(Object obj);

                  String toString();
              }

              class Test {
                  public int hashCode() {
                      return 0;
                  }

                  public boolean equals(Object obj) {
                      return false;
                  }

                  public String toString() {
                      return "";
                  }
              }
              """
          )
        );
    }

    @Test
    void compliantWhenHasMismatchingTypeInformation() {
        rewriteRun(
          //language=java
          java(
            """
              interface ITest {
                  void hashcode();

                  int hashcode(int a, int b);

                  void equal();

                  void equal(Object obj);

                  void tostring();
              }

              class Test {
                  public int hashcode(int a, int b) {
                      return a + b;
                  }
              }
              """
          )
        );
    }
}
