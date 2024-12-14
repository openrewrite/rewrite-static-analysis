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

@SuppressWarnings("RedundantThrows")
class UnnecessaryCatchTest implements RewriteTest {

    @Override
    public void defaults(RecipeSpec spec) {
        spec.recipe(new UnnecessaryCatch(false));
    }

    @DocumentExample
    @Test
    void unwrapTry() {
        rewriteRun(
          //language=java
          java(
            """
              import java.io.IOException;
                            
              public class AnExample {
                  public void method() {
                      try {
                          java.util.Base64.getDecoder().decode("abc".getBytes());
                      } catch (IOException e) {
                          System.out.println("an exception!");
                      }
                  }
              }
              """,
            """
              public class AnExample {
                  public void method() {
                      java.util.Base64.getDecoder().decode("abc".getBytes());
                  }
              }
              """
          )
        );
    }

    @Test
    void removeCatch() {
        rewriteRun(
          //language=java
          java(
            """
              import java.io.IOException;
              
              public class AnExample {
                  public void method() {
                      try {
                          java.util.Base64.getDecoder().decode("abc".getBytes());
                      } catch (IOException e1) {
                          System.out.println("an exception!");
                      } catch (IllegalStateException e2) {
                          System.out.println("another exception!");
                      }
                  }
              }
              """,
            """
              public class AnExample {
                  public void method() {
                      try {
                          java.util.Base64.getDecoder().decode("abc".getBytes());
                      } catch (IllegalStateException e2) {
                          System.out.println("another exception!");
                      }
                  }
              }
              """
          )
        );
    }

    @Test
    void doNotRemoveRuntimeException() {
        rewriteRun(
          //language=java
          java(
            """ 
              public class AnExample {
                  public void method() {
                      try {
                          java.util.Base64.getDecoder().decode("abc".getBytes());
                      } catch (IllegalStateException e) {
                          System.out.println("an exception!");
                      }
                  }
              }
              """
          )
        );
    }

    @Test
    void doNotRemoveThrownException() {
        rewriteRun(
          //language=java
          java(
            """
              import java.io.IOException;
              
              public class AnExample {
                  public void method() {
                      try {
                          fred();
                      } catch (IOException e) {
                          System.out.println("an exception!");
                      }
                  }
                  
                  public void fred() throws IOException {
                  }
              }
              """
          )
        );
    }

    @Test
    void doNotRemoveJavaLangException() {
        rewriteRun(
          //language=java
          java(
            """
              class Scratch {
                  void method() {
                      try {
                          throw new RuntimeException();
                      } catch (Exception e) {
                          System.out.println("an exception!");
                      }
                  }
              }
              """
          )
        );
    }
}
