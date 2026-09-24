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

class FindMainWithThrowsClauseTest implements RewriteTest {

    @Override
    public void defaults(RecipeSpec spec) {
        spec.recipe(new FindMainWithThrowsClause());
    }

    @DocumentExample
    @Test
    void flagsMainWithThrowsException() {
        rewriteRun(
          //language=java
          java(
            """
              class A {
                  public static void main(String[] args) throws Exception {
                      System.out.println("hello");
                  }
              }
              """,
            """
              class A {
                  /*~~(`main` declares `throws`; uncaught exceptions propagate to the JVM's default handler. Handle them explicitly and exit with a meaningful code.)~~>*/public static void main(String[] args) throws Exception {
                      System.out.println("hello");
                  }
              }
              """
          )
        );
    }

    @Test
    void flagsMainWithVarargsAndCheckedException() {
        rewriteRun(
          //language=java
          java(
            """
              import java.io.IOException;

              class A {
                  public static void main(String... args) throws IOException {
                  }
              }
              """,
            """
              import java.io.IOException;

              class A {
                  /*~~(`main` declares `throws`; uncaught exceptions propagate to the JVM's default handler. Handle them explicitly and exit with a meaningful code.)~~>*/public static void main(String... args) throws IOException {
                  }
              }
              """
          )
        );
    }

    @Test
    void doesNotFlagMainWithoutThrowsClause() {
        rewriteRun(
          //language=java
          java(
            """
              class A {
                  public static void main(String[] args) {
                      System.out.println("hello");
                  }
              }
              """
          )
        );
    }

    @Test
    void doesNotFlagNonMainMethodWithThrows() {
        rewriteRun(
          //language=java
          java(
            """
              class A {
                  public static void run(String[] args) throws Exception {
                  }
              }
              """
          )
        );
    }

    @Test
    void doesNotFlagMainWithWrongReturnType() {
        rewriteRun(
          //language=java
          java(
            """
              class A {
                  public static int main(String[] args) throws Exception {
                      return 0;
                  }
              }
              """
          )
        );
    }

    @Test
    void doesNotFlagMainWithoutArgs() {
        rewriteRun(
          //language=java
          java(
            """
              class A {
                  public static void main() throws Exception {
                  }
              }
              """
          )
        );
    }

    @Test
    void doesNotFlagNonStaticMainInstanceMethod() {
        rewriteRun(
          //language=java
          java(
            """
              class A {
                  public void main(String[] args) throws Exception {
                  }
              }
              """
          )
        );
    }

    @Test
    void doesNotFlagPackagePrivateMain() {
        rewriteRun(
          //language=java
          java(
            """
              class A {
                  static void main(String[] args) throws Exception {
                  }
              }
              """
          )
        );
    }

    @Test
    void doesNotFlagMainWithWrongParamType() {
        rewriteRun(
          //language=java
          java(
            """
              class A {
                  public static void main(int[] args) throws Exception {
                  }
              }
              """
          )
        );
    }
}
