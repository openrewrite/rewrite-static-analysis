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

@SuppressWarnings({"UnnecessaryLabelOnBreakStatement", "UnusedLabel", "unused"})
class FindNonCaseLabelsInSwitchTest implements RewriteTest {

    @Override
    public void defaults(RecipeSpec spec) {
        spec.recipe(new FindNonCaseLabelsInSwitch());
    }

    @DocumentExample
    @Test
    void labelNamedLikeEnumConstantWithoutCase() {
        rewriteRun(
          //language=java
          java(
            """
              class Test {
                  enum Day { MONDAY, TUESDAY, WEDNESDAY }

                  void test(Day day) {
                      switch (day) {
                          case MONDAY:
                          case TUESDAY:
                          WEDNESDAY:
                              System.out.println(day);
                              break;
                      }
                  }
              }
              """,
            """
              class Test {
                  enum Day { MONDAY, TUESDAY, WEDNESDAY }

                  void test(Day day) {
                      switch (day) {
                          case MONDAY:
                          case TUESDAY:
                          /*~~(`WEDNESDAY` is a statement label, not a `case`, so `WEDNESDAY` never matches. Add the missing `case` keyword.)~~>*/WEDNESDAY:
                              System.out.println(day);
                              break;
                      }
                  }
              }
              """
          )
        );
    }

    @Test
    void labeledLoopInCase() {
        rewriteRun(
          //language=java
          java(
            """
              class Test {
                  enum Day { MONDAY, TUESDAY, WEDNESDAY }

                  void test(Day day, int x) {
                      switch (day) {
                          case MONDAY:
                              break;
                          case TUESDAY:
                              MONDAY: for (int i = 0; i < x; i++) {
                                  break MONDAY;
                              }
                              foo: for (int i = 0; i < x; i++) {
                                  break foo;
                              }
                              WEDNESDAY: for (int i = 0; i < x; i++) {
                                  continue WEDNESDAY;
                              }
                      }
                  }
              }
              """,
            """
              class Test {
                  enum Day { MONDAY, TUESDAY, WEDNESDAY }

                  void test(Day day, int x) {
                      switch (day) {
                          case MONDAY:
                              break;
                          case TUESDAY:
                              /*~~(Label `MONDAY` in a `switch` case is easily confused with a `case` label.)~~>*/MONDAY: for (int i = 0; i < x; i++) {
                                  break MONDAY;
                              }
                              /*~~(Label `foo` in a `switch` case is easily confused with a `case` label.)~~>*/foo: for (int i = 0; i < x; i++) {
                                  break foo;
                              }
                              /*~~(Label `WEDNESDAY` in a `switch` case is easily confused with a `case` label.)~~>*/WEDNESDAY: for (int i = 0; i < x; i++) {
                                  continue WEDNESDAY;
                              }
                      }
                  }
              }
              """
          )
        );
    }

    @Test
    void switchExpressionWithColonCases() {
        rewriteRun(
          //language=java
          java(
            """
              class Test {
                  int test(int s, int x) {
                      return switch (s) {
                          case 1:
                              foo: {
                                  if (x > 0) break foo;
                                  System.out.println(x);
                              }
                              yield 1;
                          default:
                              yield 0;
                      };
                  }
              }
              """,
            """
              class Test {
                  int test(int s, int x) {
                      return switch (s) {
                          case 1:
                              /*~~(Label `foo` in a `switch` case is easily confused with a `case` label.)~~>*/foo: {
                                  if (x > 0) break foo;
                                  System.out.println(x);
                              }
                              yield 1;
                          default:
                              yield 0;
                      };
                  }
              }
              """
          )
        );
    }

    @Test
    void unchangedLabelNestedInCase() {
        rewriteRun(
          //language=java
          java(
            """
              class Test {
                  void test(int s, int x) {
                      switch (s) {
                          case 1:
                              if (x > 0) {
                                  foo: for (int i = 0; i < x; i++) {
                                      break foo;
                                  }
                              }
                              break;
                          case 2: {
                              bar: for (int i = 0; i < x; i++) {
                                  break bar;
                              }
                          }
                      }
                  }
              }
              """
          )
        );
    }

    @Test
    void unchangedLabelInArrowCase() {
        rewriteRun(
          //language=java
          java(
            """
              class Test {
                  void test(int s, int x) {
                      switch (s) {
                          case 1 -> {
                              foo: for (int i = 0; i < x; i++) {
                                  break foo;
                              }
                          }
                          default -> {
                          }
                      }
                  }
              }
              """
          )
        );
    }

    @Test
    void unchangedLabelOutsideSwitch() {
        rewriteRun(
          //language=java
          java(
            """
              class Test {
                  void test(int x) {
                      foo: for (int i = 0; i < x; i++) {
                          break foo;
                      }
                  }
              }
              """
          )
        );
    }

    @Test
    void labelInDefault() {
        rewriteRun(
          //language=java
          java(
            """
              class Test {
                  void test(int s, int x) {
                      switch (s) {
                          case 1:
                              break;
                          default:
                              foo: for (int i = 0; i < x; i++) {
                                  break foo;
                              }
                      }
                  }
              }
              """,
            """
              class Test {
                  void test(int s, int x) {
                      switch (s) {
                          case 1:
                              break;
                          default:
                              /*~~(Label `foo` in a `switch` case is easily confused with a `case` label.)~~>*/foo: for (int i = 0; i < x; i++) {
                                  break foo;
                              }
                      }
                  }
              }
              """
          )
        );
    }

    @Test
    void labelNamedLikeQualifiedCase() {
        rewriteRun(
          //language=java
          java(
            """
              class Test {
                  enum Day { MONDAY, TUESDAY }

                  void test(Day day) {
                      switch (day) {
                          case Day.MONDAY:
                          case Day.TUESDAY:
                              break;
                          default:
                              TUESDAY:
                              System.out.println(day);
                      }
                  }
              }
              """,
            """
              class Test {
                  enum Day { MONDAY, TUESDAY }

                  void test(Day day) {
                      switch (day) {
                          case Day.MONDAY:
                          case Day.TUESDAY:
                              break;
                          default:
                              /*~~(Label `TUESDAY` in a `switch` case is easily confused with a `case` label.)~~>*/TUESDAY:
                              System.out.println(day);
                      }
                  }
              }
              """
          )
        );
    }

    @Test
    void labelInNestedSwitchChecksInnerSelector() {
        rewriteRun(
          //language=java
          java(
            """
              class Test {
                  enum Day { MONDAY, TUESDAY }

                  void test(Day day, int s) {
                      switch (day) {
                          case MONDAY:
                              switch (s) {
                                  case 1:
                                      TUESDAY:
                                      System.out.println(s);
                                      break;
                              }
                              break;
                      }
                  }
              }
              """,
            """
              class Test {
                  enum Day { MONDAY, TUESDAY }

                  void test(Day day, int s) {
                      switch (day) {
                          case MONDAY:
                              switch (s) {
                                  case 1:
                                      /*~~(Label `TUESDAY` in a `switch` case is easily confused with a `case` label.)~~>*/TUESDAY:
                                      System.out.println(s);
                                      break;
                              }
                              break;
                      }
                  }
              }
              """
          )
        );
    }

    @Test
    void labelNameReusedInLambdaAndAnonymousClass() {
        rewriteRun(
          //language=java
          java(
            """
              class Test {
                  enum Day { MONDAY, TUESDAY }

                  void test(Day day, int x) {
                      switch (day) {
                          case MONDAY:
                          TUESDAY: {
                              Runnable r = () -> {
                                  TUESDAY: for (int i = 0; i < x; i++) {
                                      break TUESDAY;
                                  }
                              };
                              Object o = new Object() {
                                  void run() {
                                      TUESDAY: for (int i = 0; i < x; i++) {
                                          continue TUESDAY;
                                      }
                                  }
                              };
                          }
                      }
                  }
              }
              """,
            """
              class Test {
                  enum Day { MONDAY, TUESDAY }

                  void test(Day day, int x) {
                      switch (day) {
                          case MONDAY:
                          /*~~(`TUESDAY` is a statement label, not a `case`, so `TUESDAY` never matches. Add the missing `case` keyword.)~~>*/TUESDAY: {
                              Runnable r = () -> {
                                  TUESDAY: for (int i = 0; i < x; i++) {
                                      break TUESDAY;
                                  }
                              };
                              Object o = new Object() {
                                  void run() {
                                      TUESDAY: for (int i = 0; i < x; i++) {
                                          continue TUESDAY;
                                      }
                                  }
                              };
                          }
                      }
                  }
              }
              """
          )
        );
    }

    @Test
    void enumConstantLabelReferencedByBreak() {
        rewriteRun(
          //language=java
          java(
            """
              class Test {
                  enum Day { MONDAY, TUESDAY }

                  void test(Day day, int x) {
                      switch (day) {
                          case MONDAY:
                              TUESDAY: for (int i = 0; i < x; i++) {
                                  break TUESDAY;
                              }
                      }
                  }
              }
              """,
            """
              class Test {
                  enum Day { MONDAY, TUESDAY }

                  void test(Day day, int x) {
                      switch (day) {
                          case MONDAY:
                              /*~~(Label `TUESDAY` in a `switch` case is easily confused with a `case` label.)~~>*/TUESDAY: for (int i = 0; i < x; i++) {
                                  break TUESDAY;
                              }
                      }
                  }
              }
              """
          )
        );
    }

    @Test
    void labelNamedLikeExistingCase() {
        rewriteRun(
          //language=java
          java(
            """
              class Test {
                  enum Day { MONDAY, TUESDAY }

                  void test(Day day) {
                      switch (day) {
                          case MONDAY:
                          case TUESDAY:
                              break;
                          default:
                              TUESDAY:
                              System.out.println(day);
                      }
                  }
              }
              """,
            """
              class Test {
                  enum Day { MONDAY, TUESDAY }

                  void test(Day day) {
                      switch (day) {
                          case MONDAY:
                          case TUESDAY:
                              break;
                          default:
                              /*~~(Label `TUESDAY` in a `switch` case is easily confused with a `case` label.)~~>*/TUESDAY:
                              System.out.println(day);
                      }
                  }
              }
              """
          )
        );
    }
}
