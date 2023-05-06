package org.openrewrite.staticanalysis;

import static org.junit.jupiter.api.Assertions.*;
import static org.openrewrite.java.Assertions.java;

import org.junit.jupiter.api.Test;
import org.openrewrite.test.RecipeSpec;
import org.openrewrite.test.RewriteTest;

class TernaryOperatorsShouldNotBeNestedTest implements RewriteTest {

    @Override
    public void defaults(RecipeSpec spec) {
        spec.recipe(new TernaryOperatorsShouldNotBeNested());
    }

    @Test
    void doReplaceNestedTernaryWithIfFollowedByNestedTernary() {
        rewriteRun(
          //language=java
          java(
            """
              class Test {
                public String determineHardThing(String a, String b) {
                  return "a".equals(a) ? "a" : "b".equals(b) ? "b" : "nope";
                }
              }
              """,
            """
              class Test {
                public String determineHardThing(String a, String b) {
                  if("a".equals(a)){
                    return "a";
                  }
                  return  "b".equals(b) ? "b" : "nope";
                }
              }
              """
          )
        );
    }

}