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

class ExplicitThisTest implements RewriteTest {

    @Override
    public void defaults(RecipeSpec spec) {
        spec.recipe(new ExplicitThis());
    }

    @Test
    @DocumentExample
    void replacePatterns() {
        rewriteRun(
          //language=java
          java(
            """
              import java.util.function.Consumer;

              class Parent {
                  private String parentField;

                  Parent(String value) {
                      parentField = value;
                  }
              }

              class Test extends Parent {
                  private String value;
                  private String field;
                  private String field1;
                  private String field2;
                  private static String staticField;
                  private String alreadyPrefixed;

                  private String fieldInit1 = "initial";
                  private String fieldInit2 = field1;
                  private String fieldInit3 = field1 + field2;

                  private static String staticFieldInit = staticField;

                  static {
                      staticField = "static initializer";
                      staticHelper();
                  }

                  {
                      field = "instance initializer";
                      field1 = field2;
                  }

                  Test(String value) {
                      super(value);
                      // Shadowed parameter: looks like a bug but replacing would change semantics
                      value = value;
                      field = "constructor";
                      field1 = field2;
                  }

                  Test() {
                      this("default");
                  }

                  void instanceMethod(String parameter) {
                      field = "value";
                      field1 = field2;
                      helper();
                      staticField = "static context";
                      staticMethod();

                      String localVariable = parameter;
                      String result = parameter + localVariable;

                      this.alreadyPrefixed = "already has this";
                      this.alreadyPrefixedMethod();
                      super.toString();

                      Consumer<String> lambda = s -> {
                          field = s;
                          field1 = field2;
                      };

                      Runnable runnable = () -> field = "lambda";
                  }

                  static void staticMethod() {
                      staticField = "static context";
                      staticHelper();
                  }

                  // Shadowed parameter: looks like a bug but replacing would change semantics
                  void setField(String field) {
                      field = field;
                  }

                  void helper() {}
                  void alreadyPrefixedMethod() {}
                  static void staticHelper() {}

                  class Inner {
                      private String innerField;

                      void method() {
                          innerField = "inner";
                          field = "outer";
                          Consumer<String> lambda = s -> {
                              innerField = s;
                              field = s;
                          };
                      }

                      class Nested {
                          private String nestedField;

                          void method() {
                              nestedField = "nested";
                              innerField = "inner";
                              field = "outer";
                          }
                      }
                  }

                  static class StaticNested {
                      private String nestedField;
                      private static String nestedStaticField;

                      void method() {
                          nestedField = "nested";
                          nestedStaticField = "static";
                          staticField = "outer static";
                      }

                      static void staticMethod() {
                          nestedStaticField = "static";
                          staticField = "outer static";
                      }
                  }

                  void anonymousClassExample() {
                      Runnable r = new Runnable() {
                          private String anonymousField = "anonymous";

                          @Override
                          public void run() {
                              field = "outer";
                              String local = anonymousField;
                          }
                      };
                  }
              }
              """,
            """
              import java.util.function.Consumer;

              class Parent {
                  private String parentField;

                  Parent(String value) {
                      this.parentField = value;
                  }
              }

              class Test extends Parent {
                  private String value;
                  private String field;
                  private String field1;
                  private String field2;
                  private static String staticField;
                  private String alreadyPrefixed;

                  private String fieldInit1 = "initial";
                  private String fieldInit2 = this.field1;
                  private String fieldInit3 = this.field1 + this.field2;

                  private static String staticFieldInit = staticField;

                  static {
                      staticField = "static initializer";
                      staticHelper();
                  }

                  {
                      this.field = "instance initializer";
                      this.field1 = this.field2;
                  }

                  Test(String value) {
                      super(value);
                      // Shadowed parameter: looks like a bug but replacing would change semantics
                      value = value;
                      this.field = "constructor";
                      this.field1 = this.field2;
                  }

                  Test() {
                      this("default");
                  }

                  void instanceMethod(String parameter) {
                      this.field = "value";
                      this.field1 = this.field2;
                      this.helper();
                      staticField = "static context";
                      staticMethod();

                      String localVariable = parameter;
                      String result = parameter + localVariable;

                      this.alreadyPrefixed = "already has this";
                      this.alreadyPrefixedMethod();
                      super.toString();

                      Consumer<String> lambda = s -> {
                          this.field = s;
                          this.field1 = this.field2;
                      };

                      Runnable runnable = () -> this.field = "lambda";
                  }

                  static void staticMethod() {
                      staticField = "static context";
                      staticHelper();
                  }

                  // Shadowed parameter: looks like a bug but replacing would change semantics
                  void setField(String field) {
                      field = field;
                  }

                  void helper() {}
                  void alreadyPrefixedMethod() {}
                  static void staticHelper() {}

                  class Inner {
                      private String innerField;

                      void method() {
                          this.innerField = "inner";
                          Test.this.field = "outer";
                          Consumer<String> lambda = s -> {
                              this.innerField = s;
                              Test.this.field = s;
                          };
                      }

                      class Nested {
                          private String nestedField;

                          void method() {
                              this.nestedField = "nested";
                              Inner.this.innerField = "inner";
                              Test.this.field = "outer";
                          }
                      }
                  }

                  static class StaticNested {
                      private String nestedField;
                      private static String nestedStaticField;

                      void method() {
                          this.nestedField = "nested";
                          nestedStaticField = "static";
                          staticField = "outer static";
                      }

                      static void staticMethod() {
                          nestedStaticField = "static";
                          staticField = "outer static";
                      }
                  }

                  void anonymousClassExample() {
                      Runnable r = new Runnable() {
                          private String anonymousField = "anonymous";

                          @Override
                          public void run() {
                              Test.this.field = "outer";
                              String local = this.anonymousField;
                          }
                      };
                  }
              }
              """
          )
        );
    }

    @Test
    void unchangedWhenAlreadyQualified() {
        rewriteRun(
          //language=java
          java(
            """
              import java.util.function.Consumer;

              class Test {
                  private String field;
                  private static String staticField;

                  void instanceMethod(String parameter) {
                      this.field = "already has this";
                      String localVariable = parameter;
                      String result = parameter + localVariable;
                  }

                  static void staticMethod() {
                      staticField = "static context";
                  }
              }
              """
          )
        );
    }

}
