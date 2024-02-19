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
package org.openrewrite.staticanalysis.groovy;

import org.junit.jupiter.api.Test;
import org.junitpioneer.jupiter.ExpectedToFail;
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

    @SuppressWarnings("GrMethodMayBeStatic")
    @Issue("https://github.com/openrewrite/rewrite/issues/2566")
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

    @DocumentExample
    @ExpectedToFail("Temporarily until we have investigated why the behavior has changed here")
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
              if (s == "prod") {
                  println("prod")
              } else {
                  println("default")
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
