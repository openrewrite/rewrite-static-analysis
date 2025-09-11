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
import org.openrewrite.test.RecipeSpec;
import org.openrewrite.test.RewriteTest;

import static org.openrewrite.java.Assertions.java;

class MoveFieldsToTopOfClassTest implements RewriteTest {

    @Override
    public void defaults(RecipeSpec spec) {
        spec.recipe(new MoveFieldsToTopOfClass());
    }

    @DocumentExample
    @Test
    void moveFieldsToTop() {
        rewriteRun(
          //language=java
          java(
            """
              class A {
                  void foo(String bar) {
                      int i = Integer.parseInt(bar);
                  }

                  // Important field
                  String myField = "important";
              }
              """,
            """
              class A {
                  // Important field
                  String myField = "important";

                  void foo(String bar) {
                      int i = Integer.parseInt(bar);
                  }
              }
              """
          )
        );
    }

    @Test
    void moveMultipleFields() {
        rewriteRun(
          //language=java
          java(
            """
              class Example {
                  public void method1() {
                      System.out.println("method1");
                  }

                  private String field1 = "value1";

                  public Example() {
                      // constructor
                  }

                  protected int field2 = 42;

                  public void method2() {
                      System.out.println("method2");
                  }

                  public static final String CONSTANT = "constant";
              }
              """,
            """
              class Example {
                  public static final String CONSTANT = "constant";

                  protected int field2 = 42;

                  private String field1 = "value1";

                  public void method1() {
                      System.out.println("method1");
                  }

                  public Example() {
                      // constructor
                  }

                  public void method2() {
                      System.out.println("method2");
                  }
              }
              """
          )
        );
    }

    @Test
    void fieldsAlreadyAtTop() {
        rewriteRun(
          //language=java
          java(
            """
              class AlreadyOrdered {
                  public static final String CONSTANT = "constant";
                  protected int field2 = 42;
                  private String field1 = "value1";

                  public void method() {
                      System.out.println("method");
                  }

                  public AlreadyOrdered() {
                      // constructor
                  }
              }
              """
          )
        );
    }

    @Test
    void onlyFieldsInClass() {
        rewriteRun(
          //language=java
          java(
            """
              class OnlyFields {
                  public static final String CONSTANT = "constant";
                  protected int field2 = 42;
                  private String field1 = "value1";
              }
              """
          )
        );
    }

    @Test
    void onlyMethodsInClass() {
        rewriteRun(
          //language=java
          java(
            """
              class OnlyMethods {
                  public void method1() {
                      System.out.println("method1");
                  }

                  public OnlyMethods() {
                      // constructor
                  }

                  public void method2() {
                      System.out.println("method2");
                  }
              }
              """
          )
        );
    }

    @Test
    void emptyClass() {
        rewriteRun(
          //language=java
          java(
            """
              class Empty {
              }
              """
          )
        );
    }

    @Test
    void fieldsBetweenMethods() {
        rewriteRun(
          //language=java
          java(
            """
              class Mixed {
                  public void firstMethod() {
                      System.out.println("first");
                  }

                  private String field1 = "value1";

                  public void secondMethod() {
                      System.out.println("second");
                  }

                  private int field2 = 10;

                  public void thirdMethod() {
                      System.out.println("third");
                  }
              }
              """,
            """
              class Mixed {
                  private String field1 = "value1";

                  private int field2 = 10;

                  public void firstMethod() {
                      System.out.println("first");
                  }

                  public void secondMethod() {
                      System.out.println("second");
                  }

                  public void thirdMethod() {
                      System.out.println("third");
                  }
              }
              """
          )
        );
    }

    @Test
    void staticAndInstanceFields() {
        rewriteRun(
          //language=java
          java(
            """
              class StaticAndInstance {
                  public void method() {
                      System.out.println("method");
                  }

                  private static final String STATIC_FIELD = "static";
                  private String instanceField = "instance";
                  public final int publicField = 100;

                  public StaticAndInstance() {
                      // constructor
                  }
              }
              """,
            """
              class StaticAndInstance {
                  public final int publicField = 100;

                  private static final String STATIC_FIELD = "static";
                  private String instanceField = "instance";

                  public void method() {
                      System.out.println("method");
                  }

                  public StaticAndInstance() {
                      // constructor
                  }
              }
              """
          )
        );
    }

    @Test
    void innerClassesAndFields() {
        rewriteRun(
          //language=java
          java(
            """
              class OuterClass {
                  public void outerMethod() {
                      System.out.println("outer method");
                  }

                  private String outerField = "outer";

                  class InnerClass {
                      public void innerMethod() {
                          System.out.println("inner method");
                      }

                      private String innerField = "inner";
                  }

                  protected int anotherOuterField = 42;
              }
              """,
            """
              class OuterClass {
                  protected int anotherOuterField = 42;

                  private String outerField = "outer";

                  public void outerMethod() {
                      System.out.println("outer method");
                  }

                  class InnerClass {
                      private String innerField = "inner";

                      public void innerMethod() {
                          System.out.println("inner method");
                      }
                  }
              }
              """
          )
        );
    }

    @Test
    void preserveCommentsWithFields() {
        rewriteRun(
          //language=java
          java(
            """
              class A {
                  void foo(String bar) {
                      int i = Integer.parseInt(bar);
                  }
              
                  // this is my field, not yours
                  String myField = "important";
              }
              """,
            """
              class A {
                  // this is my field, not yours
                  String myField = "important";
              
                  void foo(String bar) {
                      int i = Integer.parseInt(bar);
                  }
              }
              """
          )
        );
    }

    @Test
    void preserveMultilineCommentsWithFields() {
        rewriteRun(
          //language=java
          java(
            """
              class A {
                  public void method() {
                      System.out.println("method");
                  }
              
                  /*
                   * This is a multiline comment
                   * for my field
                   */
                  private String field1 = "value1";
              
                  /**
                   * Javadoc comment for field2
                   * @deprecated this field is old
                   */
                  @Deprecated
                  private int field2 = 42;
              }
              """,
            """
              class A {
                  /*
                   * This is a multiline comment
                   * for my field
                   */
                  private String field1 = "value1";
              
                  /**
                   * Javadoc comment for field2
                   * @deprecated this field is old
                   */
                  @Deprecated
                  private int field2 = 42;
              
                  public void method() {
                      System.out.println("method");
                  }
              }
              """
          )
        );
    }

    @Test
    void preserveCommentsWithMixedMembers() {
        rewriteRun(
          //language=java
          java(
            """
              class Mixed {
                  // Method comment
                  public void firstMethod() {
                      System.out.println("first");
                  }
              
                  // Field comment 1
                  private String field1 = "value1";
              
                  /* Another method comment */
                  public void secondMethod() {
                      System.out.println("second");
                  }
              
                  // Field comment 2
                  private int field2 = 10;
              }
              """,
            """
              class Mixed {
                  // Field comment 1
                  private String field1 = "value1";
              
                  // Field comment 2
                  private int field2 = 10;
              
                  // Method comment
                  public void firstMethod() {
                      System.out.println("first");
                  }
              
                  /* Another method comment */
                  public void secondMethod() {
                      System.out.println("second");
                  }
              }
              """
          )
        );
    }

    @Test
    void variableDeclarationsWithMultipleVariables() {
        rewriteRun(
          //language=java
          java(
            """
              class MultipleVars {
                  public void method() {
                      System.out.println("method");
                  }

                  private int x = 1, y = 2, z = 3;

                  public MultipleVars() {
                      // constructor
                  }
              }
              """,
            """
              class MultipleVars {
                  private int x = 1, y = 2, z = 3;

                  public void method() {
                      System.out.println("method");
                  }

                  public MultipleVars() {
                      // constructor
                  }
              }
              """
          )
        );
    }
}
