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

    @Test
    @Issue("https://github.com/openrewrite/rewrite-static-analysis/issues/286")
    void doNotChangeParenthesisOnly() {
        rewriteRun(
          //language=java
          java(
            """
              public class A {
                  public void test() {
                      boolean b = true;
                      if (!(b)) {
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
    void removesWhenReturnInThenBlock() {
        rewriteRun(
          //language=java
          java(
            """
              public class A {
                  public void test() {
                      System.out.println("before");
                      if (true) {
                          System.out.println("then");
                          return;
                      }
                      System.out.println("after");
                  }
              }
              """,
            """
              public class A {
                  public void test() {
                      System.out.println("before");
                      System.out.println("then");
                      return;
                  }
              }
              """
          )
        );
    }

    @Test
    void removesWhenReturnInThenNoBlock() {
        rewriteRun(
          //language=java
          java(
            """
              public class A {
                  public void test() {
                      System.out.println("before");
                      if (true) return;
                      System.out.println("after");
                  }
              }
              """,
            """
              public class A {
                  public void test() {
                      System.out.println("before");
                      return;
                  }
              }
              """
          )
        );
    }

    @Test
    void removesWhenReturnInThenBlockWithElse() {
        rewriteRun(
          //language=java
          java(
            """
              public class A {
                  public void test() {
                      System.out.println("before");
                      if (true) {
                          System.out.println("then");
                          return;
                      } else {
                        System.out.println("else");
                      }
                      System.out.println("after");
                  }
              }
              """,
            """
              public class A {
                  public void test() {
                      System.out.println("before");
                      System.out.println("then");
                      return;
                  }
              }"""
          )
        );
    }

    @Test
    void removesWhenReturnInElseBlock() {
        rewriteRun(
          //language=java
          java(
            """
              public class A {
                  public void test() {
                      System.out.println("before");
                      if (false) {
                          System.out.println("then");
                      } else {
                          System.out.println("else");
                          return;
                      }
                      System.out.println("after");
                  }
              }
              """,
            """
              public class A {
                  public void test() {
                      System.out.println("before");
                      System.out.println("else");
                      return;
                  }
              }
              """
          )
        );
    }

    @Test
    void removesWhenReturnInElseNoBlock() {
        rewriteRun(
          //language=java
          java(
            """
              public class A {
                  public void test() {
                      System.out.println("before");
                      if (false) {
                          System.out.println("then");
                      } else return;
                      System.out.println("after");
                  }
              }
              """,
            """
              public class A {
                  public void test() {
                      System.out.println("before");
                      return;
                  }
              }
              """
          )
        );
    }

    @Test
    void removesWhenThrowsInThenBlock() {
        rewriteRun(
          //language=java
          java(
            """
              public class A {
                  public void test() {
                      System.out.println("before");
                      if (true) {
                          System.out.println("then");
                          throw new RuntimeException();
                      }
                      System.out.println("after");
                  }
              }
              """,
            """
              public class A {
                  public void test() {
                      System.out.println("before");
                      System.out.println("then");
                      throw new RuntimeException();
                  }
              }
              """
          )
        );
    }

    @Test
    void removesWhenThrowsInThenBlockWithElse() {
        rewriteRun(
          //language=java
          java(
            """
              public class A {
                  public void test() {
                      System.out.println("before");
                      if (true) {
                          System.out.println("then");
                          throw new RuntimeException();
                      } else {
                          System.out.println("else");
                      }
                      System.out.println("after");
                  }
              }
              """,
            """
              public class A {
                  public void test() {
                      System.out.println("before");
                      System.out.println("then");
                      throw new RuntimeException();
                  }
              }
              """
          )
        );
    }

    @Test
    void removesWhenThrowsInElseBlock() {
        rewriteRun(
          //language=java
          java(
            """
              public class A {
                  public void test() {
                      System.out.println("before");
                      if (false) {
                          System.out.println("then");
                      } else {
                          System.out.println("else");
                          throw new RuntimeException();
                      }
                      System.out.println("after");
                  }
              }
              """,
            """
              public class A {
                  public void test() {
                      System.out.println("before");
                      System.out.println("else");
                      throw new RuntimeException();
                  }
              }
              """
          )
        );
    }

    @Test
    void removesWhenBreakInThenBlockWithinWhile() {
        rewriteRun(
          //language=java
          java(
            """
              public class A {
                  public void test() {
                      System.out.println("before while");
                      while (true) {
                          System.out.println("before if");
                          if (true) {
                              System.out.println("then");
                              break;
                          }
                          System.out.println("after if");
                      }
                      System.out.println("after while");
                  }
              }
              """,
            """
              public class A {
                  public void test() {
                      System.out.println("before while");
                      while (true) {
                          System.out.println("before if");
                          System.out.println("then");
                          break;
                      }
                      System.out.println("after while");
                  }
              }
              """
          )
        );
    }

    @Test
    void removesWhenBreakInThenBlockWithElseWithinWhile() {
        rewriteRun(
          //language=java
          java(
            """
              public class A {
                  public void test() {
                      System.out.println("before while");
                      while (true) {
                          System.out.println("before if");
                          if (true) {
                              System.out.println("then");
                              break;
                          } else {
                              System.out.println("else");
                          }
                          System.out.println("after if");
                      }
                      System.out.println("after while");
                  }
              }
              """,
            """
              public class A {
                  public void test() {
                      System.out.println("before while");
                      while (true) {
                          System.out.println("before if");
                          System.out.println("then");
                          break;
                      }
                      System.out.println("after while");
                  }
              }
              """
          )
        );
    }

    @Test
    void removesWhenBreakInElseBlockWithinWhile() {
        rewriteRun(
          //language=java
          java(
            """
              public class A {
                  public void test() {
                      System.out.println("before while");
                      while (true) {
                          System.out.println("before if");
                          if (false) {
                              System.out.println("then");
                          } else {
                              System.out.println("else");
                              break;
                          }
                          System.out.println("after if");
                      }
                      System.out.println("after while");
                  }
              }
              """,
            """
              public class A {
                  public void test() {
                      System.out.println("before while");
                      while (true) {
                          System.out.println("before if");
                          System.out.println("else");
                          break;
                      }
                      System.out.println("after while");
                  }
              }
              """
          )
        );
    }

    @Test
    void removesWhenContinueInThenBlockWithinWhile() {
        rewriteRun(
          //language=java
          java(
            """
              public class A {
                  public void test() {
                      System.out.println("before while");
                      while (true) {
                          System.out.println("before if");
                          if (true) {
                              System.out.println("then");
                              continue;
                          }
                          System.out.println("after if");
                      }
                      System.out.println("after while");
                  }
              }
              """,
            """
              public class A {
                  public void test() {
                      System.out.println("before while");
                      while (true) {
                          System.out.println("before if");
                          System.out.println("then");
                          continue;
                      }
                      System.out.println("after while");
                  }
              }
              """
          )
        );
    }

    @Test
    void removesWhenContinueInThenBlockWithElseWithinWhile() {
        rewriteRun(
          //language=java
          java(
            """
              public class A {
                  public void test() {
                      System.out.println("before while");
                      while (true) {
                          System.out.println("before if");
                          if (true) {
                              System.out.println("then");
                              continue;
                          } else {
                              System.out.println("else");
                          }
                          System.out.println("after if");
                      }
                      System.out.println("after while");
                  }
              }
              """,
            """
              public class A {
                  public void test() {
                      System.out.println("before while");
                      while (true) {
                          System.out.println("before if");
                          System.out.println("then");
                          continue;
                      }
                      System.out.println("after while");
                  }
              }
              """
          )
        );
    }

    @Test
    void removesWhenContinueInElseBlockWithinWhile() {
        rewriteRun(
          //language=java
          java(
            """
              public class A {
                  public void test() {
                      System.out.println("before while");
                      while (true) {
                          System.out.println("before if");
                          if (false) {
                              System.out.println("then");
                          } else {
                              System.out.println("else");
                              continue;
                          }
                          System.out.println("after if");
                      }
                      System.out.println("after while");
                  }
              }
              """,
            """
              public class A {
                  public void test() {
                      System.out.println("before while");
                      while (true) {
                          System.out.println("before if");
                          System.out.println("else");
                          continue;
                      }
                      System.out.println("after while");
                  }
              }
              """
          )
        );
    }

    @Test
    void removesNestedWithReturn() {
        rewriteRun(
          //language=java
          java(
            """
              public class A {
                  public void test() {
                      System.out.println("before outer if");
                      if (true) {
                          System.out.println("outer then");
                          if (true) {
                              System.out.println("inner then");
                              return;
                          }
                          System.out.println("after inner if");
                      }
                      System.out.println("after outer if");
                  }
              }
              """,
            """
              public class A {
                  public void test() {
                      System.out.println("before outer if");
                      System.out.println("outer then");
                      System.out.println("inner then");
                      return;
                  }
              }
              """
          )
        );
    }

    @Test
    void simplifyNestedWithReturnAndThrowInTryCatch() {
        rewriteRun(
          //language=java
          java(
            """
              public class A {
                  public void test() {
                      System.out.println("before outer if");
                      if (true) {
                          System.out.println("outer then");
                          if (true) {
                              try {
                                  if(true) {
                                      throw new RuntimeException("Explosion");
                                  }
                                  return;
                              } catch (Exception ex) {
                                  System.out.println("catch");
                              }
                          }
                          System.out.println("after inner if");
                      }
                      System.out.println("after outer if");
                  }
              }
              """,
            """
              public class A {
                  public void test() {
                      System.out.println("before outer if");
                      System.out.println("outer then");
                      try {
                          throw new RuntimeException("Explosion");
                      } catch (Exception ex) {
                          System.out.println("catch");
                      }
                      System.out.println("after inner if");
                      System.out.println("after outer if");
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

    @Test
    void constantTernarySimplfied() {
        rewriteRun(
          //language=java
          java(
            """
              class Test {
                  boolean trueCondition1 = true ? true : false;
                  boolean trueCondition2 = false ? false : true;
                  boolean trueCondition3 = !true ? false : true;
                  boolean trueCondition4 = !false ? true : false;
              
                  boolean falseCondition1 = true ? false : true;
                  boolean falseCondition2 = false ? true : false;
                  boolean falseCondition3 = !false ? false : true;
                  boolean falseCondition4 = !true ? true : false;
              }
              """,
            """
              class Test {
                  boolean trueCondition1 = true;
                  boolean trueCondition2 = true;
                  boolean trueCondition3 = true;
                  boolean trueCondition4 = true;
              
                  boolean falseCondition1 = false;
                  boolean falseCondition2 = false;
                  boolean falseCondition3 = false;
                  boolean falseCondition4 = false;
              }
              """
          )
        );
    }
}
