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
import org.openrewrite.DocumentExample;
import org.openrewrite.staticanalysis.SimplifyBooleanExpression;
import org.openrewrite.test.RecipeSpec;
import org.openrewrite.test.RewriteTest;

import static org.openrewrite.kotlin.Assertions.kotlin;

class SimplifyBooleanExpressionTest implements RewriteTest {

    @Override
    public void defaults(RecipeSpec spec) {
        spec.recipe(new SimplifyBooleanExpression());
    }

    @Test
    void regular() {
        rewriteRun(
          kotlin(
            """
              fun getSymbol() : String? {
                  return null
              }
              """
          ),
          kotlin(
            """
              val isPositive = getSymbol().equals("+") == true
              """,
            """
              val isPositive = getSymbol().equals("+")
              """
          )
        );
    }

    @Test
    void doNotChangeWithNullable() {
        rewriteRun(
          kotlin(
            """
              fun getSymbol() : String? {
                  return null
              }

              val isPositive = getSymbol()?.equals("+") == true
              """
          )
        );
    }

    @Test
    void nullableChain() {
        rewriteRun(
          kotlin(
            """
              fun getSymbol() : String? {
                  return null
              }
              """
          ),
          kotlin(
            """
              val isPositive1 = getSymbol()?.plus("").equals("+") == true
              val isPositive2 = getSymbol()?.plus("")?.equals("+") == true
              """,
            """
              val isPositive1 = getSymbol()?.plus("").equals("+")
              val isPositive2 = getSymbol()?.plus("")?.equals("+") == true
              """
          )
        );
    }

    @DocumentExample
    @Test
    void nullableVariable() {
        rewriteRun(
          kotlin(
            """
              fun main() {
                  val name : String? = null
                  val isPositive1 = name?.equals("+") == true
                  val isPositive2 = name.equals("+") == true
              }
              """,
            """
              fun main() {
                  val name : String? = null
                  val isPositive1 = name?.equals("+") == true
                  val isPositive2 = name.equals("+")
              }
              """
          )
        );
    }
}
