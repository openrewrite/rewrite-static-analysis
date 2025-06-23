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
import org.openrewrite.Issue;
import org.openrewrite.java.JavaIsoVisitor;
import org.openrewrite.java.tree.J;
import org.openrewrite.test.RecipeSpec;
import org.openrewrite.test.RewriteTest;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.openrewrite.java.Assertions.java;

@SuppressWarnings("UnnecessarySemicolon")
class RemoveExtraSemicolonsTest implements RewriteTest {

    @Override
    public void defaults(RecipeSpec spec) {
        spec.recipe(new RemoveExtraSemicolons());
    }

    @DocumentExample
    @Test
    void emptyBlockStatements() {
        rewriteRun(
          //language=java
          java(
            """
              class Test {
                  void test() {
                      ;
                  }
              }
              """,
            """
              class Test {
                  void test() {
                  }
              }
              """
          )
        );
    }

    @Test
    void repeatedSemicolon() {
        rewriteRun(
          //language=java
          java(
            """
              class Test {
                  void test() {
                      int a = 1;;
                      int b = 2;
                      int c = 3;;;
                      int d = 4;
                      int e = 5; ;
                      int f = 6;
                  }
              }
              """,
            """
              class Test {
                  void test() {
                      int a = 1;
                      int b = 2;
                      int c = 3;
                      int d = 4;
                      int e = 5;
                      int f = 6;
                  }
              }
              """
          )
        );
    }

    @Issue("https://github.com/openrewrite/rewrite/issues/1587")
    @Test
    void enumSemicolons() {
        rewriteRun(
          //language=java
          java(
            """
              public enum FRUITS {
                  BANANA,
                  APPLE;
              }
              """,
            """
              public enum FRUITS {
                  BANANA,
                  APPLE
              }
              """
          )
        );
    }

    @Issue("https://github.com/openrewrite/rewrite/issues/1587")
    @Test
    void enumSemicolonsWithOtherStatements() {
        rewriteRun(
          //language=java
          java(
            """
              public enum FRUITS {
                  BANANA,
                  APPLE;

                  void hiFruit() {}
              }
              """
          )
        );
    }

    @Test
    void tryWithResources() {
        rewriteRun(
          //language=java
          java(
            """
              import java.io.*;
              class Test {
                  void test() {
                      try (ByteArrayInputStream b = new ByteArrayInputStream(new byte[10]);
                            Reader r = new InputStreamReader(b);) {
                      }
                  }
              }
              """,
            """
              import java.io.*;
              class Test {
                  void test() {
                      try (ByteArrayInputStream b = new ByteArrayInputStream(new byte[10]);
                            Reader r = new InputStreamReader(b)) {
                      }
                  }
              }
              """,
            spec -> spec.afterRecipe(cu -> new JavaIsoVisitor<>() {
                @Override
                public J.Try visitTry(J.Try t, Object o) {
                    List<J.Try.Resource> resources = t.getResources();
                    assertThat(resources).isNotNull();
                    assertThat(resources.getFirst().isTerminatedWithSemicolon()).isTrue();
                    assertThat(resources.get(1).isTerminatedWithSemicolon()).isFalse();
                    return t;
                }
            }.visit(cu, 0))
          )
        );
    }

    @Issue("https://github.com/openrewrite/rewrite-static-analysis/issues/99")
    @Test
    void semicolonBeforeStatement() {
        rewriteRun(
          //language=java
          java(
            """
              class Test {
                  void test() {
                      int a = 1;
                      ;int b = 2;
                  }
              }
              """,
            """
              class Test {
                  void test() {
                      int a = 1;
                      int b = 2;
                  }
              }
              """
          )
        );
    }

    @Issue("https://github.com/openrewrite/rewrite-static-analysis/issues/99")
    @Test
    void manySemicolonBeforeStatement() {
        rewriteRun(
          //language=java
          java(
            """
              class Test {
                  int test() {
                      int a = 1; //first we set a to 1
                      ;a = 2;/*then we set a to 2 */
                      a = 3;//then we set a to 3
                      ;a = 4;;;;;//then we set a to 4
                      ;a = 5;//then we set a to 5
                      a = 6;;//then we set a to 6
                      if (a == 6) { //if a is 6
                        ;a = 7;;//then if a is 6 we set a to 7
                      }
                      ;;
                      ;//next we set a to 8
                      ;a = 8;
                      return a;
                      ;
                      //we are done!
                  }
              }
              """,
            """
              class Test {
                  int test() {
                      int a = 1; //first we set a to 1
                      a = 2;/*then we set a to 2 */
                      a = 3;//then we set a to 3
                      a = 4;//then we set a to 4
                      a = 5;//then we set a to 5
                      a = 6;//then we set a to 6
                      if (a == 6) { //if a is 6
                        a = 7;//then if a is 6 we set a to 7
                      }
                      //next we set a to 8
                      a = 8;
                      return a;
                      //we are done!
                  }
              }
              """
          )
        );
    }

    @Issue("https://github.com/openrewrite/rewrite/issues/5146")
    @Test
    void noValuesJustSemicolon() {
        rewriteRun(
          java(
            """
             public enum A {
                 ;
                 public static final String X = "receipt-id";
             }
             """
          )
        );
    }
}
