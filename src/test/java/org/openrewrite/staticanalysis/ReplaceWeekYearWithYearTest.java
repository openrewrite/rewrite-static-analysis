/*
 * Copyright 2023 the original author or authors.
 * <p>
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * <p>
 * https://www.apache.org/licenses/LICENSE-2.0
 * <p>
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.openrewrite.staticanalysis;

import org.junit.jupiter.api.Test;
import org.junitpioneer.jupiter.Issue;
import org.openrewrite.DocumentExample;
import org.openrewrite.test.RecipeSpec;
import org.openrewrite.test.RewriteTest;

import static org.openrewrite.java.Assertions.java;

@SuppressWarnings("SuspiciousDateFormat")
class ReplaceWeekYearWithYearTest implements RewriteTest {
    @Override
    public void defaults(RecipeSpec spec) {
        spec
          .recipe(new ReplaceWeekYearWithYear());
    }

    @Test
    @DocumentExample
    @Issue("https://github.com/openrewrite/rewrite-static-analysis/issues/58")
    void changeSimpleDateFormat() {
        //language=java
        rewriteRun(
          java(
            """
              import java.text.SimpleDateFormat;
              import java.util.Date;
              
              class Test {
                public void formatDate() {
                  Date date = new SimpleDateFormat("yyyy/MM/dd").parse("2015/12/31");
                  String result = new SimpleDateFormat("YYYY/MM/dd").format(date);
                }
              }
              """,
            """
              import java.text.SimpleDateFormat;
              import java.util.Date;
              
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
    void worksWithOfPatternFormatter() {
        //language=java
        rewriteRun(
          java(
            """
              import java.text.SimpleDateFormat;
              import java.time.format.DateTimeFormatter;
              import java.util.Date;
              
              class Test {
                public void formatDate() {
                  Date date = new SimpleDateFormat("yyyy/MM/dd").parse("2015/12/31");
                  String result = DateTimeFormatter.ofPattern("YYYY/MM/dd").format(date.toInstant());
                }
              }
              """,
            """
              import java.text.SimpleDateFormat;
              import java.time.format.DateTimeFormatter;
              import java.util.Date;
              
              class Test {
                public void formatDate() {
                  Date date = new SimpleDateFormat("yyyy/MM/dd").parse("2015/12/31");
                  String result = DateTimeFormatter.ofPattern("yyyy/MM/dd").format(date.toInstant());
                }
              }
              """
          )
        );
    }

    @Test
    void worksWithYYUses() {
        //language=java
        rewriteRun(
          java(
            """
              import java.text.SimpleDateFormat;
              import java.time.format.DateTimeFormatter;
              import java.util.Date;
              
              class Test {
                public void formatDate() {
                  Date date = new SimpleDateFormat("yy/MM/dd").parse("2015/12/31");
                  String result = DateTimeFormatter.ofPattern("YY/MM/dd").format(date.toInstant());
                }
              }
              """,
            """
              import java.text.SimpleDateFormat;
              import java.time.format.DateTimeFormatter;
              import java.util.Date;
              
              class Test {
                public void formatDate() {
                  Date date = new SimpleDateFormat("yy/MM/dd").parse("2015/12/31");
                  String result = DateTimeFormatter.ofPattern("yy/MM/dd").format(date.toInstant());
                }
              }
              """
          )
        );
    }

    @Test
    void onlyRunsWhenFormatAndDateTypesAreUsed() {
        //language=java
        rewriteRun(
          java(
            """
              class Test {
                public static void main(String[] args) {
                  String pattern = "YYYY/MM/dd";
                  System.out.println(pattern);
                }
              }
              """
          )
        );
    }

    @Test
    void standaloneNewClassCall() {
        //language=java
        rewriteRun(
          java(
            """
              import java.text.SimpleDateFormat;
              import java.util.Date;
              
              class Test {
                public void formatDate() {
                  SimpleDateFormat format = new SimpleDateFormat("YYYY-MM-dd");
                  Date date = format.parse("2015/12/31");
                }
              }
              """,
            """
              import java.text.SimpleDateFormat;
              import java.util.Date;
              
              class Test {
                public void formatDate() {
                  SimpleDateFormat format = new SimpleDateFormat("yyyy-MM-dd");
                  Date date = format.parse("2015/12/31");
                }
              }
              """
          )
        );
    }
    @Test
    void patternUsesSingleQuotes() {
        //language=java
        rewriteRun(
          java(
            """
              import java.text.SimpleDateFormat;
              import java.util.Date;
              
              class Test {
                public void formatDate() {
                  SimpleDateFormat format = new SimpleDateFormat("'Your date is:' YYYY-MM-dd");
                  Date date = format.parse("2015/12/31");
                }
              }
              """,
            """
              import java.text.SimpleDateFormat;
              import java.util.Date;
              
              class Test {
                public void formatDate() {
                  SimpleDateFormat format = new SimpleDateFormat("'Your date is:' yyyy-MM-dd");
                  Date date = format.parse("2015/12/31");
                }
              }
              """
          )
        );
    }

    @Test
    void patternUsesMultipleSingleQuotes() {
        //language=java
        rewriteRun(
          java(
            """
              import java.text.SimpleDateFormat;
              import java.util.Date;
              
              class Test {
                public void formatDate() {
                  SimpleDateFormat format = new SimpleDateFormat("'Your date is:' YYYY-MM-dd, 'yy'");
                  Date date = format.parse("2015/12/31");
                }
              }
              """,
            """
              import java.text.SimpleDateFormat;
              import java.util.Date;
              
              class Test {
                public void formatDate() {
                  SimpleDateFormat format = new SimpleDateFormat("'Your date is:' yyyy-MM-dd, 'yy'");
                  Date date = format.parse("2015/12/31");
                }
              }
              """
          )
        );
    }

    @Test
    void doesNotChangeWhyInSingleQuotes() {
        //language=java
        rewriteRun(
          java(
            """
              import java.text.SimpleDateFormat;
              import java.util.Date;
              
              class Test {
                public void formatDate() {
                  SimpleDateFormat format = new SimpleDateFormat("'Y' dd-MM");
                  Date date = format.parse("2015/12/31");
                }
              }
              """
          
        );
    }
}