/*
 * Copyright 2025 the original author or authors.
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
    void commentContainingNonAscii() {
        rewriteRun(
            java(
                """
                /**
                * this is a sample class
                * ₤€ contains non ascii characters
                */
                class A {
                }
                """,
                """
                /**
                * this is a sample class
                *  contains non ascii characters
                */
                class A {
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

    @Test
    void removesNonAsciiWhitespaceAndPunctuation() {
        rewriteRun(
          java(
            """
            /**
             * This line contains non-breaking space\u00A0and em dash\u2014and en dash\u2013.
             * Also: “smart quotes”, ‘single quotes’, «guillemets», …ellipsis, •bullet, §section.
             * Invisible:  🎉 🚀zero-width space\u200B, zero-width non-joiner\u200C, zero-width joiner\u200D.
             */
            class A {
            }
            """,
            """
            /**
             * This line contains non-breaking space and em dashand en dash.
             * Also: smart quotes, single quotes, guillemets, ...ellipsis, bullet, section.
             * Invisible:  \szero-width space, zero-width non-joiner, zero-width joiner.
             */
            class A {
            }
            """
          )
        );
    }

     @Test
    void removesLatinWithDiacritics() {
        rewriteRun(
            java(
                """
                /**
                 * Français: café, résumé, naïve, piñata.
                 * Español: niño, señor, corazón, José García.
                 * Deutsch: Müller, Größe, weiß, Björn.
                 * Português: São Paulo, coração, não.
                 */
                 class A {
                }
                """,
                """
                /**
                 * Francais: cafe, resume, naive, pinata.
                 * Espanol: nino, senor, corazon, Jose Garcia.
                 * Deutsch: Muller, Groe, wei, Bjorn.
                 * Portugues: Sao Paulo, coracao, nao.
                 */
                 class A {
                }
                """
            )
        );
    }
}
