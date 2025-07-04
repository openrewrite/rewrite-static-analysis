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
import org.openrewrite.java.JavaParser;
import org.openrewrite.test.RecipeSpec;
import org.openrewrite.test.RewriteTest;

import static org.openrewrite.java.Assertions.java;

class ReplaceValidateNotNullHavingVarargsWithObjectsRequireNonNullTest implements RewriteTest {
    @Override
    public void defaults(RecipeSpec spec) {
        spec
          .parser(JavaParser.fromJavaVersion()
            .logCompilationWarningsAndErrors(true)
            .classpath("commons-lang3"))
          .recipe(new ReplaceValidateNotNullHavingVarargsWithObjectsRequireNonNull());
    }

    @Test
    @DocumentExample
    void replaceMethodsWithTwoArg() {
        rewriteRun(
          //language=java
          java(
            """
              import org.apache.commons.lang3.Validate;
              class Test {
                  void test(Object obj) {
                      Validate.notNull(obj, "Object should not be null");
                  }
              }
              """,
            """
              import java.util.Objects;

              class Test {
                  void test(Object obj) {
                      Objects.requireNonNull(obj, "Object should not be null");
                  }
              }
              """
          )
        );
    }

    @Test
    void doNothingIfMethodNotFound() {
        rewriteRun(
          //language=java
          java(
            """
              import org.apache.commons.lang3.Validate;
              class Test {
                  void test(Object obj) {
                  }
              }
              """
          )
        );
    }

    @Test
    void doNothingOnMethodsWithOneArg() {
        rewriteRun(
          //language=java
          java(
            """
              import org.apache.commons.lang3.Validate;
              class Test {
                  void test(Object obj) {
                      Validate.notNull(obj);
                  }
              }
              """
          )
        );
    }

    @Test
    void replaceMethodsWithThreeArg() {
        rewriteRun(
          //language=java
          java(
            """
              import org.apache.commons.lang3.Validate;
              class Test {
                  void test(Object obj) {
                        Validate.notNull(obj, "Object in %s should not be null", "request xyz");
                  }
              }
              """,
            """
              import java.util.Objects;

              class Test {
                  void test(Object obj) {
                      Objects.requireNonNull(obj, () -> String.format("Object in %s should not be null", "request xyz"));
                  }
              }
              """
          )
        );
    }

    @Test
    void replaceMethodsWithThreeArgRetainWhitespace() {
        rewriteRun(
          //language=java
          java(
            """
              import org.apache.commons.lang3.Validate;
              class Test {
                  void test(Object obj) {
                      Validate.notNull(obj, "Object in %s should not be null",
                            "request xyz");
                  }
              }
              """,
            """
              import java.util.Objects;

              class Test {
                  void test(Object obj) {
                      Objects.requireNonNull(obj, () -> String.format("Object in %s should not be null",
                              "request xyz"));
                  }
              }
              """
          )
        );
    }

    @Test
    void replaceMethodsWithThreeArgRetainComment() {
        rewriteRun(
          //language=java
          java(
            """
              import org.apache.commons.lang3.Validate;
              class Test {
                  void test(Object obj) {
                      Validate.notNull(/* FOO */ obj, "Object in %s should not be null", /* Bar */ "request xyz");
                  }
              }
              """,
            """
              import java.util.Objects;

              class Test {
                  void test(Object obj) {
                      Objects.requireNonNull(/* FOO */ obj, () -> String.format("Object in %s should not be null", /* Bar */ "request xyz"));
                  }
              }
              """
          )
        );
    }
}
