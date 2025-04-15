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
import org.openrewrite.Issue;
import org.openrewrite.test.RecipeSpec;
import org.openrewrite.test.RewriteTest;

import static org.openrewrite.java.Assertions.java;

@SuppressWarnings("ArrayHashCode")
class RemoveHashCodeCallsFromArrayInstancesTest implements RewriteTest {
    @Override
    public void defaults(RecipeSpec spec) {
        spec.recipe(new RemoveHashCodeCallsFromArrayInstances());
    }

    @Test
    @DocumentExample
    @Issue("https://github.com/openrewrite/rewrite-static-analysis/issues/44")
    void replaceHashCodeCalls() {
        //language=java
        rewriteRun(
          java(
                """
            class SomeClass {
              public static void main(String[] args) {
                int argHash = args.hashCode();
              }
            }
            """, """
            import java.util.Arrays;

            class SomeClass {
              public static void main(String[] args) {
                int argHash = Arrays.hashCode(args);
              }
            }
            """)
        );
    }

    @Test
    void selectIsAMethod() {
        //language=java
        rewriteRun(
          java(
                """
            class SomeClass {
              void foo() {
                int hashCode = getArr().hashCode();
              }

              public int[] getArr() {
                return new int[]{1, 2, 3};
              }
            }
            """, """
            import java.util.Arrays;

            class SomeClass {
              void foo() {
                int hashCode = Arrays.hashCode(getArr());
              }

              public int[] getArr() {
                return new int[]{1, 2, 3};
              }
            }
            """)
        );
    }

    @Test
    void onlyRunOnArrayInstances() {
        //language=java
        rewriteRun(
          java(
                """
            class SomeClass {
              void foo() {
                String name = "bill";
                int hashCode = name.hashCode();
              }
            }
            """)
        );
    }
}
