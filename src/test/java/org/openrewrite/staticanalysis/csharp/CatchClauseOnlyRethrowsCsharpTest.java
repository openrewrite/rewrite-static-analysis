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
package org.openrewrite.staticanalysis.csharp;

import org.junit.jupiter.api.Test;
import org.openrewrite.DocumentExample;
import org.openrewrite.ExecutionContext;
import org.openrewrite.InMemoryExecutionContext;
import org.openrewrite.Tree;
import org.openrewrite.java.JavaIsoVisitor;
import org.openrewrite.java.tree.J;
import org.openrewrite.java.tree.Space;
import org.openrewrite.marker.Markers;
import org.openrewrite.staticanalysis.CatchClauseOnlyRethrows;
import org.openrewrite.test.RecipeSpec;
import org.openrewrite.test.RewriteTest;

import static org.openrewrite.java.Assertions.java;
import static org.openrewrite.staticanalysis.csharp.JavaToCsharp.toCsRecipe;

class CatchClauseOnlyRethrowsCsharpTest implements RewriteTest {

    @Override
    public void defaults(RecipeSpec spec) {
        spec.recipe(toCsRecipe(new CatchClauseOnlyRethrows()));
    }

    @DocumentExample
    @Test
    void verifyCsharpImplicitThrow() {
        rewriteRun(
          //language=java
          java(
            """
              class A {
                  void foo() throws IllegalAccessException {
                      try {
                          throw new IllegalAccessException();
                      } catch (Exception e) {
                          throw e; // `e` is removed below
                      }
                  }
              }
              """,
            """
              class A {
                  void foo() throws IllegalAccessException {
                      throw new IllegalAccessException();
                  }
              }
              """,
            // C# can rethrow the caught exception implicitly and so the `e` Identifier is removed by the inline visitor below
            spec -> spec.mapBeforeRecipe(cu -> new JavaIsoVisitor<ExecutionContext>() {
                  @Override
                  public J.Throw visitThrow(J.Throw thrown, ExecutionContext ctx) {
                      if (thrown.getException() instanceof J.Identifier) {
                          return thrown.withException(new J.Empty(Tree.randomId(), Space.EMPTY, Markers.EMPTY));
                      }
                      return thrown;
                  }
              }.visitCompilationUnit(cu, new InMemoryExecutionContext())
            )
          )
        );
    }
}
