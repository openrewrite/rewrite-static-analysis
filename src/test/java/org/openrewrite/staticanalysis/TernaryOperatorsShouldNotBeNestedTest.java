package org.openrewrite.staticanalysis;

import static org.openrewrite.java.Assertions.java;

import org.junit.jupiter.api.Test;
import org.junitpioneer.jupiter.ExpectedToFail;
import org.openrewrite.Issue;
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
                    if ("a".equals(a)) {
                        return "a";
                    }
                    return "b".equals(b) ? "b" : "nope";
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
                    if ("a".equals(a)) {
                        return "a";
                    }
                    if ("b".equals(b)) {
                        return "b";
                    }
                    return "c".equals(b) ? "c" : "nope";
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
                    if ("a".equals(a)) {
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
                public String determineSomething(String a, String b, String c) {
                    if ("a".equals(a)) {
                        return "b".equals(b) ? "b" : "a";
                    }
                    return "c".equals(c) ? "c" : "nope";
                }
              }
              """
          )
        );
    }

    @Issue("todo")
    @ExpectedToFail("only directly returned ternaries are taken into account")
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
                    if ("a".equals(a)) {
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

    @Issue("todo")
    @ExpectedToFail("Not yet implemented")
    @Test
    void doReplaceNestedOrTernaryInStreamWithIfInBlock() {
        rewriteRun(
          //language=java
          java(
            """
              import java.util.Set;
              import java.util.Arrays;
              import java.util.List;
              import java.util.stream.Collectors;
              class Test {
                public Set<String> makeASet() {
                   List<String> s = Arrays.asList("a","b","c","nope");
                   return s.stream().map(item -> item.startsWith("a") ? "a" : item.startsWith("b") ? "b" : "nope").collect(Collectors.toSet());
                }
              }
              """,
            """
              import java.util.Set;
              import java.util.Arrays;
              import java.util.List;
              import java.util.stream.Collectors;
              class Test {
                public Set<String> makeASet() {
                   List<String> s = Arrays.asList("a","b","c","nope");
                   return s.stream().map(item -> {
                    if (item.startsWith("a")) {
                        return "a";
                    }
                    return item.startsWith("b") ? "b" : "nope";
                   }).collect(Collectors.toSet());
                }
              }
              """
          )
        );
    }



    @Test
    void doReplaceNestedOrTernaryContainingNull() {
        rewriteRun(
          //language=java
          java(
            """
              class Test {
                public String determineSomething(String a, String b) {
                  return "a".equals(a) ? null : "b".equals(b) ? "b" : null;
                }
              }
              """,
            """
              class Test {
                public String determineSomething(String a, String b) {
                    if ("a".equals(a)) {
                        return null;
                    }
                    return "b".equals(b) ? "b" : null;
                }
              }
              """
          )
        );
    }

    @Test
    void doReplaceNestedOrTernaryContainingExpression() {
        rewriteRun(
          //language=java
          java(
            """
              class Test {
                public String determineSomething(String a, String b) {
                  return "a".equals(a) ? "foo" + "bar" : "b".equals(b) ? a + b : b + a;
                }
              }
              """,
            """
              class Test {
                public String determineSomething(String a, String b) {
                    if ("a".equals(a)) {
                        return "foo" + "bar";
                    }
                    return "b".equals(b) ? a + b : b + a;
                }
              }
              """
          )
        );
    }

    @Test
    void doReplaceNestedOrTernaryContainingComments() {
        rewriteRun(
          //language=java
          java(
            """
              class Test {
                public String determineSomething(String a, String b) {
                  //this should be before the if and followed by a new line
                 
                  return "a".equals(a) ? "a" : "b".equals(b) ? "b" : "nope"; //this should be behind the ternary
                }
              }
              """,
            """
              class Test {
                public String determineSomething(String a, String b) {
                    //this should be before the if and followed by a new line
                    
                    if ("a".equals(a)) {
                        return "a";
                    }
                    return "b".equals(b) ? "b" : "nope"; //this should be behind the ternary
                }
              }
              """
          )
        );
    }



    @Test
    void doReplaceNestedOrTernaryContainingMethodCall() {
        //language=java
        rewriteRun(
          java("""
            class M{
              static String a(){return "a";}
              static String b(){return "b";}
              static String c(){return "c";}
              static String nope(){return "nope";}
            }
            """),
          java(
            """
              class Test {
                public String determineSomething(String a, String b) {
                  return "a".equals(a) ? M.a() : "b".equals(b) ? M.b() : M.nope();
                }
              }
              """,
            """
              class Test {
                public String determineSomething(String a, String b) {
                    if ("a".equals(a)) {
                        return M.a();
                    }
                    return "b".equals(b) ? M.b() : M.nope();
                }
              }
              """
          )
        );
    }


}