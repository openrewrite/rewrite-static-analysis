package org.openrewrite.staticanalysis;

import org.junit.jupiter.api.Test;
import static org.openrewrite.java.Assertions.java;
import org.openrewrite.test.RecipeSpec;
import org.openrewrite.test.RewriteTest;

class ReplaceClassIsInstanceWithInstanceofTest implements RewriteTest {

    
    @Override
    public void defaults(RecipeSpec spec) {
        spec.recipe(new ReplaceClassIsInstanceWithInstanceof());
        //spec.recipe(new AvoidBoxedBooleanExpressions());
    }

    @Test
    void doesNotMatchMethod() {
        rewriteRun(
          //language=java
          java(
            """
              class A {
                  boolean foo() {
                    String s = "";
                    return s instanceof String;
                }
              }
            """
          )
        );
    }

    @Test
    void changeInstanceOf() {
        rewriteRun(
          //language=java
          java(
            """
              class A {
                  boolean foo() {
                    String s = "";
                    String s2 = null;
                    boolean result = String.class.isInstance(s);
                    return result;
                }
              }
              """,
            """
              class A {
                  boolean foo() {
                    String s = "";
                    String s2 = null;
                    boolean result = s instanceof String;
                    return result;
                }
              }
            """
          )
        );
    }

}
