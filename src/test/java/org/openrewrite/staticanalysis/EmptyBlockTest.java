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
import org.openrewrite.java.style.Checkstyle;
import org.openrewrite.java.style.EmptyBlockStyle;
import org.openrewrite.style.NamedStyles;
import org.openrewrite.test.RecipeSpec;
import org.openrewrite.test.RewriteTest;

import java.util.Set;

import static org.junit.jupiter.api.Assumptions.assumeTrue;
import static org.openrewrite.Tree.randomId;
import static org.openrewrite.golang.Assertions.go;
import static org.openrewrite.java.Assertions.java;
import static org.openrewrite.scala.Assertions.scala;

@SuppressWarnings({"ClassInitializerMayBeStatic", "StatementWithEmptyBody", "ConstantConditions", "SynchronizationOnLocalVariableOrMethodParameter", "CatchMayIgnoreException", "EmptyFinallyBlock", "InfiniteLoopStatement", "UnnecessaryContinue", "EmptyClassInitializer", "EmptyTryBlock", "resource", "RedundantFileCreation", "ExpressionComparedToItself"})
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
          java(
            //language=java
            """
              public class A {
                  {
                      // comment
                  }
              }
              """,
            spec -> spec.markers(
              new NamedStyles(
                randomId(),
                "Checkstyle",
                "Checkstyle",
                "Checkstyle defaults to only preserving blocks but the recipe should support other configurations.",
                      Set.of(),
                      Set.of(Checkstyle.emptyBlock().withBlockPolicy(
                              EmptyBlockStyle.BlockPolicy.TEXT))
              )
            )
          )
        );
    }

    @SuppressWarnings("EmptySynchronizedStatement")
    @Test
    void unchangedEmptySynchronized() {
        rewriteRun(
          //language=java
          java(
            """
              class A {
                  void foo(Object lock) {
                      synchronized (lock) {
                      }
                  }
              }
              """
          )
        );
    }

    @Test
    void emptySwitchKeepsSelectorSideEffects() {
        rewriteRun(
          //language=java
          java(
            """
              class A {
                  void foo(java.util.function.IntSupplier sel) {
                      switch (sel.getAsInt()) {
                      }
                  }
              }
              """,
            """
              class A {
                  void foo(java.util.function.IntSupplier sel) {
                      sel.getAsInt();
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
    void emptyTryKeepsFinallyStatements() {
        rewriteRun(
          //language=java
          java(
            """
              class A {
                  void foo(Runnable r) {
                      // release
                      try {
                      } finally {
                          r.run();
                          r.run();
                      }
                  }
              }
              """,
            """
              class A {
                  void foo(Runnable r) {
                      // release
                      r.run();
                      r.run();
                  }
              }
              """
          )
        );
    }

    @Test
    void emptyTryKeepsFinallyDeclarationsInBlock() {
        rewriteRun(
          //language=java
          java(
            """
              class A {
                  void foo(Runnable r) {
                      try {
                      } finally {
                          Runnable s = r;
                          s.run();
                      }
                      Runnable s = r;
                  }
              }
              """,
            """
              class A {
                  void foo(Runnable r) {
                      {
                          Runnable s = r;
                          s.run();
                      }
                      Runnable s = r;
                  }
              }
              """
          )
        );
    }

    @Test
    void emptyTryKeepsFinallyFormattingAndNestedFixes() {
        rewriteRun(
          //language=java
          java(
            """
              class A {
                  void foo(java.util.function.BooleanSupplier b) {
                      try {
                      } finally {
                          if (b.getAsBoolean()) {
                          }
                          System.out.println(new int[]{1,2});
                      }
                  }
              }
              """,
            """
              class A {
                  void foo(java.util.function.BooleanSupplier b) {
                      b.getAsBoolean();
                      System.out.println(new int[]{1,2});
                  }
              }
              """
          )
        );
    }

    @Test
    void emptyTryKeepsFinallyPatternVariablesAndCommentsInBlock() {
        rewriteRun(
          //language=java
          java(
            """
              class A {
                  void foo(Object o, Runnable r) {
                      try {
                      } finally {
                          if (!(o instanceof String s)) {
                              throw new IllegalStateException();
                          }
                          System.out.println(s);
                      }
                      String s = "";
                      try {
                      } finally {
                          r.run();
                          // released
                      }
                  }
              }
              """,
            """
              class A {
                  void foo(Object o, Runnable r) {
                      {
                          if (!(o instanceof String s)) {
                              throw new IllegalStateException();
                          }
                          System.out.println(s);
                      }
                      String s = "";
                      {
                          r.run();
                          // released
                      }
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

    @Issue("https://github.com/openrewrite/rewrite-static-analysis/issues/570")
    @Test
    void emptyLoopsUnchanged() {
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
    void doNotExtractConditionallyEvaluatedSideEffects() {
        rewriteRun(
          //language=java
          java(
            """
              import java.util.regex.Matcher;

              class A {
                  void foo(Matcher m, boolean b, int[] arr, java.util.function.IntSupplier i) {
                      if (m.find() && m.group(1) != null) {
                      }
                      if (b || m.find()) {
                      }
                      if (b ? m.find() : m.hitEnd()) {
                      }
                      if (arr[i.getAsInt()] == 0) {
                      }
                  }
              }
              """
          )
        );
    }

    @Test
    void extractSideEffectsFromShortCircuitLeftOperand() {
        rewriteRun(
          //language=java
          java(
            """
              import java.util.regex.Matcher;

              class A {
                  void foo(Matcher m, boolean b) {
                      if (m.find() && b) {
                      }
                  }
              }
              """,
            """
              import java.util.regex.Matcher;

              class A {
                  void foo(Matcher m, boolean b) {
                      m.find();
                  }
              }
              """
          )
        );
    }

    @Test
    void doNotInvertFloatingPointComparison() {
        rewriteRun(
          //language=java
          java(
            """
              class A {
                  void foo(double d, Float f) {
                      if (d > 0) {
                      } else {
                          throw new IllegalArgumentException();
                      }
                      if (f <= 1) {
                      } else {
                          throw new IllegalArgumentException();
                      }
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

    @Test
    void empty2() {
        rewriteRun(
          java(
            """
            public class WithEmptyBlock {
              public void shouldNotFlipCondition() {
                  if (1 > 0 || 0 > 1) {
                  } else {
                    System.out.println("wrong");
                  }
              }
            }
            """
          )
        );
    }

    @Test
    void invertIfWithEmptyThenInGo() {
        assumeTrue(GoEngineTestListener.isAvailable(), "rewrite-go-rpc engine unavailable");
        rewriteRun(
          go(
            """
              package main

              func test() {
                  if err := doSomething(); err == nil {
                  } else {
                      handle(err)
                  }
              }
              """,
            """
              package main

              func test() {
                  if err := doSomething(); err != nil {
                      handle(err)
                  }
              }
              """
          )
        );
    }

    @Test
    void scalaEmptyIfBlock() {
        rewriteRun(
          //language=scala
          scala(
            """
              class A {
                def foo(a: Boolean): Unit = {
                  if (a) {
                  }
                }
              }
              """,
            """
              class A {
                def foo(a: Boolean): Unit = {
                }
              }
              """
          )
        );
    }
}
