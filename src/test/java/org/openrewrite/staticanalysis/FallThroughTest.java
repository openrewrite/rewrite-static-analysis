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
import org.junitpioneer.jupiter.ExpectedToFail;
import org.openrewrite.DocumentExample;
import org.openrewrite.Issue;
import org.openrewrite.Tree;
import org.openrewrite.java.JavaParser;
import org.openrewrite.java.style.FallThroughStyle;
import org.openrewrite.style.NamedStyles;
import org.openrewrite.test.RecipeSpec;
import org.openrewrite.test.RewriteTest;

import static java.util.Collections.emptySet;
import static java.util.Collections.singletonList;
import static org.openrewrite.java.Assertions.java;

@SuppressWarnings({"EnhancedSwitchMigration", "ConstantConditions", "StatementWithEmptyBody", "SwitchStatementWithTooFewBranches", "ReassignedVariable", "UnusedAssignment"})
class FallThroughTest implements RewriteTest {
    @Override
    public void defaults(RecipeSpec spec) {
        spec.recipe(new FallThrough());
    }

    @Test
    @DocumentExample
    void addBreakWhenPreviousCaseHasCodeButLacksBreak() {
        rewriteRun(
          //language=java
          java(
            """
              public class A {
                  int i;
                  {
                      switch (i) {
                      case 0:
                          i++;
                      case 99:
                          i++;
                      }
                  }
              }
              """,
            """
              public class A {
                  int i;
                  {
                      switch (i) {
                      case 0:
                          i++;
                          break;
                      case 99:
                          i++;
                      }
                  }
              }
              """
          )
        );
    }

    @Test
    @Issue("https://github.com/openrewrite/rewrite-static-analysis/issues/173")
    void switchInSwitch() {
        //language=java
        rewriteRun(
          java(
            """
              class Test {
                  void test(int day) {
                      switch (day) {
                          case 1:
                              int month = 1;
                              switch (month) {
                                  case 1:
                                      return "January";
                                  default:
                                      return "no valid month";
                              }
                          default:
                              return "No valid day";
                      }
                  }
              }
              """
          )
        );
    }

    @Test
    void switchExpressions() {
        rewriteRun(
          //language=java
          java(
            """
              class Test {
                  int test(int n) {
                      return switch(n) {
                         case 1 -> n+1;
                         case 2 -> n+2;
                         default -> n;
                      };
                  }
              }
              """
          )
        );
    }

    @Test
    void doNotAddBreakWhenPreviousCaseDoesNotContainCode() {
        rewriteRun(
          //language=java
          java(
            """
              public class A {
                  int i;
                  {
                      switch (i) {
                      case 0:
                      case 99:
                          i++;
                      }
                  }
              }
              """
          )
        );
    }

    @Test
    void checkLastCaseGroupAddsBreakToLastCase() {
        rewriteRun(
          spec -> spec.parser(JavaParser.fromJavaVersion().styles(
            singletonList(
              new NamedStyles(
                Tree.randomId(), "test", "test", "test", emptySet(),
                singletonList(new FallThroughStyle(true)))))
          ),
          //language=java
          java(
            """
              public class A {
                  int i;
                  {
                      switch (i) {
                      case 0:
                      case 99:
                          i++;
                      }
                  }
              }
              """,
            """
              public class A {
                  int i;
                  {
                      switch (i) {
                      case 0:
                      case 99:
                          i++;
                          break;
                      }
                  }
              }
              """
          )
        );
    }

    @Test
    void acceptableStatementsAreBreakOrReturnOrThrowOrContinue() {
        rewriteRun(
          //language=java
          java(
            """
              public class A {
                  int i;
                  {
                      switch (i) {
                      case 0:
                          i++;
                          break;
                      case 1:
                          i++;
                          return;
                      case 2:
                          i++;
                          throw new Exception();
                      case 3:
                          i++;
                          continue;
                      }
                  }
              }
              """
          )
        );
    }

    @Test
    void reliefPatternExpectedMatchesVariations() {
        rewriteRun(
          //language=java
          java(
            """
              public class A {
                  int i;
                  {
                      switch (i) {
                      case 0:
                          i++; // fall through
                      case 1:
                          i++; // falls through
                      case 2:
                          i++; // fallthrough
                      case 3:
                          i++; // fallthrough
                      case 4:
                          i++; // fall-through
                      case 5:
                          i++; // fallthrough
                      case 99:
                          i++;
                          break;
                      }
                  }
              }
              """
          )
        );
    }

    @Test
    void handlesSwitchesWithOneOrNoneCases() {
        rewriteRun(
          //language=java
          java(
            """
              public class A {
                  public void noCase(int i) {
                      switch (i) {
                      }
                  }

                  public void oneCase(int i) {
                      switch (i) {
                          case 0:
                              i++;
                      }
                  }
              }
              """
          )
        );
    }

    @Test
    @ExpectedToFail
    @Issue("https://github.com/openrewrite/rewrite-static-analysis/issues/229")
    void switchAsLastStatement() {
        rewriteRun(
          //language=java
          java(
            """
              public class A {
                  public int m(int i) {
                      switch (i) {
                          case 0:
                              if (true) return i;
                          default:
                              throw new IllegalStateException();
                      }
                  }
              }
              """,
            """
              public class A {
                  public int m(int i) {
                      switch (i) {
                          case 0:
                              if (true) return i;
                              // fall through
                          default:
                              throw new IllegalStateException();
                      }
                  }
              }
              """
          )
        );
    }

    @Test
    void nestedBlocks() {
        rewriteRun(
          //language=java
          java(
            """
              public class A {
                  public int n(int i) {
                      switch (i) {
                          case 1:
                              try {
                                  if (true) {
                                      return 1;
                                  }
                              } catch (Exception e) {
                                  if (true) {
                                      return 1;
                                  }
                              }
                          default:
                              throw new IllegalStateException();
                      }
                  }
              }
              """,
            """
              public class A {
                  public int n(int i) {
                      switch (i) {
                          case 1:
                              try {
                                  if (true) {
                                      return 1;
                                  }
                              } catch (Exception e) {
                                  if (true) {
                                      return 1;
                                  }
                              }
                              break;
                          default:
                              throw new IllegalStateException();
                      }
                  }
              }
              """
          )
        );
    }

    @Test
    void abortOnAbruptCompletion() {
        rewriteRun(
          //language=java
          java(
            """
              public class A {
                  public void noCase(int i) {
                      for (;;) {
                          switch (i) {
                              case 0:
                                  if (true)
                                      return;
                                  else
                                      break;
                              case 1:
                                  if (true)
                                      return;
                                  else {
                                      {
                                          continue;
                                      }
                                  }
                              case 2:
                                  try {
                                      return;
                                  } catch (Exception e) {
                                      return;
                                  }
                              default:
                                  System.out.println("default");
                          }
                      }
                  }
              }
              """
          )
        );
    }

    @Test
    void addBreaksFallthroughCasesComprehensive() {
        rewriteRun(
          //language=java
          java(
            """
              public class A {
                  int i;
                  {
                      switch (i) {
                      case 0:
                          i++; // fall through

                      case 1:
                          i++;
                          // falls through
                      case 2:
                      case 3: {{
                      }}
                      case 4: {
                          i++;
                      }
                      // fallthrough
                      case 5:
                          i++;
                      /* fallthrough */case 6:
                          i++;
                          // fall-through
                      case 7:
                          i++;
                          break;
                      case 8: {
                          // fallthrough
                      }
                      case 9:
                          i++;
                      }
                  }
              }
              """,
            """
              public class A {
                  int i;
                  {
                      switch (i) {
                      case 0:
                          i++; // fall through

                      case 1:
                          i++;
                          // falls through
                      case 2:
                      case 3: {{
                          break;
                      }}
                      case 4: {
                          i++;
                      }
                      // fallthrough
                      case 5:
                          i++;
                      /* fallthrough */case 6:
                          i++;
                          // fall-through
                      case 7:
                          i++;
                          break;
                      case 8: {
                          // fallthrough
                      }
                      case 9:
                          i++;
                      }
                  }
              }
              """
          )
        );
    }

    @Test
    void infiniteLoopWithBreak() {
        rewriteRun(
          //language=java
          java (
            """
              enum Enum {
                  A, B
              }
              public class Test {
                  void foo(Enum a) {
                      switch(a) {
                          case A:
                              while (true) {
                                  break;
                              }
                          default:
                      }
                  }
              }
              """,
            """
              enum Enum {
                  A, B
              }
              public class Test {
                  void foo(Enum a) {
                      switch(a) {
                          case A:
                              while (true) {
                                  break;
                              }
                              break;
                          default:
                      }
                  }
              }
              """
          )
        );
    }

    @Test
    void nestedSwitch() {
        rewriteRun(
          //language=java
          java(
            """
              enum Enum {
                  A, B
              }
              public class Test {
                  void foo(Enum a) {
                      switch(a) {
                          case A:
                          default:
                              switch (a) {
                                  case B:
                                      System.out.println("B");
                                  default:
                                      System.out.print("other");
                              }
                      }
                  }
              }
              """,
            """
              enum Enum {
                  A, B
              }
              public class Test {
                  void foo(Enum a) {
                      switch(a) {
                          case A:
                          default:
                              switch (a) {
                                  case B:
                                      System.out.println("B");
                                      break;
                                  default:
                                      System.out.print("other");
                              }
                      }
                  }
              }
              """
          )
        );
    }

    @Test
    void returnNestedInLiteralTrue() {
        rewriteRun(
          //language=java
          java(
            """
              enum Enum {
                  A, B, C, D
              }
              public class Test {
                  void foo(Enum a) {
                      switch(a) {
                          case A:
                              for (; true; ) {
                                  return;
                              }
                          case B:
                              while (true) {
                                  return;
                              }
                          case C:
                              for (; true; ) {
                                  if (false) {
                                      break;
                                  }
                                  return;
                              }
                          case D:
                              while (true) {
                                  if (false) {
                                      break;
                                  }
                                  return;
                              }
                          default:
                      }
                  }
              }
              """,
            """
              enum Enum {
                  A, B, C, D
              }
              public class Test {
                  void foo(Enum a) {
                      switch(a) {
                          case A:
                              for (; true; ) {
                                  return;
                              }
                          case B:
                              while (true) {
                                  return;
                              }
                          case C:
                              for (; true; ) {
                                  if (false) {
                                      break;
                                  }
                                  return;
                              }
                              break;
                          case D:
                              while (true) {
                                  if (false) {
                                      break;
                                  }
                                  return;
                              }
                              break;
                          default:
                      }
                  }
              }
              """
          )
        );
    }
    @Test
    void returnNestedInInfiniteForLoop() {
        rewriteRun(
          //language=java
          java(
            """
              enum Enum {
                  A, B
              }
              public class Test {
                  void foo(Enum a) {
                      switch(a) {
                          case A:
                              for (; ; ) {
                                  return;
                              }
                          case B:
                              for (; ; ) {
                                  if (false) {
                                      break;
                                  }
                                  return;
                              }
                          default:
                      }
                  }
              }
              """,
            """
              enum Enum {
                  A, B
              }
              public class Test {
                  void foo(Enum a) {
                      switch(a) {
                          case A:
                              for (; ; ) {
                                  return;
                              }
                          case B:
                              for (; ; ) {
                                  if (false) {
                                      break;
                                  }
                                  return;
                              }
                              break;
                          default:
                      }
                  }
              }
              """
          )
        );
    }

    @Test
    void returnNestedInNonFinalBooleanInfiniteLoop() {
        rewriteRun(
          //language=java
          java(
            """
              enum Enum {
                  A
              }
              public class Test {
                  void foo(Enum a) {
                      boolean b = true;
                      switch(a) {
                          case A:
                              while (b) {
                                  return;
                              }
                          default:
                      }
                  }
              }
              """,
            """
              enum Enum {
                  A
              }
              public class Test {
                  void foo(Enum a) {
                      boolean b = true;
                      switch(a) {
                          case A:
                              while (b) {
                                  return;
                              }
                              break;
                          default:
                      }
                  }
              }
              """
          )
        );
    }

    @Test
    void returnNestedInFinalBooleanInfiniteLoop() {
        //language=java
        rewriteRun(
          java(
            """
              enum Enum {
                  A, B, C
              }
              public class Test {

                  final boolean classBoolean = true;

                  void foo(Enum a) {
                      final boolean methodBoolean = true;
                      switch(a) {
                          case A:
                              while (classBoolean) {
                                  return;
                              }
                          case B:
                              while (methodBoolean) {
                                  return;
                              }
                          case C:
                              final boolean caseBoolean = true;
                              while (caseBoolean) {
                                  return;
                              }
                          default:
                      }
                  }
              }
              """
          )
        );
    }
}
