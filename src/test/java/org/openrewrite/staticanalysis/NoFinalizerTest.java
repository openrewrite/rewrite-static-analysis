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

class NoFinalizerTest implements RewriteTest {

    @Override
    public void defaults(RecipeSpec spec) {
        spec.recipe(new NoFinalizer());
    }

    @DocumentExample
    @SuppressWarnings("FinalizeCalledExplicitly")
    @Test
    void noFinalizer() {
        rewriteRun(
          //language=java
          java(
            """
              class Test {
                  public void method() {
                  }

                  @Override
                  protected void finalize() throws Throwable {
                      super.finalize();
                  }

                  protected void finalize(Object param) throws Throwable {
                      super.finalize();
                  }
              }
              """,
            """
              class Test {
                  public void method() {
                  }

                  protected void finalize(Object param) throws Throwable {
                      super.finalize();
                  }
              }
              """
          )
        );
    }
    @Test
    void retainExplicitCrossFileCall() {
        rewriteRun(
          java(
            """
              package example;
              class AutoCloseInputStream {
                  @Override
                  protected void finalize() throws Throwable {
                      super.finalize();
                  }
              }
              """
          ),
          java(
            """
              package example;
              class StreamTest {
                  void close(AutoCloseInputStream stream) throws Throwable {
                      stream.finalize();
                  }
              }
              """
          )
        );
    }

    @Test
    void retainMethodReferenceAndInheritedFinalizer() {
        rewriteRun(
          java(
            """
              package example;
              class Parent {
                  @Override
                  public void finalize() {}
              }
              class Child extends Parent {}
              """
          ),
          java(
            """
              package example;
              class Caller {
                  Runnable cleanup(Child child) {
                      return child::finalize;
                  }
              }
              """
          )
        );
    }

    @Test
    void retainSuperFinalizerOfExplicitlyCalledOverride() {
        rewriteRun(
          java(
            """
              class Parent {
                  @Override
                  public void finalize() {}
              }
              class Child extends Parent {
                  @Override
                  public void finalize() {
                      super.finalize();
                  }
              }
              class Caller {
                  void cleanup(Child child) {
                      child.finalize();
                  }
              }
              """
          )
        );
    }

    @Test
    void retainOverridesOfExplicitlyCalledBaseMethod() {
        rewriteRun(
          java(
            """
              class Parent {
                  @Override
                  public void finalize() {}
              }
              class Child extends Parent {
                  @Override
                  public void finalize() {}
              }
              class Caller {
                  void cleanup(Parent parent) {
                      parent.finalize();
                  }
              }
              """
          )
        );
    }

    @Test
    void removeUncalledFinalizerHierarchy() {
        rewriteRun(
          java(
            """
              class Parent {
                  @Override
                  protected void finalize() throws Throwable {
                      super.finalize();
                  }
              }
              class Child extends Parent {
                  @Override
                  protected void finalize() throws Throwable {
                      super.finalize();
                  }
              }
              """,
            """
              class Parent {
              }
              class Child extends Parent {
              }
              """
          )
        );
    }

    @Test
    void retainOrdinarySuperCallButRemoveUnrelatedFinalizer() {
        rewriteRun(
          java(
            """
              class Parent {
                  @Override
                  public void finalize() {}
              }
              class Child extends Parent {
                  void cleanup() {
                      super.finalize();
                  }
              }
              class Unrelated {
                  @Override
                  public void finalize() {}
              }
              """,
            """
              class Parent {
                  @Override
                  public void finalize() {}
              }
              class Child extends Parent {
                  void cleanup() {
                      super.finalize();
                  }
              }
              class Unrelated {
              }
              """
          )
        );
    }

}
