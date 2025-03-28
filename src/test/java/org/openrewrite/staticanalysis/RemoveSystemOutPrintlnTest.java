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

class RemoveSystemOutPrintlnTest implements RewriteTest {

    @Override
    public void defaults(RecipeSpec spec) {
        spec.recipe(new RemoveSystemOutPrintln());
    }

    @DocumentExample
    @Test
    void removePrintln() {
        rewriteRun(
          //language=java
          java(
            """
              class Test {
                  void test() {
                      System.out.println("Hello, world!");
                  }
              }
              """,
            """
              class Test {
                  void test() {
                  }
              }
              """
          )
        );
    }

    @Test
    void lambda() {
        rewriteRun(
          //language=java
          java(
            """
              import java.util.function.Consumer;

              class Test {
                  void test() {
                      Consumer<String> c = s -> System.out.println(s);
                  }
              }
              """,
            """
              import java.util.function.Consumer;

              class Test {
                  void test() {
                      Consumer<String> c = s -> {};
                  }
              }
              """
          )
        );
    }
}
