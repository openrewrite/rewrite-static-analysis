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
import org.openrewrite.test.RecipeSpec;
import org.openrewrite.test.RewriteTest;

import static org.openrewrite.java.Assertions.java;

class NewStringBuilderBufferWithCharArgumentTest implements RewriteTest {
    @Override
    public void defaults(RecipeSpec spec) {
        spec.recipe(new NewStringBuilderBufferWithCharArgument());
    }

    @DocumentExample
    @SuppressWarnings("NewStringBufferWithCharArgument")
    @Test
    void charIsConstructorArg() {
        rewriteRun(
          //language=java
          java(
            """
              class A {
                  StringBuffer buffer = new StringBuffer('a');
                  StringBuilder builder = new StringBuilder('a');
                  char notALiteral = 'c';
                  StringBuffer buffer2 = new StringBuffer(notALiteral);
              }
              """,
            """
              class A {
                  StringBuffer buffer = new StringBuffer("a");
                  StringBuilder builder = new StringBuilder("a");
                  char notALiteral = 'c';
                  StringBuffer buffer2 = new StringBuffer(String.valueOf(notALiteral));
              }
              """
          )
        );
    }

    @Test
    void validStringArg() {
        rewriteRun(
          //language=java
          java(
            """
              class A {
                  StringBuffer buffer = new StringBuffer("a");
                  StringBuilder builder = new StringBuilder("a");
              }
              """
          )
        );
    }
}
