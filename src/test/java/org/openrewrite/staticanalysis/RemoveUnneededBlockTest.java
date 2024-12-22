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
import org.openrewrite.test.RecipeSpec;
import org.openrewrite.test.RewriteTest;

import static org.openrewrite.java.Assertions.java;

@SuppressWarnings({"UnusedLabel", "StatementWithEmptyBody", "Convert2Diamond", "ConstantConditions", "ClassInitializerMayBeStatic"})
class RemoveUnneededBlockTest implements RewriteTest {
    @Override
    public void defaults(RecipeSpec spec) {
        spec.recipe(new RemoveUnneededBlock());
    }

    @Test
    void doNotChangeMethod() {
        rewriteRun(
          //language=java
          java(
            """
              class A {
                  void test() {
                  }
              }
              """
          )
        );
    }

    @Test
    void doNotChangeLabeledBlock() {
        rewriteRun(
          //language=java
          java(
            """
              class A {
                  void test() {
                      testLabel: {
                          System.out.println("hello!");
                      }
                  }
              }
              """
          )
        );
    }

    @Test
    void doNotChangeEmptyIfBlock() {
        rewriteRun(
          //language=java
          java(
            """
              class A {
                  void test() {
                      if(true) { }
                  }
              }
              """
          )
        );
    }

    @Test
    void doNotRemoveDoubleBraceInitBlocksInMethod() {
        rewriteRun(
          //language=java
          java(
            """
              import java.util.HashSet;
              import java.util.Set;
              public class T {
                  public void whenInitializeSetWithDoubleBraces_containsElements() {
                      Set<String> countries = new HashSet<String>() {
                          {
                             add("a");
                             add("b");
                          }
                      };
                  }
              }
              """
          )
        );
    }

    @Test
    void doNotRemoveDoubleBraceInitBlocks() {
        rewriteRun(
          //language=java
          java(
            """
              import java.util.HashSet;
              import java.util.Set;
              public class T {
                  final Set<String> countries = new HashSet<String>() {
                      {
                         add("a");
                         add("b");
                      }
                  };
              }
              """
          )
        );
    }

    @Test
    void doNotRemoveObjectArrayInitializer() {
        rewriteRun(
          //language=java
          java(
            """
              public class A {
                  Object[] a = new Object[] {
                      "a",
                      "b"
                  };
              }
              """
          )
        );
    }

    @Test
    void doNotRemoveObjectArrayArrayInitializer() {
        rewriteRun(
          //language=java
          java(
            """
              public class A {
                  Object[][] a = new Object[][] {
                      { "a", "b" },
                      { "c", "d" }
                  };
              }
              """
          )
        );
    }

    @DocumentExample
    @Test
    void simplifyNestedBlock() {
        rewriteRun(
          //language=java
          java(
            """
              public class A {
                  void test() {
                      {
                          System.out.println("hello!");
                      }
                  }
              }
              """,
            """
              public class A {
                  void test() {
                      System.out.println("hello!");
                  }
              }
              """
          )
        );
    }

    @Test
    void simplifyDoublyNestedBlock() {
        rewriteRun(
          //language=java
          java(
            """
              public class A {
                  void test() {
                      {
                           { System.out.println("hello!"); }
                      }
                  }
              }
              """,
            """
              public class A {
                  void test() {
                      System.out.println("hello!");
                  }
              }
              """
          )
        );
    }

    @Test
    void simplifyBlockNestedInIfBlock() {
        rewriteRun(
          //language=java
          java(
            """
              public class A {
                  void test() {
                      if (true) {
                           { System.out.println("hello!"); }
                      }
                  }
              }
              """,
            """
              public class A {
                  void test() {
                      if (true) {
                          System.out.println("hello!");
                      }
                  }
              }
              """
          )
        );
    }

    @Test
    void simplifyBlockInStaticInitializerIfBlock() {
        rewriteRun(
          //language=java
          java(
            """
              public class A {
                  static {
                      {
                           {
                              System.out.println("hello static!");
                              System.out.println("goodbye static!");
                           }
                      }
                  }

                  {
                      {
                          System.out.println("hello init!");
                          System.out.println("goodbye init!");
                      }
                  }
              }
              """,
            """
              public class A {
                  static {
                      System.out.println("hello static!");
                      System.out.println("goodbye static!");
                  }

                  {
                      System.out.println("hello init!");
                      System.out.println("goodbye init!");
                  }
              }
              """
          )
        );
    }

    @Test
    void simplifyCraziness() {
        rewriteRun(
          //language=java
          java(
            """
              import java.util.HashSet;
              import java.util.Set;
              public class A {
                  static {
                      {
                           new HashSet<String>() {
                              {
                                  add("a");
                                  add("b");
                                  {
                                      System.out.println("hello static!");
                                      System.out.println("goodbye static!");
                                  }
                              }
                           };
                      }
                  }

                  {
                      {
                           new HashSet<String>() {
                              {
                                  add("a");
                                  add("b");
                                  {
                                      System.out.println("hello init!");
                                      System.out.println("goodbye init!");
                                  }
                              }
                           };
                      }
                  }
              }
              """,
            """
              import java.util.HashSet;
              import java.util.Set;
              public class A {
                  static {
                      new HashSet<String>() {
                          {
                              add("a");
                              add("b");
                              System.out.println("hello static!");
                              System.out.println("goodbye static!");
                          }
                      };
                  }

                  {
                      new HashSet<String>() {
                          {
                              add("a");
                              add("b");
                              System.out.println("hello init!");
                              System.out.println("goodbye init!");
                          }
                      };
                  }
              }
              """
          )
        );
    }

    @Test
    void simplifyDoesNotFormatSurroundingCode() {
        rewriteRun(
          //language=java
          java(
            """
              public class A {
                  static int[] a;
                  static int[] b;
                  static {
                      a = new int[] { 1, 2, 3 };
                      b = new int[] {4,5,6};
                      {
                          System.out.println("hello static!");
                      }
                  }
              }
              """,
            """
              public class A {
                  static int[] a;
                  static int[] b;
                  static {
                      a = new int[] { 1, 2, 3 };
                      b = new int[] {4,5,6};
                      System.out.println("hello static!");
                  }
              }
              """
          )
        );
    }

    @Test
    void simplifyDoesNotFormatInternalCode() {
        rewriteRun(
          //language=java
          java(
            """
              public class A {
                  int[] a;
                  int[] b;
                  static {
                      a = new int[] { 1, 2, 3 };
                      b = new int[] {4,5,6};
                      {
                          System.out.println("hello!");
                          System.out.println( "world!" );
                      }
                  }
              }
              """,
            """
              public class A {
                  int[] a;
                  int[] b;
                  static {
                      a = new int[] { 1, 2, 3 };
                      b = new int[] {4,5,6};
                      System.out.println("hello!");
                      System.out.println("world!");
                  }
              }
              """
          )
        );
    }

    @Test
    @Issue("https://github.com/openrewrite/rewrite/issues/3073")
    void preserveComments() {
        rewriteRun(
          //language=java
          java(
            """
              public class A {
                  static {
                      // comment before (outside)
                      {
                          // comment before (inside)
                          System.out.println("hello world!");
                          // comment after (inside)
                      }
                      // comment after (outside)
                  }
              }
              """,
            """
              public class A {
                  static {
                      // comment before (outside)
                      // comment before (inside)
                      System.out.println("hello world!");
                      // comment after (inside)
                      // comment after (outside)
                  }
              }
              """
          )
        );
    }

    @Test
    void preserveBlocksContainingVariableDeclarations() {
        rewriteRun(
          //language=java
          java(
            """
              public class A {
                  static {
                      {
                          int i = 0;
                      }
                      System.out.println("hello world!");
                  }
              }
              """
          )
        );
    }

    @Test
    void inlineLastBlockContainingVariableDeclarations() {
        rewriteRun(
          //language=java
          java(
            """
              public class A {
                  static {
                      {
                          System.out.println("hello world!");
                      }
                      {
                          int i = 0;
                      }
                  }
              }
              """,
            """
              public class A {
                  static {
                      System.out.println("hello world!");
                      int i = 0;
                  }
              }
              """
          )
        );
    }

    @Test
    @SuppressWarnings("EmptyFinallyBlock")
    void removeEmptyTryFinallyBlock() {
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
              """,
            """
              public class A {
                  public int foo() {
                      int i = 1;
                  }
              }
              """
          )
        );
    }

    @Test
    void keepNonEmptyTryFinallyBlock() {
        rewriteRun(
          //language=java
          java(
            """
              public class A {
                  public int foo() {
                      try {
                          int i = 1;
                      } finally {
                          System.out.println("hello world!");
                      }
                  }
              }
              """
          )
        );
    }

    @Test
    @SuppressWarnings("EmptyFinallyBlock")
    void keepNonEmptyTryFinallyBlock2() {
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
                      int i = 1;
                  }
              }
              """
          )
        );
    }

}
