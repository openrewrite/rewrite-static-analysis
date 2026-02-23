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
import org.openrewrite.test.RecipeSpec;
import org.openrewrite.test.RewriteTest;

import static org.openrewrite.java.Assertions.java;

class RemoveTrailingWhitespaceTest implements RewriteTest {

    @Override
    public void defaults(RecipeSpec spec) {
        spec.recipe(new RemoveTrailingWhitespace());
    }

    private static String trailing(String s) {
        return s.replace("_", " ").replace("TAB", "\t");
    }

    @DocumentExample
    @Test
    void trailingSpacesOnCodeLines() {
        rewriteRun(
          java(
            trailing("""
            class Test {
                void foo() {___
                    int a = 1;_
                }
            }
            """),
            """
            class Test {
                void foo() {
                    int a = 1;
                }
            }
            """
          )
        );
    }

    @Test
    void trailingTabsOnCodeLines() {
        rewriteRun(
          java(
            trailing("""
            class Test {
                void foo() {TAB
                    int a = 1;TABTAB
                }
            }
            """),
            """
            class Test {
                void foo() {
                    int a = 1;
                }
            }
            """
          )
        );
    }

    @Test
    void trailingSpacesInBlockComment() {
        rewriteRun(
          java(
            trailing("""
            /*___
             * A class___
             */
            class Test {
            }
            """),
            """
            /*
             * A class
             */
            class Test {
            }
            """
          )
        );
    }

    @Test
    void trailingSpacesInLineComment() {
        rewriteRun(
          java(
            trailing("""
            class Test {
                // comment___
                void foo() {
                }
            }
            """),
            """
            class Test {
                // comment
                void foo() {
                }
            }
            """
          )
        );
    }

    @Test
    void unchangedNoTrailingWhitespace() {
        rewriteRun(
          //language=java
          java(
            """
            class Test {
                void foo() {
                    int a = 1;
                }
            }
            """
          )
        );
    }

    @Test
    void mixedCleanAndDirtyLines() {
        rewriteRun(
          java(
            trailing("""
            class Test {
                void foo() {
                    int a = 1;___
                    int b = 2;
                    int c = 3;_
                }
            }
            """),
            """
            class Test {
                void foo() {
                    int a = 1;
                    int b = 2;
                    int c = 3;
                }
            }
            """
          )
        );
    }
}
