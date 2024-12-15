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
import org.openrewrite.Tree;
import org.openrewrite.java.JavaParser;
import org.openrewrite.java.style.DefaultComesLastStyle;
import org.openrewrite.style.NamedStyles;
import org.openrewrite.test.RecipeSpec;
import org.openrewrite.test.RewriteTest;
import org.openrewrite.test.SourceSpec;

import static java.util.Collections.emptySet;
import static java.util.Collections.singletonList;
import static org.openrewrite.java.Assertions.java;

@SuppressWarnings({"ConstantConditions", "EnhancedSwitchMigration", "SwitchStatementWithTooFewBranches"})
class DefaultComesLastTest implements RewriteTest {

    @Override
    public void defaults(RecipeSpec spec) {
        spec.recipe(new DefaultComesLast());
    }

    @DocumentExample
    @Test
    void moveDefaultToLastAlongWithItsStatementsAndAddBreakIfNecessary() {
        rewriteRun(
          //language=java
          java(
            """
              class Test {
                  int n;
                  {
                      switch (n) {
                          case 1:
                              break;
                          case 2:
                              break;
                          default:
                              System.out.println("default");
                              break;
                          case 3:
                              System.out.println("case3");
                      }
                  }
              }
              """,
            """
              class Test {
                  int n;
                  {
                      switch (n) {
                          case 1:
                              break;
                          case 2:
                              break;
                          case 3:
                              System.out.println("case3");
                              break;
                          default:
                              System.out.println("default");
                      }
                  }
              }
              """
          )
        );
    }

    @Test
    void moveDefaultToLastWhenSharedWithAnotherCaseStatement() {
        rewriteRun(
          //language=java
          java(
            """
              class Test {
                  int n;
                  {
                      switch (n) {
                          case 1:
                              break;
                          case 2:
                              break;
                          case 3:
                          default:
                              break;
                          case 4:
                          case 5:
                      }
                  }
              }
              """,
            """
              class Test {
                  int n;
                  {
                      switch (n) {
                          case 1:
                              break;
                          case 2:
                              break;
                          case 4:
                          case 5:
                              break;
                          case 3:
                          default:
                      }
                  }
              }
              """
          )
        );
    }

    @Test
    void skipIfLastAndSharedWithCase() {
        rewriteRun(
          spec -> spec.parser(
            JavaParser.fromJavaVersion().styles(singletonList(new NamedStyles(
              Tree.randomId(), "test", "test", "test", emptySet(),
              singletonList(new DefaultComesLastStyle(true)))))
          ),
          //language=java
          java(
            """
              class Test {
                  int n;
                  {
                      switch (n) {
                          case 1:
                              break;
                          case 2:
                          default:
                              break;
                          case 3:
                              break;
                      }
                  }
              }
              """
          )
        );
    }

    @Test
    void defaultIsLastAndThrows() {
        rewriteRun(
          //language=java
          java(
            """
              class Test {
                  int n;
                  {
                      switch (n) {
                          case 1:
                              break;
                          default:
                              throw new RuntimeException("unexpected value");
                      }
                  }
              }
              """
          )
        );
    }

    @Test
    void defaultIsLastAndReturnsNonVoid() {
        rewriteRun(
          //language=java
          java(
            """
              class Test {
                  public int foo(int n) {
                      switch (n) {
                          case 1:
                              return 1;
                          default:
                              return 2;
                      }
                  }
              }
              """
          )
        );
    }

    @Test
    void dontAddBreaksIfCasesArentMoving() {
        rewriteRun(
          //language=java
          java(
            """
              class Test {
                  int n;
                  boolean foo() {
                      switch (n) {
                          case 1:
                          case 2:
                              System.out.println("side effect");
                          default:
                              return true;
                      }
                  }
              }
              """
          )
        );
    }

    @Test
    void dontRemoveExtraneousDefaultCaseBreaks() {
        rewriteRun(
          //language=java
          java(
            """
              class Test {
                  int n;
                  void foo() {
                      switch (n) {
                          default:
                              break;
                      }
                  }
              }
              """
          )
        );
    }

    @Test
    void allCasesGroupedWithDefault() {
        rewriteRun(
          //language=java
          java(
            """
              class Test {
                  int n;
                  boolean foo() {
                      switch (n) {
                          case 1:
                          case 2:
                          default:
                              return true;
                      }
                  }
              }
              """
          )
        );
    }

    @Test
    void defensivelySkipSwitchExpressionsForNow() {
        //language=java
        rewriteRun(
          java(
            """
              enum Product {
                  A, B, C
              }
              """,
            SourceSpec::skip
          ),
          java(
            """
              class Foo {
                  int bar(Product product) {
                      int var = 0;
                      switch (product) {
                          default -> var = 1;
                          case B -> { var = 2; }
                          case C -> { var = 3; }
                      }
                      return var;
                  }
              }
              """
          )
        );
    }
}
