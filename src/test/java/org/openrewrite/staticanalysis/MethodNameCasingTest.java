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
import org.openrewrite.ExecutionContext;
import org.openrewrite.Issue;
import org.openrewrite.java.JavaIsoVisitor;
import org.openrewrite.java.tree.J;
import org.openrewrite.test.RecipeSpec;
import org.openrewrite.test.RewriteTest;
import org.openrewrite.test.TypeValidation;

import static org.openrewrite.java.Assertions.*;
import static org.openrewrite.test.RewriteTest.toRecipe;

@Issue("https://github.com/openrewrite/rewrite/issues/466")
class MethodNameCasingTest implements RewriteTest {

    @Override
    public void defaults(RecipeSpec spec) {
        spec.recipe(new MethodNameCasing(false, false));
    }

    @Issue("https://github.com/openrewrite/rewrite/issues/2571")
    @Test
    void noChangesOnMethodsBeginningWithUnderscore() {
        rewriteRun(
          //language=java
          java(
            """
              class Test {
                  void _1() {}
                  void _finally() {}
              }
              """
          )
        );
    }

    @Issue("https://github.com/openrewrite/rewrite/issues/2557")
    @Test
    void interfaceMethods() {
        rewriteRun(
          //language=java
          java(
            """
              interface Test {
                  void getFoo_bar() {}
              }
              """
          )
        );
    }

    @Issue("https://github.com/openrewrite/rewrite/issues/2557")
    @Test
    void annotationMethods() {
        rewriteRun(
          //language=java
          java(
            """
              @interface Test {
                  String getFoo_bar();
              }
              """
          )
        );
    }

    @Issue("https://github.com/openrewrite/rewrite/issues/2424")
    @Test
    void correctMethodNameCasing() {
        rewriteRun(
          srcMainJava(
            //language=java
            java(
              """
                class Test {
                    private String getFoo_bar() {
                        return "foobar";
                    }
                }
                """,
              """
                class Test {
                    private String getFooBar() {
                        return "foobar";
                    }
                }
                """
            )
          )
        );
    }

    @Issue("https://github.com/openrewrite/rewrite/issues/2424")
    @Test
    void doNotRenamePublicMethods() {
        rewriteRun(
          //language=java
          java(
            """
              class Test {
                  public void getFoo_bar() {}
              }
              """
          )
        );
    }

    @Issue("https://github.com/openrewrite/rewrite/issues/2424")
    @Test
    void doNotRenamePublicMethodsNullOptions() {
        rewriteRun(
          spec -> spec.recipe(new MethodNameCasing(null, null)),
          //language=java
          java(
            """
              class Test {
                  public void getFoo_bar() {}
              }
              """
          )
        );
    }

    @Issue("https://github.com/openrewrite/rewrite/issues/2424")
    @Test
    void okToRenamePublicMethods() {
        rewriteRun(
          spec -> spec.recipe(new MethodNameCasing(true, true)),
          srcTestJava(
            //language=java
            java(
              """
                class Test {
                    public void getFoo_bar(){}
                }
                """,
              """
                class Test {
                    public void getFooBar(){}
                }
                """
            )
          )
        );
    }

    @Issue("https://github.com/openrewrite/rewrite/issues/1741")
    @Test
    void doNotApplyToTest() {
        rewriteRun(
          srcTestJava(
            //language=java
            java(
              """
                class Test {
                    void MyMethod_with_über() {
                    }
                }
                """
            )
          )
        );
    }

    @Issue("https://github.com/openrewrite/rewrite/issues/1741")
    @Test
    void applyChangeToTest() {
        rewriteRun(
          spec -> spec.recipe(new MethodNameCasing(true, false)),
          srcTestJava(
            //language=java
            java(
              """
                class Test {
                    void MyMethod_with_über() {
                    }
                }
                """,
              """
                class Test {
                    void myMethodWithUber() {
                    }
                }
                """
            )
          )
        );
    }

    @Test
    void changeMethodDeclaration() {
        rewriteRun(
          srcMainJava(
            //language=java
            java(
              """
                class Test {
                    void MyMethod_with_über() {
                    }
                }
                """,
              """
                class Test {
                    void myMethodWithUber() {
                    }
                }
                """
            )
          )
        );
    }

    @Test
    void changeCamelCaseMethodWithFirstLetterUpperCase() {
        rewriteRun(
          srcMainJava(
            //language=java
            java(
              """
                class Test {
                    void MyMethod() {
                    }
                }
                """,
              """
                class Test {
                    void myMethod() {
                    }
                }
                """
            )
          )
        );
    }

    @Test
    void changeMethodInvocations() {
        rewriteRun(
          srcMainJava(
            //language=java
            java(
              """
                class Test {
                    void MyMethod_with_über() {
                    }
                }
                """, """
                class Test {
                    void myMethodWithUber() {
                    }
                }
                """
            ),
            //language=java
            java(
              """
                class A {
                    void test() {
                        new Test().MyMethod_with_über();
                    }
                }
                """,
              """
                class A {
                    void test() {
                        new Test().myMethodWithUber();
                    }
                }
                """
            )
          )
        );
    }

    @Test
    void dontChangeCorrectlyCasedMethods() {
        rewriteRun(
          //language=java
          java(
            """
              class Test {
                  void dontChange() {
                  }
              }
              """
          )
        );
    }

    @Test
    void changeMethodNameWhenOverride() {
        rewriteRun(
          srcMainJava(
            //language=java
            java(
              """
                class ParentClass {
                    void Method() {
                    }
                }
                """,
              """
                class ParentClass {
                    void method() {
                    }
                }
                """
            ),
            //language=java
            java(
              """
                class Test extends ParentClass {
                    @Override
                    void Method() {
                    }
                }
                """,
              """
                class Test extends ParentClass {
                    @Override
                    void method() {
                    }
                }
                """
            )
          )
        );
    }

    @Test
    void newNameExists() {
        rewriteRun(
          //language=java
          java(
            """
              class Test {
                  void Method() {
                  }
                  void method() {
                  }
              }
              """
          )
        );
    }

    @Test
    void nameExistsInInnerClass() {
        rewriteRun(
          srcMainJava(
            //language=java
            java(
              """
                class T {
                    void Method(){}

                    private static class M {
                        void Method(){}
                    }
                }
                """,
              """
                class T {
                    void method(){}

                    private static class M {
                        void method(){}
                    }
                }
                """
            )
          )
        );
    }

    @SuppressWarnings("UnusedAssignment")
    @Issue("https://github.com/openrewrite/rewrite/issues/2103")
    @Test
    void snakeCaseToCamelCase() {
        rewriteRun(
          srcMainJava(
            //language=java
            java(
              """
                class T {
                    private static int SOME_METHOD() {
                      return 1;
                    }
                    private static int some_method_2() {
                      return 1;
                    }
                    private static int some_über_method() {
                      return 1;
                    }
                    public static void anotherMethod() {
                      int i = SOME_METHOD();
                      i = some_method_2();
                      i = some_über_method();
                    }
                }
                """,
              """
                class T {
                    private static int someMethod() {
                      return 1;
                    }
                    private static int someMethod2() {
                      return 1;
                    }
                    private static int someUberMethod() {
                      return 1;
                    }
                    public static void anotherMethod() {
                      int i = someMethod();
                      i = someMethod2();
                      i = someUberMethod();
                    }
                }
                """
            )
          )
        );
    }

    // This test uses a recipe remove ClassDeclaration types information prior to running the MethodNameCasing recipe.
    // This results in a change with an empty diff, thus before and after sources are identical
    @Issue("https://github.com/openrewrite/rewrite/issues/2103")
    @Test
    void doesNotRenameMethodInvocationsWhenTheMethodDeclarationsClassTypeIsNull() {
        rewriteRun(
          spec -> spec
            .typeValidationOptions(TypeValidation.none())
            .recipes(
              toRecipe(() -> new JavaIsoVisitor<>() {
                  @Override
                  public J.ClassDeclaration visitClassDeclaration(J.ClassDeclaration classDecl, ExecutionContext ctx) {
                      return super.visitClassDeclaration(classDecl, ctx).withType(null);
                  }
              }),
              new MethodNameCasing(true, false)
            ),
          //language=java
          java(
            """
              package abc;
              class T {
                  public static int MyMethod() {return -1;}
                  public static void anotherMethod() {
                      int i = MyMethod();
                  }
              }
              """,
            """
              package abc;
              class T {
                  public static int MyMethod() {return -1;}
                  public static void anotherMethod() {
                      int i = MyMethod();
                  }
              }
              """
          )
        );
    }

    @Test
    void keepCamelCase() {
        rewriteRun(
          srcMainJava(
            //language=java
            java(
              """
                class Test {
                    private void Method() {

                    }
                }
                """,
              """
                class Test {
                    private void method() {

                    }
                }
                """
            )
          )
        );
    }

    @Test
    void keepCamelCase2() {
        rewriteRun(
          srcMainJava(
            //language=java
            java(
              """
                import java.util.*;

                class Test {
                    private List<String> GetNames() {
                        List<String> result = new ArrayList<>();
                        result.add("Alice");
                        result.add("Bob");
                        result.add("Carol");
                        return result;
                    }

                    public void run() {
                        for (String n: GetNames()) {
                            System.out.println(n);
                        }
                    }
                }
                """,
              """
                import java.util.*;

                class Test {
                    private List<String> getNames() {
                        List<String> result = new ArrayList<>();
                        result.add("Alice");
                        result.add("Bob");
                        result.add("Carol");
                        return result;
                    }

                    public void run() {
                        for (String n: getNames()) {
                            System.out.println(n);
                        }
                    }
                }
                """
            )
          )
        );
    }

    @SuppressWarnings({"UnnecessaryLocalVariable", "unchecked", "rawtypes"})
    @Test
    void changeNameOfMethodWithArrayArgument() {
        rewriteRun(
          srcMainJava(
            //language=java
            java(
              """
                import java.util.*;

                class Test {
                    private List<String> GetNames(String[] names) {
                        List<String> result = new ArrayList<>(Arrays.asList(names));
                        return result;
                    }
                }
                """,
              """
                import java.util.*;

                class Test {
                    private List<String> getNames(String[] names) {
                        List<String> result = new ArrayList<>(Arrays.asList(names));
                        return result;
                    }
                }
                """
            )
          )
        );
    }

    @Issue("https://github.com/openrewrite/rewrite/issues/2261")
    @Test
    void unknownParameterTypes() {
        rewriteRun(
          spec -> spec.typeValidationOptions(TypeValidation.none()),
          srcMainJava(
            //language=java
            java(
              """
                class Test {
                    private void Foo(Unknown u) {
                    }
                }
                """,
              """
                class Test {
                    private void foo(Unknown u) {
                    }
                }
                """
            )
          )
        );
    }
}
