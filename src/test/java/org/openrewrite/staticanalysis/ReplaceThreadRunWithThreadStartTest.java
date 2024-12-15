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
import org.openrewrite.config.Environment;
import org.openrewrite.test.RecipeSpec;
import org.openrewrite.test.RewriteTest;

import static org.openrewrite.java.Assertions.java;

class ReplaceThreadRunWithThreadStartTest implements RewriteTest {

    @Override
    public void defaults(RecipeSpec spec) {
        spec.recipe(Environment.builder()
          .scanRuntimeClasspath("org.openrewrite.staticanalysis")
          .build()
          .activateRecipes("org.openrewrite.staticanalysis.ReplaceThreadRunWithThreadStart"));
    }

    @DocumentExample
    @SuppressWarnings("CallToThreadRun")
    @Test
    void replaceThreadRun() {
        rewriteRun(
          //language=java
          java(
            """
              public class A {
                  public static void main(String[] args) {
                     Runnable r = ()-> System.out.println("Hello world");
                     Thread myThread = new Thread(r);
                     myThread.run();
                  }
              }
              """,
            """
              public class A {
                  public static void main(String[] args) {
                     Runnable r = ()-> System.out.println("Hello world");
                     Thread myThread = new Thread(r);
                     myThread.start();
                  }
              }
              """
          )
        );
    }
}
