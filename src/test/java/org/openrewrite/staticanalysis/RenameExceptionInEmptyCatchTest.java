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

import org.junit.jupiter.api.Test;
import org.openrewrite.DocumentExample;
import org.openrewrite.test.RecipeSpec;
import org.openrewrite.test.RewriteTest;

import static org.openrewrite.java.Assertions.java;

@SuppressWarnings({"EmptyTryBlock", "CatchMayIgnoreException"})
class RenameExceptionInEmptyCatchTest implements RewriteTest {
    @Override
    public void defaults(RecipeSpec spec) {
        spec.recipe(new RenameExceptionInEmptyCatch());
    }

    @Test
    void notEmpty() {
        rewriteRun(
          //language=java
          java(
            """
              class Test {
                  void method() {
                      try {
                      } catch (Exception ex) {
                          // comment
                      }
                  }
              }
              """
          )
        );
    }

    @DocumentExample
    @Test
    void nameScopeTest() {
        rewriteRun(
          //language=java
          java(
            """
              class Test {
                  int ignored = 0;
                  void method(int ignored1) {
                      int ignored2 = 0;
                      for (int ignored3 = 0; ignored3 < 10; ignored3++) { // scope does not apply.
                          int ignored4 = 0; // scope does not apply.
                      }
                      if (ignored1 > 0) {
                          int ignored5 = 0; // scope does not apply.
                      }
                      try {
                          int ignored6 = 0; // scope does not apply.
                      } catch (Exception ex) {
                      }
                  }
              }
              """,
            """
              class Test {
                  int ignored = 0;
                  void method(int ignored1) {
                      int ignored2 = 0;
                      for (int ignored3 = 0; ignored3 < 10; ignored3++) { // scope does not apply.
                          int ignored4 = 0; // scope does not apply.
                      }
                      if (ignored1 > 0) {
                          int ignored5 = 0; // scope does not apply.
                      }
                      try {
                          int ignored6 = 0; // scope does not apply.
                      } catch (Exception ignored3) {
                      }
                  }
              }
              """
          )
        );
    }

    @Test
    void emptyCatchBlock() {
        rewriteRun(
          //language=java
          java(
            """
              class Test {
                  void method() {
                      try {
                      } catch (Exception ex) {
                      }
                  }
              }
              """,
            """
              class Test {
                  void method() {
                      try {
                      } catch (Exception ignored) {
                      }
                  }
              }
              """
          )
        );
    }

    @SuppressWarnings("all")
    @Test
    void multipleCatches() {
        rewriteRun(
          //language=java
          java(
            """
              class Test {
                  void method() {
                      try {
                      } catch (IllegalArgumentException ex) {
                      } catch (IllegalAccessException ex) {
                      }
                  }
              }
              """,
            """
              class Test {
                  void method() {
                      try {
                      } catch (IllegalArgumentException ignored) {
                      } catch (IllegalAccessException ignored) {
                      }
                  }
              }
              """
          )
        );
    }
}
