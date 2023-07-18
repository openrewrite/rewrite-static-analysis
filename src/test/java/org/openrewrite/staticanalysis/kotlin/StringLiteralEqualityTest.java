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
import org.openrewrite.staticanalysis.StringLiteralEquality;
import org.openrewrite.test.RecipeSpec;
import org.openrewrite.test.RewriteTest;

import static org.openrewrite.kotlin.Assertions.kotlin;

class StringLiteralEqualityTest implements RewriteTest {

    @Override
    public void defaults(RecipeSpec spec) {
        spec.recipe(new StringLiteralEquality());
    }

    // Don't change for Kotlin because In Kotlin, `==` means structural equality and it's redundant to call equals().
    @Test
    void doNotChangeForKotlin() {
        rewriteRun(
          kotlin(
            """
              class MqttRegex() {
                  val str: java.lang.String = java.lang.String("123")
                  fun processToken(token: String?) {
                      if (token == null || "" == token.trim { it <= ' ' }) {
                      }
                  }
              }
              """
          )
        );
    }
}
