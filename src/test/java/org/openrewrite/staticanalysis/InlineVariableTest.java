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

    @Test
    @SuppressWarnings("UnnecessaryLocalVariable")
    @Issue("https://github.com/openrewrite/rewrite/issues/3201")
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
}
