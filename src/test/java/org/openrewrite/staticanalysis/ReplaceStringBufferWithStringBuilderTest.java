/*
 * Copyright 2026 the original author or authors.
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

class ReplaceStringBufferWithStringBuilderTest implements RewriteTest {
    @Override
    public void defaults(RecipeSpec spec) {
        spec.recipe(new ReplaceStringBufferWithStringBuilder());
    }

    @DocumentExample
    @Test
    void replaceConfinedStringBuffer() {
        rewriteRun(
          //language=java
          java(
            """
              class Test {
                  String test() {
                      StringBuffer sb = new StringBuffer();
                      sb.append("a").append("b");
                      return sb.toString();
                  }
              }
              """,
            """
              class Test {
                  String test() {
                      StringBuilder sb = new StringBuilder();
                      sb.append("a").append("b");
                      return sb.toString();
                  }
              }
              """
          )
        );
    }

    @Test
    void replaceWithCapacityAndInitialValue() {
        rewriteRun(
          //language=java
          java(
            """
              class Test {
                  String test() {
                      StringBuffer sb = new StringBuffer("start");
                      sb.append("more");
                      return sb.substring(0);
                  }
              }
              """,
            """
              class Test {
                  String test() {
                      StringBuilder sb = new StringBuilder("start");
                      sb.append("more");
                      return sb.substring(0);
                  }
              }
              """
          )
        );
    }

    @Test
    void replaceChainedAppendAssignedToLocal() {
        // Exercises method-type return re-typing: the inner `append` must return `StringBuilder`
        // so the outer `append` and the assignment target resolve without any lingering `StringBuffer`.
        rewriteRun(
          //language=java
          java(
            """
              class Test {
                  int test() {
                      StringBuffer sb = new StringBuffer();
                      CharSequence cs = sb.append("a").append("b");
                      return sb.length();
                  }
              }
              """,
            """
              class Test {
                  int test() {
                      StringBuilder sb = new StringBuilder();
                      CharSequence cs = sb.append("a").append("b");
                      return sb.length();
                  }
              }
              """
          )
        );
    }

    @Test
    void replaceWhenCapturedInLocalLambda() {
        rewriteRun(
          //language=java
          java(
            """
              import java.util.List;

              class Test {
                  String test(List<String> items) {
                      StringBuffer sb = new StringBuffer();
                      items.forEach(x -> sb.append(x));
                      return sb.toString();
                  }
              }
              """,
            """
              import java.util.List;

              class Test {
                  String test(List<String> items) {
                      StringBuilder sb = new StringBuilder();
                      items.forEach(x -> sb.append(x));
                      return sb.toString();
                  }
              }
              """
          )
        );
    }

    @Test
    void doNotReplaceWhenCapturedInLambdaThatEscapesToThread() {
        // Data flow tracks the buffer through the capturing lambda: because `r` is handed to a
        // `Thread`, the buffer may be mutated concurrently, so the synchronization is not redundant.
        rewriteRun(
          //language=java
          java(
            """
              class Test {
                  void test() {
                      StringBuffer sb = new StringBuffer();
                      Runnable r = () -> sb.append("x");
                      new Thread(r).start();
                  }
              }
              """
          )
        );
    }

    @Test
    void doNotReplaceWhenReturned() {
        rewriteRun(
          //language=java
          java(
            """
              class Test {
                  StringBuffer test() {
                      StringBuffer sb = new StringBuffer();
                      sb.append("a");
                      return sb;
                  }
              }
              """
          )
        );
    }

    @Test
    void doNotReplaceWhenAssignedToField() {
        rewriteRun(
          //language=java
          java(
            """
              class Test {
                  StringBuffer field;

                  void test() {
                      StringBuffer sb = new StringBuffer();
                      this.field = sb;
                  }
              }
              """
          )
        );
    }

    @Test
    void doNotReplaceWhenPassedAsArgument() {
        rewriteRun(
          //language=java
          java(
            """
              class Test {
                  void consume(StringBuffer other) {
                  }

                  void test() {
                      StringBuffer sb = new StringBuffer();
                      consume(sb);
                  }
              }
              """
          )
        );
    }

    @Test
    void doNotReplaceField() {
        rewriteRun(
          //language=java
          java(
            """
              class Test {
                  StringBuffer field = new StringBuffer();
              }
              """
          )
        );
    }
}
