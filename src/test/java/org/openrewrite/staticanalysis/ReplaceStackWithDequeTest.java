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

import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.openrewrite.DocumentExample;
import org.openrewrite.analysis.search.FindMethods;
import org.openrewrite.test.RecipeSpec;
import org.openrewrite.test.RewriteTest;

import static org.openrewrite.java.Assertions.java;

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

    @Disabled
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

    @Disabled
    @Test
    void dataFlow() {
        rewriteRun(
          spec -> spec.recipe(new FindMethods("java.util.Stack <constructor>(..)", false, "data")),
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
}
