/*
 * Copyright 2026 the original author or authors.
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

@SuppressWarnings({"unused", "StaticViaInstance"})
class StaticAccessViaInstanceTest implements RewriteTest {

    @Override
    public void defaults(RecipeSpec spec) {
        spec.recipe(new StaticAccessViaInstance());
    }

    @DocumentExample
    @Test
    void staticFieldViaInstance() {
        rewriteRun(
          //language=java
          java(
            """
            class MyClass {
                static int COUNT = 0;
                void foo(MyClass instance) {
                    int x = instance.COUNT;
                }
            }
            """,
            """
            class MyClass {
                static int COUNT = 0;
                void foo(MyClass instance) {
                    int x = MyClass.COUNT;
                }
            }
            """
          )
        );
    }

    @Test
    void staticMethodViaInstance() {
        rewriteRun(
          //language=java
          java(
            """
            class MyClass {
                static int compute() { return 0; }
                void foo(MyClass instance) {
                    int x = instance.compute();
                }
            }
            """,
            """
            class MyClass {
                static int compute() { return 0; }
                void foo(MyClass instance) {
                    int x = MyClass.compute();
                }
            }
            """
          )
        );
    }

    @Test
    void staticAccessViaThis() {
        rewriteRun(
          //language=java
          java(
            """
            class MyClass {
                static int COUNT = 0;
                void foo() {
                    int x = this.COUNT;
                }
            }
            """,
            """
            class MyClass {
                static int COUNT = 0;
                void foo() {
                    int x = MyClass.COUNT;
                }
            }
            """
          )
        );
    }

    @Test
    void staticMethodViaThis() {
        rewriteRun(
          //language=java
          java(
            """
            class MyClass {
                static int compute() { return 0; }
                void foo() {
                    int x = this.compute();
                }
            }
            """,
            """
            class MyClass {
                static int compute() { return 0; }
                void foo() {
                    int x = MyClass.compute();
                }
            }
            """
          )
        );
    }

    @Test
    void staticMethodWithArguments() {
        rewriteRun(
          //language=java
          java(
            """
            class MyClass {
                static int add(int a, int b) { return a + b; }
                void foo(MyClass instance) {
                    int x = instance.add(1, 2);
                }
            }
            """,
            """
            class MyClass {
                static int add(int a, int b) { return a + b; }
                void foo(MyClass instance) {
                    int x = MyClass.add(1, 2);
                }
            }
            """
          )
        );
    }

    @Test
    void inheritedStaticField() {
        rewriteRun(
          //language=java
          java(
            """
            class Parent {
                static int COUNT = 0;
            }
            """
          ),
          //language=java
          java(
            """
            class Child extends Parent {
                void foo(Child child) {
                    int x = child.COUNT;
                }
            }
            """,
            """
            class Child extends Parent {
                void foo(Child child) {
                    int x = Parent.COUNT;
                }
            }
            """
          )
        );
    }

    @Test
    void doNotChangeAlreadyViaClassName() {
        rewriteRun(
          //language=java
          java(
            """
            class MyClass {
                static int COUNT = 0;
                void foo() {
                    int x = MyClass.COUNT;
                }
            }
            """
          )
        );
    }

    @Test
    void doNotChangeUnqualifiedAccess() {
        rewriteRun(
          //language=java
          java(
            """
            class MyClass {
                static int compute() { return 0; }
                void foo() {
                    int x = compute();
                }
            }
            """
          )
        );
    }

    @Test
    void doNotChangeMethodCallAsSelect() {
        rewriteRun(
          //language=java
          java(
            """
            class MyClass {
                static int COUNT = 0;
                static MyClass getInstance() { return new MyClass(); }
                void foo() {
                    int x = getInstance().COUNT;
                }
            }
            """
          )
        );
    }

    @Test
    void doNotChangeNewExpressionAsSelect() {
        rewriteRun(
          //language=java
          java(
            """
            class MyClass {
                static int COUNT = 0;
                void foo() {
                    int x = new MyClass().COUNT;
                }
            }
            """
          )
        );
    }
}
