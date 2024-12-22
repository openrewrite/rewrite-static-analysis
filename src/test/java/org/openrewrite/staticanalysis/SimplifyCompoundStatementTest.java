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

@SuppressWarnings({"PointlessBooleanExpression", "UnusedAssignment", "ConstantConditions", "DuplicateCondition"})
class SimplifyCompoundStatementTest implements RewriteTest {
    @Override
    public void defaults(RecipeSpec spec) {
        spec.recipe(new SimplifyCompoundStatement());
    }

    @DocumentExample
    @Test
    void removeCompoundAnd() {
        rewriteRun(
          //language=java
          java(
            """
              class Test {
                  void test() {
                      boolean b = true;
                      b &= true;
                  }
              }
              """,
            """
              class Test {
                  void test() {
                      boolean b = true;
                  }
              }
              """
          )
        );
    }

    @Test
    void fixCompoundAnd() {
        rewriteRun(
          //language=java
          java(
            """
              class Test {
                  void test() {
                      boolean b = true;
                      b &= false;
                  }
              }
              """,
            """
              class Test {
                  void test() {
                      boolean b = true;
                      b = false;
                  }
              }
              """
          )
        );
    }

    @Test
    void removeCompoundOr() {
        rewriteRun(
          //language=java
          java(
            """
              class Test {
                  void test() {
                      boolean b = true;
                      b |= false;
                  }
              }
              """,
            """
              class Test {
                  void test() {
                      boolean b = true;
                  }
              }
              """
          )
        );
    }

    @Test
    void fixCompoundOr() {
        rewriteRun(
          //language=java
          java(
            """
              class Test {
                  void test() {
                      boolean b = true;
                      b |= true;
                  }
              }
              """,
            """
              class Test {
                  void test() {
                      boolean b = true;
                      b = true;
                  }
              }
              """
          )
        );
    }

    @Test
    void removeCompoundOrComplex() {
        rewriteRun(
          //language=java
          java(
            """
              class Test {
                  void test() {
                      boolean b = true;
                      b |= false && true && true;
                  }
              }
              """,
            """
              class Test {
                  void test() {
                      boolean b = true;
                  }
              }
              """
          )
        );
    }
}
