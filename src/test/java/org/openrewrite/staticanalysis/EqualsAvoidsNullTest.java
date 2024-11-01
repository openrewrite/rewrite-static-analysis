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

        @DocumentExample
        @Test
        void rawValue() {
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

        @DocumentExample
        @Test
        void nullValueConstant() {
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
        void valueConstant() {
            rewriteRun(
              // language=java
              java(
                """
                  public class A {
                      public static final String KEY = "null";
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
                      public static final String KEY = "null";
                      {
                          KEY.equals("s");
                          KEY.equalsIgnoreCase("s");
                          KEY.compareTo("s");
                          KEY.compareToIgnoreCase("s");
                          KEY.contentEquals("s");
                      }
                  }
                  """)
            );
        }

        @DocumentExample
        @Test
        void nullValueField() {
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
                      if (s != null && s.equals("test")) {}
                      if (null != s && s.equals("test")) {}
                  }
              }
              """,
            """
              public class A {
                  {
                      String s = null;
                      if ("test".equals(s)) {}
                      if ("test".equals(s)) {}
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
                      if (s.equals(null)) {
                      }
                  }
              }
              """,
            """
              public class A {
                  void foo(String s) {
                      if (s == null) {
                      }
                  }
              }
              """
          )
        );
    }
}
