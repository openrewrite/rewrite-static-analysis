/*
 * Copyright 2026 the original author or authors.
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
import org.openrewrite.test.RecipeSpec;
import org.openrewrite.test.RewriteTest;

import static org.openrewrite.java.Assertions.java;

class EmptyBlockJavaUnboxingTest implements RewriteTest {
    @Override
    public void defaults(RecipeSpec spec) {
        spec.recipe(new EmptyBlock());
    }

    @Test
    void preserveBooleanUnboxing() {
        rewriteRun(java(
          """
            import java.util.Map;
            class A {
                void direct(Map<String, Boolean> values) {
                    if (values.get("required")) {}
                }
                void compound(Boolean flag) {
                    if (flag && true) {}
                }
            }
            """
        ));
    }

    @Test
    void preservePrimitiveInvocationWhileRemovingEmptyIf() {
        rewriteRun(java(
          """
            import java.util.function.BooleanSupplier;
            class A {
                void direct(BooleanSupplier value) {
                    if (value.getAsBoolean()) {}
                }
            }
            """,
          """
            import java.util.function.BooleanSupplier;
            class A {
                void direct(BooleanSupplier value) {
                    value.getAsBoolean();
                }
            }
            """
        ));
    }
}
