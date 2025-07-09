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

@SuppressWarnings({"RedundantArrayCreation", "ArraysAsListWithZeroOrOneArgument"})
class SimplifyArraysAsListTest implements RewriteTest {

    @Override
    public void defaults(RecipeSpec spec) {
        spec.recipe(new SimplifyArraysAsList());
    }

    @DocumentExample
    @Test
    void simplifyStringArrayCreation() {
        rewriteRun(
          //language=java
          java(
            """
              import java.util.Arrays;
              import java.util.List;

              class Test {
                  void method() {
                      List<String> list = Arrays.asList(new String[]{"w", "k", "s"});
                  }
              }
              """,
            """
              import java.util.Arrays;
              import java.util.List;

              class Test {
                  void method() {
                      List<String> list = Arrays.asList("w", "k", "s");
                  }
              }
              """
          )
        );
    }

    @Test
    void simplifyIntegerArrayCreation() {
        rewriteRun(
          //language=java
          java(
            """
              import java.util.Arrays;
              import java.util.List;

              class Test {
                  void method() {
                      List<Integer> list = Arrays.asList(new Integer[]{1, 2, 3});
                  }
              }
              """,
            """
              import java.util.Arrays;
              import java.util.List;

              class Test {
                  void method() {
                      List<Integer> list = Arrays.asList(1, 2, 3);
                  }
              }
              """
          )
        );
    }

    @Test
    void simplifyEmptyArray() {
        rewriteRun(
          //language=java
          java(
            """
              import java.util.Arrays;
              import java.util.List;

              class Test {
                  void method() {
                      List<String> list = Arrays.asList(new String[]{});
                  }
              }
              """,
            """
              import java.util.Arrays;
              import java.util.List;

              class Test {
                  void method() {
                      List<String> list = Arrays.asList();
                  }
              }
              """
          )
        );
    }

    @Test
    void simplifyWithMethodArguments() {
        rewriteRun(
          //language=java
          java(
            """
              import java.util.Arrays;
              import java.util.List;

              class Test {
                  void method(String a, String b) {
                      List<String> list = Arrays.asList(new String[]{a, b, "c"});
                  }
              }
              """,
            """
              import java.util.Arrays;
              import java.util.List;

              class Test {
                  void method(String a, String b) {
                      List<String> list = Arrays.asList(a, b, "c");
                  }
              }
              """
          )
        );
    }

    @Test
    void doNotChangeArrayWithExplicitSize() {
        rewriteRun(
          //language=java
          java(
            """
              import java.util.Arrays;
              import java.util.List;

              class Test {
                  void method() {
                      List<String> list = Arrays.asList(new String[3]);
                  }
              }
              """
          )
        );
    }

    @Test
    void doNotChangeExistingVarargs() {
        rewriteRun(
          //language=java
          java(
            """
              import java.util.Arrays;
              import java.util.List;

              class Test {
                  void method() {
                      List<String> list = Arrays.asList("a", "b", "c");
                  }
              }
              """
          )
        );
    }

    @Test
    void doNotChangeArrayVariable() {
        rewriteRun(
          //language=java
          java(
            """
              import java.util.Arrays;
              import java.util.List;

              class Test {
                  void method(String[] args) {
                      List<String> list = Arrays.asList(args);
                  }
              }
              """
          )
        );
    }

    @Test
    void simplifyNestedMethodCall() {
        rewriteRun(
          //language=java
          java(
            """
              import java.util.Arrays;
              import java.util.List;

              class Test {
                  void method() {
                      process(Arrays.asList(new String[]{"a", "b"}));
                  }

                  void process(List<String> list) {
                  }
              }
              """,
            """
              import java.util.Arrays;
              import java.util.List;

              class Test {
                  void method() {
                      process(Arrays.asList("a", "b"));
                  }

                  void process(List<String> list) {
                  }
              }
              """
          )
        );
    }

    @Test
    void preserveComments() {
        rewriteRun(
          //language=java
          java(
            """
              import java.util.Arrays;
              import java.util.List;

              class Test {
                  void method() {
                      List<String> list = Arrays.asList(new String[]{
                          "a", // first
                          "b", // second
                          "c"  // third
                      });
                  }
              }
              """,
            """
              import java.util.Arrays;
              import java.util.List;

              class Test {
                  void method() {
                      List<String> list = Arrays.asList(
                          "a", // first
                          "b", // second
                          "c");
                  }
              }
              """
          )
        );
    }

    @Test
    void doNotChangeMultiDimensionalArrayWithInitializer() {
        rewriteRun(
          //language=java
          java(
            """
              import java.util.Arrays;
              import java.util.List;

              class Test {
                  void method() {
                      List<String[]> list = Arrays.asList(new String[][]{{"a", "b"}, {"c", "d"}});
                  }
              }
              """
          )
        );
    }

    @Test
    void doNotChangeMultiDimensionalArrayWithExplicitDimensions() {
        rewriteRun(
          //language=java
          java(
            """
              import java.util.Arrays;
              import java.util.List;

              class Test {
                  void method() {
                      List<String[]> list = Arrays.asList(new String[2][3]);
                  }
              }
              """
          )
        );
    }
}
