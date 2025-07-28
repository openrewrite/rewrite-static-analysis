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
package org.openrewrite.staticanalysis.maven;

import org.junit.jupiter.api.Test;
import org.openrewrite.DocumentExample;
import org.openrewrite.test.RecipeSpec;
import org.openrewrite.test.RewriteTest;

import static org.openrewrite.java.Assertions.java;

class MavenJavadocNonAsciiRecipeTest implements RewriteTest {

    @Override
    public void defaults(RecipeSpec spec) {
        spec.recipe(new MavenJavadocNonAsciiRecipe());
    }

    @DocumentExample
    @Test
    void originalTestCase() {
        rewriteRun(
            java(
                """
                /**
                * this is a sample class
                * ₤€ contains non ascii characters
                */
                class A {
                    /**
                    * this is the main method
                    */
                    public static void main(String... args) {
                        System.out.println("Hello World!");
                    }
                }
                """,
                """
                /**
                * this is a sample class
                *  contains non ascii characters
                */
                class A {
                    /**
                    * this is the main method
                    */
                    public static void main(String... args) {
                        System.out.println("Hello World!");
                    }
                }
                """
            )
        );
    }

    @Test
    void doesNotChangeRegularAsciiJavadoc() {
        rewriteRun(
            java(
                """
                /**
                 * This is a regular ASCII comment.
                 * No changes should be made here.
                 */
                public class Example {
                    
                    /**
                     * Another regular method comment.
                     */
                    public void regularMethod() {
                    }
                }
                """
            )
        );
    }
} 