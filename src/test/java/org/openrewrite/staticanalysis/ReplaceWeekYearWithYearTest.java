package org.openrewrite.staticanalysis;

import org.junit.jupiter.api.Test;
import org.junitpioneer.jupiter.Issue;
import org.openrewrite.DocumentExample;
import org.openrewrite.test.RecipeSpec;
import org.openrewrite.test.RewriteTest;

import static org.openrewrite.java.Assertions.java;

@SuppressWarnings("SuspiciousDateFormat")
public class ReplaceWeekYearWithYearTest implements RewriteTest {
    @Override
    public void defaults(RecipeSpec spec) {
        spec
          .recipe(new ReplaceWeekYearWithYear());
    }

    @Test
    @DocumentExample
    @Issue("https://github.com/openrewrite/rewrite-static-analysis/issues/58")
    public void changeSimpleDateFormat() {
        //language=java
        rewriteRun(
          java(
            """
              import java.text.SimpleDateFormat;
              
              class Test {
                public void formatDate() {
                  Date date = new SimpleDateFormat("yyyy/MM/dd").parse("2015/12/31");
                  String result = new SimpleDateFormat("YYYY/MM/dd").format(date);
                }
              }
              """,
            """
              import java.text.SimpleDateFormat;
              
              class Test {
                public void formatDate() {
                  Date date = new SimpleDateFormat("yyyy/MM/dd").parse("2015/12/31");
                  String result = new SimpleDateFormat("yyyy/MM/dd").format(date);
                }
              }
              """
          )
        );
    }

    @Test
    public void worksWithOfPatternFormatter() {
        //language=java
        rewriteRun(
          java(
            """
              import java.time.format.DateTimeFormatter;
              
              class Test {
                public void formatDate() {
                  Date date = new SimpleDateFormat("yyyy/MM/dd").parse("2015/12/31");
                  String result = DateTimeFormatter.ofPattern("YYYY/MM/dd").format(date);
                }
              }
              """,
            """
              import java.time.format.DateTimeFormatter;
              
              class Test {
                public void formatDate() {
                  Date date = new SimpleDateFormat("yyyy/MM/dd").parse("2015/12/31");
                  String result = DateTimeFormatter.ofPattern("yyyy/MM/dd").format(date);
                }
              }
              """
          )
        );
    }
}
