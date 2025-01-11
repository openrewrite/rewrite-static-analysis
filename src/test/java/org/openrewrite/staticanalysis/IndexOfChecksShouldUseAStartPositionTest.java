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
import org.openrewrite.Issue;
import org.openrewrite.test.RecipeSpec;
import org.openrewrite.test.RewriteTest;

import static org.openrewrite.java.Assertions.java;

class IndexOfChecksShouldUseAStartPositionTest implements RewriteTest {
    @Override
    public void defaults(RecipeSpec spec) {
        spec.recipe(new IndexOfChecksShouldUseAStartPosition());
    }

    @Test
    void doNotChangeCompliantRhs() {
        rewriteRun(
          //language=java
          java(
            """
              class Test {
                  boolean hasIndex(String str) {
                      return str.indexOf("x", 2) > -1;
                  }
              }
              """
          )
        );
    }

    @Issue("https://github.com/openrewrite/rewrite/issues/1225")
    @Test
    void intentIsStringDoesNotStartWithSearchString() {
        rewriteRun(
          //language=java
          java(
            """
              class Test {
                  boolean doesNotStartWithX(String str) {
                      return str.indexOf("x") > 0;
                  }
              }
              """
          )
        );
    }

    @DocumentExample
    @Test
    void changeLhsWithLiteral() {
        rewriteRun(
          //language=java
          java(
            """
              class Test {
                  boolean hasIndex(String str) {
                      return str.indexOf("x") > 2;
                  }
              }
              """,
            """
              class Test {
                  boolean hasIndex(String str) {
                      return str.indexOf("x", 2) > -1;
                  }
              }
              """
          )
        );
    }

    @Test
    void changeLhsWithMethodInvocation() {
        rewriteRun(
          //language=java
          java(
            """
              class Test {
                  boolean hasIndex(String str) {
                      return str.indexOf(testVal()) > 2;
                  }
                  String testVal() {
                      return "";
                  }
              }
              """,
            """
              class Test {
                  boolean hasIndex(String str) {
                      return str.indexOf(testVal(), 2) > -1;
                  }
                  String testVal() {
                      return "";
                  }
              }
              """
          )
        );
    }

    @Test
    void doNotChangeRhs() {
        rewriteRun(
          //language=java
          java(
            """
              class Test {
                  boolean hasIndex(String str) {
                      return 2 < str.indexOf("str");
                  }
              }
              """
          )
        );
    }
}
