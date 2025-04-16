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
import org.openrewrite.Recipe;
import org.openrewrite.java.JavaParser;
import org.openrewrite.java.marker.JavaVersion;
import org.openrewrite.test.RecipeSpec;
import org.openrewrite.test.RewriteTest;

import java.util.UUID;

import static org.openrewrite.java.Assertions.java;

@SuppressWarnings({"ConstantConditions", "AnonymousHasLambdaAlternative", "ResultOfMethodCallIgnored"})
class RenamePrivateFieldsToCamelCaseTest implements RewriteTest {
    @Override
    public void defaults(RecipeSpec spec) {
        spec.recipe(new RenamePrivateFieldsToCamelCase());
    }

    @Issue("https://github.com/openrewrite/rewrite/issues/2461")
    @Test
    void upperSnakeToLowerCamel() {
        rewriteRun(
          //language=java
          java(
            """
              class Test {
                  private String D_TYPE_CONNECT = "";
              }
              """,
            """
              class Test {
                  private String dTypeConnect = "";
              }
              """
          )
        );
    }

    @Test
    void lowerCaseWithLeadingDollarCharacter() {
        rewriteRun(
          //language=java
          java(
            """
              class Test {
                  private String $t = "";
              }
              """
          )
        );
    }

    @Issue("https://github.com/openrewrite/rewrite/issues/2294")
    @Test
    void nameConflict() {
        rewriteRun(
          //language=java
          java(
            """
              class A {
                  private final String _var = "";
                  private void a() {
                      if (true) {
                          Thread t = new Thread() {
                              public void run() {
                                  String var = "a";
                              }
                          };
                      }
                  }
              }
              """
          )
        );
    }

    @Issue("https://github.com/openrewrite/rewrite/issues/2285")
    @Test
    void doesNotRenameAssociatedIdentifiers() {
        rewriteRun(
          //language=java
          java(
            """
              class A {
                  private static String MY_STRING = "VAR";
                  void doSomething() {
                      MY_STRING.toLowerCase();
                      AB.INNER_STRING.toLowerCase();
                  }

                  private static class AB {
                      private static String INNER_STRING = "var";
                      void doSomething() {
                          MY_STRING.toLowerCase();
                      }
                  }
              }
              """,
            """
              class A {
                  private static String myString = "VAR";
                  void doSomething() {
                      myString.toLowerCase();
                      AB.INNER_STRING.toLowerCase();
                  }

                  private static class AB {
                      private static String INNER_STRING = "var";
                      void doSomething() {
                          myString.toLowerCase();
                      }
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
                  private int member = _staticImport_;
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
                  private int _inheritedField_ = super._inheritedField_;
              }
              """,
            """
              class Test extends A {
                  private int inheritedField = super._inheritedField_;
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
                  int test_value;

                  public int addTen(int testValue) {
                      return test_value + testValue;
                  }
              }
              """
          )
        );
    }

    @Test
    void doNotChangeExistsInOnlyOneMethod() {
        rewriteRun(
          //language=java
          java(
            """
              class Test {
                  private int DoNoTChange;

                  public int addTwenty(String doNoTChange) {
                      return DoNoTChange + 20;
                  }
                  public int addTen(String value) {
                      return DoNoTChange + 10;
                  }
              }
              """
          )
        );
    }

    @DocumentExample
    @Test
    void renamePrivateMembers() {
        rewriteRun(
          //language=java
          java(
            """
              class Test {
                  private int DoChange = 10;
                  public int DoNotChangePublicMember;
                  int DoNotChangeDefaultMember;

                  public int getTen() {
                      return DoChange;
                  }

                  public int getTwenty() {
                      return this.DoChange * 2;
                  }

                  public int getThirty() {
                      return DoChange * 3;
                  }
              }
              """,
            """
              class Test {
                  private int doChange = 10;
                  public int DoNotChangePublicMember;
                  int DoNotChangeDefaultMember;

                  public int getTen() {
                      return doChange;
                  }

                  public int getTwenty() {
                      return this.doChange * 2;
                  }

                  public int getThirty() {
                      return doChange * 3;
                  }
              }
              """
          )
        );
    }

    @Test
    void renameWithFieldAccess() {
        rewriteRun(
          //language=java
          java(
            """
              class ClassWithPublicField {
                  public int publicField = 10;
              }
              """
          ),
          //language=java
          java(
            """
              class Test {
                  private ClassWithPublicField DoChange = new ClassWithPublicField();

                  public int getTen() {
                      return DoChange.publicField;
                  }
              }
              """,
            """
              class Test {
                  private ClassWithPublicField doChange = new ClassWithPublicField();

                  public int getTen() {
                      return doChange.publicField;
                  }
              }
              """
          )
        );
    }

    @Test
    void doNotRenameInnerClassesMembers() {
        rewriteRun(
          //language=java
          java(
            """
              class Test {
                  private int test = new InnerClass().DoNotChange + new InnerClass().DoNotChange2;

                  private class InnerClass{
                      public int DoNotChange = 10;
                      private int DoNotChange2 = 10;
                  }
              }
              """
          )
        );
    }

    @Test
    void renameUsageInInnerClasses() {
        rewriteRun(
          //language=java
          java(
            """
              class Test {
                  private int DoChange = 10;

                  private class InnerClass{
                      private int test = DoChange + 1;
                  }
              }
              """,
            """
              class Test {
                  private int doChange = 10;

                  private class InnerClass{
                      private int test = doChange + 1;
                  }
              }
              """
          )
        );
    }

    @Test
    void doNotRenameAnonymousInnerClasses() {
        rewriteRun(
          //language=java
          java(
            """
              interface Book{}
              """
          ),
          //language=java
          java(
            """
              class B {
                  B(){
                      new Book() {
                        private String DoChange;

                        @Override
                        public String toString() {
                          return DoChange;
                        }
                      };
                  }
              }
              """
          )
        );
    }

    @Test
    void handleStaticMethods() {
        rewriteRun(
          //language=java
          java(
            """
              class A {
                  private int _variable;
                  public static A getInstance(){
                      A a = new A();
                      a._variable = 12;
                      return a;
                  }
              }
              """,
            """
              class A {
                  private int variable;
                  public static A getInstance(){
                      A a = new A();
                      a.variable = 12;
                      return a;
                  }
              }
              """
          )
        );
    }

    @Test
    void renameFinalMembers() {
        rewriteRun(
          //language=java
          java(
            """
              class A {
                  private final int _final_variable;
                  private static int _static_variable;
                  private static final int DO_NOT_CHANGE;
              }
              """,
            """
              class A {
                  private final int finalVariable;
                  private static int staticVariable;
                  private static final int DO_NOT_CHANGE;
              }
              """
          )
        );
    }

    @Test
    void doNotChangeWhenSameMethodParam() {
        rewriteRun(
          //language=java
          java(
            """
              class A {
                  private int _variable;
                  public void getInstance(int _variable) {
                      this._variable = _variable;
                  }
              }
              """
          )
        );
    }

    @Test
    void renameWhenSameMethodExists() {
        rewriteRun(
          //language=java
          java(
            """
              class A {
                  private boolean _hasMethod;
                  public boolean hasMethod() {
                      return _hasMethod;
                  }
              }
              """,
            """
              class A {
                  private boolean hasMethod;
                  public boolean hasMethod() {
                      return hasMethod;
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
              class A {
                  private boolean _20th;
                   public boolean method() {
                       return _20th;
                   }
              }
              """
          )
        );
    }

    @Test
    void markConstantsStatic() {
        rewriteRun(
          //language=java
          java(
            """
              class A {
                private final int CONSTANT = 42;
              }
              """,
            """
              class A {
                private static final int CONSTANT = 42;
              }
              """
          )
        );
    }

    @Test
    void leaveNonConstantsNonStatic() {
        rewriteRun(
          //language=java
          java(
            """
              class A {
                private final int CONSTANT = getFortyTwo();
                private int getFortyTwo() {
                  return 42;
                }
              }
              """,
            """
              class A {
                private final int constant = getFortyTwo();
                private int getFortyTwo() {
                  return 42;
                }
              }
              """
          )
        );
    }

    @Issue("https://github.com/openrewrite/rewrite-static-analysis/issues/103")
    @Test
    void doNotRenameUnderscoresOnly() {
        rewriteRun(
          //language=java
          java(
            """
              class A {
                  private boolean __;
                   public boolean method() {
                       return __;
                   }
              }
              """
          )
        );
    }

    @Issue("https://github.com/openrewrite/rewrite/issues/2526")
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
    @Issue("https://github.com/openrewrite/rewrite-static-analysis/issues/267")
    void doNotChangeLombokAnnotatedClasses() {
        rewriteRun(
          spec -> spec.parser(JavaParser.fromJavaVersion().classpath("lombok")),
          //language=java
          java(
            """
              @lombok.RequiredArgsConstructor
              class Test {
                  private String D_TYPE_CONNECT = "";
              }
              """
          )
        );
    }

    @Test
    @Issue("https://github.com/openrewrite/rewrite-static-analysis/issues/267")
    void doNotChangeLombokAnnotatedFields() {
        rewriteRun(
          spec -> spec.parser(JavaParser.fromJavaVersion().classpath("lombok")),
          //language=java
          java(
            """
              class Test {
                  @lombok.Setter
                  private String D_TYPE_CONNECT = "";
              }
              """
          )
        );
    }
}
