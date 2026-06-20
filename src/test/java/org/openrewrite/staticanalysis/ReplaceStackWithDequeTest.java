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
import static org.openrewrite.kotlin.Assertions.kotlin;

class ReplaceStackWithDequeTest implements RewriteTest {
    @Override
    public void defaults(RecipeSpec spec) {
        spec.recipe(new ReplaceStackWithDeque());
    }

    @DocumentExample
    @Test
    void replaceStack() {
        rewriteRun(
          //language=java
          java(
            """
              import java.util.Stack;

              class Test {
                  void test() {
                      Stack<Integer> stack = new Stack<>();
                      stack.add(1);
                      stack.add(2);
                  }
              }
              """,
            """
              import java.util.ArrayDeque;
              import java.util.Deque;
              import java.util.Stack;

              class Test {
                  void test() {
                      Deque<Integer> stack = new ArrayDeque<>();
                      stack.add(1);
                      stack.add(2);
                  }
              }
              """
          )
        );
    }

    @Test
    void doNotReplaceIfReturned() {
        rewriteRun(
          //language=java
          java(
            """
              import java.util.Stack;

              class Test {
                  Stack<Integer> test() {
                      Stack<Integer> stack = new Stack<>();
                      stack.add(1);
                      stack.add(2);
                      return stack;
                  }
              }
              """
          )
        );
    }

    @Test
    void doNotFailOnAnonymousClassWithMultipleReturningMethods() {
        // Regression: ControlFlow used to throw "No current node!" while analyzing the
        // outer method's data flow because it descended into the anonymous class body
        // and treated each nested method declaration as part of the enclosing flow.
        // After the fix, dataflow correctly detects that `result` is returned, so the
        // Stack is not rewritten; the recipe completes without throwing.
        rewriteRun(
          //language=java
          java(
            """
              import java.util.Stack;

              class Test {
                  Stack<String> rows() {
                      Stack<String> result = new Stack<>();
                      Object visitor = new Object() {
                          @Override
                          public boolean equals(Object obj) {
                              return obj == this;
                          }

                          @Override
                          public String toString() {
                              return "v";
                          }
                      };
                      result.add(visitor.toString());
                      return result;
                  }
              }
              """,
            """
              import java.util.Stack;

              class Test {
                  Stack<String> rows() {
                      Stack<String> result = new Stack<>();
                      Object visitor = new Object() {
                          @Override
                          public boolean equals(Object obj) {
                              return obj == this;
                          }

                          @Override
                          public String toString() {
                              return "v";
                          }
                      };
                      result.add(visitor.toString());
                      return result;
                  }
              }
              """
          )
        );
    }

    @Test
    void doNotFailOnKotlinExpressionBodyWithBooleanOr() {
        // Regression: ControlFlow used to throw "Condition Node is not a guard!" while
        // analyzing a Kotlin expression-body function whose `||` operand was a binary
        // equality expression or a method invocation returning `kotlin.Boolean` — the
        // operand cursor wasn't recognized as a Guard because `kotlin.Boolean` isn't
        // assignable to the JVM `boolean` primitive in OpenRewrite's type system.
        rewriteRun(
          //language=kotlin
          kotlin(
            """
              import java.util.Stack

              fun isImperative(name: String?, stack: Stack<String> = Stack()): Boolean =
                  name == "Recipe" || (!stack.contains(name) && isImperative(name, stack.apply { push(name) }))
              """,
            """
              import java.util.ArrayDeque
              import java.util.Deque
              import java.util.Stack

              fun isImperative(name: String?, stack: Deque<String> = ArrayDeque()): Boolean =
                  name == "Recipe" || (!stack.contains(name) && isImperative(name, stack.apply { push(name) }))
              """
          )
        );
    }

    @Issue("https://github.com/openrewrite/rewrite-analysis/pull/95")
    @Test
    void thisAsArgumentInMethodInvocation() {
        rewriteRun(
          //language=java
          java(
            """
              import java.util.Stack;

              class Test {
                  void test(java.util.List<Test> result) {
                      Stack<Integer> stack = new Stack<>();
                      stack.add(1);
                      if (stack.isEmpty()) {
                          result.add(this);
                      }
                  }
              }
              """,
            """
              import java.util.ArrayDeque;
              import java.util.Deque;
              import java.util.Stack;

              class Test {
                  void test(java.util.List<Test> result) {
                      Deque<Integer> stack = new ArrayDeque<>();
                      stack.add(1);
                      if (stack.isEmpty()) {
                          result.add(this);
                      }
                  }
              }
              """
          )
        );
    }

}
