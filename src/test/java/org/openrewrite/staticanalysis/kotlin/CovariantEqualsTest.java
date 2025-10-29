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
import org.openrewrite.Issue;
import org.openrewrite.staticanalysis.CovariantEquals;
import org.openrewrite.test.RecipeSpec;
import org.openrewrite.test.RewriteTest;

import static org.openrewrite.kotlin.Assertions.kotlin;

class CovariantEqualsTest implements RewriteTest {

    @Override
    public void defaults(RecipeSpec spec) {
        spec.recipe(new CovariantEquals());
    }

    @Test
    void topLevelFunctionDoesNotCrash() {
        // This test verifies that the recipe doesn't crash when encountering a top-level Kotlin function
        // Top-level functions don't have a class parent, which was causing the dropParentUntil error
        rewriteRun(
          kotlin(
            """
              // Top-level equals function - should not cause a crash
              fun equals(other: String): Boolean {
                  return false
              }

              fun main() {
                  println("Test")
              }
              """
          )
        );
    }

    @Test
    void kotlinClassWithCovariantEquals() {
        // Test that Kotlin classes with covariant equals are left unchanged
        // (Kotlin has its own mechanisms for handling equals)
        rewriteRun(
          kotlin(
            """
              class Test {
                  var n: Int = 0

                  fun equals(other: Test): Boolean {
                      return n == other.n
                  }
              }
              """
          )
        );
    }

    @Test
    void kotlinDataClassUnchanged() {
        // Data classes have compiler-generated equals methods
        rewriteRun(
          kotlin(
            """
              data class Person(val name: String, val age: Int)
              """
          )
        );
    }

    @Test
    void kotlinInterfaceWithEquals() {
        // Test that interfaces with equals methods don't cause crashes
        rewriteRun(
          kotlin(
            """
              interface TestInterface {
                  fun equals(other: TestInterface): Boolean
              }

              class TestImpl : TestInterface {
                  override fun equals(other: TestInterface): Boolean {
                      return true
                  }
              }
              """
          )
        );
    }

    @Test
    void kotlinObjectDeclaration() {
        // Test that object declarations (singletons) don't cause crashes
        rewriteRun(
          kotlin(
            """
              object Singleton {
                  fun equals(other: Singleton): Boolean {
                      return true
                  }
              }
              """
          )
        );
    }

    @Test
    void kotlinEnumWithEquals() {
        // Test that enum classes with equals methods don't cause crashes
        rewriteRun(
          kotlin(
            """
              enum class Color {
                  RED, GREEN, BLUE;

                  fun equals(other: Color): Boolean {
                      return this == other
                  }
              }
              """
          )
        );
    }

    @Test
    void kotlinNestedClassWithEquals() {
        // Test nested classes with equals methods
        rewriteRun(
          kotlin(
            """
              class Outer {
                  class Inner {
                      var value: Int = 0

                      fun equals(other: Inner): Boolean {
                          return value == other.value
                      }
                  }
              }
              """
          )
        );
    }

    @Test
    void kotlinCompanionObjectWithEquals() {
        // Test companion objects with equals methods
        rewriteRun(
          kotlin(
            """
              class MyClass {
                  companion object {
                      fun equals(other: MyClass): Boolean {
                          return false
                      }
                  }
              }
              """
          )
        );
    }
}
