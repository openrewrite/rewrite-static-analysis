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
import org.openrewrite.test.RewriteTest;
import static org.openrewrite.kotlin.Assertions.kotlin;

class EmptyBlockKotlinEvaluationTest implements RewriteTest {
    @Test
    void preserveNullablePreferenceAssertion() {
        rewriteRun(spec -> spec.recipe(new EmptyBlock()), kotlin("""
            class Preference(val key: String)
            fun click(preference: Preference, downloadFolderPref: Preference?): Boolean {
                if (preference.key == downloadFolderPref!!.key) {
                    // Feature not implemented yet.
                }
                return false
            }
            """));
    }

    @Test
    void preservePropertyGetterEvaluation() {
        rewriteRun(spec -> spec.recipe(new EmptyBlock()), kotlin("""
            class State {
                var reads = 0
                val enabled: Boolean
                    get() { reads++; return true }
                fun check() {
                    if (enabled) {
                        // Intentionally empty.
                    }
                }
            }
            """));
    }

    @Test
    void removeEmptyLiteralCondition() {
        rewriteRun(spec -> spec.recipe(new EmptyBlock()), kotlin(
            """
            fun check() {
                if (true) {
                    // Intentionally empty.
                }
                println("done")
            }
            """,
            """
            fun check() {
                println("done")
            }
            """));
    }
}
