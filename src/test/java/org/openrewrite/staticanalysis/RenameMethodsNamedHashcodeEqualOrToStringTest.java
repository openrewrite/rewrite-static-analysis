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
import static org.openrewrite.java.Assertions.version;

@SuppressWarnings({"MethodMayBeStatic", "MisspelledEquals", "BooleanMethodNameMustStartWithQuestion", "unused"})
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

    @Test
    void doNotRenameWhenTargetIsAlreadyDeclared() {
        rewriteRun(
          //language=java
          java(
            """
              class Test {
                  int hashcode() {
                      return 1;
                  }

                  public int hashCode() {
                      return 2;
                  }

                  boolean equal(Object value) {
                      return false;
                  }

                  public boolean equals(Object value) {
                      return true;
                  }

                  String tostring() {
                      return "near";
                  }

                  public String toString() {
                      return "proper";
                  }
              }
              """
          )
        );
    }

    @Test
    void doNotRenameWhenInterfaceAlreadyDeclaresTarget() {
        rewriteRun(
          //language=java
          java(
            """
              interface ITest {
                  int hashcode();

                  int hashCode();

                  boolean equal(Object obj);

                  boolean equals(Object obj);

                  String tostring();

                  String toString();
              }
              """
          )
        );
    }

    @Test
    void doNotRenameWhenEnumAlreadyDeclaresTarget() {
        rewriteRun(
          //language=java
          java(
            """
              enum Test {
                  A;

                  String tostring() {
                      return "near";
                  }

                  @Override
                  public String toString() {
                      return "proper";
                  }
              }
              """
          )
        );
    }

    @Test
    void doNotRenameWhenRecordAlreadyDeclaresTarget() {
        rewriteRun(
          version(
            //language=java
            java(
              """
                record Test(int value) {
                    int hashcode() {
                        return 1;
                    }

                    @Override
                    public int hashCode() {
                        return 2;
                    }
                }
                """
            ), 17)
        );
    }

    @Test
    void renameWhenRecordDoesNotExplicitlyDeclareTarget() {
        rewriteRun(
          version(
            //language=java
            java(
              """
                record Test(int value) {
                    public int hashcode() {
                        return 1;
                    }
                }
                """,
              """
                record Test(int value) {
                    public int hashCode() {
                        return 1;
                    }
                }
                """
            ), 17)
        );
    }

    @Test
    void doNotRenameWhenInheritedTargetIsFinal() {
        rewriteRun(
          //language=java
          java(
            """
              class Base {
                  @Override
                  public final int hashCode() {
                      return 1;
                  }
              }

              class Test extends Base {
                  int hashcode() {
                      return 2;
                  }
              }
              """
          )
        );
    }

    @Test
    void renameWhenExistingMethodIsAnOverloadWithDifferentParameters() {
        rewriteRun(
          //language=java
          java(
            """
              class Test {
                  public boolean equal(Object obj) {
                      return false;
                  }

                  public boolean equals(String other) {
                      return true;
                  }
              }
              """,
            """
              class Test {
                  public boolean equals(Object obj) {
                      return false;
                  }

                  public boolean equals(String other) {
                      return true;
                  }
              }
              """
          )
        );
    }

    @Test
    void renameUpdatesCallSitesWhenThereIsNoCollision() {
        rewriteRun(
          //language=java
          java(
            """
              import java.util.function.Supplier;

              class Test {
                  public String tostring() {
                      return "";
                  }

                  String local() {
                      return tostring();
                  }

                  Supplier<String> reference() {
                      return this::tostring;
                  }
              }

              class Caller {
                  String call(Test test) {
                      return test.tostring();
                  }
              }
              """,
            """
              import java.util.function.Supplier;

              class Test {
                  public String toString() {
                      return "";
                  }

                  String local() {
                      return toString();
                  }

                  Supplier<String> reference() {
                      return this::toString;
                  }
              }

              class Caller {
                  String call(Test test) {
                      return test.toString();
                  }
              }
              """
          )
        );
    }
}
