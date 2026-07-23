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
import org.openrewrite.staticanalysis.table.MissingJavadocOnPublicMethods;
import org.openrewrite.test.RecipeSpec;
import org.openrewrite.test.RewriteTest;

import static org.assertj.core.api.Assertions.assertThat;
import static org.openrewrite.groovy.Assertions.groovy;
import static org.openrewrite.java.Assertions.java;
import static org.openrewrite.kotlin.Assertions.kotlin;

@SuppressWarnings("RedundantVisibilityModifier")
class FindMissingJavadocOnPublicMethodsTest implements RewriteTest {

    @Override
    public void defaults(RecipeSpec spec) {
        spec.recipe(new FindMissingJavadocOnPublicMethods());
    }

    @Test
    void dataTableRecordsUndocumentedMethod() {
        rewriteRun(
          spec -> spec.dataTable(MissingJavadocOnPublicMethods.Row.class, rows -> {
              assertThat(rows).hasSize(1);
              MissingJavadocOnPublicMethods.Row row = rows.getFirst();
              assertThat(row.getSourcePath()).isEqualTo("com/example/A.java");
              assertThat(row.getClassName()).isEqualTo("com.example.A");
              assertThat(row.getMethodName()).isEqualTo("foo");
              assertThat(row.getParameterTypes()).isEqualTo("java.lang.String, int");
          }),
          //language=java
          java(
            """
            package com.example;
            class A {
                public void foo(String s, int i) {
                }
            }
            """,
            """
            package com.example;
            class A {
                public void /*~~>*/foo(String s, int i) {
                }
            }
            """
          )
        );
    }

    @Test
    void doNotChangePublicMethodWithJavadoc() {
        rewriteRun(
          //language=java
          java(
            """
            class A {
                /**
                 * Does something.
                 */
                public void foo() {
                }
            }
            """
          )
        );
    }

    @Test
    void doNotChangeNonPublicMethods() {
        rewriteRun(
          //language=java
          java(
            """
            class A {
                void packagePrivate() {
                }

                protected void prot() {
                }

                private void priv() {
                }
            }
            """
          )
        );
    }

    @Test
    void interfaceMethodsAreImplicitlyPublic() {
        rewriteRun(
          //language=java
          java(
            """
            interface A {
                void foo();

                private void bar() {
                }
            }
            """,
            """
            interface A {
                void /*~~>*/foo();

                private void bar() {
                }
            }
            """
          )
        );
    }

    @Test
    void javadocPrecedingAnnotationIsRecognized() {
        rewriteRun(
          //language=java
          java(
            """
            class A {
                /**
                 * Documented.
                 */
                @Deprecated
                public void foo() {
                }
            }
            """
          )
        );
    }

    @Test
    void doNotConfuseBlockCommentWithJavadoc() {
        rewriteRun(
          //language=java
          java(
            """
            class A {
                // not javadoc
                public void foo() {
                }
            }
            """,
            """
            class A {
                // not javadoc
                public void /*~~>*/foo() {
                }
            }
            """
          )
        );
    }

    @Test
    void groovyPublicMethodMissingJavadoc() {
        rewriteRun(
          //language=groovy
          groovy(
            """
            class A {
                void foo(String s) {
                }
            }
            """,
            """
            class A {
                void /*~~>*/foo(String s) {
                }
            }
            """
          )
        );
    }

    @Test
    void kotlinPublicMethodMissingJavadoc() {
        rewriteRun(
          //language=kotlin
          kotlin(
            """
            class A {
                fun foo(s: String) {
                }
            }
            """,
            """
            class A {
                fun /*~~>*/foo(s: String) {
                }
            }
            """
          )
        );
    }

    @Test
    void doNotChangeKotlinNonPublicMethods() {
        rewriteRun(
          //language=kotlin
          kotlin(
            """
            class A {
                internal fun internalFn() {
                }

                private fun privateFn() {
                }

                protected fun protectedFn() {
                }
            }
            """
          )
        );
    }

}
