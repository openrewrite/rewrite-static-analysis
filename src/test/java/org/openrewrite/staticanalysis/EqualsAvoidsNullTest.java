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
        void equals() {
            rewriteRun(
              // language=java
              java(
                """
                  public class A {
                      public static final String EXTERNAL_KEY = null;
                      {
                          EXTERNAL_KEY.equals("s");
                      }
                  }
                  """,
                """
                  public class A {
                      public static final String EXTERNAL_KEY = null;
                      {
                          "s".equals(EXTERNAL_KEY);
                      }
                  }
                  """)
            );
        }

        @DocumentExample
        @Test
        void equalsIgnoreCase() {
            rewriteRun(
              // language=java
              java(
                """
                  public class A {
                      public static final String EXTERNAL_KEY = null;
                      {
                          EXTERNAL_KEY.equalsIgnoreCase("s");
                      }
                  }
                  """,
                """
                  public class A {
                      public static final String EXTERNAL_KEY = null;
                      {
                          "s".equalsIgnoreCase(EXTERNAL_KEY);
                      }
                  }
                  """)
            );
        }

        @DocumentExample
        @Test
        void compareTo() {
            rewriteRun(
              // language=java
              java(
                """
                  public class A {
                      public static final String EXTERNAL_KEY = null;
                      {
                          EXTERNAL_KEY.compareTo("s");
                      }
                  }
                  """,
                """
                  public class A {
                      public static final String EXTERNAL_KEY = null;
                      {
                          "s".compareTo(EXTERNAL_KEY);
                      }
                  }
                  """)
            );
        }

        @DocumentExample
        @Test
        void compareToIgnoreCase() {
            rewriteRun(
              // language=java
              java(
                """
                  public class A {
                      public static final String EXTERNAL_KEY = null;
                      {
                          EXTERNAL_KEY.compareToIgnoreCase("s");
                      }
                  }
                  """,
                """
                  public class A {
                      public static final String EXTERNAL_KEY = null;
                      {
                          "s".compareToIgnoreCase(EXTERNAL_KEY);
                      }
                  }
                  """)
            );
        }

        @DocumentExample
        @Test
        void contentEquals() {
            rewriteRun(
              // language=java
              java(
                """
                  public class A {
                      public static final String EXTERNAL_KEY = null;
                      {
                          EXTERNAL_KEY.contentEquals("s");
                      }
                  }
                  """,
                """
                  public class A {
                      public static final String EXTERNAL_KEY = null;
                      {
                          "s".contentEquals(EXTERNAL_KEY);
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
