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

import static org.junit.jupiter.api.Assumptions.assumeTrue;
import static org.openrewrite.golang.Assertions.go;
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

    @Test
    void goConvertsPlainOctal() {
        assumeTrue(GoEngineTestListener.isAvailable(), "rewrite-go-rpc engine unavailable");
        rewriteRun(
          go(
            """
              package main

              func f(n int) {}

              func test() {
                  f(010)
              }
              """,
            """
              package main

              func f(n int) {}

              func test() {
                  f(8)
              }
              """
          )
        );
    }

    @Test
    void goDoNotChangeFilePermissions() {
        assumeTrue(GoEngineTestListener.isAvailable(), "rewrite-go-rpc engine unavailable");
        rewriteRun(
          go(
            """
              package main

              import "os"

              func mode(perm os.FileMode) {}

              func test() {
                  os.OpenFile("f", os.O_WRONLY|os.O_CREATE|os.O_TRUNC, 0600)
                  os.WriteFile("f", nil, 0640)
                  os.Mkdir("d", 0755)
                  mode(0755)
                  var m os.FileMode = 0644
                  m = os.FileMode(0600)
                  _ = m
              }
              """
          )
        );
    }
}
