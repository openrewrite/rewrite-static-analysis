package org.openrewrite.staticanalysis;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.openrewrite.DocumentExample;
import org.openrewrite.test.RewriteTest;

import static org.openrewrite.java.Assertions.java;

public class ReorderAnnotationsTest implements RewriteTest {
    @Nested
    class ParameterlessConstructor {
        @Test
        @DocumentExample
        void doesNotReorderAnnotationsIfNotNeeded() {
            rewriteRun(
              spec -> spec.recipe(new ReorderAnnotations()),
              //language=java
              java(
                """
                    import org.junit.jupiter.api.Test;
                    import org.junitpioneer.jupiter.ExpectedToFail;
                    import org.junitpioneer.jupiter.Issue;

                    class A {
                        @ExpectedToFail
                        @Issue("https://github.com/openrewrite/rewrite/issues/2973")
                        @Test
                        void explicitImplementationClassInApi() {
                        }
                    }
                  """
              )
            );
        }

        @Test
        @DocumentExample
        void reordersMethodAnnotations() {
            rewriteRun(
              spec -> spec.recipe(new ReorderAnnotations()),
              //language=java
              java(
                """
                    import org.junit.jupiter.api.Test;
                    import org.junitpioneer.jupiter.ExpectedToFail;
                    import org.junitpioneer.jupiter.Issue;

                    class A {
                        @Issue("https://github.com/openrewrite/rewrite/issues/2973")
                        @Test
                        @ExpectedToFail
                        void explicitImplementationClassInApi() {
                        }
                    }
                  """,
                """
                    import org.junit.jupiter.api.Test;
                    import org.junitpioneer.jupiter.ExpectedToFail;
                    import org.junitpioneer.jupiter.Issue;

                    class A {
                        @ExpectedToFail
                        @Issue("https://github.com/openrewrite/rewrite/issues/2973")
                        @Test
                        void explicitImplementationClassInApi() {
                        }
                    }
                  """
              )
            );
        }
    }
}
