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
import org.openrewrite.Issue;
import org.openrewrite.test.RecipeSpec;
import org.openrewrite.test.RewriteTest;

import static org.openrewrite.java.Assertions.java;

class UseSystemLineSeparatorTest implements RewriteTest {

    @Override
    public void defaults(RecipeSpec spec) {
        spec.recipe(new UseSystemLineSeparator());
    }

    @Issue("https://github.com/openrewrite/rewrite/issues/2363")
    @Test
    void useSystemLineSeparator() {
        rewriteRun(
          //language=java
          java(
            """
              class A {
                  String s = System.getProperty("line.separator");
                  void method1() {
                      var a = System.getProperty("line.separator");
                  }
              }
              """,
            """
              class A {
                  String s = System.lineSeparator();
                  void method1() {
                      var a = System.lineSeparator();
                  }
              }
              """
          )
        );
    }

    @Issue("https://github.com/openrewrite/rewrite/issues/2363")
    @Test
    void useSystemLineSeparatorStaticImport() {
        rewriteRun(
          //language=java
          java(
            """
              import static java.lang.System.getProperty;
              
              class A {
                  String s = getProperty("line.separator");
                  void method1() {
                      var a = getProperty("line.separator");
                  }
              }
              """,
            """
              import static java.lang.System.lineSeparator;
              
              class A {
                  String s = lineSeparator();
                  void method1() {
                      var a = lineSeparator();
                  }
              }
              """
          )
        );
    }

    @Test
    void doNotChangeIfNotGetLineSeparator() {
        rewriteRun(
          //language=java
          java(
            """
              import static java.lang.System.getProperty;
              class A {
                  String s1 = System.getProperty("unknown");
                  String s2 = getProperty("unknown");
                  void method1() {
                      var s3 = System.getProperty("unknown");
                  }
              }
              """
          )
        );
    }
}
