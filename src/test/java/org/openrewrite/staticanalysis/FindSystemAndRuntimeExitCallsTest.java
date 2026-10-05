/*
 * Copyright 2026 the original author or authors.
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

class FindSystemAndRuntimeExitCallsTest implements RewriteTest {

    @Override
    public void defaults(RecipeSpec spec) {
        spec.recipe(new FindSystemAndRuntimeExitCalls());
    }

    @DocumentExample
    @Test
    void flagsSystemExit() {
        rewriteRun(
          //language=java
          java(
            """
              class A {
                  void bail() {
                      System.exit(1);
                  }
              }
              """,
            """
              class A {
                  void bail() {
                      /*~~(JVM exit call; terminating from application code bypasses normal shutdown.)~~>*/System.exit(1);
                  }
              }
              """
          )
        );
    }

    @Test
    void flagsRuntimeExit() {
        rewriteRun(
          //language=java
          java(
            """
              class A {
                  void bail() {
                      Runtime.getRuntime().exit(1);
                  }
              }
              """,
            """
              class A {
                  void bail() {
                      /*~~(JVM exit call; terminating from application code bypasses normal shutdown.)~~>*/Runtime.getRuntime().exit(1);
                  }
              }
              """
          )
        );
    }

    @Test
    void flagsRuntimeHalt() {
        rewriteRun(
          //language=java
          java(
            """
              class A {
                  void bail() {
                      Runtime.getRuntime().halt(1);
                  }
              }
              """,
            """
              class A {
                  void bail() {
                      /*~~(JVM exit call; terminating from application code bypasses normal shutdown.)~~>*/Runtime.getRuntime().halt(1);
                  }
              }
              """
          )
        );
    }

    @Test
    void doesNotFlagUnrelatedMethods() {
        rewriteRun(
          //language=java
          java(
            """
              class A {
                  int exit() {
                      return 0;
                  }

                  void run() {
                      exit();
                      System.gc();
                      System.out.println("still running");
                  }
              }
              """
          )
        );
    }

    @Test
    void doesNotFlagOtherExitOverloads() {
        rewriteRun(
          //language=java
          java(
            """
              class A {
                  void notReallyExit() {
                      new Thread().interrupt();
                  }
              }
              """
          )
        );
    }
}
