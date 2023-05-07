package org.openrewrite.staticanalysis;

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
    void doReplaceNestedOrTernaryWithIfFollowedByTernary() {
        rewriteRun(
          //language=java
          java(
            """
              class Test {
                public String determineSomething(String a, String b) {
                  return "a".equals(a) ? "a" : "b".equals(b) ? "b" : "nope";
                }
              }
              """,
            """
              class Test {
                public String determineSomething(String a, String b) {
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

    @Test
    void doReplaceNestedOrTernaryRecursive() {
        rewriteRun(
          //language=java
          java(
            """
              class Test {
                public String determineSomething(String a, String b, String c) {
                  return "a".equals(a) ? "a" : "b".equals(b) ? "b" : "c".equals(b) ? "c" :"nope";
                }
              }
              """,
            """
              class Test {
                public String determineSomething(String a, String b, String c) {
                  if("a".equals(a)){
                    return "a";
                  }
                  if("b".equals(b)){
                    return "b";
                  }
                  return  "c".equals(b) ? "c" : "nope";
                }
              }
              """
          )
        );
    }

    @Test
    void doReplaceNestedAndTernaryWithIfThenTernary() {
        rewriteRun(
          //language=java
          java(
            """
              class Test {
                public String determineSomething(String a, String b) {
                  return "a".equals(a) ? "b".equals(b) ? "b" : "a" : "nope";
                }
              }
              """,
            """
              class Test {
                public String determineSomething(String a, String b) {
                  if("a".equals(a)) {
                      return "b".equals(b) ? "b" : "a";
                  }
                  return "nope";
                }
              }
              """
          )
        );
    }

    @Test
    void doReplaceNestedAndOrTernaryWithIfThenTernaryElseTernary() {
        rewriteRun(
          //language=java
          java(
            """
              class Test {
                public String determineSomething(String a, String b, String c) {
                  return "a".equals(a) ? "b".equals(b) ? "b" : "a" : "c".equals(c) ? "c" : "nope";
                }
              }
              """,
            """
              class Test {
                public String determineSomething(String a, String b) {
                  if("a".equals(a)) {
                      return "b".equals(b) ? "b" : "a";
                  }
                  return "c".equals(b) ? "c" : "nope";
                }
              }
              """
          )
        );
    }


    @Test
    void doReplaceNestedOrAssignmentTernaryWithIfElse() {
        rewriteRun(
          //language=java
          java(
            """
              class Test {
                public void doThing(String a, String b) {
                  String result = "a".equals(a) ? "a" : "b".equals(b) ? "b" : "nope";
                  System.out.println(result);
                }
              }
              """,
            """
              class Test {
                public void doThing(String a, String b) {
                  String result;
                  if("a".equals(a)){
                    result = "a";
                  }
                  else {
                    result = "b".equals(b) ? "b" : "nope";
                  }
                  System.out.println(result);
                }
              }
              """
          )
        );
    }

}