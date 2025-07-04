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

class RemoveCallsToObjectFinalizeTest implements RewriteTest {

    @Override
    public void defaults(RecipeSpec spec) {
        spec.recipe(new RemoveCallsToObjectFinalize());
    }

    @Test
    @DocumentExample
    void removeCallToFinalize() {
        rewriteRun(
          //language=java
          java(
            """
              public class A {

                      @Override
                      protected void finalize() {
                          super.finalize();
                      }

                      public static void main(String[] args) throws Throwable {

                          A a = new A();
                          System.out.println("Clean object");
                          a.finalize();
                      }
                  }
              """,
            """
                public class A {

                        @Override
                        protected void finalize() {
                            super.finalize();
                        }

                        public static void main(String[] args) throws Throwable {

                            A a = new A();
                            System.out.println("Clean object");
                        }
                    }
                """
          )
        );
    }


    @Test
    void privateFinalizeIsNotChanged() {
        rewriteRun(
          //language=java
          java(
            """
              public class A {

                      private void finalize() {
                         System.out.println("I am just a friendly finalizer");
                      }

                      public static void main(String[] args) throws Throwable {

                          A a = new A();
                          System.out.println("Clean object");
                          a.finalize();
                      }
                  }
              """
          )
        );
    }


    @Test
    void publicFinalizeIsNotChanged() {
        rewriteRun(
          //language=java
          java(
            """
              public class A {

                      public void finalize() {
                         System.out.println("I am just a friendly finalizer");
                      }

                      public static void main(String[] args) throws Throwable {

                          A a = new A();
                          System.out.println("Clean object");
                          a.finalize();
                      }
                  }
              """
          )
        );
    }


    @Test
    void protectedFinalizeIsNotRemoved() {
        rewriteRun(
          //language=java
          java(
            """
              public class A {

                      protected void finalize() {
                         System.out.println("I am just a friendly finalizer");
                      }

                      public static void main(String[] args) throws Throwable {

                          A a = new A();
                          System.out.println("Clean object");
                          a.finalize();
                      }
                  }
              """
          )
        );
    }
        @Test
        void staticFinalizeIsNotRemoved() {
            rewriteRun(
              //language=java
              java(
                """
                  public class A {

                          static void finalize() {
                             System.out.println("I am just a friendly finalizer");
                          }

                          public static void main(String[] args) throws Throwable {

                              A.finalize();
                          }
                      }
                  """
              )
            );
    }
}
