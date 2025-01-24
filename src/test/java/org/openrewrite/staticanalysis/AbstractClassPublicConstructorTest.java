package org.openrewrite.staticanalysis;

import org.junit.jupiter.api.Test;
import org.openrewrite.DocumentExample;
import org.openrewrite.test.RecipeSpec;
import org.openrewrite.test.RewriteTest;

import static org.openrewrite.java.Assertions.java;

@SuppressWarnings("java:S2699")
class AbstractClassPublicConstructorTest implements RewriteTest {

    @Override
    public void defaults(RecipeSpec spec) {
        spec.recipe(new AbstractClassPublicConstructor());
    }

    @DocumentExample
    @Test
    void replacePublicByProtected() {
        rewriteRun(
          //language=java
          java("""
            abstract class Test {
                public Test() {
                }
            }
            """, """
            abstract class Test {
                protected Test() {
                }
            }
            """));
    }

    @Test
    void noReplaceOnNonAbstractClass() {
        rewriteRun(
          //language=java
          java("""
            class Test {
                public Test() {
                }
            }
            """));
    }

    @Test
    void noReplaceOnPackageProtectedConstructor() {
        rewriteRun(
          //language=java
          java("""
            abstract class Test {
                Test() {
                }
            }
            """));
    }
}

