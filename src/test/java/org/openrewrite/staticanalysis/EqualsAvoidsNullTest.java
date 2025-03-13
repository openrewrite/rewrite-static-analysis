/*
 * Copyright 2024 the original author or authors.
 * <p>
 * Licensed under the Moderne Source Available License (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * <p>
 * https://docs.moderne.io/licensing/moderne-source-available-license
 * <p>
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.openrewrite.staticanalysis;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.openrewrite.DocumentExample;
import org.openrewrite.Issue;
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
                      String s = null;
                      if(s.equals("test")) {}
                      if(s.equalsIgnoreCase("test")) {}
                      System.out.println(s.contentEquals("test"));
                  }
              }
              """,
            """
              public class A {
                  {
                      String s = null;
                      if("test".equals(s)) {}
                      if("test".equalsIgnoreCase(s)) {}
                      System.out.println("test".contentEquals(s));
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
                      if(s != null && s.equals("test")) {}
                      if(null != s && s.equals("test")) {}
                  }
              }
              """,
            """
              public class A {
                  {
                      String s = null;
                      if("test".equals(s)) {}
                      if("test".equals(s)) {}
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

    @Test
    void ObjectEquals() {
        rewriteRun(
          //language=java
          java(
            """
              class A {
                  void foo(Object s) {
                      if (s.equals("null")) {
                      }
                  }
              }
              """,
            """
              class A {
                  void foo(Object s) {
                      if ("null".equals(s)) {
                      }
                  }
              }
              """
          )
        );
    }

    @Nested
    class ReplaceConstantMethodArg {

        @Test
        @Issue("https://github.com/openrewrite/rewrite-static-analysis/issues/434")
        void missingWhitespace() {
            rewriteRun(
              // language=java
              java(
                """
                  class A {
                      private static final String FOO = null;

                      boolean withParentExpression(String foo) {
                          return foo != null && foo.equals(FOO);
                      }
                  }
                  """,
                """
                  class A {
                      private static final String FOO = null;

                      boolean withParentExpression(String foo) {
                          return foo.equals(FOO);
                      }
                  }
                  """
              )
            );
        }
    }

    @Issue("https://github.com/openrewrite/rewrite-static-analysis/issues/442")
    @Test
    void retainCompareToAsToNotChangeOrder() {
        rewriteRun(
          //language=java
          java(
            """
              public class A {
                  {
                      String s = null;
                      System.out.println(s.compareTo("test"));
                      System.out.println(s.compareToIgnoreCase("test"));
                  }
              }
              """
          )
        );
    }

    @Issue("https://github.com/openrewrite/rewrite-static-analysis/issues/472")
    @Nested
    class equalsAvoidsNullNonIdempotent {

        @Test
        void raw() {
            rewriteRun(
              //language=java
              java(
                """
                  public class Foo {
                      public void bar() {
                          "FOO".equals("BAR");
                          "FOO".equalsIgnoreCase("BAR");
                          "FOO".compareTo("BAR");
                          "FOO".compareToIgnoreCase("BAR");
                      }
                  }
                  """
              )
            );
        }

        @Test
        void rawOverReference() {
            rewriteRun(
              //language=java
              java(
                """
                  public class Foo {
                      private static final String FOO = null;
                      public void bar(String _null) {
                          String _null2 = null;
                          FOO.equals("RAW");
                          FOO.compareTo("RAW");
                          FOO.compareToIgnoreCase("RAW");
                          _null.equals("RAW");
                          _null2.equals("RAW");
                      }
                  }
                  """
                , """
                  public class Foo {
                      private static final String FOO = null;
                      public void bar(String _null) {
                          String _null2 = null;
                          "RAW".equals(FOO);
                          "RAW".compareTo(FOO);
                          "RAW".compareToIgnoreCase(FOO);
                          "RAW".equals(_null);
                          "RAW".equals(_null2);
                      }
                  }
                  """
              )
            );
        }

    }

}
