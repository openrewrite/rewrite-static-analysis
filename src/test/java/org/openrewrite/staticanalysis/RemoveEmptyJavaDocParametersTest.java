/*
 * Copyright 2022 the original author or authors.
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
import org.openrewrite.Issue;
import org.openrewrite.test.RecipeSpec;
import org.openrewrite.test.RewriteTest;

import static org.openrewrite.java.Assertions.java;
import static org.openrewrite.test.SourceSpecs.other;

@SuppressWarnings("JavadocDeclaration")
class RemoveEmptyJavaDocParametersTest implements RewriteTest {

    @Override
    public void defaults(RecipeSpec spec) {
        spec.recipe(new RemoveEmptyJavaDocParameters());
    }

    @DocumentExample
    @Test
    void emptyParam() {
        rewriteRun(
          //language=java
          java(
            """
              class Test {
                  /**
                   * @param arg0
                   */
                  void method(int arg0) {
                  }
              }
              """,
            """
              class Test {
                  /**
                   */
                  void method(int arg0) {
                  }
              }
              """
          )
        );
    }

    @Test
    void emptyReturn() {
        rewriteRun(
          //language=java
          java(
            """
              class Test {
                  /**
                   * @return
                   */
                  int method() {
                  }
              }
              """,
            """
              class Test {
                  /**
                   */
                  int method() {
                  }
              }
              """
          )
        );
    }

    @Test
    void emptyThrows() {
        rewriteRun(
          //language=java
          java(
            """
              class Test {
                  /**
                   * @throws
                   */
                  void method() throws IllegalStateException {
                  }
              }
              """,
            """
              class Test {
                  /**
                   */
                  void method() throws IllegalStateException {
                  }
              }
              """
          )
        );
    }

    @Test
    void removeEmptyParams() {
        rewriteRun(
          //language=java
          java(
            """
              class Test {
                  /**
                   * @param arg0 description1
                   * @param arg1
                   * @param arg2 description3
                   */
                  void method(int arg0, int arg1, int arg2) {
                  }
              }
              """,
            """
              class Test {
                  /**
                   * @param arg0 description1
                   * @param arg2 description3
                   */
                  void method(int arg0, int arg1, int arg2) {
                  }
              }
              """
          )
        );
    }

    @Test
    void multipleEmptyLines() {
        rewriteRun(
          //language=java
          java(
            """
              class Test {
                  /**
                   * @param arg0
                   * 
                   * 
                   * 
                   * @param arg1 description
                   */
                  void method(int arg0, int arg1) {
                  }
              }
              """,
            """
              class Test {
                  /**
                   * @param arg1 description
                   */
                  void method(int arg0, int arg1) {
                  }
              }
              """
          )
        );
    }

    @Nested
    class NoSpace {
        @Test
        void emptyParamNoSpace() {
            rewriteRun(
              //language=java
              java(
                """
                  class Test {
                      /**
                       *@param arg0
                       */
                      void method(int arg0) {
                      }
                  }
                  """,
                """
                  class Test {
                      /**
                       */
                      void method(int arg0) {
                      }
                  }
                  """
              )
            );
        }

        @Test
        void emptyReturnNoSpace() {
            rewriteRun(
              //language=java
              java(
                """
                  class Test {
                      /**
                       *@return
                       */
                      int method() {
                      }
                  }
                  """,
                """
                  class Test {
                      /**
                       */
                      int method() {
                      }
                  }
                  """
              )
            );
        }

        @Test
        void emptyThrowsNoSpace() {
            rewriteRun(
              //language=java
              java(
                """
                  class Test {
                      /**
                       *@throws
                       */
                      void method() throws IllegalStateException {
                      }
                  }
                  """,
                """
                  class Test {
                      /**
                       */
                      void method() throws IllegalStateException {
                      }
                  }
                  """
              )
            );
        }

    }

    @Nested
    class SingleLine {
        @Test
        void singleLineParam() {
            rewriteRun(
              //language=java
              java(
                """
                  class Test {
                      /** @param arg0*/
                      void method(int arg0) {
                      }
                  }
                  """,
                """
                  class Test {
                      /***/
                      void method(int arg0) {
                      }
                  }
                  """
              )
            );
        }

        @Test
        void singleLineReturn() {
            rewriteRun(
              //language=java
              java(
                """
                  class Test {
                      /** @return*/
                      int method() {
                      }
                  }
                  """,
                """
                  class Test {
                      /***/
                      int method() {
                      }
                  }
                  """
              )
            );
        }

        @Test
        void singleLineThrows() {
            rewriteRun(
              //language=java
              java(
                """
                  class Test {
                      /** @throws*/
                      void method() throws IllegalStateException {
                      }
                  }
                  """,
                """
                  class Test {
                      /***/
                      void method() throws IllegalStateException {
                      }
                  }
                  """
              )
            );
        }

        @Test
        void singleLineParamNoSpace() {
            rewriteRun(
              //language=java
              java(
                """
                  class Test {
                      /**@param arg0*/
                      void method(int arg0) {
                      }
                  }
                  """,
                """
                  class Test {
                      /***/
                      void method(int arg0) {
                      }
                  }
                  """
              )
            );
        }

        @Test
        void singleLineReturnNoSpace() {
            rewriteRun(
              //language=java
              java(
                """
                  class Test {
                      /**@return*/
                      int method() {
                      }
                  }
                  """,
                """
                  class Test {
                      /***/
                      int method() {
                      }
                  }
                  """
              )
            );
        }

        @Test
        void singleLineThrowsNoSpace() {
            rewriteRun(
              //language=java
              java(
                """
                  class Test {
                      /**@throws*/
                      void method() throws IllegalStateException {
                      }
                  }
                  """,
                """
                  class Test {
                      /***/
                      void method() throws IllegalStateException {
                      }
                  }
                  """
              )
            );
        }
    }

    @Nested
    class FirstLine {
        @Test
        void firstLineParam() {
            rewriteRun(
              //language=java
              java(
                """
                  class Test {
                      /** @param arg0
                       */
                      void method(int arg0) {
                      }
                  }
                  """,
                """
                  class Test {
                      /**
                       */
                      void method(int arg0) {
                      }
                  }
                  """
              )
            );
        }

        @Test
        void firstLineReturn() {
            rewriteRun(
              //language=java
              java(
                """
                  class Test {
                      /** @return
                       */
                      int method() {
                      }
                  }
                  """,
                """
                  class Test {
                      /**
                       */
                      int method() {
                      }
                  }
                  """
              )
            );
        }

        @Test
        void firstLineThrows() {
            rewriteRun(
              //language=java
              java(
                """
                  class Test {
                      /** @throws
                       */
                      int method() throws IllegalStateException {
                      }
                  }
                  """,
                """
                  class Test {
                      /**
                       */
                      int method() throws IllegalStateException {
                      }
                  }
                  """
              )
            );
        }

        @Test
        void firstLineParamNoSpace() {
            rewriteRun(
              //language=java
              java(
                """
                  class Test {
                      /**@param arg0
                       */
                      void method(int arg0) {
                      }
                  }
                  """,
                """
                  class Test {
                      /**
                       */
                      void method(int arg0) {
                      }
                  }
                  """
              )
            );
        }

        @Test
        void firstLineReturnNoSpace() {
            rewriteRun(
              //language=java
              java(
                """
                  class Test {
                      /**@return
                       */
                      int method() {
                      }
                  }
                  """,
                """
                  class Test {
                      /**
                       */
                      int method() {
                      }
                  }
                  """
              )
            );
        }

        @Test
        void firstLineThrowsNoSpace() {
            rewriteRun(
              //language=java
              java(
                """
                  class Test {
                      /**@throws
                       */
                      int method() throws IllegalStateException {
                      }
                  }
                  """,
                """
                  class Test {
                      /**
                       */
                      int method() throws IllegalStateException {
                      }
                  }
                  """
              )
            );
        }
    }

    @Test
    @Issue("https://github.com/openrewrite/rewrite/issues/3078")
    void visitingQuarkMustNotFail() {
        rewriteRun(
          other(
            """
              foo
              """
          )
        );
    }
}
