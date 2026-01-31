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
import org.openrewrite.Tree;
import org.openrewrite.java.JavaParser;
import org.openrewrite.java.style.IntelliJ;
import org.openrewrite.java.style.SpacesStyle;
import org.openrewrite.style.NamedStyles;
import org.openrewrite.test.RecipeSpec;
import org.openrewrite.test.RewriteTest;

import static java.util.Collections.emptySet;
import static java.util.Collections.singletonList;
import static org.openrewrite.groovy.Assertions.groovy;
import static org.openrewrite.java.Assertions.java;

class TypecastParenPadTest implements RewriteTest {

    @Override
    public void defaults(RecipeSpec spec) {
        spec.recipe(new TypecastParenPad());
    }

    @DocumentExample
    @Test
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
    void doesNotChangeMethodArgumentSpacingWithDefaultStyle() {
        // Test with default SpacesStyle - should not change method arguments
        rewriteRun(
          java(
            """
              class A {
                  void m(int a, int b) {
                      Object o = (Object) m(1, 2);
                  }
              }
              """
          )
        );
    }

    @Test
    void doesNotChangeMethodArgumentSpacingWithAfterCommaFalse() {
        // SpacesStyle with afterComma=false should not affect method arguments inside typecast
        SpacesStyle noSpaceAfterComma = IntelliJ.spaces()
            .withOther(IntelliJ.spaces().getOther().withAfterComma(false));
        rewriteRun(
          spec -> spec.parser(JavaParser.fromJavaVersion()
              .styles(singletonList(
                  new NamedStyles(Tree.randomId(), "test", "test", "test", emptySet(),
                      singletonList(noSpaceAfterComma))))),
          java(
            """
              class A {
                  void m(int a, int b) {
                      Object o = (Object) m(1, 2);
                  }
              }
              """
          )
        );
    }

    @Issue("https://github.com/openrewrite/rewrite-static-analysis/issues/561")
    @Test
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
