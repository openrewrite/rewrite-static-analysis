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
import org.openrewrite.java.JavaIsoVisitor;
import org.openrewrite.java.tree.J;
import org.openrewrite.test.RecipeSpec;
import org.openrewrite.test.RewriteTest;

import static java.util.Collections.emptyList;
import static org.openrewrite.java.Assertions.java;

class ForLoopIncrementInUpdateTest implements RewriteTest {
    @Override
    public void defaults(RecipeSpec spec) {
        spec.recipe(new ForLoopIncrementInUpdate());
    }

    @DocumentExample
    @SuppressWarnings("StatementWithEmptyBody")
    @Test
    void moveIncrementToUpdate() {
        rewriteRun(
          //language=java
          java(
            """
              class Test {
                  void test() {
                      int h, j;
                      for (int i = 0; i < 10; h++, j++) {
                          i++;
                      }
                  }
              }
              """,
            """
              class Test {
                  void test() {
                      int h, j;
                      for (int i = 0; i < 10; h++, i++, j++) {
                      }
                  }
              }
              """
          )
        );
    }

    @Test
    void doNotChangeLoopWithoutInitOrUpdateClause() {
        rewriteRun(
          //language=java
          java(
            """
              class Test {
                  void test(int n) {
                      for (; n > 0 ;) {
                          n--;
                      }
                  }
              }
              """,
            spec -> spec.mapBeforeRecipe(cu -> (J.CompilationUnit) new JavaIsoVisitor<Integer>() {
                @Override
                public J.ForLoop visitForLoop(J.ForLoop forLoop, Integer p) {
                    return forLoop.withControl(forLoop.getControl().withInit(emptyList()).withUpdate(emptyList()));
                }
            }.visit(cu, 0))
          )
        );
    }
}
