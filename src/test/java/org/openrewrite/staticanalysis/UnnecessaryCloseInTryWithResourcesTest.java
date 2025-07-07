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

@SuppressWarnings("RedundantExplicitClose")
class UnnecessaryCloseInTryWithResourcesTest implements RewriteTest {
    @Override
    public void defaults(RecipeSpec spec) {
        spec.recipe(new UnnecessaryCloseInTryWithResources());
    }

    @DocumentExample
    @Test
    void hasUnnecessaryClose() {
        rewriteRun(
          //language=java
          java(
            """
              import java.io.FileWriter;
              import java.util.Scanner;

              class A {
                  public void doSomething() {
                      try (FileWriter fileWriter = new FileWriter("test"); Scanner scanner = new Scanner("abc")) {
                          fileWriter.write('c');
                          scanner.close();
                      }
                  }
              }
              """,
            """
              import java.io.FileWriter;
              import java.util.Scanner;

              class A {
                  public void doSomething() {
                      try (FileWriter fileWriter = new FileWriter("test"); Scanner scanner = new Scanner("abc")) {
                          fileWriter.write('c');
                      }
                  }
              }
              """
          )
        );
    }

    @Test
    void noChangeRequired() {
        rewriteRun(
          //language=java
          java(
            """
              import java.io.FileWriter;
              class A {
                  public void doSomething() {
                      try (FileWriter fileWriter = new FileWriter("test")){
                          fileWriter.append('c');
                          fileWriter.flush();
                      }
                  }
              }
              """
          )
        );
    }

    @Test
    void onlyRemoveAutoCloseableClose() {
        rewriteRun(
          //language=java
          java(
            """
              package abc;
              import java.util.Scanner;
              class A {
                  public void doSomething() {
                      Scanner scannerNotInTry = new Scanner("def");
                      try (Scanner scanner = new Scanner("abc")) {
                          boolean hasNext = scanner.hasNext();
                          scannerNotInTry.close();
                          scanner.close();
                      }
                  }
              }
              """,
            """
              package abc;
              import java.util.Scanner;
              class A {
                  public void doSomething() {
                      Scanner scannerNotInTry = new Scanner("def");
                      try (Scanner scanner = new Scanner("abc")) {
                          boolean hasNext = scanner.hasNext();
                          scannerNotInTry.close();
                      }
                  }
              }
              """
          )
        );
    }
}
