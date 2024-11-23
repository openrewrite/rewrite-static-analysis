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

import org.junit.jupiter.api.Nested;
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

    @Nested
    class LiteralsFirstInComparisons {

        @Nested
        class KeepOrderForStringVsString {

            @DocumentExample
            @Test
            void rawStringVsRawString() {
                rewriteRun(
                  // language=java
                  java(
                    """
                      public class A {
                          {
                              "KEY".equals("s");
                              "KEY".equalsIgnoreCase("s");
                              "KEY".compareTo("s");
                              "KEY".compareToIgnoreCase("s");
                              "KEY".contentEquals("s");
                          }
                      }
                      """,
                    """
                      public class A {
                          {
                              "KEY".equals("s");
                              "KEY".equalsIgnoreCase("s");
                              "KEY".compareTo("s");
                              "KEY".compareToIgnoreCase("s");
                              "KEY".contentEquals("s");
                          }
                      }
                      """)
                );
            }
        }

        @DocumentExample
        @Test
        void constantSingular() {
            rewriteRun(
              // language=java
              java(
                """
                  public class A {
                      public static final String KEY = null;
                      {
                          KEY.equals("s");
                      }
                  }
                  """,
                """
                  public class A {
                      public static final String KEY = null;
                      {
                          "s".equals(KEY);
                      }
                  }
                  """)
            );
        }

        @DocumentExample
        @Test
        void constantMulti() {
            rewriteRun(
              // language=java
              java(
                """
                  public class A {
                      public static final String KEY = null;
                      {
                          KEY.equals("s");
                          KEY.equalsIgnoreCase("s");
                          KEY.compareTo("s");
                          KEY.compareToIgnoreCase("s");
                          KEY.contentEquals("s");
                      }
                  }
                  """,
                """
                  public class A {
                      public static final String KEY = null;
                      {
                          "s".equals(KEY);
                          "s".equalsIgnoreCase(KEY);
                          "s".compareTo(KEY);
                          "s".compareToIgnoreCase(KEY);
                          "s".contentEquals(KEY);
                      }
                  }
                  """)
            );
        }

        @DocumentExample
        @Test
        void constantMultiExternal() {
            rewriteRun(
              // language=java
              java(
                """
                  public class AB {
                      public static final String KEY = null;
                  }
                  public class A {
                      {
                          AB.KEY.equals("s");
                          AB.KEY.equalsIgnoreCase("s");
                          AB.KEY.compareTo("s");
                          AB.KEY.compareToIgnoreCase("s");
                          AB.KEY.contentEquals("s");
                      }
                  }
                  """,
                """
                  public class AB {
                      public static final String KEY = null;
                  }
                  public class A {
                      {
                          "s".equals(AB.KEY);
                          "s".equalsIgnoreCase(AB.KEY);
                          "s".compareTo(AB.KEY);
                          "s".compareToIgnoreCase(AB.KEY);
                          "s".contentEquals(AB.KEY);
                      }
                  }
                  """)
            );
        }

        @DocumentExample
        @Test
        void externalConstant() {
            rewriteRun(
              // language=java
              java(
                """
                  public class A {
                      public static final String KEY = null;
                  }
                  static class B {
                      {
                          A.KEY.equals("s");
                          A.KEY.equalsIgnoreCase("s");
                          A.KEY.compareTo("s");
                          A.KEY.compareToIgnoreCase("s");
                          A.KEY.contentEquals("s");
                      }
                      private boolean foo () {
                          A.KEY.equals("s");
                          A.KEY.equalsIgnoreCase("s");
                          A.KEY.compareTo("s");
                          A.KEY.compareToIgnoreCase("s");
                          A.KEY.contentEquals("s");
                      }
                  }
                  """,
                """
                  public class A {
                      public static final String KEY = null;
                  }
                  static class B {
                      {
                          "s".equals(A.KEY);
                          "s".equalsIgnoreCase(A.KEY);
                          "s".compareTo(A.KEY);
                          "s".compareToIgnoreCase(A.KEY);
                          "s".contentEquals(A.KEY);
                      }
                      private boolean foo () {
                          "s".equals(A.KEY);
                          "s".equalsIgnoreCase(A.KEY);
                          "s".compareTo(A.KEY);
                          "s".compareToIgnoreCase(A.KEY);
                          "s".contentEquals(A.KEY);
                      }
                  }
                  """)
            );
        }

        @Nested
        class Parameter {


            @DocumentExample
            @Test
            void rawInline() {
                rewriteRun(
                  // language=java
                  java(
                    """
                      static class B {
                          private boolean bar (String param) {
                              param.equals("KEY");
                              param.equalsIgnoreCase("KEY");
                              param.compareTo("KEY");
                              param.compareToIgnoreCase("KEY");
                              param.contentEquals("KEY");
                          }
                      }
                      """,
                    """
                      static class B {
                          private boolean bar (String param) {
                              "KEY".equals(param);
                              "KEY".equalsIgnoreCase(param);
                              "KEY".compareTo(param);
                              "KEY".compareToIgnoreCase(param);
                              "KEY".contentEquals(param);
                          }
                      }
                      """)
                );
            }

            @DocumentExample
            @Test
            void onConstant() {
                rewriteRun(
                  // language=java
                  java(
                    """
                      static class B {
                          final String KEY = "KEY";
                          private boolean bar (String param) {
                              param.equals(KEY);
                              param.equalsIgnoreCase(KEY);
                              param.compareTo(KEY);
                              param.compareToIgnoreCase(KEY);
                              param.contentEquals(KEY);
                          }
                      }
                      """,
                    """
                      static class B {
                          final String KEY = "KEY";
                          private boolean bar (String param) {
                              KEY.equals(param);
                              KEY.equalsIgnoreCase(param);
                              KEY.compareTo(param);
                              KEY.compareToIgnoreCase(param);
                              KEY.contentEquals(param);
                          }
                      }
                      """)
                );
            }
        }

        @DocumentExample
        @Test
        void field() {
            rewriteRun(
              // language=java
              java(
                """
                  public class A {
                      {
                          final String KEY = null;
                          KEY.equals("s");
                          KEY.equalsIgnoreCase("s");
                          KEY.compareTo("s");
                          KEY.compareToIgnoreCase("s");
                          KEY.contentEquals("s");
                      }
                  }
                  """,
                """
                  public class A {
                      {
                          final String KEY = null;
                          "s".equals(KEY);
                          "s".equalsIgnoreCase(KEY);
                          "s".compareTo(KEY);
                          "s".compareToIgnoreCase(KEY);
                          "s".contentEquals(KEY);
                      }
                  }
                  """)
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
              java(
                """
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
}
