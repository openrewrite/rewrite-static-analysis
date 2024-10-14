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

class InlineOneTimeUsageVariableTest implements RewriteTest {
    @Override
    public void defaults(RecipeSpec spec) {
        spec.recipe(new InlineOneTimeUsageVariable());
    }

    @DocumentExample
    @SuppressWarnings({"UnnecessaryLocalVariable", "CodeBlock2Expr", "Convert2MethodRef"})
    @Test
    void inlineVariable() {
        rewriteRun(
          //language=java
          java(
            """
              class Test {
                  int test() {
                      int y = 0;
                      int n = y;
                      return n;
                  }
                  int test2() {
                      int y = 0;
                      int n = y;
                      System.out.println(n);
                      return n;
                  }
                  int test3() {
                      String s = "0";
                      String s2 = s;
                      return s2;
                  }
                  int test4() {
                      String s = "0";
                      String s2 = s;
                      String s3 = s2;
                      return s3;
                  }
              }
              """,
            """
              class Test {
                  int test() {
                      return 0;
                  }
                  int test2() {
                      int n = 0;
                      System.out.println(n);
                      return n;
                  }
                  int test3() {
                      return "0";
                  }
                  int test4() {
                     return "0";
                  }
              }
              """
          )
        );
    }

}
