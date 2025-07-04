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
import org.openrewrite.test.RecipeSpec;
import org.openrewrite.test.RewriteTest;

import static org.openrewrite.java.Assertions.java;


class MaskCreditCardNumbersTest implements RewriteTest {

    @Override
    public void defaults(RecipeSpec spec) {
        spec.recipe(new MaskCreditCardNumbers());
    }

    @Test
    @DocumentExample
    void noSpaces() {
        rewriteRun(
          //language=java
          java(
            """
              class A {
                  String cc = "1234567890123456";
              }
              """,
            """
              class A {
                  String cc = "12345678XXXXXXXX";
              }
              """
          )
        );
    }

    @Test
    void withSpaces() {
        rewriteRun(
          //language=java
          java(
            """
              class A {
                  String cc = "1234 5678 9012 3456";
              }
              """,
            """
              class A {
                  String cc = "1234 5678 XXXX XXXX";
              }
              """
          )
        );
    }
}
