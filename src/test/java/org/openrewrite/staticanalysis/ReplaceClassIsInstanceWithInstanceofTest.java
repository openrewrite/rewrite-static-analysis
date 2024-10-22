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
                  void foo() {
                    String s = "";
                    boolean result = String.class.isInstance(s);
                    result = Integer.class.isInstance(s);
                }
              }
              """,
            """
              class A {
                  void foo() {
                    String s = "";
                    boolean result = s instanceof String;
                    result = s instanceof Integer;
                }
              }
            """
          )
        );
    }

}
