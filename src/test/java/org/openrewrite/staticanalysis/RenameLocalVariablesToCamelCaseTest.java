/*
 * Copyright 2020 the original author or authors.
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

import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.junitpioneer.jupiter.ExpectedToFail;
import org.openrewrite.DocumentExample;
import org.openrewrite.Issue;
import org.openrewrite.Recipe;
import org.openrewrite.java.marker.JavaVersion;
import org.openrewrite.test.RecipeSpec;
import org.openrewrite.test.RewriteTest;

import java.util.UUID;

import static org.openrewrite.java.Assertions.java;

@SuppressWarnings("ConstantConditions")
class RenameLocalVariablesToCamelCaseTest implements RewriteTest {

    @Override
    public void defaults(RecipeSpec spec) {
        spec.recipe(new RenameLocalVariablesToCamelCase());
    }

    @Issue("https://github.com/openrewrite/rewrite/issues/2227")
    @Test
    void lowerCamelVariableHasNonLowerCamelVariableSibling() {
        rewriteRun(
          //language=java
          java(
            """
              class A {
                  void m()  {
                      final int _secure = 0;
                      boolean secure = _secure > 0;
                      
                      int _notSecure = 0;
                      boolean notSecure = _notSecure < 1;
                  }
              }
              """
          )
        );
    }

    @DocumentExample
    @Test
    void renameAllCapsAcronyms() {
        rewriteRun(
          //language=java
          java(
            """
              class Test {
                  void test() {
                      String ID;
                  }
              }
              """,
            """
              class Test {
                  void test() {
                      String id;
                  }
              }
              """
          )
        );
    }

    @Test
    void renameLocalVariables() {
        rewriteRun(
          //language=java
          java(
            """
              class Test {
                  int DoNoTChange;

                  public int addTen(int dont_rename_one) {
                      double RenameTwo = 2.0;
                      float __rename__three__ = 2.0;
                      long _Rename__Four = 2.0;
                      return dont_rename_one + RenameTwo + __rename__three__ + _Rename__Four + 10;
                  }
              }
              """,
            """
              class Test {
                  int DoNoTChange;

                  public int addTen(int dont_rename_one) {
                      double renameTwo = 2.0;
                      float renameThree = 2.0;
                      long renameFour = 2.0;
                      return dont_rename_one + renameTwo + renameThree + renameFour + 10;
                  }
              }
              """
          )
        );
    }

    @SuppressWarnings("JavadocDeclaration")
    @Disabled
    @Issue("https://github.com/openrewrite/rewrite/issues/2437")
    @Test
    void renameJavaDocParam() {
        rewriteRun(
          //language=java
          java(
            """
              class Test {
                  /**
                   * @param rename_one
                   */
                  public void addTen(int rename_one) {
                  }
              }
              """,
            """
              class Test {
                  /**
                   * @param renameOne
                   */
                  public void addTen(int renameOne) {
                  }
              }
              """
          )
        );
    }

    @Test
    void doNotChangeStaticImports() {
        rewriteRun(
          //language=java
          java(
            """
              package p;
              public class B {
                  public static int _staticImport_ = 0;
              }
              """
          ),
          //language=java
          java(
            """
              import static p.B._staticImport_;

              class Test {
                  public int addTen(int testValue) {
                      _staticImport_++;
                      return testValue;
                  }
              }
              """
          )
        );
    }

    @Test
    void doNotChangeInheritedFields() {
        rewriteRun(
          //language=java
          java(
            """
              class A {
                  public int _inheritedField_ = 0;
              }
              """
          ),
          //language=java
          java(
            """
              class Test extends A {
                  public int addTen(int testValue) {
                      _inheritedField_++;
                      return testValue;
                  }
              }
              """
          )
        );
    }

    @Test
    void doNotChangeIfToNameExists() {
        rewriteRun(
          //language=java
          java(
            """
              class Test {
                  int DoNoTChange;

                  public int addTen(int test_value) {
                      int testValue = 10;
                      return test_value + testValue;
                  }
              }
              """
          )
        );
    }

    @Test
    void doNotChangeCatch() {
        rewriteRun(
          //language=java
          java(
            """
              class Test {
                  int DoNoTChange;

                  public int addTen(String value) {
                      try {
                          Integer.valueOf(value);
                      // Rule does not apply to catch variables with 1 character.
                      } catch (Exception E){
                          throw new IllegalArgumentException("Test", E);
                      }
                      return DoNoTChange + 10;
                  }
              }
              """
          )
        );
    }

    @Test
    void doNotForLoop() {
        rewriteRun(
          //language=java
          java(
            """
              class Test {
                  int DoNoTChange;

                  public void addTen() {
                      // Rule does not apply to for loop control variables.
                      for (int do_not_change = 0; do_not_change < 10; do_not_change++) {
                         DoNoTChange += do_not_change;
                      }
                  }
              }
              """
          )
        );
    }

    @Test
    void doNotRenameConstantVariable() {
        rewriteRun(
          //language=java
          java(
            """
                  import java.util.ArrayList;
                  import java.util.List;
                  class Test {
                      public List<String> testFoo() {
                          return new ArrayList<>() {
                              private final int DO_NOT_CHANGE = 1;
                         
                          };
                          
                      }
                  }

              """
          )
        );
    }


    @Issue("https://github.com/openrewrite/rewrite-static-analysis/issues/103")
    @Test
    void doNotRenameUnderscoreNumber() {
        rewriteRun(
          //language=java
          java(
            """
              class Test {
                  public int testFoo() {
                      int _20th = 20;
                      int _40th = 40;
                      return _20th + _40th;
                  }
              }
              """
          )
        );
    }

    @Issue("https://github.com/openrewrite/rewrite-static-analysis/issues/103")
    @Test
    void doNotRenameUnderscoreOnly() {
        rewriteRun(
          //language=java
          java(
            """
              class Test {
                  public int testFoo() {
                      int _ = 20;
                      return _;
                  }
              }
              """
          )
        );
    }

    @Test
    void recordCompactConstructor() {
        rewriteRun(
          spec -> spec.beforeRecipe(cu -> {
              var javaRuntimeVersion = System.getProperty("java.runtime.version");
              var javaVendor = System.getProperty("java.vm.vendor");
              if (new JavaVersion(UUID.randomUUID(), javaRuntimeVersion, javaVendor, javaRuntimeVersion, javaRuntimeVersion).getMajorVersion() != 17) {
                  spec.recipe(Recipe.noop());
              }
          }),
          //language=java
          java(
            """
              public record MyRecord(
                 boolean bar,
                 String foo
              ) {
                 public MyRecord {
                    if (foo == null) {
                        foo = "defaultValue";
                    }
                }
              }
              """
          )
        );
    }

    @Test
    void noChangeInAnonymousClass() {
        rewriteRun(
          java(
            """
              interface MyInterface {
                  void doSomething();
              }

              public class A {
                  void test() {
                      new MyInterface() {
                          int SOME_THING_NOT_LOCAL = 1;

                          @Override
                          public void doSomething() {
                          }
                      };
                  }
              }
              """
          )
        );
    }

    @Test
    @Issue("https://github.com/openrewrite/rewrite-static-analysis/issues/171")
    void renameMultipleOcurrencesDifferentScope() {
        rewriteRun(
          java(
            """
              class Test {
                  void test() {
                      String ID;
                  }
                  void test2() {
                      String ID;
                  }
              }
              """,
            """
              class Test {
                  void test() {
                      String id;
                  }
                  void test2() {
                      String id;
                  }
              }
              """
          )
        );
    }

    @Test
    void doNotRenameIfInParent() {
        rewriteRun(
          java(
            """
              class Test {
                  String id;
                  void test() {
                      String ID;
                  }
              }
              """
          )
        );
    }

    @Test
    void doNotRenameIfInParentFromInnerClass() {
        rewriteRun(
          java(
            """
              class Test {
                  String id;
                  class InnerClass {
                      void test() {
                          String ID;
                      }
                  }
              }
              """
          )
        );
    }

    @Test
    void renameFinalLocalVariables() {
        //language=java
        rewriteRun(
          java(
            """
              class Test {
                  void test() {
                      final String FINAL_VARIABLE;
                  }
              }
              """,
            """
              class Test {
                  void test() {
                      final String finalVariable;
                  }
              }
              """
          )
        );
    }

    @Issue("https://github.com/openrewrite/rewrite-static-analysis/pull/205")
    @Test
    void doNotRenameMethodArguments() {
        //language=java
        rewriteRun(
          java(
            """
              class MyController {
                  String getHello(String your_name) {
                      return "hello " + your_name;
                  }
              }
              """
          )
        );
    }

}
