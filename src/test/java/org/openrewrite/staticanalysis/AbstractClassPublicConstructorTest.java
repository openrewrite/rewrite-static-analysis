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
package org.openrewrite.staticanalysis;

import org.junit.jupiter.api.Test;
import org.openrewrite.DocumentExample;
import org.openrewrite.Issue;
import org.openrewrite.test.RecipeSpec;
import org.openrewrite.test.RewriteTest;

import static org.openrewrite.java.Assertions.java;

@SuppressWarnings("java:S2699")
class AbstractClassPublicConstructorTest implements RewriteTest {

    @Override
    public void defaults(RecipeSpec spec) {
        spec.recipe(new AbstractClassPublicConstructor());
    }

    @Test
    @DocumentExample
    void replacePublicByProtected() {
        rewriteRun(
          //language=java
          java(
            """
            abstract class Test {
                public Test() {
                }
            }
            """,
            """
            abstract class Test {
                protected Test() {
                }
            }
            """
          )
        );
    }

    @Test
    void noReplaceOnNonAbstractClass() {
        rewriteRun(
          //language=java
          java(
            """
            class Test {
                public Test() {
                }
            }
            """
          )
        );
    }

    @Test
    void noReplaceOnPackageProtectedConstructor() {
        rewriteRun(
          //language=java
          java(
                """
            abstract class Test {
                Test() {
                }
            }
            """
          )
        );
    }

    @Test
    @Issue("https://github.com/openrewrite/rewrite-static-analysis/issues/449")
    void noReplaceOnNestedClasses() {
        rewriteRun(
          //language=java
          java(
            """
            abstract class Test {
                static class Nested {
                    public Nested() {
                    }
                }
            }
            """
          )
        );
    }
}
