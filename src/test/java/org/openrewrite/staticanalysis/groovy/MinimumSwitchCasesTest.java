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
package org.openrewrite.staticanalysis.groovy;

import org.junit.jupiter.api.Test;
import org.openrewrite.DocumentExample;
import org.openrewrite.Issue;
import org.openrewrite.staticanalysis.MinimumSwitchCases;
import org.openrewrite.test.RecipeSpec;
import org.openrewrite.test.RewriteTest;

import static org.openrewrite.groovy.Assertions.groovy;

class MinimumSwitchCasesTest implements RewriteTest {

    @Override
    public void defaults(RecipeSpec spec) {
        spec.recipe(new MinimumSwitchCases());
    }

    @DocumentExample
    @Test
    void twoCases() {
        rewriteRun(
          //language=groovy
          groovy(
            """
              def s = "prod"
              switch(s) {
                  case "prod":
                      println("prod")
                      break
                  default:
                      println("default")
                      break
              }
              """,
            """
              def s = "prod"
              if ("prod".equals(s)) {
                  println("prod")
              } else {
                  println("default")
              }
              """
          )
        );
    }

    @Issue("https://github.com/openrewrite/rewrite/issues/2566")
    @SuppressWarnings("GrMethodMayBeStatic")
    @Test
    void nonIdentifierEnum() {
        rewriteRun(
          //language=groovy
          groovy(
            """
              import java.nio.file.*
              class Test {
                  void test(OpenOption o) {
                      switch(o) {
                          case StandardOpenOption.READ:
                              System.out.println("read")
                      }
                  }
              }
              """,
            """
              import java.nio.file.*
              class Test {
                  void test(OpenOption o) {
                      if (o == StandardOpenOption.READ) {
                          System.out.println("read")
                      }
                  }
              }
              """
          )
        );
    }

    @Test
    void threeCases() {
        rewriteRun(
          //language=groovy
          groovy(
            """
              def s = "prod"
              switch(s) {
                  case "prod":
                      println("prod")
                      break
                  case "test":
                      println("test")
                      break
                  default:
                      println("default")
                      break
              }
              """
          )
        );
    }
}
