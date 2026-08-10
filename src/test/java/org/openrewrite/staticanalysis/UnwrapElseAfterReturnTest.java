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
import org.junit.jupiter.api.condition.EnabledForJreRange;
import org.junit.jupiter.api.condition.JRE;
import org.openrewrite.DocumentExample;
import org.openrewrite.test.RecipeSpec;
import org.openrewrite.test.RewriteTest;

import static org.openrewrite.java.Assertions.java;
import static org.openrewrite.java.Assertions.version;

@SuppressWarnings({"ConstantConditions", "unused"})
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
    void preserveElseIfButUnwrapFinalElse() {
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
    void preserveElseIfButUnwrapFinalElseWithMissingReturn() {
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
                      } else if ("two".equals(str)) {
                          System.out.println("two");
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
    void preserveElseIfButUnwrapFinalElseWithMixedReturnAndThrow() {
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
    void doNotUnwrapWhenElseDeclarationCollidesWithLaterLocalVariable() {
        rewriteRun(
          //language=java
          java(
            """
              class Test {
                  void plain(boolean stop) {
                      if (stop) {
                          return;
                      } else {
                          int value = 1;
                          System.out.println(value);
                      }
                      int value = 2;
                      System.out.println(value);
                  }

                  void chain(boolean first, boolean second) {
                      if (first) {
                          return;
                      } else if (second) {
                          return;
                      } else {
                          int value = 1;
                          System.out.println(value);
                      }
                      int value = 2;
                      System.out.println(value);
                  }

                  void afterThrow(boolean stop) {
                      if (stop) {
                          throw new IllegalStateException();
                      } else {
                          String value = "1";
                          System.out.println(value);
                      }
                      long value = 2;
                      System.out.println(value);
                  }
              }
              """
          )
        );
    }

    @Test
    void doNotUnwrapWhenElseDeclarationCollidesWithNestedScope() {
        rewriteRun(
          //language=java
          java(
            """
              import java.io.IOException;
              import java.io.StringReader;
              import java.util.List;

              class Test {
                  void catchVariable(boolean stop) {
                      if (stop) {
                          return;
                      } else {
                          int value = 1;
                          System.out.println(value);
                      }
                      try {
                          System.out.println("try");
                      } catch (RuntimeException value) {
                          System.out.println(value);
                      }
                  }

                  void resourceVariable(boolean stop) throws IOException {
                      if (stop) {
                          return;
                      } else {
                          int reader = 1;
                          System.out.println(reader);
                      }
                      try (StringReader reader = new StringReader("")) {
                          System.out.println(reader.read());
                      }
                  }

                  void lambdaParameter(boolean stop, List<String> values) {
                      if (stop) {
                          return;
                      } else {
                          int element = 1;
                          System.out.println(element);
                      }
                      values.forEach(element -> System.out.println(element));
                  }

                  void loopVariable(boolean stop) {
                      if (stop) {
                          return;
                      } else {
                          int index = 1;
                          System.out.println(index);
                      }
                      for (int index = 0; index < 2; index++) {
                          System.out.println(index);
                      }
                  }
              }
              """
          )
        );
    }

    @Test
    void doNotUnwrapWhenElseLocalClassCollidesWithLaterLocalClass() {
        rewriteRun(
          //language=java
          java(
            """
              class Test {
                  void foo(boolean stop) {
                      if (stop) {
                          return;
                      } else {
                          class Helper {
                          }
                          System.out.println(new Helper());
                      }
                      class Helper {
                      }
                      System.out.println(new Helper());
                  }
              }
              """
          )
        );
    }

    @Test
    void doNotUnwrapWhenElseDeclarationCollidesWithLaterPatternVariable() {
        rewriteRun(
          version(
            //language=java
            java(
              """
                class Test {
                    void foo(boolean stop, Object o) {
                        if (stop) {
                            return;
                        } else {
                            String text = "1";
                            System.out.println(text);
                        }
                        if (o instanceof String text) {
                            System.out.println(text);
                        }
                    }
                }
                """
            ), 17
          )
        );
    }

    @Test
    void doNotUnwrapWhenEscapedPatternVariableCollidesWithLaterDeclaration() {
        rewriteRun(
          version(
            //language=java
            java(
              """
                class Test {
                    void plain(Object o, boolean stop) {
                        if (stop) {
                            return;
                        } else {
                            if (!(o instanceof String s)) {
                                return;
                            }
                            System.out.println(s);
                        }
                        String s = "later";
                        System.out.println(s);
                    }

                    void chain(Object o, boolean first, boolean second) {
                        if (first) {
                            return;
                        } else if (second) {
                            return;
                        } else {
                            if (!(o instanceof String s)) {
                                return;
                            }
                            System.out.println(s);
                        }
                        String s = "later";
                        System.out.println(s);
                    }

                    void singleStatementElse(Object o, boolean stop) {
                        if (stop) {
                            return;
                        } else
                            while (!(o instanceof String s))
                                o = o.toString();
                        String s = "later";
                        System.out.println(s);
                    }
                }
                """
            ), 17
          )
        );
    }

    @Test
    void doNotUnwrapWhenElseDeclarationShadowsNameUsedLater() {
        rewriteRun(
          //language=java
          java(
            """
              class Test {
                  int value;

                  void plain(boolean stop) {
                      if (stop) {
                          return;
                      } else {
                          String value = "1";
                          System.out.println(value);
                      }
                      int doubled = value * 2;
                      System.out.println(doubled);
                  }

                  void chain(boolean first, boolean second) {
                      if (first) {
                          return;
                      } else if (second) {
                          return;
                      } else {
                          String value = "1";
                          System.out.println(value);
                      }
                      int doubled = value * 2;
                      System.out.println(doubled);
                  }

                  int sameType(boolean stop) {
                      if (stop) {
                          return -1;
                      } else {
                          int value = 1;
                          System.out.println(value);
                      }
                      return value;
                  }
              }
              """
          )
        );
    }

    @Test
    void unwrapWhenLaterUsesAreNotCapturedByHoistedNames() {
        rewriteRun(
          //language=java
          java(
            """
              class Test {
                  int value;

                  int value() {
                      return 42;
                  }

                  void qualifiedFieldUse(boolean stop) {
                      if (stop) {
                          return;
                      } else {
                          String value = "1";
                          System.out.println(value);
                      }
                      System.out.println(this.value);
                  }

                  void methodNameUse(boolean stop) {
                      if (stop) {
                          return;
                      } else {
                          String value = "1";
                          System.out.println(value);
                      }
                      System.out.println(value());
                  }
              }
              """,
            """
              class Test {
                  int value;

                  int value() {
                      return 42;
                  }

                  void qualifiedFieldUse(boolean stop) {
                      if (stop) {
                          return;
                      }
                      String value = "1";
                      System.out.println(value);
                      System.out.println(this.value);
                  }

                  void methodNameUse(boolean stop) {
                      if (stop) {
                          return;
                      }
                      String value = "1";
                      System.out.println(value);
                      System.out.println(value());
                  }
              }
              """
          )
        );
    }

    @Test
    void unwrapWhenEscapedPatternVariableDoesNotCollide() {
        rewriteRun(
          version(
            //language=java
            java(
              """
                class Test {
                    void foo(Object o, boolean stop) {
                        if (stop) {
                            return;
                        } else {
                            if (!(o instanceof String s)) {
                                return;
                            }
                            System.out.println(s);
                        }
                        System.out.println("done");
                    }
                }
                """,
              """
                class Test {
                    void foo(Object o, boolean stop) {
                        if (stop) {
                            return;
                        }
                        if (!(o instanceof String s)) {
                            return;
                        }
                        System.out.println(s);
                        System.out.println("done");
                    }
                }
                """
            ), 17
          )
        );
    }

    @EnabledForJreRange(min = JRE.JAVA_21)
    @Test
    void unwrapOnlyWhenDeconstructionPatternBindingsDoNotCollide() {
        rewriteRun(
          version(
            //language=java
            java(
              """
                class Test {
                    record Point(int x, int y) {}

                    void collides(Object o, boolean stop) {
                        if (stop) {
                            return;
                        } else {
                            if (!(o instanceof Point(int x, int y))) {
                                return;
                            }
                            System.out.println(x + y);
                        }
                        int x = 5;
                        System.out.println(x);
                    }

                    void doesNotCollide(Object o, boolean stop) {
                        if (stop) {
                            return;
                        } else {
                            if (!(o instanceof Point(int x, int y))) {
                                return;
                            }
                            System.out.println(x + y);
                        }
                        Point p = new Point(1, 2);
                        System.out.println(p);
                    }
                }
                """,
              """
                class Test {
                    record Point(int x, int y) {}

                    void collides(Object o, boolean stop) {
                        if (stop) {
                            return;
                        } else {
                            if (!(o instanceof Point(int x, int y))) {
                                return;
                            }
                            System.out.println(x + y);
                        }
                        int x = 5;
                        System.out.println(x);
                    }

                    void doesNotCollide(Object o, boolean stop) {
                        if (stop) {
                            return;
                        }
                        if (!(o instanceof Point(int x, int y))) {
                            return;
                        }
                        System.out.println(x + y);
                        Point p = new Point(1, 2);
                        System.out.println(p);
                    }
                }
                """
            ), 21
          )
        );
    }

    @Test
    void unwrapWhenElseDeclarationsDoNotCollide() {
        rewriteRun(
          //language=java
          java(
            """
              class Test {
                  void foo(boolean stop) {
                      if (stop) {
                          return;
                      } else {
                          int value = 1;
                          System.out.println(value);
                      }
                      int other = 2;
                      System.out.println(other);
                  }
              }
              """,
            """
              class Test {
                  void foo(boolean stop) {
                      if (stop) {
                          return;
                      }
                      int value = 1;
                      System.out.println(value);
                      int other = 2;
                      System.out.println(other);
                  }
              }
              """
          )
        );
    }

    @Test
    void unwrapWhenCollidingDeclarationRemainsNestedInTheElseBlock() {
        rewriteRun(
          //language=java
          java(
            """
              class Test {
                  void foo(boolean stop) {
                      if (stop) {
                          return;
                      } else {
                          for (int value = 0; value < 2; value++) {
                              System.out.println(value);
                          }
                      }
                      int value = 2;
                      System.out.println(value);
                  }
              }
              """,
            """
              class Test {
                  void foo(boolean stop) {
                      if (stop) {
                          return;
                      }
                      for (int value = 0; value < 2; value++) {
                          System.out.println(value);
                      }
                      int value = 2;
                      System.out.println(value);
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
