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
import org.openrewrite.Issue;
import org.openrewrite.test.RecipeSpec;
import org.openrewrite.test.RewriteTest;

import static org.openrewrite.java.Assertions.java;

class RemoveUnusedParamsTest implements RewriteTest {

    @Override
    public void defaults(RecipeSpec spec) {
        spec.recipe(new RemoveUnusedParams());
    }

    @DocumentExample
    @Test
    void removeUnusedMethodParameter() {
        rewriteRun(
          java(
            """
              public class Test {
                  void method(String unused) {
                      System.out.println("Hello");
                  }
              }
              """,
            """
              public class Test {
                  void method() {
                      System.out.println("Hello");
                  }
              }
              """
          )
        );
    }

    @Test
    void doNotRemoveUsedParameter() {
        rewriteRun(
          java(
            """
              public class Test {
                  void method(String input) {
                      System.out.println(input);
                  }
              }
              """
          )
        );
    }

    @Test
    void doNotRemoveOverriddenMethodParameter() {
        rewriteRun(
          java(
            """
              class Test {
                  void method(String param) {}
              }
              class Base {
                  void method(String param) {}
              }
              class Derived extends Base {
                  @Override
                  void method(String param) {
                      // not used but required
                  }
              }
              """,
            """
              class Test {
                  void method() {}
              }
              class Base {
                  void method(String param) {}
              }
              class Derived extends Base {
                  @Override
                  void method(String param) {
                      // not used but required
                  }
              }
              """
          )
        );
    }

    @Test
    void doNotRemoveAnnotatedParameter() {
        rewriteRun(
          java(
            """
              public class Test {
                  void method(@Deprecated String param) {}
              }
              """
          )
        );
    }

    @Test
    void removeMultipleUnusedParams() {
        rewriteRun(
          java(
            """
              public class Test {
                  void method(String a, int b, double c) {
                      System.out.println("Only prints this");
                  }
              }
              """,
            """
              public class Test {
                  void method() {
                      System.out.println("Only prints this");
                  }
              }
              """
          )
        );
    }

    @Test
    void preserveJavadocAndComments() {
        rewriteRun(
          java(
            """
              public class Test {
                  /**
                   * Some doc
                   * @param unused this param is never used
                   */
                  void method(String unused) {
                      // comment
                      System.out.println("used");
                  }
              }
              """,
            """
              public class Test {
                  /**
                   * Some doc
                   * @param unused this param is never used
                   */
                  void method() {
                      // comment
                      System.out.println("used");
                  }
              }
              """
          )
        );
    }

    @Issue("https://github.com/openrewrite/rewrite-static-analysis/issues/559")
    @Test
    void shadowedParameterShouldStillBeRemoved() {
        rewriteRun(
          java(
            """
              public class Test {
                  void method(String input) {
                      String input = "shadowed";
                      System.out.println(input);
                  }
              }
              """,
            """
              public class Test {
                  void method() {
                      String input = "shadowed";
                      System.out.println(input);
                  }
              }
              """
          )
        );
    }

    @Test
    void removeUnusedStaticMethodParam() {
        rewriteRun(
          java(
            """
              public class Test {
                  static void helper(String unused) {
                      // no use
                  }
              }
              """,
            """
              public class Test {
                  static void helper() {
                      // no use
                  }
              }
              """
          )
        );
    }

    @Test
    void removeUnusedConstructorParam() {
        rewriteRun(
          java(
            """
              public class Test {
                  Test(String unused) {
                      // ctor body
                  }
              }
              """,
            """
              public class Test {
                  Test() {
                      // ctor body
                  }
              }
              """
          )
        );
    }

    @Test
    void removeUnusedVarargs() {
        rewriteRun(
          java(
            """
              public class Test {
                  void m(String... args) {
                      System.out.println("no args used");
                  }
              }
              """,
            """
              public class Test {
                  void m() {
                      System.out.println("no args used");
                  }
              }
              """
          )
        );
    }

    @Test
    void preserveAnnotatedParamButRemoveOthers() {
        rewriteRun(
          java(
            """
              public class Test {
                  void method(@Deprecated String keep, int removeMe) {
                      System.out.println(keep);
                  }
              }
              """,
            """
              public class Test {
                  void method(@Deprecated String keep) {
                      System.out.println(keep);
                  }
              }
              """
          )
        );
    }

    @Test
    void doNotRemoveNativeMethodParam() {
        rewriteRun(
          java(
            """
              public class Test {
                  native void nativeCall(int mustStay);
              }
              """
          )
        );
    }

    @Test
    void interfaceMethodUnchanged() {
        rewriteRun(
          java(
            """
              interface I {
                  void foo(String param);
              }
              """
          )
        );
    }

    @Test
    void skipWronglyDueToSignatureCollision() {
        rewriteRun(
          java(
            """
            class Base {
                void foo(int a, String b) {}
            }
            class Derived extends Base {
                @Override
                void foo(int a, String b) {}
                void foo(String a, int b) {
                    // no use of a or b
                }
            }
            """,
            """
            class Base {
                void foo(int a, String b) {}
            }
            class Derived extends Base {
                @Override
                void foo(int a, String b) {}
                void foo() {
                    // no use of a or b
                }
            }
            """
          )
        );
    }

    @Test
    void nestedInheritanceOverrideChain() {
        rewriteRun(
          java(
            """
            class Lone { void solo(String x) {} }

            class Grandparent { void greet(String msg) {} }
            class Parent extends Grandparent { }
            class Child extends Parent {
                @Override
                void greet(String msg) {
                    // required override
                }
            }
            """,
            """
            class Lone { void solo() {} }

            class Grandparent { void greet(String msg) {} }
            class Parent extends Grandparent { }
            class Child extends Parent {
                @Override
                void greet(String msg) {
                    // required override
                }
            }
            """
          )
        );
    }

    @Test
    void avoidDirectConflict() {
        rewriteRun(
          java(
            """
              public class Test {
                  void method() {
                      System.out.println("Hello");
                  }
                  void method(String unused) {
                      System.out.println("Hello");
                  }
              }
              """
          )
        );
    }

    @Test
    void avoidInheritedConflict() {
        rewriteRun(
          java(
            """
              public class A {
                  String method() {
                      return "A String";
                  }
              }
              """
          ),
          java(
            """
              public class B extends A {
                  Long method(String unused) {
                      return 42L;
                  }
              }
              """
          )
        );
    }

    @Test
    void cascadeRemoveUnusedArguments() {
        rewriteRun(
          java(
            """
              public class Test {
                  void method1(String unused) {
                      method2(unused);
                  }
                  void method2(String unused) {
                      method3(unused);
                  }
                  void method3(String unused) {
                      System.out.println("Hello");
                  }
              }
              """,
            """
              public class Test {
                  void method1() {
                      method2();
                  }
                  void method2() {
                      method3();
                  }
                  void method3() {
                      System.out.println("Hello");
                  }
              }
              """
          )
        );
    }
}
