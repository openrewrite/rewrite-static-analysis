package org.openrewrite.staticanalysis;

import org.junit.jupiter.api.Test;
import static org.openrewrite.java.Assertions.java;
import org.openrewrite.test.RecipeSpec;
import org.openrewrite.test.RewriteTest;

class PreferSystemGetPropertyOverGetenvTest implements RewriteTest {

    @Override
    public void defaults(RecipeSpec spec) {
        spec.recipe(new PreferSystemGetPropertyOverGetenv());
    }

    @Test
    void replacesEnvWithProperty() {
        rewriteRun(
          java(
            """
            class A {
                void test() {
                    String home = System.getenv("HOME");
                }
            }
            """,
            """
            class A {
                void test() {
                    String home = System.getProperty("user.home");
                }
            }
            """
          )
        );
    }
}
