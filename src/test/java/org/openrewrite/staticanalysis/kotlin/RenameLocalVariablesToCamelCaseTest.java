/*
 * Copyright 2023 the original author or authors.
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
package org.openrewrite.staticanalysis.kotlin;

import org.junit.jupiter.api.Test;
import org.openrewrite.staticanalysis.RenameLocalVariablesToCamelCase;
import org.openrewrite.test.RecipeSpec;
import org.openrewrite.test.RewriteTest;

import static org.openrewrite.kotlin.Assertions.kotlin;

class RenameLocalVariablesToCamelCaseTest implements RewriteTest {

    @Override
    public void defaults(RecipeSpec spec) {
        spec.recipe(new RenameLocalVariablesToCamelCase());
    }

    @Test
    void regular() {
        rewriteRun(
          kotlin(
            """
              fun foo() {
                  val EMPTY_METAS = HashMap<String, Any>()
              }
              """,
            """
              fun foo() {
                  val emptyMetas = HashMap<String, Any>()
              }
              """
          )
        );
    }

    // `internal` modifier means package-private in Kotlin, so it's not a local variable
    @Test
    void doNotChangeIfHasInternalModifier() {
        rewriteRun(
          kotlin(
            """
              internal val EMPTY_METAS = HashMap<String, Any>()
              """
          )
        );
    }


}
