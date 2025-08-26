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

@SuppressWarnings("ConstantConditions")
class UnwrapElseAfterReturnTest implements RewriteTest {

    @Override
    public void defaults(RecipeSpec spec) {
        spec.recipe(new UnwrapElseAfterReturn());
    }

    @DocumentExample
    @Test
    void simpleIfElseWithReturn() {
        rewriteRun(
          java(
            """
              class Test {
                  int foo(boolean condition) {
                      if (condition) {
                          return 1;
                      } else {
                          return 2;
                      }
                  }
              }
              """,
            """
              class Test {
                  int foo(boolean condition) {
                      if (condition) {
                          return 1;
                      }
                      return 2;
                  }
              }
              """
          )
        );
    }

    @Test
    void ifElseWithBlocksAndReturn() {
        rewriteRun(
          java(
            """
              class Test {
                  int foo(boolean condition) {
                      if (condition) {
                          System.out.println("condition is true");
                          return 1;
                      } else {
                          System.out.println("condition is false");
                          int result = 2;
                          return result;
                      }
                  }
              }
              """,
            """
              class Test {
                  int foo(boolean condition) {
                      if (condition) {
                          System.out.println("condition is true");
                          return 1;
                      }
                      System.out.println("condition is false");
                      int result = 2;
                      return result;
                  }
              }
              """
          )
        );
    }

    @Test
    void nestedIfElse() {
        rewriteRun(
          java(
            """
              class Test {
                  int foo(int x) {
                      if (x > 0) {
                          return 1;
                      } else {
                          if (x < 0) {
                              return -1;
                          } else {
                              return 0;
                          }
                      }
                  }
              }
              """,
            """
              class Test {
                  int foo(int x) {
                      if (x > 0) {
                          return 1;
                      }
                      if (x < 0) {
                          return -1;
                      }
                      return 0;
                  }
              }
              """
          )
        );
    }

    @Test
    void ifWithoutReturnNotChanged() {
        rewriteRun(
          java(
            """
              class Test {
                  void foo(boolean condition) {
                      if (condition) {
                          System.out.println("true");
                      } else {
                          System.out.println("false");
                      }
                  }
              }
              """
          )
        );
    }

    @Test
    void ifWithReturnButNoElse() {
        rewriteRun(
          java(
            """
              class Test {
                  int foo(boolean condition) {
                      if (condition) {
                          return 1;
                      }
                      return 0;
                  }
              }
              """
          )
        );
    }

    @Test
    void multipleStatementsAfterIf() {
        rewriteRun(
          java(
            """
              class Test {
                  int foo(boolean condition) {
                      System.out.println("start");
                      if (condition) {
                          return 1;
                      } else {
                          System.out.println("else branch");
                          return 2;
                      }
                      // unreachable but should still work
                  }
              }
              """,
            """
              class Test {
                  int foo(boolean condition) {
                      System.out.println("start");
                      if (condition) {
                          return 1;
                      }
                      System.out.println("else branch");
                      return 2;
                      // unreachable but should still work
                  }
              }
              """
          )
        );
    }

    @Test
    void elseWithSingleStatement() {
        rewriteRun(
          java(
            """
              class Test {
                  String foo(boolean condition) {
                      if (condition) {
                          return "yes";
                      } else
                          return "no";
                  }
              }
              """,
            """
              class Test {
                  String foo(boolean condition) {
                      if (condition) {
                          return "yes";
                      }
                      return "no";
                  }
              }
              """
          )
        );
    }

    @Test
    void preserveComments() {
        rewriteRun(
          java(
            """
              class Test {
                  int foo(boolean condition) {
                      if (condition) {
                          // Return early for true condition
                          return 1;
                      } else {
                          // Handle false condition
                          int result = 2;
                          return result;
                      }
                  }
              }
              """,
            """
              class Test {
                  int foo(boolean condition) {
                      if (condition) {
                          // Return early for true condition
                          return 1;
                      }
                      // Handle false condition
                      int result = 2;
                      return result;
                  }
              }
              """
          )
        );
    }

    @Test
    void complexElseBlock() {
        rewriteRun(
          java(
            """
              class Test {
                  String process(String input) {
                      if (input == null) {
                          return "null";
                      } else {
                          String trimmed = input.trim();
                          if (trimmed.isEmpty()) {
                              return "empty";
                          }
                          String processed = trimmed.toUpperCase();
                          return processed;
                      }
                  }
              }
              """,
            """
              class Test {
                  String process(String input) {
                      if (input == null) {
                          return "null";
                      }
                      String trimmed = input.trim();
                      if (trimmed.isEmpty()) {
                          return "empty";
                      }
                      String processed = trimmed.toUpperCase();
                      return processed;
                  }
              }
              """
          )
        );
    }

    @Test
    void ifBlockWithoutBracesButWithReturn() {
        rewriteRun(
          java(
            """
              class Test {
                  int foo(boolean condition) {
                      if (condition)
                          return 1;
                      else {
                          return 2;
                      }
                  }
              }
              """,
            """
              class Test {
                  int foo(boolean condition) {
                      if (condition)
                          return 1;
                      return 2;
                  }
              }
              """
          )
        );
    }

    @Test
    void chainedIfElseIfElse() {
        rewriteRun(
          java(
            """
              class Test {
                  int foo(String str) {
                      if ("one".equals(str)) {
                          return 1;
                      } else if ("two".equals(str)) {
                          return 2;
                      } else if ("three".equals(str)) {
                          return 3;
                      } else {
                          return Integer.MAX_VALUE;
                      }
                  }
              }
              """,
            """
              class Test {
                  int foo(String str) {
                      if ("one".equals(str)) {
                          return 1;
                      } else if ("two".equals(str)) {
                          return 2;
                      } else if ("three".equals(str)) {
                          return 3;
                      }
                      return Integer.MAX_VALUE;
                  }
              }
              """
          )
        );
    }

    @Test
    void chainedIfElseIfElseWithMissingReturn() {
        rewriteRun(
          java(
            """
              class Test {
                  int foo(String str) {
                      if ("one".equals(str)) {
                          return 1;
                      } else if ("two".equals(str)) {
                          System.out.println("two");
                      } else if ("three".equals(str)) {
                          return 3;
                      } else {
                          return Integer.MAX_VALUE;
                      }
                  }
              }
              """,
            """
              class Test {
                  int foo(String str) {
                      if ("one".equals(str)) {
                          return 1;
                      }
                      if ("two".equals(str)) {
                          System.out.println("two");
                      } else if ("three".equals(str)) {
                          return 3;
                      } else {
                          return Integer.MAX_VALUE;
                      }
                  }
              }
              """
          )
        );
    }

    @Test
    void nestedIfWithReturnInInnerIf() {
        rewriteRun(
          java(
            """
              class Test {
                  String foo(boolean someCondition, boolean somethingRare) {
                      if (someCondition) {
                          // default logic
                          if (somethingRare) {
                              return "terminate all processing";
                          }
                      } else {
                          // non-standard logic
                          return "else branch result";
                      }
                      return "continue processing";
                  }
              }
              """
          )
        );
    }

    @Test
    void simpleIfElseWithThrow() {
        rewriteRun(
          java(
            """
              class Test {
                  void foo(boolean condition) {
                      if (condition) {
                          throw new IllegalArgumentException("Invalid condition");
                      } else {
                          System.out.println("Valid condition");
                      }
                  }
              }
              """,
            """
              class Test {
                  void foo(boolean condition) {
                      if (condition) {
                          throw new IllegalArgumentException("Invalid condition");
                      }
                      System.out.println("Valid condition");
                  }
              }
              """
          )
        );
    }

    @Test
    void ifElseWithBlocksAndThrow() {
        rewriteRun(
          java(
            """
              class Test {
                  void validateInput(String input) {
                      if (input == null) {
                          System.err.println("Null input detected");
                          throw new NullPointerException("Input cannot be null");
                      } else {
                          System.out.println("Processing input: " + input);
                          input = input.trim();
                      }
                  }
              }
              """,
            """
              class Test {
                  void validateInput(String input) {
                      if (input == null) {
                          System.err.println("Null input detected");
                          throw new NullPointerException("Input cannot be null");
                      }
                      System.out.println("Processing input: " + input);
                      input = input.trim();
                  }
              }
              """
          )
        );
    }

    @Test
    void mixedReturnAndThrow() {
        rewriteRun(
          java(
            """
              class Test {
                  String process(int value) {
                      if (value < 0) {
                          throw new IllegalArgumentException("Negative value");
                      } else if (value == 0) {
                          return "zero";
                      } else {
                          return "positive";
                      }
                  }
              }
              """,
            """
              class Test {
                  String process(int value) {
                      if (value < 0) {
                          throw new IllegalArgumentException("Negative value");
                      } else if (value == 0) {
                          return "zero";
                      }
                      return "positive";
                  }
              }
              """
          )
        );
    }

    @Test
    void throwWithoutBraces() {
        rewriteRun(
          java(
            """
              class Test {
                  void check(boolean flag) {
                      if (flag)
                          throw new RuntimeException("Flag is true");
                      else {
                          System.out.println("Flag is false");
                      }
                  }
              }
              """,
            """
              class Test {
                  void check(boolean flag) {
                      if (flag)
                          throw new RuntimeException("Flag is true");
                      System.out.println("Flag is false");
                  }
              }
              """
          )
        );
    }

    @Test
    void throwInTryCatch() {
        rewriteRun(
          java(
            """
              class Test {
                  void process(String data) {
                      try {
                          if (data == null) {
                              throw new IllegalArgumentException("Data is null");
                          } else {
                              data.toLowerCase();
                          }
                      } catch (Exception e) {
                          System.err.println("Error: " + e.getMessage());
                      }
                  }
              }
              """,
            """
              class Test {
                  void process(String data) {
                      try {
                          if (data == null) {
                              throw new IllegalArgumentException("Data is null");
                          }
                          data.toLowerCase();
                      } catch (Exception e) {
                          System.err.println("Error: " + e.getMessage());
                      }
                  }
              }
              """
          )
        );
    }

    @Test
    void commentsEverywhere() {
        rewriteRun(
          java(
            """
            class Test {
                int foo(boolean condition) {
                    if (condition) {
                        return 1; // end 1
                    } else {
                        return 2; // end 2
                    } // end else
                }
            }
            """,
            """
              class Test {
                  int foo(boolean condition) {
                      if (condition) {
                          return 1; // end 1
                      }
                      return 2; // end 2
                      // end else
                  }
              }
              """
          )
        );
    }

    @Test
    void commentsOnlyInBlocks() {
        rewriteRun(
          java(
            """
            class Test {
                int foo(boolean condition) {
                    if (condition) {
                        return 1; // end 1
                    } else {
                        return 2; // end 2
                    }
                }
            }
            """,
            """
              class Test {
                  int foo(boolean condition) {
                      if (condition) {
                          return 1; // end 1
                      }
                      return 2; // end 2
                  }
              }
              """
          )
        );
    }

    @Test
    void commentsOnlyInBlocksWithNewLine() {
        rewriteRun(
          java(
            """
            class Test {
                int foo(boolean condition) {
                    if (condition) {
                        return 1; // end 1
                    } else {
                        return 2;
                        // next line after 2
                    }
                }
            }
            """,
            """
              class Test {
                  int foo(boolean condition) {
                      if (condition) {
                          return 1; // end 1
                      }
                      return 2;
                      // next line after 2
                  }
              }
              """
          )
        );
    }
}
