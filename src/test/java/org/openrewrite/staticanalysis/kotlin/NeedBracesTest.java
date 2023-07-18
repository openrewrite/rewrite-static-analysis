/*
 * Copyright 2023 the original author or authors.
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
package org.openrewrite.staticanalysis.kotlin;

import org.junit.jupiter.api.Test;
import org.junitpioneer.jupiter.ExpectedToFail;
import org.openrewrite.staticanalysis.NeedBraces;
import org.openrewrite.test.RecipeSpec;
import org.openrewrite.test.RewriteTest;

import static org.openrewrite.kotlin.Assertions.kotlin;

class NeedBracesTest implements RewriteTest {

    @Override
    public void defaults(RecipeSpec spec) {
        spec.recipe(new NeedBraces());
    }

    @ExpectedToFail("Kotlin version visitor to be implemented")
    @Test
    void addBracesForIfBranch() {
        rewriteRun(
          kotlin(
            """
              fun getSymbol(num : Int) : String {
                  if (num > 0) return "+"

                  return "-"
              }
              """,
            """
              fun getSymbol(num : Int) : String {
                  if (num > 0) {
                      return "+"
                  }

                  return "-"
              }
              """
          )
        );
    }

    @ExpectedToFail("Kotlin version visitor to be implemented")
    @Test
    void addBracesForElseBranch() {
        rewriteRun(
          kotlin(
            """
              fun getSymbol(num : Int) : String {
                  return if (num > 0)
                      "+"
                  else "-"
              }
              """,
            """
              fun getSymbol(num : Int) : String {
                  return if (num > 0) {
                      "+"
                  } else {
                      "-"
                  }
              }
              """
          )
        );
    }

    @Test
    void doNotChangeForArguments() {
        rewriteRun(
          kotlin(
            """
              fun run(foo: String, bar: String) {}
              var x = run( if (true) "" else "",
                  if (true) "" else "")
              """
          )
        );
    }
}
