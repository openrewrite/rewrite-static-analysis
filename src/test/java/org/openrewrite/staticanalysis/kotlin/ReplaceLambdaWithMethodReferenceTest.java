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
package org.openrewrite.staticanalysis.kotlin;

import org.junit.jupiter.api.Test;
import org.junitpioneer.jupiter.ExpectedToFail;
import org.openrewrite.DocumentExample;
import org.openrewrite.staticanalysis.ReplaceLambdaWithMethodReference;
import org.openrewrite.test.RecipeSpec;
import org.openrewrite.test.RewriteTest;

import static org.openrewrite.kotlin.Assertions.kotlin;

class ReplaceLambdaWithMethodReferenceTest implements RewriteTest {
    @Override
    public void defaults(RecipeSpec spec) {
        spec.recipe(new ReplaceLambdaWithMethodReference());
    }

    @DocumentExample
    @ExpectedToFail("Kotlin visitor to be implemented")
    @Test
    void toQualifiedMethodReference() {
        rewriteRun(
          kotlin(
            """
              interface Pet {
                  fun move() {
                  }
              }

              class Cat : Pet {
                  override fun move() {
                      println("Cat is moving")
                  }
              }

              class Dog : Pet {
                  override fun move() {
                      println("Dog is moving")
                  }
              }
              """
          ),
          kotlin(
            """
              fun main() {
                  val pets = listOf(Cat(), Dog())
                  pets.forEach { it.move() }
              }
              """,
            """
              fun main() {
                  val pets = listOf(Cat(), Dog())
                  pets.forEach ( Pet::move )
              }
              """
          )
        );
    }

    @ExpectedToFail("Kotlin visitor to be implemented")
    @Test
    void toUnqualifiedMethodReference() {
        rewriteRun(
          kotlin(
            """
              fun isEven(number: Int): Boolean {
                  return number % 2 == 0
              }
              """
          ),
          kotlin(
            """
              val numbers = listOf(1, 2, 3, 4, 5)
              val evenNumbers = numbers.filter{isEven(it)}
              """
            ,
            """
              val numbers = listOf(1, 2, 3, 4, 5)
              val evenNumbers = numbers.filter(::isEven)
              """
          )
        );
    }

}
