/*
 * Copyright 2020 the original author or authors.
 * <p>
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * <p>
 * https://www.apache.org/licenses/LICENSE-2.0
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

@SuppressWarnings({
  "ClassInitializerMayBeStatic", "StatementWithEmptyBody", "ConstantConditions",
  "SynchronizationOnLocalVariableOrMethodParameter", "CatchMayIgnoreException", "EmptyFinallyBlock",
  "InfiniteLoopStatement", "UnnecessaryContinue", "EmptyClassInitializer", "EmptyTryBlock",
  "resource"
})
class EmptyBlockTest implements RewriteTest {

    @Override
    public void defaults(RecipeSpec spec) {
        spec.recipe(new EmptyBlock());
    }

    @DocumentExample
    @SuppressWarnings("ClassInitializerMayBeStatic")
    @Test
    void emptySwitch() {
        rewriteRun(
          //language=java
          java(
            """
              public class A {
                  {
                      int i = 0;
                      switch(i) {
                      }
                  }
              }
              """,
            """
              public class A {
                  {
                      int i = 0;
                  }
              }
              """
          )
        );
    }

    @Test
    void emptyBlockWithComment() {
        rewriteRun(
          //language=java
          java(
            """
              public class A {
                  {
                      // comment
                  }
              }
              """
          )
        );
    }

    @SuppressWarnings("EmptySynchronizedStatement")
    @Test
    void emptySynchronized() {
        rewriteRun(
          //language=java
          java(
            """
              public class A {
                  {
                      final Object o = new Object();
                      synchronized(o) {
                      }
                  }
              }
              """,
            """
              public class A {
                  {
                      final Object o = new Object();
                  }
              }
              """
          )
        );
    }

    @Test
    void emptyTry() {
        rewriteRun(
          //language=java
          java(
            """
              import java.io.*;

              public class A {
                  {
                      final String fileName = "fileName";
                      try {
                      } catch (IOException e) {
                      }
                  }
              }
              """,
            """
              public class A {
                  {
                      final String fileName = "fileName";
                  }
              }
              """
          )
        );
    }

    @Test
    void emptyCatchBlockWithIOException() {
        rewriteRun(
          //language=java
          java(
            """
              import java.io.File;
              import java.io.FileInputStream;
              import java.io.IOException;
              import java.nio.file.*;

              public class A {
                  public void foo() {
                      try {
                          new FileInputStream(new File("somewhere"));
                      } catch (IOException e) {
                      }
                  }
              }
              """
          )
        );
    }

    @Test
    void emptyTryFinallyBlock() {
        rewriteRun(
          //language=java
          java(
            """
              public class A {
                  public int foo() {
                      try {
                          int i = 1;
                      } finally {
                      }
                  }
              }
              """
          )
        );
    }

    @SuppressWarnings("RedundantFileCreation")
    @Test
    void emptyCatchBlockWithExceptionAndEmptyFinally() {
        rewriteRun(
          //language=java
          java(
            """
              import java.io.File;
              import java.io.FileInputStream;
              import java.nio.file.*;

              public class A {
                  public void foo() {
                      try {
                          new FileInputStream(new File("somewhere"));
                      } catch (Throwable t) {
                      } finally {
                      }
                  }
              }
              """,
            """
              import java.io.File;
              import java.io.FileInputStream;
              import java.nio.file.*;

              public class A {
                  public void foo() {
                      try {
                          new FileInputStream(new File("somewhere"));
                      } catch (Throwable t) {
                      }
                  }
              }
              """
          )
        );
    }

    @Test
    void emptyLoops() {
        rewriteRun(
          //language=java
          java(
            """
              public class A {
                  public void foo() {
                      while(true) {
                      }
                      do {
                      } while(true);
                  }
              }
              """,
            """
              public class A {
                  public void foo() {
                      while(true) {
                          continue;
                      }
                      do {
                          continue;
                      } while(true);
                  }
              }
              """
          )
        );
    }

    @Test
    void emptyInstanceAndStaticInit() {
        rewriteRun(
          //language=java
          java(
            """
              public class A {
                  static {}
                  {}
              }
              """,
            """
              public class A {
              }
              """
          )
        );
    }

    @SuppressWarnings("UnusedAssignment")
    @Test
    void extractSideEffectsFromEmptyIfsWithNoElse() {
        rewriteRun(
          //language=java
          java(
            """
              public class A {
                  int n = sideEffect();

                  int sideEffect() {
                      return new java.util.Random().nextInt();
                  }

                  boolean boolSideEffect() {
                      return sideEffect() == 0;
                  }

                  public void lotsOfIfs() {
                      if(sideEffect() == 1) {}
                      if(sideEffect() == sideEffect()) {}
                      int n;
                      if((n = sideEffect()) == 1) {}
                      if((n /= sideEffect()) == 1) {}
                      if(new A().n == 1) {}
                      if(!boolSideEffect()) {}
                      if(1 == 2) {}
                  }
              }
              """,
            """
              public class A {
                  int n = sideEffect();

                  int sideEffect() {
                      return new java.util.Random().nextInt();
                  }

                  boolean boolSideEffect() {
                      return sideEffect() == 0;
                  }

                  public void lotsOfIfs() {
                      sideEffect();
                      sideEffect();
                      sideEffect();
                      int n;
                      n = sideEffect();
                      n /= sideEffect();
                      new A();
                      boolSideEffect();
                  }
              }
              """
          )
        );
    }

    @Test
    void invertIfWithOnlyElseClauseAndBinaryOperator() {
        rewriteRun(
          // extra spaces after the original if condition to ensure that we preserve the if statement's block formatting
          //language=java
          java(
            """
              public class A {
                  {
                      if("foo".length() > 3)   {
                      } else {
                          System.out.println("this");
                      }
                  }
              }
              """,
            """
              public class A {
                  {
                      if("foo".length() <= 3)   {
                          System.out.println("this");
                      }
                  }
              }
              """
          )
        );
    }

    @Test
    void invertIfWithElseIfElseClause() {
        rewriteRun(
          //language=java
          java(
            """
              public class A {
                  {
                      if("foo".length() > 3) {
                      } else if("foo".length() > 4) {
                          System.out.println("longer");
                      }
                      else {
                          System.out.println("this");
                      }
                  }
              }
              """,
            """
              public class A {
                  {
                      if("foo".length() <= 3) {
                          if("foo".length() > 4) {
                              System.out.println("longer");
                          }
                          else {
                              System.out.println("this");
                          }
                      }
                  }
              }
              """
          )
        );
    }

    @Test
    void emptyElseBlock() {
        rewriteRun(
          //language=java
          java(
            """
              public class A {
                  {
                      if (true) {
                          System.out.println("this");
                      } else {
                      }
                  }
              }
              """,
            """
              public class A {
                  {
                      if (true) {
                          System.out.println("this");
                      }
                  }
              }
              """
          )
        );
    }

    @Test
    void emptyTryWithResources() {
        rewriteRun(
          //language=java
          java(
            """
              import java.io.*;

              public class A {
                  {
                      final String fileName = "fileName";
                      try (FileInputStream fis = new FileInputStream(fileName)) {
                      } catch (IOException e) {
                      }
                  }
              }
              """
          )
        );
    }
}
