package org.openrewrite.staticanalysis;

import org.junit.jupiter.api.Test;
import org.openrewrite.DocumentExample;
import org.openrewrite.test.RecipeSpec;
import org.openrewrite.test.RewriteTest;

import static org.openrewrite.java.Assertions.java;

public class UnnecessaryReturnAsLastStatementTest implements RewriteTest {
    @Override
    public void defaults(RecipeSpec spec) {
        spec.recipe(new UnnecessaryReturnAsLastStatement());
    }

    @Test
    @DocumentExample
    void simpleReturn() {
        //language=java
        rewriteRun(
          java(
            """
              class Hello {
                  void world() {
                      System.out.println("Hello world");
                      return;
                  }
              }
              """,
            """
              class Hello {
                  void world() {
                      System.out.println("Hello world");
                  }
              }
              """
          )
        );
    }

    @Test
    void ifBranches() {
        //language=java
        rewriteRun(
          java(
            """
              class Hello {
                  void world(int i) {
                      if (i > 0) {
                        System.out.println("Positive");
                        return;
                      } else {
                        System.out.println("Zero or negative");
                        return;
                      }
                  }
              }
              """,
            """
              class Hello {
                  void world(int i) {
                      if (i > 0) {
                        System.out.println("Positive");
                      } else {
                        System.out.println("Zero or negative");
                      }
                  }
              }
              """
          )
        );
    }

    @Test
    void ifIsNotTheLast() {
        //language=java
        rewriteRun(
          java(
            """
              class Hello {
                  void world(int i) {
                      if (i > 0) {
                        System.out.println("Positive");
                        return;
                      } else {
                        System.out.println("Zero or negative");
                      }
                      System.out.println("Some extra logic");
                  }
              }
              """));
    }
}
