/*
 * Copyright 2025 the original author or authors.
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
import static org.openrewrite.java.Assertions.version;

class CombineMergeableIfStatementsTest implements RewriteTest {

    @Override
    public void defaults(RecipeSpec spec) {
        spec.recipe(new CombineMergeableIfStatements());
    }

    @DocumentExample
    @Test
    void combineMergeableIfStatements() {
        rewriteRun(
          // language=java
          java(
            """
              class A {
                  void a(boolean condition1, boolean condition2) {
                      if (condition1) {
                          if (condition2) {
                              System.out.println("OK");
                          }
                      }
                  }
              }
              """,
            """
              class A {
                  void a(boolean condition1, boolean condition2) {
                      if (condition1 && condition2) {
                          System.out.println("OK");
                      }
                  }
              }
              """
          )
        );
    }

    @Test
    void simplifyWithPatternMatchingForInstanceOf() {
        rewriteRun(
          spec -> spec
            .recipes(new InstanceOfPatternMatch(), new CombineMergeableIfStatements())
            .allSources(sourceSpec -> version(sourceSpec, 17)),
          // language=java
          java(
            """
              class A {
                  void a(Object o) {
                      if (o instanceof String) {
                          String s = (String) o;
                          if (s.isEmpty()) {
                              System.out.println("OK");
                          }
                      }
                  }
              }
              """,
            """
              class A {
                  void a(Object o) {
                      if (o instanceof String s && s.isEmpty()) {
                          System.out.println("OK");
                      }
                  }
              }
              """
          )
        );

    }

    @Test
    void simplifyWithMultiplePatternMatchingForInstanceOf() {
        // This test doesn't fully simplify but could with an 'Inline Local Variable Used Once' recipe
        rewriteRun(
          spec -> spec
            .recipes(new InstanceOfPatternMatch(), new CombineMergeableIfStatements())
            .allSources(sourceSpec -> version(sourceSpec, 17)),
          // language=java
          java(
            """
              import java.util.List;

              class A {
                  void a(Object o1) {
                      if (o1 instanceof List<?>) {
                          List<?> list = (List<?>) o1;
                          if (!list.isEmpty()) {
                              Object o2 = list.get(0);
                              if (o2 instanceof String) {
                                  String s = (String) o2;
                                  if (s.isEmpty()) {
                                      System.out.println("OK");
                                  }
                              }
                          }
                      }
                  }
              }
              """,
            """
              import java.util.List;

              class A {
                  void a(Object o1) {
                      if (o1 instanceof List<?> list && !list.isEmpty()) {
                          Object o2 = list.get(0);
                          if (o2 instanceof String s && s.isEmpty()) {
                              System.out.println("OK");
                          }
                      }
                  }
              }
              """
          )
        );
    }

    @Test
    void combineWithoutBlocks() {
        rewriteRun(
          // language=java
          java(
            """
              class A {
                  void a(boolean condition1, boolean condition2) {
                      if (condition1)
                          if (condition2)
                              System.out.println("OK");
                  }
              }
              """,
            """
              class A {
                  void a(boolean condition1, boolean condition2) {
                      if (condition1 && condition2)
                          System.out.println("OK");
                  }
              }
              """
          )
        );
    }

    @Test
    void combineSeveralNestedIfs() {
        rewriteRun(
          // language=java
          java(
            """
              class A {
                  void a(boolean b1, boolean b2, boolean b3, boolean b4, boolean b5, boolean b6) {
                      if (b1) {
                          if (b2) {
                              if (b3) {
                                  if (b4) {
                                      if (b5) {
                                          if (b6) {
                                              System.out.println("OK");
                                          }
                                      }
                                  }
                              }
                          }
                      }
                  }
              }
              """,
            """
              class A {
                  void a(boolean b1, boolean b2, boolean b3, boolean b4, boolean b5, boolean b6) {
                      if (b1 && b2 && b3 && b4 && b5 && b6) {
                          System.out.println("OK");
                      }
                  }
              }
              """
          )
        );
    }

    @Test
    void noSimplificationWhenOuterIfHasElsePart() {
        rewriteRun(
          // language=java
          java(
            """
              class A {
                  void a(boolean condition1, boolean condition2) {
                      if (condition1) {
                          if (condition2) {
                              System.out.println("OK");
                          }
                      } else {
                          System.out.println("KO");
                      }
                  }
              }
              """
          )
        );
    }

    @Test
    void noSimplificationWhenOuterIfHasEmptyBlockAsElsePart() {
        rewriteRun(
          // language=java
          java(
            """
              class A {
                  void a(boolean condition1, boolean condition2) {
                      if (condition1) {
                          if (condition2) {
                              System.out.println("OK");
                          }
                      } else {
                      }
                  }
              }
              """
          )
        );
    }

    @Test
    void noSimplificationWhenOuterIfHasEmptyStatementAsElsePart() {
        rewriteRun(
          // language=java
          java(
            """
              class A {
                  void a(boolean condition1, boolean condition2) {
                      if (condition1) {
                          if (condition2) {
                              System.out.println("OK");
                          }
                      } else;
                  }
              }
              """
          )
        );
    }

    @Test
    void noSimplificationWhenOuterIfHasOneStatementInThenPartButIsNotIf() {
        rewriteRun(
          // language=java
          java(
            """
              class A {
                  void a(boolean condition1, boolean condition2) {
                      if (condition1) {
                          System.out.println("KO");
                      }
                  }
              }
              """
          )
        );
    }

    @Test
    void noSimplificationWhenOuterIfHasOneStatementWithoutBlockInThenPartButIsNotIf() {
        rewriteRun(
          // language=java
          java(
            """
              class A {
                  void a(boolean condition1, boolean condition2) {
                      if (condition1)
                          System.out.println("KO");
                  }
              }
              """
          )
        );
    }

    @Test
    void noSimplificationWhenOuterIfHasTwoStatementsInThenPart() {
        rewriteRun(
          // language=java
          java(
            """
              class A {
                  void a(boolean condition1, boolean condition2) {
                      if (condition1) {
                          if (condition2) {
                              System.out.println("OK");
                          }
                          System.out.println("KO");
                      }
                  }
              }
              """
          )
        );
    }

    @Test
    void noSimplificationWhenInnerIfHasElsePart() {
        rewriteRun(
          // language=java
          java(
            """
              class A {
                  void a(boolean condition1, boolean condition2) {
                      if (condition1) {
                          if (condition2) {
                              System.out.println("OK");
                          } else {
                              System.out.println("KO");
                          }
                      }
                  }
              }
              """
          )
        );
    }

    @Test
    void noSimplificationWhenInnerIfHasEmptyBlockAsElsePart() {
        rewriteRun(
          // language=java
          java(
            """
              class A {
                  void a(boolean condition1, boolean condition2) {
                      if (condition1) {
                          if (condition2) {
                              System.out.println("OK");
                          } else {
                          }
                      }
                  }
              }
              """
          )
        );
    }

    @Test
    void noSimplificationWhenInnerIfHasEmptyStatementAsElsePart() {
        rewriteRun(
          // language=java
          java(
            """
              class A {
                  void a(boolean condition1, boolean condition2) {
                      if (condition1) {
                          if (condition2) {
                              System.out.println("OK");
                          } else;
                      }
                  }
              }
              """
          )
        );
    }

    @Test
    void combineMergeableIfStatementsWithComments() {
        rewriteRun(
          // language=java
          java(
            """
              class A {
                  void a(boolean condition1, boolean condition2) {
                      // Comment -1
                      if (condition1) /* Comment 0 */ { // Comment 1
                          // Comment 2
                          if (condition2) /* Comment 3 */ { // Comment 4
                              System.out.println("OK");
                          }
                      }
                  }
              }
              """,
            """
              class A {
                  void a(boolean condition1, boolean condition2) {
                      // Comment -1
                      /* Comment 0 */ // Comment 1
                      // Comment 2
                      if (condition1 && condition2) /* Comment 3 */ { // Comment 4
                          System.out.println("OK");
                      }
                  }
              }
              """
          )
        );
    }

    @Test
    void combineBinaryConditions() {
        rewriteRun(
          // language=java
          java(
            """
              class A {
                  void a(boolean condition1, boolean condition2, boolean condition3) {
                      if (condition1) {
                          if (condition2 || condition3) {
                              System.out.println("OK");
                          }
                      }
                  }
              }
              """,
            """
              class A {
                  void a(boolean condition1, boolean condition2, boolean condition3) {
                      if (condition1 && (condition2 || condition3)) {
                          System.out.println("OK");
                      }
                  }
              }
              """
          )
        );
    }
}
