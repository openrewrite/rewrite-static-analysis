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

import static org.openrewrite.groovy.Assertions.groovy;
import static org.openrewrite.java.Assertions.java;

class TypecastParenPadTest implements RewriteTest {

    @Override
    public void defaults(RecipeSpec spec) {
        spec.recipe(new TypecastParenPad());
    }

    @Test
    @DocumentExample
    void typecastParenPad() {
        rewriteRun(
          //language=java
          java(
            """
              class A {
                  void m() {
                      int i = ( int ) 0L;
                      int j = (int) 0L;
                      int k = (int)0L;
                      int l = ( int )0L;
                  }
              }
              """,
            """
              class A {
                  void m() {
                      int i = (int) 0L;
                      int j = (int) 0L;
                      int k = (int) 0L;
                      int l = (int) 0L;
                  }
              }
              """
          )
        );
    }

    @Test
    @Issue("https://github.com/openrewrite/rewrite-static-analysis/issues/561")
    void noChangeForGroovy() {
        rewriteRun(
          //language=groovy
          groovy(
            """
              class A {
                  void m(Object o) {
                      int l = ( o as String).length()
                  }
              }
              """
          )
        );
    }
}
