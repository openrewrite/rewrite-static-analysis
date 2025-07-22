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

class InlineVariableTest implements RewriteTest {
    @Override
    public void defaults(RecipeSpec spec) {
        spec.recipe(new InlineVariable());
    }

    @DocumentExample
    @SuppressWarnings({"UnnecessaryLocalVariable", "CodeBlock2Expr", "Convert2MethodRef"})
    @Test
    void inlineVariable() {
        rewriteRun(
          //language=java
          java(
            """
              import java.util.List;
              import java.util.stream.Collectors;

              class Test {
                  int test() {
                      int n = 0;
                      return n;
                  }

                  int test2() {
                      int n = 0;
                      System.out.println(n);
                      return n;
                  }

                  void test3() {}

                  void test4(String arg) throws IllegalArgumentException {
                      if (arg == null || arg.isEmpty()) {
                          IllegalArgumentException e = new IllegalArgumentException("arg should not be empty or null");
                          throw e;
                      }
                  }

                  List<String> testLambda(List<String> names) {
                      return names.stream().map(n -> {
                          String un = n.toLowerCase();
                          return un;
                      }).collect(Collectors.toList());
                  }
              }
              """,
            """
              import java.util.List;
              import java.util.stream.Collectors;

              class Test {
                  int test() {
                      return 0;
                  }

                  int test2() {
                      int n = 0;
                      System.out.println(n);
                      return n;
                  }

                  void test3() {}

                  void test4(String arg) throws IllegalArgumentException {
                      if (arg == null || arg.isEmpty()) {
                          throw new IllegalArgumentException("arg should not be empty or null");
                      }
                  }

                  List<String> testLambda(List<String> names) {
                      return names.stream().map(n -> {
                          return n.toLowerCase();
                      }).collect(Collectors.toList());
                  }
              }
              """
          )
        );
    }

    @Test
    void inlineSwitch() {
        rewriteRun(
          //language=java
          java(
            """
              import java.util.List;
              import java.util.stream.Collectors;

              class Test {
                  String test(int n) {
                      String s = switch (n) {
                          case 1 -> "one";
                          case 2 -> "two";
                          case 3 -> "three";
                          default -> "unknown";
                      };
                      return s;
                  }
              }
              """,
            """
              import java.util.List;
              import java.util.stream.Collectors;

              class Test {
                  String test(int n) {
                      return switch (n) {
                          case 1 -> "one";
                          case 2 -> "two";
                          case 3 -> "three";
                          default -> "unknown";
                      };
                  }
              }
              """
          )
        );
    }

    @SuppressWarnings("UnnecessaryLocalVariable")
    @Test
    void preserveComments() {
        rewriteRun(
          //language=java
          java(
            """
              class Test {
                  String getGreeting() {
                      // sometimes there are comments
                      // keep them
                      String s = "hello";
                      return s;
                  }

                  void test4(String arg) throws IllegalArgumentException {
                      if (arg == null || arg.isEmpty()) {
                          // some comment for the illegal argument
                          IllegalArgumentException e = new IllegalArgumentException("arg should not be empty or null");
                          throw e;
                      }
                  }
              }
              """,
            """
              class Test {
                  String getGreeting() {
                      // sometimes there are comments
                      // keep them
                      return "hello";
                  }

                  void test4(String arg) throws IllegalArgumentException {
                      if (arg == null || arg.isEmpty()) {
                          // some comment for the illegal argument
                          throw new IllegalArgumentException("arg should not be empty or null");
                      }
                  }
              }
              """
          )
        );
    }

    @Test
    void annotatedReturnIdentifier() {
        rewriteRun(
          //language=java
          java(
            """
              class Test {
                  String test() {
                      @SuppressWarnings("all")
                      String someString = (String) getSomething();
                      return someString;
                  }

                  Object getSomething() {return null;}
              }
              """
          )
        );
    }

    @Issue("https://github.com/openrewrite/rewrite/issues/3201")
    @SuppressWarnings("UnnecessaryLocalVariable")
    @Test
    void preserveComment() {
        rewriteRun(
          //language=java
          java(
            """
              class Test {
                  int test(int i1, int i2) {
                      // leading var comment
                      int result = i1 - i2; // trailing var comment
                      return result;
                  }
              }
              """,
            """
              class Test {
                  int test(int i1, int i2) {
                      // leading var comment
                      // trailing var comment
                      return i1 - i2;
                  }
              }
              """
          )
        );
    }

    @Test
    void preserveArray() {
        rewriteRun(
          //language=java
          java(
            """
              class Test {
                  int[] test() {
                      int[] arr = {1, 2, 3};
                      return arr;
                  }
              }
              """
          )
        );
    }

    @Test
    void inlineAssignmentReturn() {
        rewriteRun(
          //language=java
          java(
            """
              class Test {
                  String test() {
                      String result;
                      result = "hello";
                      return result;
                  }
              }
              """,
            """
              class Test {
                  String test() {
                      String result;
                      return "hello";
                  }
              }
              """
          )
        );
    }

    @Test
    void inlineAssignmentThrow() {
        rewriteRun(
          //language=java
          java(
            """
              class Test {
                  void test() {
                      RuntimeException e;
                      e = new RuntimeException("error");
                      throw e;
                  }
              }
              """,
            """
              class Test {
                  void test() {
                      RuntimeException e;
                      throw new RuntimeException("error");
                  }
              }
              """
          )
        );
    }

    @Test
    void inlineComplexAssignmentReturn() {
        rewriteRun(
          //language=java
          java(
            """
              class Test {
                  String test(String input) {
                      String result;
                      result = input.trim().toUpperCase();
                      return result;
                  }
              }
              """,
            """
              class Test {
                  String test(String input) {
                      String result;
                      return input.trim().toUpperCase();
                  }
              }
              """
          )
        );
    }

    @Test
    void doNotInlineFieldAssignment() {
        rewriteRun(
          //language=java
          java(
            """
              class Test {
                  private String field;

                  String test() {
                      field = "value";
                      return field;
                  }
              }
              """
          )
        );
    }


    @Test
    void preserveCommentsOnAssignment() {
        rewriteRun(
          //language=java
          java(
            """
              class Test {
                  String test() {
                      String result;
                      // important assignment
                      result = "value"; // trailing comment
                      return result;
                  }
              }
              """,
            """
              class Test {
                  String test() {
                      String result;
                      // important assignment
                      // trailing comment
                      return "value";
                  }
              }
              """
          )
        );
    }

    @Test
    void doNotInlineWhenMultipleVariables() {
        rewriteRun(
          //language=java
          java(
            """
              class Test {
                  String getString() {
                      String a = "Hello", b = "World";
                      return a;
                  }
              }
              """
          )
        );
    }

    @Test
    void inlineAssignmentToParameter() {
        rewriteRun(
          //language=java
          java(
            """
              class Test {
                  String test(String param) {
                      param = toString();
                      return param;
                  }
              }
              """,
            """
              class Test {
                  String test(String param) {
                      return toString();
                  }
              }
              """
          )
        );
    }

    @Test
    void inlineMultipleAssignmentsBeforeReturn() {
        rewriteRun(
          //language=java
          java(
            """
              class Test {
                  String test() {
                      String variable = null;
                      variable = toString();
                      return variable;
                  }
              }
              """,
            """
              class Test {
                  String test() {
                      String variable = null;
                      return toString();
                  }
              }
              """
          )
        );
    }

    @Test
    void inlineAssignmentWithMethodCall() {
        rewriteRun(
          //language=java
          java(
            """
              class Test {
                  String spy() { return "spy"; }

                  String test() {
                      String variable = spy();
                      return variable;
                  }
              }
              """,
            """
              class Test {
                  String spy() { return "spy"; }

                  String test() {
                      return spy();
                  }
              }
              """
          )
        );
    }

    @Test
    void inlineAssignmentInElseBlockWhilePreservingIfBlock() {
        rewriteRun(
          //language=java
          java(
            """
              class Test {
                  String test(boolean condition) {
                      String variable = toString();
                      if (condition) {
                          return variable;
                      } else {
                          variable = "foo";
                          return variable;
                      }
                  }
              }
              """,
            """
              class Test {
                  String test(boolean condition) {
                      String variable = toString();
                      if (condition) {
                          return variable;
                      } else {
                          return "foo";
                      }
                  }
              }
              """
          )
        );
    }

    @Test
    void inlineVariableInElseBlock() {
        rewriteRun(
          //language=java
          java(
            """
              class Test {
                  String test(boolean condition) {
                      if (condition) {
                          return "bar";
                      } else {
                          String variable = "foo";
                          return variable;
                      }
                  }
              }
              """,
            """
              class Test {
                  String test(boolean condition) {
                      if (condition) {
                          return "bar";
                      } else {
                          return "foo";
                      }
                  }
              }
              """
          )
        );
    }

    @Test
    void inlineAssignmentInTryBlock() {
        rewriteRun(
          //language=java
          java(
            """
              class Test {
                  String test() {
                      try {
                          String result = someMethod();
                          return result;
                      } catch (Exception e) {
                          return null;
                      }
                  }

                  String someMethod() throws Exception {
                      return "value";
                  }
              }
              """,
            """
              class Test {
                  String test() {
                      try {
                          return someMethod();
                      } catch (Exception e) {
                          return null;
                      }
                  }

                  String someMethod() throws Exception {
                      return "value";
                  }
              }
              """
          )
        );
    }

    @Test
    void inlineVariableWithCast() {
        rewriteRun(
          //language=java
          java(
            """
              class Test {
                  Object test() {
                      String str = (String) getObject();
                      return str;
                  }

                  Object getObject() {
                      return "string";
                  }
              }
              """,
            """
              class Test {
                  Object test() {
                      return (String) getObject();
                  }

                  Object getObject() {
                      return "string";
                  }
              }
              """
          )
        );
    }

    @Test
    void inlineVariableWithTernary() {
        rewriteRun(
          //language=java
          java(
            """
              class Test {
                  String test(boolean flag) {
                      String result = flag ? "yes" : "no";
                      return result;
                  }
              }
              """,
            """
              class Test {
                  String test(boolean flag) {
                      return flag ? "yes" : "no";
                  }
              }
              """
          )
        );
    }

    @Test
    void doNotInlineWhenVariableIsReassigned() {
        rewriteRun(
          //language=java
          java(
            """
              class Test {
                  String test(boolean condition) {
                      String result = "initial";
                      if (condition) {
                          result = "changed";
                      }
                      return result;
                  }
              }
              """
          )
        );
    }
}
