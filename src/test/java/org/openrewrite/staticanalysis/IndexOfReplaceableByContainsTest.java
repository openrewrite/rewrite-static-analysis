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

class IndexOfReplaceableByContainsTest implements RewriteTest {
    @Override
    public void defaults(RecipeSpec spec) {
        spec.recipe(new IndexOfReplaceableByContains());
    }

    @DocumentExample
    @Test
    void stringIndexOfReplaceableByContains() {
        rewriteRun(
          //language=java
          java(
            """
              class Test {
                  static boolean hasIndex(String str) {
                      if (str.indexOf("str") > -1) {
                      }
                      return str.indexOf("str") >= 0;
                  }
              }
              """,
            """
              class Test {
                  static boolean hasIndex(String str) {
                      if (str.contains("str")) {
                      }
                      return str.contains("str");
                  }
              }
              """
          )
        );
    }

    @Test
    void listIndexOfReplaceableByContains() {
        rewriteRun(
          //language=java
          java(
            """
              import java.util.List;

              class Test {
                  static boolean hasIndex(List<String> strList, String str) {
                      if (strList.indexOf(str) > -1) {
                      }
                      return strList.indexOf(str) >= 0;
                  }
              }
              """,
            """
              import java.util.List;

              class Test {
                  static boolean hasIndex(List<String> strList, String str) {
                      if (strList.contains(str)) {
                      }
                      return strList.contains(str);
                  }
              }
              """
          )
        );
    }

    @Test
    void listIndexOfInImplementationClass() {
        rewriteRun(
          //language=java
          java(
            """
              import java.util.List;
              
              abstract class Test implements List<String> {
                  boolean m(Object o) {
                      return indexOf(o) >= 0;
                  }
              }
              """
          )
        );
    }
}
