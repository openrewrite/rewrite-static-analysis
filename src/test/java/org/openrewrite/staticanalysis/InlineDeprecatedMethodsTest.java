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
import org.openrewrite.java.JavaParser;
import org.openrewrite.test.RecipeQualityValidation;
import org.openrewrite.test.RecipeSpec;
import org.openrewrite.test.RewriteTest;
import org.openrewrite.test.TypeValidation;

import static org.openrewrite.java.Assertions.java;

class InlineDeprecatedMethodsTest implements RewriteTest {

    @Override
    public void defaults(RecipeSpec spec) {
        spec
          .parser(JavaParser.fromJavaVersion().classpath(JavaParser.runtimeClasspath()))
          // Without `classpathFromResources` on `InlineMethodCalls`, the template can't attribute the new constructor call
          .recipeQualityValidation(RecipeQualityValidation.all().types(TypeValidation.all().constructorInvocations(false)))
          .recipeFromResources("org.openrewrite.recipe.rewrite-static-analysis.InlineDeprecatedMethods");
    }

    @DocumentExample
    @Test
    void inlineDeprecatedRemoveUnusedLocalVariablesConstructor() {
        rewriteRun(
          //language=java
          java(
            """
              import org.openrewrite.staticanalysis.RemoveUnusedLocalVariables;

              class Test {
                  RemoveUnusedLocalVariables recipe = new RemoveUnusedLocalVariables(new String[]{"ignored"}, true);
              }
              """,
            """
              import org.openrewrite.staticanalysis.RemoveUnusedLocalVariables;

              class Test {
                  RemoveUnusedLocalVariables recipe = new RemoveUnusedLocalVariables(new String[]{"ignored"}, null, true);
              }
              """
          )
        );
    }
}
