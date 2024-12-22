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

@SuppressWarnings("OctalInteger")
class WriteOctalValuesAsDecimalTest implements RewriteTest {
    @Override
    public void defaults(RecipeSpec spec) {
        spec.recipe(new WriteOctalValuesAsDecimal());
    }

    @DocumentExample
    @Test
    void writeAsDecimal() {
        rewriteRun(
          //language=java
          java(
            """
              class Test {
                  void test() {
                      int m = 010;
                      short m2 = 010;
                      int n = 0x01;
                      int o = 0b01;
                      int p = 12;
                      int q = 1;
                      long r = 0L;
                      float s = 0.01f;
                      double t = 0.01;
                  }
              }
              """,
            """
              class Test {
                  void test() {
                      int m = 8;
                      short m2 = 8;
                      int n = 0x01;
                      int o = 0b01;
                      int p = 12;
                      int q = 1;
                      long r = 0L;
                      float s = 0.01f;
                      double t = 0.01;
                  }
              }
              """
          )
        );
    }
}
