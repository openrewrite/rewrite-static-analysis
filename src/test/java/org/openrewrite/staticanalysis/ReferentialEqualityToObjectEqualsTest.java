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

@SuppressWarnings({
  "ConstantConditions", "StatementWithEmptyBody", "NewObjectEquality", "StringEquality",
  "EqualsWhichDoesntCheckParameterClass"
})
class ReferentialEqualityToObjectEqualsTest implements RewriteTest {

    @Override
    public void defaults(RecipeSpec spec) {
        spec.recipe(new ReferentialEqualityToObjectEquals());
    }

    @Test
    void doesNotModifyBoxedTypes() {
        rewriteRun(
          //language=java
          java(
            """
              class C {
                  char c = 'c';
                  boolean isC(Integer value){
                      return value == c || value == 99;
                  }
              }
              """
          )
        );
    }

    @Test
    void doesNotModifyEnumComparison() {
        rewriteRun(
          //language=java
          java(
            """
              class B {
                private void method() {
                  if(Foo.FOO == Foo.BAR) {}
                }
                enum Foo {FOO, BAR}
              }
              """
          )
        );
    }

    @Test
    void doesNotModifyClassComparisons() {
        rewriteRun(
          //language=java
          java(
            """
              class A {
                  void check() {
                      B b = new B();
                      if(b == this) {}
                  }
                  
                  class B {
                      @Override
                      public boolean equals(Object anObject) {return true;}
                  }
              }
              """
          )
        );
    }

    @Test
    void typeDoesNotOverrideEquals() {
        rewriteRun(
          //language=java
          java(
            """
              class T {
                  void doSomething() {
                      A a1 = new A();
                      B b1 = new B();
                      if (a1 == b1) {}
                  }
                  class A {}
                  class B {}
              }
              """
          )
        );
    }

    @Test
    void onlyOneSideOverridesEquals() {
        rewriteRun(
          //language=java
          java(
            """
              class T {
                  void doSomething() {
                      A a1 = new A();
                      B b1 = new B();
                      if (a1 == b1) {}
                  }
                  class A {
                      @Override
                      public boolean equals(Object anObject) {return true;}
                  }
                  class B {}
              }
              """
          )
        );
    }

    @Test
    void doNotModifyWithinEqualsMethod() {
        rewriteRun(
          //language=java
          java(
            """
              class T {
                  String s1;
                  String s2;
                  @Override
                  public boolean equals(Object obj) {
                      if (s1 != s2) {}
                      if (s1 == s2) {}
                      return super.equals(obj);
                  }
              }
              """
          )
        );
    }

    @DocumentExample
    @Test
    void bothSidesOverrideEquals() {
        rewriteRun(
          //language=java
          java(
            """
              class T {
                  void doSomething() {
                      A a1 = new A();
                      A a2 = new A();
                      if (a1 == a2) {}
                  }
                  class A {
                      @Override
                      public boolean equals(Object anObject) {return true;}
                  }
              }
              """,
            """
              class T {
                  void doSomething() {
                      A a1 = new A();
                      A a2 = new A();
                      if (a1.equals(a2)) {}
                  }
                  class A {
                      @Override
                      public boolean equals(Object anObject) {return true;}
                  }
              }
              """
          )
        );
    }

    @Test
    void notEquals() {
        rewriteRun(
          //language=java
          java(
            """
              class T {
                  void doSomething() {
                      A a1 = new A();
                      A a2 = new A();
                      if (a1 != a2) {}
                  }
                  class A {
                      @Override
                      public boolean equals(Object anObject) {return true;}
                  }
              }
              """,
            """
              class T {
                  void doSomething() {
                      A a1 = new A();
                      A a2 = new A();
                      if (!a1.equals(a2)) {}
                  }
                  class A {
                      @Override
                      public boolean equals(Object anObject) {return true;}
                  }
              }
              """
          )
        );
    }
}
