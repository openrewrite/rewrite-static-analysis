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
import org.openrewrite.Issue;
import org.openrewrite.java.JavaParser;
import org.openrewrite.test.RecipeSpec;
import org.openrewrite.test.RewriteTest;

import static org.openrewrite.java.Assertions.java;

class ExplicitInitializationTest implements RewriteTest {

    @Override
    public void defaults(RecipeSpec spec) {
        spec.recipe(new ExplicitInitialization());
    }

    @DocumentExample
    @Test
    void removeExplicitInitialization() {
        rewriteRun(
          //language=java
          java(
            """
              class Test {
                  private int a = 0;
                  private long b = 0L;
                  private short c = 0;
                  private int d = 1;
                  private long e = 2L;
                  private int f;
                  private char g = '\\0';

                  private boolean h = false;
                  private boolean i = true;

                  private Object j = new Object();
                  private Object k = null;

                  int[] l = null;
                  int[] m = new int[0];

                  private final Long n = null;
              }
              """,
            """
              class Test {
                  private int a;
                  private long b;
                  private short c;
                  private int d = 1;
                  private long e = 2L;
                  private int f;
                  private char g;

                  private boolean h;
                  private boolean i = true;

                  private Object j = new Object();
                  private Object k;

                  int[] l;
                  int[] m = new int[0];

                  private final Long n = null;
              }
              """
          )
        );
    }

    @Test
    void ignoreLombokDefaultBuilder() {
        rewriteRun(
          spec -> spec.parser(JavaParser.fromJavaVersion().classpath("lombok")),
          //language=java
          java(
            """
              import lombok.Builder;
              class Test {
                  @Builder.Default
                  private boolean b = false;
              }
              """
          )
        );
    }

    @Test
    void ignoreFinalField() {
        rewriteRun(
          //language=java
          java(
            """
              class Test {
                  private final boolean b = false;
              }
              """
          )
        );
    }

    @Issue("https://github.com/openrewrite/rewrite-static-analysis/issues/101")
    @Test
    void ignoreLombokValueField() {
        rewriteRun(
          spec -> spec.parser(JavaParser.fromJavaVersion().classpath("lombok")),
          //language=java
          java(
            """
              import lombok.Value;
              @Value
              class Test {
                  boolean b = false;
              }
              """
          )
        );
    }

    @Test
    void ignoreVariablesInMethods() {
        rewriteRun(
          //language=java
          java(
            """
              class Test {
                  private void test() {
                      int i = 0;
                  }
              }
              """
          )
        );
    }

    @Test
    void ignoreInterfaces() {
        rewriteRun(
          //language=java
          java(
            """
              interface Test {
                  int a = 0;
              }
              """
          )
        );
    }

    @Test
    void blockStatement() {
        rewriteRun(
          //language=java
          java(
            """
              class Test {
                  void doSomething() {
                      for (int i=0; i<10; i++) {
                          System.out.println(i);
                      }
                  }
              }
              """
          )
        );
    }

    @Test
    void charLiteral() {
        rewriteRun(
          //language=java
          java(
            """
              class Test {
                  int n = '0';
              }
              """
          )
        );
    }

    @Issue("https://github.com/openrewrite/rewrite-static-analysis/issues/109")
    @Test
    void removeExplicitInitializationInAnonymousSubClasses() {
        rewriteRun(
          //language=java
          java(
            """
              class Test {
                  Object o = new Object() {
                      private int a = 0;
                      private long b = 0L;
                      private short c = 0;
                      private int d = 1;
                      private long e = 2L;
                      private int f;
                      private char g = '\\0';

                      private boolean h = false;
                      private boolean i = true;

                      private Object j = new Object();
                      private Object k = null;

                      int[] l = null;
                      int[] m = new int[0];

                      private final Long n = null;
                  };
              }
              """,
            """
              class Test {
                  Object o = new Object() {
                      private int a;
                      private long b;
                      private short c;
                      private int d = 1;
                      private long e = 2L;
                      private int f;
                      private char g;

                      private boolean h;
                      private boolean i = true;

                      private Object j = new Object();
                      private Object k;

                      int[] l;
                      int[] m = new int[0];

                      private final Long n = null;
                  };
              }
              """
          )
        );
    }

    @Issue("https://github.com/openrewrite/rewrite-static-analysis/issues/109")
    @Test
    void ignoreInAnonymousSubClasses() {
        rewriteRun(
          //language=java
          java(
            """
              class Test {
                  Object o = new Object() {
                      private final boolean b = false;

                      private void method() {
                          int i = 0;
                      }
                  };
              }
              """
          )
        );
    }
}
