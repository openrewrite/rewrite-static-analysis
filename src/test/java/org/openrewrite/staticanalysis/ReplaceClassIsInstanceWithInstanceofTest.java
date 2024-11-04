/*
 * Copyright 2024 the original author or authors.
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

import org.junit.jupiter.api.Test;
import org.openrewrite.DocumentExample;
import org.openrewrite.test.RecipeSpec;
import org.openrewrite.test.RewriteTest;

import static org.openrewrite.java.Assertions.java;
import static org.openrewrite.java.Assertions.javaVersion;

@SuppressWarnings({"RedundantClassCall", "ConstantValue", "UnusedAssignment"})
class ReplaceClassIsInstanceWithInstanceofTest implements RewriteTest {

    @Override
    public void defaults(RecipeSpec spec) {
        spec.recipe(new ReplaceClassIsInstanceWithInstanceof());
    }

    @Test
    @DocumentExample
    void changeInstanceOf() {
        rewriteRun(
          //language=java
          java(
            """
              class A {
                  void foo() {
                      String s = "";
                      boolean result = String.class.isInstance(s);
                      result = Integer.class.isInstance(s);
                  }
              }
              """,
            """
              class A {
                  void foo() {
                      String s = "";
                      boolean result = s instanceof String;
                      result = s instanceof Integer;
                  }
              }
              """
          )
        );
    }

    @Test
    void doNotChangeWhenAlreadyInstanceOf() {
        rewriteRun(
          //language=java
          java(
            """
              class A {
                  boolean foo() {
                    String s = "";
                    return s instanceof String;
                  }
              }
              """
          )
        );
    }

    @Test
    void doNotChangeWhenVariable() {
        rewriteRun(
          //language=java
          java(
            """
              class A {
                  void foo(Class<?> clazz) {
                      String s = "";
                      boolean result = clazz.isInstance(s);
                  }
              }
              """
          )
        );
    }

    @Test
    void doNotChangeInstanceOfWithVariable() {
        rewriteRun(
          //language=java
          java(
            """
              class A {
                  String foo(Object obj) {
                      if (obj instanceof String s) {
                          return s;
                      }
                      return null;
                  }
              }
              """,
            spec -> spec.markers(javaVersion(17))
          )
        );
    }

    @Test
    void methodInvocation() {
        rewriteRun(
          //language=java
          java(
            """
              class A {
                  boolean foo(Object obj) {
                      return String.class.isInstance(obj.hashCode());
                  }
              }
              """,
            """
              class A {
                  boolean foo(Object obj) {
                      return obj.hashCode() instanceof String;
                  }
              }
              """
          )
        );
    }

    @Test
    void fieldAccess() {
        rewriteRun(
          //language=java
          java(
            """
              class A {
                  Object content;
                  boolean foo(Object obj) {
                      return String.class.isInstance(this.content);
                  }
              }
              """,
            """
              class A {
                  Object content;
                  boolean foo(Object obj) {
                      return this.content instanceof String;
                  }
              }
              """
          )
        );
    }

}
