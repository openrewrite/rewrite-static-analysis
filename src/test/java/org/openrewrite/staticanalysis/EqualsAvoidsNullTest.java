/*
 * Copyright 2020 the original author or authors.
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
import org.openrewrite.DocumentExample;
import org.openrewrite.test.RecipeSpec;
import org.openrewrite.test.RewriteTest;

import static org.openrewrite.java.Assertions.java;

@SuppressWarnings({"ClassInitializerMayBeStatic", "StatementWithEmptyBody", "ConstantConditions"})
class EqualsAvoidsNullTest implements RewriteTest {

    @Override
    public void defaults(RecipeSpec spec) {
        spec.recipe(new EqualsAvoidsNull());
    }

    @DocumentExample
    @Test
    void invertConditional() {
        rewriteRun(
          //language=java
          java(
            """
              public class A {
                  {
                      String s = "LiteralsFirstInComparisons";
                      System.out.println(s.compareTo("LiteralsFirstInComparisons"));
                      System.out.println(s.compareToIgnoreCase("LiteralsFirstInComparisons"));
                      System.out.println(s.contentEquals("LiteralsFirstInComparisons"));
                      System.out.println(s.equals("LiteralsFirstInComparisons"));
                      System.out.println(s.equalsIgnoreCase("LiteralsFirstInComparisons"));
                  }
              }
              """,
            """
              public class A {
                  {
                      String s = "LiteralsFirstInComparisons";
                      System.out.println("LiteralsFirstInComparisons".compareTo(s));
                      System.out.println("LiteralsFirstInComparisons".compareToIgnoreCase(s));
                      System.out.println("LiteralsFirstInComparisons".contentEquals(s));
                      System.out.println("LiteralsFirstInComparisons".equals(s));
                      System.out.println("LiteralsFirstInComparisons".equalsIgnoreCase(s));
                  }
              }
              """
          )
        );
    }

    @Test
    void removeUnnecessaryNullCheck() {
        rewriteRun(
          //language=java
          java(
            """
              public class A {
                  {
                      String s = null;
                      if(s != null && s.equals("LiteralsFirstInComparisons")) {}
                      if(null != s && s.equals("LiteralsFirstInComparisons")) {}
                  }
              }
              """,
            """
              public class A {
                  {
                      String s = null;
                      if("LiteralsFirstInComparisons".equals(s)) {}
                      if("LiteralsFirstInComparisons".equals(s)) {}
                  }
              }
              """
          )
        );
    }

    @Test
    void nullLiteral() {
        rewriteRun(
          //language=java
          java("""
              public class A {
                    void foo(String s) {
                        if(s.equals(null)) {
                        }
                    }
                }
              """,
            """
              
              public class A {
                    void foo(String s) {
                        if(s == null) {
                        }
                    }
                }
              """)
        );
    }
}
