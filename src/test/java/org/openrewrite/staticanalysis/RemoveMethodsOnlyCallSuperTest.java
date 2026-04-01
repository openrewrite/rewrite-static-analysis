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

@SuppressWarnings({"RedundantMethodOverride"})
class RemoveMethodsOnlyCallSuperTest implements RewriteTest {

    @Override
    public void defaults(RecipeSpec spec) {
        spec.recipe(new RemoveMethodsOnlyCallSuper());
    }

    @DocumentExample
    @Test
    void removeVoidMethodOnlyCallingSuper() {
        rewriteRun(
          //language=java
          java(
            """
              class Parent {
                  void foo() {
                  }
              }
              """
          ),
          //language=java
          java(
            """
              class Child extends Parent {
                  @Override
                  void foo() {
                      super.foo();
                  }
              }
              """,
            """
              class Child extends Parent {
              }
              """
          )
        );
    }

    @Test
    void removeMethodWithReturnValue() {
        rewriteRun(
          //language=java
          java(
            """
              class Parent {
                  int calculate() {
                      return 42;
                  }
              }
              """
          ),
          //language=java
          java(
            """
              class Child extends Parent {
                  @Override
                  int calculate() {
                      return super.calculate();
                  }
              }
              """,
            """
              class Child extends Parent {
              }
              """
          )
        );
    }

    @Test
    void removeMethodWithArguments() {
        rewriteRun(
          //language=java
          java(
            """
              class Parent {
                  void foo(int a, String b) {
                  }
              }
              """
          ),
          //language=java
          java(
            """
              class Child extends Parent {
                  @Override
                  void foo(int a, String b) {
                      super.foo(a, b);
                  }
              }
              """,
            """
              class Child extends Parent {
              }
              """
          )
        );
    }

    @Test
    void removeMethodWithOnlyOverrideAnnotation() {
        rewriteRun(
          //language=java
          java(
            """
              class Parent {
                  String getValue() {
                      return "value";
                  }
              }
              """
          ),
          //language=java
          java(
            """
              class Child extends Parent {
                  @Override
                  String getValue() {
                      return super.getValue();
                  }
              }
              """,
            """
              class Child extends Parent {
              }
              """
          )
        );
    }

    @Test
    void doNotChangeMethodWithDeprecatedAnnotation() {
        rewriteRun(
          //language=java
          java(
            """
              class Parent {
                  void foo() {
                  }
              }
              """
          ),
          //language=java
          java(
            """
              class Child extends Parent {
                  @Deprecated
                  @Override
                  void foo() {
                      super.foo();
                  }
              }
              """
          )
        );
    }

    @Test
    void doNotChangeMethodWithJavadoc() {
        rewriteRun(
          //language=java
          java(
            """
              class Parent {
                  void foo() {
                  }
              }
              """
          ),
          //language=java
          java(
            """
              class Child extends Parent {
                  /**
                   * Important documentation.
                   */
                  @Override
                  void foo() {
                      super.foo();
                  }
              }
              """
          )
        );
    }

    @Test
    void doNotChangeMethodThatWidensVisibility() {
        rewriteRun(
          //language=java
          java(
            """
              class Parent {
                  protected void foo() {
                  }
              }
              """
          ),
          //language=java
          java(
            """
              class Child extends Parent {
                  @Override
                  public void foo() {
                      super.foo();
                  }
              }
              """
          )
        );
    }

    @Test
    void doNotChangeMethodWithDifferentArgOrder() {
        rewriteRun(
          //language=java
          java(
            """
              class Parent {
                  void foo(int a, int b) {
                  }
              }
              """
          ),
          //language=java
          java(
            """
              class Child extends Parent {
                  @Override
                  void foo(int a, int b) {
                      super.foo(b, a);
                  }
              }
              """
          )
        );
    }

    @Test
    void doNotChangeMethodWithExtraLogic() {
        rewriteRun(
          //language=java
          java(
            """
              class Parent {
                  void foo() {
                  }
              }
              """
          ),
          //language=java
          java(
            """
              class Child extends Parent {
                  @Override
                  void foo() {
                      System.out.println("logging");
                      super.foo();
                  }
              }
              """
          )
        );
    }

    @Test
    void doNotChangeMethodCallingDifferentSuperMethod() {
        rewriteRun(
          //language=java
          java(
            """
              class Parent {
                  void foo() {
                  }
                  void bar() {
                  }
              }
              """
          ),
          //language=java
          java(
            """
              class Child extends Parent {
                  @Override
                  void foo() {
                      super.bar();
                  }
              }
              """
          )
        );
    }

    @Test
    void doNotChangeConstructor() {
        rewriteRun(
          //language=java
          java(
            """
              class Parent {
                  Parent() {
                  }
              }
              """
          ),
          //language=java
          java(
            """
              class Child extends Parent {
                  Child() {
                      super();
                  }
              }
              """
          )
        );
    }

    @Test
    void doNotChangeMethodNotCallingSuper() {
        rewriteRun(
          //language=java
          java(
            """
              class Parent {
                  void foo() {
                  }
              }
              """
          ),
          //language=java
          java(
            """
              class Child extends Parent {
                  @Override
                  void foo() {
                      this.toString();
                  }
              }
              """
          )
        );
    }
}
