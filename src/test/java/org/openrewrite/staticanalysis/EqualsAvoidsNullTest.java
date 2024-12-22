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
                      System.out.println(s.compareTo("test"));
                      System.out.println(s.compareToIgnoreCase("test"));
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
                      System.out.println("test".compareTo(s));
                      System.out.println("test".compareToIgnoreCase(s));
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

    @Nested
    class ReplaceConstantMethodArg {

        @Issue("https://github.com/openrewrite/rewrite-static-analysis/pull/398")
        @Test
        void one() {
            rewriteRun(
              // language=java
              java(
                """
                  public class Constants {
                      public static final String FOO = "FOO";
                  }
                  class A {
                      private boolean isFoo(String foo) {
                          return foo.contentEquals(Constants.FOO);
                      }
                  }
                  """,
                """
                  public class Constants {
                      public static final String FOO = "FOO";
                  }
                  class A {
                      private boolean isFoo(String foo) {
                          return Constants.FOO.contentEquals(foo);
                      }
                  }
                  """
              )
            );
        }

        @Test
        void staticImport() {
            rewriteRun(
              // language=java
              java(
                """
                  package c;
                  public class Constants {
                      public static final String FOO = "FOO";
                  }
                  """
              ),
              // language=java
              java(
                """
                  import static c.Constants.FOO;
                  class A {
                      private boolean isFoo(String foo) {
                          return foo.contentEquals(FOO);
                      }
                  }
                  """,
                """
                  import static c.Constants.FOO;
                  class A {
                      private boolean isFoo(String foo) {
                          return FOO.contentEquals(foo);
                      }
                  }
                  """
              )
            );
        }

        @Test
        void multiple() {
            rewriteRun(
              //language=java
              java(
                """
                  public class Constants {
                      public static final String FOO = "FOO";
                  }
                  class A {
                      private boolean isFoo(String foo, String bar) {
                          return foo.contentEquals(Constants.FOO)
                              || bar.compareToIgnoreCase(Constants.FOO);
                      }
                  }
                  """,
                """
                  public class Constants {
                      public static final String FOO = "FOO";
                  }
                  class A {
                      private boolean isFoo(String foo, String bar) {
                          return Constants.FOO.contentEquals(foo)
                              || Constants.FOO.compareToIgnoreCase(bar);
                      }
                  }
                  """
              )
            );
        }

        @Test
        void nonStaticNonFinalNoChange() {
            rewriteRun(
              // language=java
              java(
                """
                  public class Constants {
                      public final String FOO = "FOO";
                      public static String BAR = "BAR";
                  }
                  class A {
                      private boolean isFoo(String foo) {
                          return foo.contentEquals(new Constants().FOO);
                      }
                      private boolean isBar(String bar) {
                          return bar.contentEquals(Constants.BAR);
                      }
                  }
                  """
              )
            );
        }
    }
}
