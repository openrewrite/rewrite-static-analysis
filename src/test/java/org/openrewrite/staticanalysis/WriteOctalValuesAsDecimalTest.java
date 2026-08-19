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
import static org.openrewrite.python.Assertions.python;

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

    @Test
    void octalLongPreservesSuffix() {
        rewriteRun(
          //language=java
          java(
            """
              class Test {
                  long a = 010L;
                  long b = 010l;
              }
              """,
            """
              class Test {
                  long a = 8L;
                  long b = 8l;
              }
              """
          )
        );
    }

    @Test
    void doNotChangeFloatingPointWithLeadingZero() {
        rewriteRun(
          //language=java
          java(
            """
              class Test {
                  float a = 010f;
                  float b = 010F;
                  double c = 010d;
                  double d = 010D;
                  double e = 0.010;
              }
              """
          )
        );
    }

    @Test
    void pythonComplexLiteral() {
        rewriteRun(
          //language=py
          python(
            """
              def f(parameter: complex = 0j):
                  pass
              """
          )
        );
    }

    @Test
    void pythonExplicitOctalUnchanged() {
        rewriteRun(
          //language=py
          python(
            """
              a = 0o755
              b = 0
              """
          )
        );
    }
}
