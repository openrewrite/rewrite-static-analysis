package org.openrewrite.staticanalysis;

import org.junit.jupiter.api.Test;
import org.openrewrite.java.JavaParser;
import org.openrewrite.test.RecipeSpec;
import org.openrewrite.test.RewriteTest;

import static org.openrewrite.java.Assertions.java;

public class EqualsToContentEqualsTest implements RewriteTest {

    @Override
    public void defaults(RecipeSpec spec) {
        spec
          .parser(JavaParser.fromJavaVersion())
          .recipe(new EqualsToContentEquals());
    }

    @Test
    public void replaceStringBuilder() {
        //language=java
        rewriteRun(
          java(
            """
              class SomeClass {
                  boolean foo(StringBuilder sb) {
                      String str = "example string";
                      return str.equals(sb.toString());
                  }
              }
              """,
            """
              class SomeClass {
                  boolean foo(StringBuilder sb) {
                      String str = "example string";
                      return str.contentEquals(sb);
                  }
              }
              """
          )
        );
    }

    @Test
    public void onlyRunsOnCorrectInvocations() {
        //language=java
        rewriteRun(
          java(
            """
              class SomeClass {
                  boolean foo(int number, String str) {
                      return str.equals(number.toString());
                  }
              }
              """
          )
        );
    }

    @Test
    void runsOnStringBuffer() {
        //language=java
        rewriteRun(
          java(
            """
              class SomeClass {
                  boolean foo(StringBuffer sb, String str) {
                      return str.equals(sb.toString());
                  }
              }
              """,
            """
              class SomeClass {
                  boolean foo(StringBuffer sb, String str) {
                      return str.contentEquals(sb);
                  }
              }
              """
          )
        );
    }

    @Test
    void runsOnCharSequence() {
        //language=java
        rewriteRun(
          java(
            """
              class SomeClass {
                  boolean foo(CharSequence cs, String str) {
                      return str.equals(cs.toString());
                  }
              }
              """,
            """
              class SomeClass {
                  boolean foo(CharSequence cs, String str) {
                      return str.contentEquals(cs);
                  }
              }
              """
          )
        );
    }
}
