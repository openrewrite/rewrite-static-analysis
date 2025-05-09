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
import org.openrewrite.java.JavaParser;
import org.openrewrite.test.RecipeSpec;
import org.openrewrite.test.RewriteTest;

import static org.openrewrite.java.Assertions.java;

@SuppressWarnings("FunctionName")
class RemoveUnneededAssertionTest implements RewriteTest {
    @Override
    public void defaults(RecipeSpec spec) {
        spec.recipe(new RemoveUnneededAssertion());
    }

    @DocumentExample
    @Test
    void assertTrue() {
        rewriteRun(
          //language=java
          java(
            """
              public class A {
                  public void m() {
                      System.out.println("Hello");
                      assert true;
                      System.out.println("World");
                  }
              }
              """,
            """
              public class A {
                  public void m() {
                      System.out.println("Hello");
                      System.out.println("World");
                  }
              }
              """
          )
        );
    }

    @Test
    void assertFalse() {
        rewriteRun(
          //language=java
          java(
            """
              public class A {
                  public void m() {
                      System.out.println("Hello");
                      assert false;
                      System.out.println("World");
                  }
              }
              """
          )
        );
    }

    @Nested
    class UsingJUnitJupiter {
        @Test
        void assertTrueWithTrueArgument() {
            rewriteRun(
              spec -> spec.parser(JavaParser.fromJavaVersion().classpath("junit-jupiter-api")),
              //language=java
              java(
                """
                  import static org.junit.jupiter.api.Assertions.assertTrue;
                  public class A {
                      public void m() {
                          assertTrue(true);
                      }
                  }
                  """,
                """
                  public class A {
                      public void m() {
                      }
                  }
                  """
              )
            );
        }

        @Test
        void assertFalseWithFalseArgument() {
            rewriteRun(
              spec -> spec.parser(JavaParser.fromJavaVersion().classpath("junit-jupiter-api")),
              //language=java
              java(
                """
                  import static org.junit.jupiter.api.Assertions.assertFalse;
                  public class A {
                      public void m() {
                          assertFalse(false);
                      }
                  }
                  """,
                """
                  public class A {
                      public void m() {
                      }
                  }
                  """
              )
            );
        }

        @Test
        void assertTrueWithTrueArgumentAndMessage() {
            rewriteRun(
              spec -> spec.parser(JavaParser.fromJavaVersion().classpath("junit-jupiter-api")),
              //language=java
              java(
                """
                  import static org.junit.jupiter.api.Assertions.assertTrue;
                  public class A {
                      public void m() {
                          assertTrue(true, "message");
                      }
                  }
                  """,
                """
                  public class A {
                      public void m() {
                      }
                  }
                  """
              )
            );
        }

        @Test
        void assertFalseWithFalseArgumentAndMessage() {
            rewriteRun(
              spec -> spec.parser(JavaParser.fromJavaVersion().classpath("junit-jupiter-api")),
              //language=java
              java(
                """
                  import static org.junit.jupiter.api.Assertions.assertFalse;
                  public class A {
                      public void m() {
                          assertFalse(false, "message");
                      }
                  }
                  """,
                """
                  public class A {
                      public void m() {
                      }
                  }
                  """
              )
            );
        }
    }

    @Nested
    class UsingJUnit4 {
        @Test
        void assertTrueWithTrueArgument() {
            rewriteRun(
              spec -> spec.parser(JavaParser.fromJavaVersion().classpath("junit")),
              //language=java
              java(
                """
                  import static org.junit.Assert.assertTrue;
                  public class A {
                      public void m() {
                          assertTrue(true);
                      }
                  }
                  """,
                """
                  public class A {
                      public void m() {
                      }
                  }
                  """
              )
            );
        }

        @Test
        void assertFalseWithFalseArgument() {
            rewriteRun(
              spec -> spec.parser(JavaParser.fromJavaVersion().classpath("junit")),
              //language=java
              java(
                """
                  import static org.junit.Assert.assertFalse;
                  public class A {
                      public void m() {
                          assertFalse(false);
                      }
                  }
                  """,
                """
                  public class A {
                      public void m() {
                      }
                  }
                  """
              )
            );
        }

        @Test
        void assertTrueWithMessageAndTrueArgument() {
            rewriteRun(
              spec -> spec.parser(JavaParser.fromJavaVersion().classpath("junit")),
              //language=java
              java(
                """
                  import static org.junit.Assert.assertTrue;
                  public class A {
                      public void m() {
                          assertTrue("message", true);
                      }
                  }
                  """,
                """
                  public class A {
                      public void m() {
                      }
                  }
                  """
              )
            );
        }

        @Test
        void assertTrueWithMessageAndFalseArgument() {
            rewriteRun(
              spec -> spec.parser(JavaParser.fromJavaVersion().classpath("junit")),
              //language=java
              java(
                """
                  import static org.junit.Assert.assertFalse;
                  public class A {
                      public void m() {
                          assertFalse("message", false);
                      }
                  }
                  """,
                """
                  public class A {
                      public void m() {
                      }
                  }
                  """
              )
            );
        }
    }

    @Nested
    class UsingTestNG {
        @Test
        void assertTrueWithTrueArgument() {
            rewriteRun(
              spec -> spec.parser(JavaParser.fromJavaVersion().classpath("testng")),
              //language=java
              java(
                """
                  import static org.testng.Assert.assertTrue;
                  public class A {
                      public void m() {
                          assertTrue(true);
                      }
                  }
                  """,
                """
                  public class A {
                      public void m() {
                      }
                  }
                  """
              )
            );
        }

        @Test
        void assertFalseWithFalseArgument() {
            rewriteRun(
              spec -> spec.parser(JavaParser.fromJavaVersion().classpath("testng")),
              //language=java
              java(
                """
                  import static org.testng.Assert.assertFalse;
                  public class A {
                      public void m() {
                          assertFalse(false);
                      }
                  }
                  """,
                """
                  public class A {
                      public void m() {
                      }
                  }
                  """
              )
            );
        }

        @Test
        void assertTrueWithTrueArgumentAndMessage() {
            rewriteRun(
              spec -> spec.parser(JavaParser.fromJavaVersion().classpath("testng")),
              //language=java
              java(
                """
                  import static org.testng.Assert.assertTrue;
                  public class A {
                      public void m() {
                          assertTrue(true, "message");
                      }
                  }
                  """,
                """
                  public class A {
                      public void m() {
                      }
                  }
                  """
              )
            );
        }

        @Test
        void assertFalseWithFalseArgumentAndMessage() {
            rewriteRun(
              spec -> spec.parser(JavaParser.fromJavaVersion().classpath("testng")),
              //language=java
              java(
                """
                  import static org.testng.Assert.assertFalse;
                  public class A {
                      public void m() {
                          assertFalse(false, "message");
                      }
                  }
                  """,
                """
                  public class A {
                      public void m() {
                      }
                  }
                  """
              )
            );
        }
    }
}
