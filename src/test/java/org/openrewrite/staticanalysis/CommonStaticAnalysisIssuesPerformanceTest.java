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
import org.openrewrite.test.RecipeSpec;
import org.openrewrite.test.RewriteTest;

import static org.openrewrite.java.Assertions.java;

class CommonStaticAnalysisIssuesPerformanceTest implements RewriteTest {

    @Override
    public void defaults(RecipeSpec spec) {
        spec.recipe(RewriteTest.fromRuntimeClasspath("org.openrewrite.staticanalysis.CommonStaticAnalysis"));
//          .afterRecipe(run -> {
//              System.out.println(run.getStats().printAsMermaidGantt(1e6));
//              System.out.println("\n");
//              System.out.println(run.getStats().printAsCsv());
//          });
    }

    @DocumentExample
    @Test
    void indexOfOnList() {
        rewriteRun(
          //language=java
          java(
            """
              import java.util.List;

              class Test {
                  static boolean hasIndex(List<String> strList, String str) {
                      if (strList.indexOf(str) > 0) {
                      }
                      return strList.indexOf(str) > 0;
                  }
              }
              """,
            """
              import java.util.List;

              class Test {
                  static boolean hasIndex(List<String> strList, String str) {
                      strList.indexOf(str);
                      return strList.indexOf(str) >= 1;
                  }
              }
              """
          )
        );
    }
}
