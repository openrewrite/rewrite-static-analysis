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

import org.junit.jupiter.api.Test;
import org.openrewrite.DocumentExample;
import org.openrewrite.Issue;
import org.openrewrite.test.RecipeSpec;
import org.openrewrite.test.RewriteTest;

import static org.openrewrite.java.Assertions.java;

@SuppressWarnings({"ConstantConditions", "FunctionName", "PointlessBooleanExpression", "StatementWithEmptyBody", "LoopStatementThatDoesntLoop", "InfiniteLoopStatement", "DuplicateCondition"})
class SimplifyConstantIfBranchExecutionTest implements RewriteTest {
    @Override
    public void defaults(RecipeSpec spec) {
        spec.recipe(new SimplifyConstantIfBranchExecution());
    }

    @Test
    void doNotChangeNonIf() {
        rewriteRun(
          //language=java
          java(
            """
              public class A {
                  public void test() {
                      boolean b = true;
                      if (!b) {
                          System.out.println("hello");
                      }
                  }
              }
              """
          )
        );
    }

    @DocumentExample
    @Test
    void simplifyConstantIfTrue() {
        rewriteRun(
          //language=java
          java(
            """
              public class A {
                  public void test() {
                      if (true) {
                          System.out.println("hello");
                      }
                  }
              }
              """,
            """
              public class A {
                  public void test() {
                      System.out.println("hello");
                  }
              }
              """
          )
        );
    }

    @Test
    void simplifyConstantIfTrueInParens() {
        rewriteRun(
          //language=java
          java(
            """
              public class A {
                  public void test() {
                      if ((true)) {
                          System.out.println("hello");
                      }
                  }
              }
              """,
            """
              public class A {
                  public void test() {
                      System.out.println("hello");
                  }
              }
              """
          )
        );
    }

    @Test
    void simplifyConstantIfNotFalse() {
        rewriteRun(
          //language=java
          java(
            """
              public class A {
                  public void test() {
                      if (!false) {
                          System.out.println("hello");
                      }
                  }
              }
              """,
            """
              public class A {
                  public void test() {
                      System.out.println("hello");
                  }
              }
              """
          )
        );
    }

    @Test
    @SuppressWarnings("DuplicateCondition")
    void simplifyConstantIfTrueOrTrue() {
        rewriteRun(
          //language=java
          java(
            """
              public class A {
                  public void test() {
                      if (true || true) {
                          System.out.println("hello");
                      }
                  }
              }
              """,
            """
              public class A {
                  public void test() {
                      System.out.println("hello");
                  }
              }
              """
          )
        );
    }

    @Test
    void simplifyConstantIfFalse() {
        rewriteRun(
          //language=java
          java(
            """
              public class A {
                  public void test() {
                      if (false) {
                          System.out.println("hello");
                      }
                  }
              }
              """,
            """
              public class A {
                  public void test() {
                  }
              }
              """
          )
        );
    }

    @Test
    void simplifyConstantIfTrueElse() {
        rewriteRun(
          //language=java
          java(
            """
              public class A {
                  public void test() {
                      if (true) {
                      } else {
                          System.out.println("hello");
                      }
                  }
              }
              """,
            """
              public class A {
                  public void test() {
                  }
              }
              """
          )
        );
    }

    @Test
    void simplifyConstantIfFalseElse() {
        rewriteRun(
          //language=java
          java(
            """
              public class A {
                  public void test() {
                      if (false) {
                      } else {
                          System.out.println("hello");
                      }
                  }
              }
              """,
            """
              public class A {
                  public void test() {
                      System.out.println("hello");
                  }
              }
              """
          )
        );
    }

    @Test
    void simplifyConstantIfTrueNoBlock() {
        rewriteRun(
          //language=java
          java(
            """
              public class A {
                  public void test() {
                      if (true) System.out.println("hello");
                  }
              }
              """,
            """
              public class A {
                  public void test() {
                      System.out.println("hello");
                  }
              }
              """
          )
        );
    }

    @Test
    void simplifyConstantIfFalseNoBlock() {
        rewriteRun(
          //language=java
          java(
            """
              public class A {
                  public void test() {
                      if (false) System.out.println("hello");
                  }
              }
              """,
            """
              public class A {
                  public void test() {
                  }
              }
              """
          )
        );
    }

    @Test
    void simplifyConstantIfTrueEmptyBlock() {
        rewriteRun(
          //language=java
          java(
            """
              public class A {
                  public void test() {
                      if (true) {}
                  }
              }
              """,
            """
              public class A {
                  public void test() {
                  }
              }
              """
          )
        );
    }

    @Test
    void simplifyConstantIfFalseEmptyBlock() {
        rewriteRun(
          //language=java
          java(
            """
              public class A {
                  public void test() {
                      if (false) {}
                  }
              }
              """,
            """
              public class A {
                  public void test() {
                  }
              }
              """
          )
        );
    }

    @Test
    void simplifyConstantIfTrueElseIf() {
        rewriteRun(
          //language=java
          java(
            """
              public class A {
                  public void test(boolean a) {
                      if (true) {
                          System.out.println("hello");
                      } else if (a) {
                          System.out.println("goodbye");
                      }
                  }
              }
              """,
            """
              public class A {
                  public void test(boolean a) {
                      System.out.println("hello");
                  }
              }
              """
          )
        );
    }

    @Test
    void simplifyConstantIfFalseElseIf() {
        rewriteRun(
          //language=java
          java(
            """
              public class A {
                  public void test(boolean a) {
                      if (false) {
                          System.out.println("hello");
                      } else if (a) {
                          System.out.println("goodbye");
                      }
                  }
              }
              """,
            """
              public class A {
                  public void test(boolean a) {
                      if (a) {
                          System.out.println("goodbye");
                      }
                  }
              }
              """
          )
        );
    }

    @Test
    void simplifyConstantIfTrueElseIfFalse() {
        rewriteRun(
          //language=java
          java(
            """
              public class A {
                  public void test() {
                      if (true) {
                      } else if (false) {
                          System.out.println("hello");
                      }
                  }
              }
              """,
            """
              public class A {
                  public void test() {
                  }
              }
              """
          )
        );
    }

    @Test
    void simplifyConstantIfFalseElseIfTrue() {
        rewriteRun(
          //language=java
          java(
            """
              public class A {
                  public void test() {
                      if (false) {
                      } else if (true) {
                          System.out.println("hello");
                      }
                  }
              }
              """,
            """
              public class A {
                  public void test() {
                      System.out.println("hello");
                  }
              }
              """
          )
        );
    }

    @Test
    void simplifyConstantIfTrueElseIfFalseNoBlock() {
        rewriteRun(
          //language=java
          java(
            """
              public class A {
                  public void test() {
                      if (true) System.out.println("hello");
                      else if (false) System.out.println("goodbye");
                  }
              }
              """,
            """
              public class A {
                  public void test() {
                      System.out.println("hello");
                  }
              }
              """
          )
        );
    }

    @Test
    void simplifyConstantIfFalseElseIfNoBlock() {
        rewriteRun(
          //language=java
          java(
            """
              public class A {
                  public void test() {
                      if (false) System.out.println("hello");
                      else if (true) System.out.println("goodbye");
                  }
              }
              """,
            """
              public class A {
                  public void test() {
                      System.out.println("goodbye");
                  }
              }
              """
          )
        );
    }

    @Test
    void simplifyConstantIfTrueElseIfFalseEmptyBlock() {
        rewriteRun(
          //language=java
          java(
            """
              public class A {
                  public void test() {
                      if (true) {}
                      else if (false) {}
                  }
              }
              """,
            """
              public class A {
                  public void test() {
                  }
              }
              """
          )
        );
    }

    @Test
    void simplifyConstantIfFalseElseIfTrueEmptyBlock() {
        rewriteRun(
          //language=java
          java(
            """
              public class A {
                  public void test() {
                      if (false) {}
                      else if (true) {}
                  }
              }
              """,
            """
              public class A {
                  public void test() {
                  }
              }
              """
          )
        );
    }

    @Test
    void simplifyConstantIfVariableElseIfTrueEmptyBlock() {
        rewriteRun(
          //language=java
          java(
            """
              public class A {
                  public void test(boolean a) {
                      if (a) {
                          System.out.println("hello");
                      } else if (true) {
                      }
                  }
              }
              """,
            """
              public class A {
                  public void test(boolean a) {
                      if (a) {
                          System.out.println("hello");
                      }
                  }
              }
              """
          )
        );
    }

    @Test
    void simplifyConstantIfVariableElseIfTruePrint() {
        rewriteRun(
          //language=java
          java(
            """
              public class A {
                  public void test(boolean a) {
                      if (a) {
                          System.out.println("hello");
                      } else if (true) {
                          System.out.println("goodbye");
                      }
                  }
              }
              """,
            """
              public class A {
                  public void test(boolean a) {
                      if (a) {
                          System.out.println("hello");
                      } else {
                          System.out.println("goodbye");
                      }
                  }
              }
              """
          )
        );
    }

    @Test
    void simplifyConstantIfVariableElseIfFalseEmptyBlock() {
        rewriteRun(
          //language=java
          java(
            """
              public class A {
                  public void test(boolean a) {
                      if (a) {
                          System.out.println("hello");
                      } else if (false) {
                      }
                  }
              }
              """,
            """
              public class A {
                  public void test(boolean a) {
                      if (a) {
                          System.out.println("hello");
                      }
                  }
              }
              """
          )
        );
    }

    @SuppressWarnings("InfiniteLoopStatement")
    @Test
    void simplifyConstantIfFalseElseWhileTrueEmptyBlock() {
        rewriteRun(
          //language=java
          java(
            """
              public class A {
                  public void test() {
                      if (false) {}
                      else while (true) {
                          System.out.println("hello");
                      }
                  }
              }
              """,
            """
              public class A {
                  public void test() {
                      while (true) {
                          System.out.println("hello");
                      }
                  }
              }
              """
          )
        );
    }

    @Test
    void doNotFormatCodeOutsideRemovedBlock() {
        rewriteRun(
          //language=java
          java(
            """
              public class A {
                  public void test() {
                      int[] a = new int[] { 1, 2, 3 };
                      int[] b = new int[] {4,5,6};
                      if (true) {
                          System.out.println("hello");
                      }
                      int[] c = new int[] { 1, 2, 3 };
                      int[] d = new int[] {4,5,6};
                  }
              }
              """,
            """
              public class A {
                  public void test() {
                      int[] a = new int[] { 1, 2, 3 };
                      int[] b = new int[] {4,5,6};
                      System.out.println("hello");
                      int[] c = new int[] { 1, 2, 3 };
                      int[] d = new int[] {4,5,6};
                  }
              }
              """
          )
        );
    }

    @Test
    void doesNotRemoveWhenReturnInIfBlock() {
        rewriteRun(
          //language=java
          java(
            """
              public class A {
                  public void test() {
                      if (true) {
                          System.out.println("hello");
                          return;
                      }
                      System.out.println("goodbye");
                  }
              }
              """
          )
        );
    }

    @Test
    void doesNotRemoveWhenThrowsInIfBlock() {
        rewriteRun(
          //language=java
          java(
            """
              public class A {
                  public void test() {
                      if (true) {
                          System.out.println("hello");
                          throw new RuntimeException();
                      }
                      System.out.println("goodbye");
                  }
              }
              """
          )
        );
    }

    @Test
    void doesNotRemoveWhenBreakInIfBlockWithinWhile() {
        rewriteRun(
          //language=java
          java(
            """
              public class A {
                  public void test() {
                      while (true){
                          if (true) {
                              System.out.println("hello");
                              break;
                          }
                          System.out.println("goodbye");
                      }
                      System.out.println("goodbye");
                  }
              }
              """
          )
        );
    }

    @Test
    void doesNotRemoveWhenContinueInIfBlockWithinWhile() {
        rewriteRun(
          //language=java
          java(
            """
              public class A {
                  public void test() {
                      while (true) {
                          if (true) {
                              System.out.println("hello");
                              continue;
                          }
                          System.out.println("goodbye");
                      }
                      System.out.println("goodbye");
                  }
              }
              """
          )
        );
    }

    @Test
    void binaryOrIsAlwaysFalse() {
        rewriteRun(
          //language=java
          java(
            """
              public class A {
                  void test() {
                      if (!true || !true) {
                          throw new RuntimeException();
                      }
                  }
              }
              """,
            """
              public class A {
                  void test() {
                  }
              }
              """
          )
        );
    }

    @Test
    void negatedTrueTrue() {
        rewriteRun(
          //language=java
          java(
            """
              public class A {
                  void test() {
                      if (!(true && true)) {
                          throw new RuntimeException();
                      }
                  }
              }
              """,
            """
              public class A {
                  void test() {
                  }
              }
              """
          )
        );
    }

    @Test
    @Issue("https://github.com/openrewrite/rewrite-static-analysis/issues/89")
    void preserveComments() {
        rewriteRun(
          //language=java
          java(
            """
              public class A {
                  void test() {
                      // if comment
                      if (true) {
                          // statement comment
                          System.out.println("hello");
                      }
                  }
              }
              """,
            """
              public class A {
                  void test() {
                      // if comment
                      // statement comment
                      System.out.println("hello");
                  }
              }
              """
          )
        );
    }
}
