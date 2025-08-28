/*
 * Copyright 2025 the original author or authors.
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
import org.openrewrite.test.RecipeSpec;
import org.openrewrite.test.RewriteTest;

import static org.openrewrite.java.Assertions.java;

class SimplifyElseBranchTest implements RewriteTest {

    @Override
    public void defaults(RecipeSpec spec) {
        spec.recipe(new SimplifyElseBranch());
    }

    @DocumentExample
    @Issue("https://github.com/openrewrite/rewrite-static-analysis/issues/666")
    @Test
    void simplifyElseBranch() {
        rewriteRun(
          // language=java
          java(
            """
              class A {
                  void a(String password) {
                      if (password.length() < 6) {
                          System.out.println("Password is too short.");
                      } else {
                          if (password.length() > 12) {
                              System.out.println("Password is too long.");
                          }
                      }
                  }
              }
              """,
            """
              class A {
                  void a(String password) {
                      if (password.length() < 6) {
                          System.out.println("Password is too short.");
                      } else if (password.length() > 12) {
                          System.out.println("Password is too long.");
                      }
                  }
              }
              """
          )
        );
    }

    @Test
    void noSimplifyWithTwoStatementsInElseBranch() {
        rewriteRun(
          // language=java
          java(
            """
              class A {
                  void a(String password) {
                      if (password.length() < 6) {
                          System.out.println("Password is too short.");
                      } else {
                          if (password.length() > 12) {
                              System.out.println("Password is too long.");
                          }
                          System.out.println("Password is six or more characters long.");
                      }
                  }
              }
              """
          )
        );
    }

    @Test
    void simplifyIfWithElse() {
        rewriteRun(
          // language=java
          java(
            """
              class A {
                  void a(String password) {
                      if (password.length() < 6) {
                          System.out.println("Password is too short.");
                      } else {
                          if (password.length() <= 12) {
                              System.out.println("Password is ok.");
                          } else {
                              System.out.println("Password is too long.");
                          }
                      }
                  }
              }
              """,
            """
              class A {
                  void a(String password) {
                      if (password.length() < 6) {
                          System.out.println("Password is too short.");
                      } else if (password.length() <= 12) {
                          System.out.println("Password is ok.");
                      } else {
                          System.out.println("Password is too long.");
                      }
                  }
              }
              """
          )
        );
    }

    @Test
    void simplifyIfWithElseIf() {
        rewriteRun(
          // language=java
          java(
            """
              class A {
                  void a(String password) {
                      if (password.length() < 6) {
                          System.out.println("Password is too short.");
                      } else {
                          if (password.length() == 6) {
                              System.out.println("Password is six characters long.");
                          } else if (password.length() == 7) {
                              System.out.println("Password is seven characters long.");
                          } else if (password.length() == 8) {
                              System.out.println("Password is eight characters long.");
                          } else if (password.length() <= 12) {
                              System.out.println("Password is ok.");
                          } else {
                              System.out.println("Password is too long.");
                          }
                      }
                  }
              }
              """,
            """
              class A {
                  void a(String password) {
                      if (password.length() < 6) {
                          System.out.println("Password is too short.");
                      } else if (password.length() == 6) {
                          System.out.println("Password is six characters long.");
                      } else if (password.length() == 7) {
                          System.out.println("Password is seven characters long.");
                      } else if (password.length() == 8) {
                          System.out.println("Password is eight characters long.");
                      } else if (password.length() <= 12) {
                          System.out.println("Password is ok.");
                      } else {
                          System.out.println("Password is too long.");
                      }
                  }
              }
              """
          )
        );
    }

    @Test
    void simplifyIfWithComments() {
        rewriteRun(
          // language=java
          java(
            """
              class A {
                  void a(String password) {
                      if (password.length() < 6) {
                          System.out.println("Password is too short.");
                      } else /* Comment 0 */ { // Comment 1
                          // Comment 2
                          if (password.length() <= 12) { // Comment 3
                              // Comment 4
                              System.out.println("Password is ok.");
                          } else {
                              System.out.println("Password is too long.");
                          }
                      }
                  }
              }
              """,
            """
              class A {
                  void a(String password) {
                      if (password.length() < 6) {
                          System.out.println("Password is too short.");
                      } else /* Comment 0 */ // Comment 1
                      // Comment 2
                      if (password.length() <= 12) { // Comment 3
                          // Comment 4
                          System.out.println("Password is ok.");
                      } else {
                          System.out.println("Password is too long.");
                      }
                  }
              }
              """
          )
        );
    }

    @Test
    void simplifyNestedIfs() {
        rewriteRun(
          // language=java
          java(
            """
              class A {
                  void a(String password) {
                      if (password.length() < 6) {
                          System.out.println("Password is too short.");
                      } else {
                          if (password.length() == 6) {
                              System.out.println("Password is six characters long.");
                          } else {
                              if (password.length() == 7) {
                                  System.out.println("Password is seven characters long.");
                              } else {
                                  if (password.length() <= 12) {
                                      System.out.println("Password is ok.");
                                  } else {
                                      System.out.println("Password is too long.");
                                  }
                              }
                          }
                      }
                  }
              }
              """,
            """
              class A {
                  void a(String password) {
                      if (password.length() < 6) {
                          System.out.println("Password is too short.");
                      } else if (password.length() == 6) {
                          System.out.println("Password is six characters long.");
                      } else if (password.length() == 7) {
                          System.out.println("Password is seven characters long.");
                      } else if (password.length() <= 12) {
                          System.out.println("Password is ok.");
                      } else {
                          System.out.println("Password is too long.");
                      }
                  }
              }
              """
          )
        );
    }

    @Test
    void simplifyOuterIfWithElseIf() {
        rewriteRun(
          // language=java
          java(
            """
              class A {
                  void a(String password) {
                      if (password.length() < 6) {
                          System.out.println("Password is too short.");
                      } else if (password.length() == 6) {
                          System.out.println("Password is six characters long.");
                      } else {
                          if (password.length() <= 12) {
                              System.out.println("Password is ok.");
                          } else {
                              System.out.println("Password is too long.");
                          }
                      }
                  }
              }
              """,
            """
              class A {
                  void a(String password) {
                      if (password.length() < 6) {
                          System.out.println("Password is too short.");
                      } else if (password.length() == 6) {
                          System.out.println("Password is six characters long.");
                      } else if (password.length() <= 12) {
                          System.out.println("Password is ok.");
                      } else {
                          System.out.println("Password is too long.");
                      }
                  }
              }
              """
          )
        );
    }

    @Test
    void simplifyElseBranchWithElseBlockOnNewLine() {
        rewriteRun(
          // language=java
          java(
            """
              class A {
                  void a(String password) {
                      if (password.length() < 6)
                      {
                          System.out.println("Password is too short.");
                      }
                      else
                      {
                          if (password.length() > 12)
                          {
                              System.out.println("Password is too long.");
                          }
                      }
                  }
              }
              """,
            """
              class A {
                  void a(String password) {
                      if (password.length() < 6)
                      {
                          System.out.println("Password is too short.");
                      }
                      else if (password.length() > 12)
                      {
                          System.out.println("Password is too long.");
                      }
                  }
              }
              """
          )
        );
    }}
