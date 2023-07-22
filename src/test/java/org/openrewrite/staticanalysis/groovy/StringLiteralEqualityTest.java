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
package org.openrewrite.staticanalysis.groovy;

import org.junit.jupiter.api.Test;
import org.openrewrite.staticanalysis.StringLiteralEquality;
import org.openrewrite.test.RecipeSpec;
import org.openrewrite.test.RewriteTest;

import static org.openrewrite.groovy.Assertions.groovy;

class StringLiteralEqualityTest implements RewriteTest {

    @Override
    public void defaults(RecipeSpec spec) {
        spec.recipe(new StringLiteralEquality());
    }

    // Don't change for Groovy because in Groovy, `==` means structural equality and it's redundant to call equals().
    @Test
    void doNotChangeForGroovy() {
        rewriteRun(
          groovy(
            """
              def foo(String token) {
                  if (token == "Foo" ) {
                  }
              }
              """
          )
        );
    }
}
