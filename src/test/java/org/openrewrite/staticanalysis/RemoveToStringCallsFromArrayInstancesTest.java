package org.openrewrite.staticanalysis;

import org.junit.Test;
import org.openrewrite.DocumentExample;
import org.openrewrite.java.JavaParser;
import org.openrewrite.test.RecipeSpec;
import org.openrewrite.test.RewriteTest;

import static org.openrewrite.java.Assertions.java;

public class RemoveToStringCallsFromArrayInstancesTest implements RewriteTest {
    @Override
    public void defaults(RecipeSpec spec) {
        spec
          .parser(JavaParser.fromJavaVersion())
          .recipe(new RemoveToStringCallsFromArrayInstances());
    }

    @Test
    @DocumentExample
    public void fixNonCompliantToString() {
        //language=java
        rewriteRun(
          java(
            """
              class SomeClass {
                  public static void main(String[] args) {
                      String argStr = args.toString();
                  }
              }
              """,
            """
              import java.util.Arrays;
              
              class SomeClass {
                  public static void main(String[] args) {
                      String argStr = Arrays.toString(args);
                  }
              }
              """
          )
        );
    }
}
