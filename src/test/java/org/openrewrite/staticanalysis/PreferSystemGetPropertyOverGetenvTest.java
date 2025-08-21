/*
 * Copyright 2025 the original author or authors.
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

class PreferSystemGetPropertyOverGetenvTest implements RewriteTest {

    @Override
    public void defaults(RecipeSpec spec) {
        spec.recipe(new PreferSystemGetPropertyOverGetenv());
    }

    @DocumentExample
    @Test
    void replacesMultipleEnvVariables() {
        rewriteRun(
          java(
            """
              class A {
                  void test() {
                      String user = System.getenv("USER");
                      String username = System.getenv("USERNAME");
                      String home = System.getenv("HOME");
                      String profile = System.getenv("USERPROFILE");
                      String temp = System.getenv("TEMP");
                      String tmpdir = System.getenv("TMPDIR");
                      String tmp = System.getenv("TMP");
                  }
              }
              """,
            """
              class A {
                  void test() {
                      String user = System.getProperty("user.name");
                      String username = System.getProperty("user.name");
                      String home = System.getProperty("user.home");
                      String profile = System.getProperty("user.home");
                      String temp = System.getProperty("java.io.tmpdir");
                      String tmpdir = System.getProperty("java.io.tmpdir");
                      String tmp = System.getProperty("java.io.tmpdir");
                  }
              }
              """
          )
        );
    }
}
