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
import static org.openrewrite.java.Assertions.srcTestJava;

class ReplaceDuplicateStringLiteralsTest implements RewriteTest {

    @Override
    public void defaults(RecipeSpec spec) {
        spec.recipe(new ReplaceDuplicateStringLiterals(true));
    }

    @Test
    @DocumentExample
    void replaceRedundantFinalStrings() {
        rewriteRun(
          //language=java
          java(
            """
              package org.foo;
              class A {
                  final String val1 = "value";
                  final String val2 = "value";
                  final String val3 = "value";
              }
              """,
            """
              package org.foo;
              class A {
                  private static final String VALUE = "value";
                  final String val1 = VALUE;
                  final String val2 = VALUE;
                  final String val3 = VALUE;
              }
              """
          )
        );
    }

    @Test
    void doesNotMeetCharacterLimit() {
        rewriteRun(
          //language=java
          java(
            """
              class A {
                  final String val1 = "val";
                  final String val2 = "val";
                  final String val3 = "val";
              }
              """
          )
        );
    }

    @Test
    @Issue("https://github.com/openrewrite/rewrite/issues/1740")
    void doesNotApplyToTest() {
        rewriteRun(
          spec -> spec.recipe(new ReplaceDuplicateStringLiterals(false)),
          srcTestJava(
            //language=java
            java(
              """
                class A {
                    final String val1 = "value";
                    final String val2 = "value";
                    final String val3 = "value";
                }
                """
            )
          )
        );
    }

    @Test
    @Issue("https://github.com/openrewrite/rewrite/issues/1740")
    void sourcesEnabled() {
        rewriteRun(
          srcTestJava(
            //language=java
            java(
              """
                class A {
                    final String val1 = "value";
                    final String val2 = "value";
                    final String val3 = "value";
                }
                """,
              """
                class A {
                    private static final String VALUE = "value";
                    final String val1 = VALUE;
                    final String val2 = VALUE;
                    final String val3 = VALUE;
                }
                """
            )
          )
        );
    }

    @Test
    void doNotChangeLiteralsInAnnotations() {
        rewriteRun(
          //language=java
          java(
            """
              public @interface Example {
                  String value() default "";
              }
              """
          ),
          //language=java
          java(
            """
              class A {
                  @Example(value = "value")
                  void method1() {}
                  @Example(value = "value")
                  void method2() {}
                  @Example(value = "value")
                  void method3() {}
              }
              """
          )
        );
    }

    @Test
    void enumCollidesWithNewStringLiteral() {
        rewriteRun(
          //language=java
          java(
            """
              package org.foo;
              enum TYPES {
                  VALUE, NUMBER, TEXT
              }

              class A {
                  final String val1 = "types";
                  final String val2 = "types";
                  final String val3 = "types";
                  TYPES type = TYPES.VALUE;
              }

              """,
            """
              package org.foo;
              enum TYPES {
                  VALUE, NUMBER, TEXT
              }

              class A {
                  private static final String TYPES_1 = "types";
                  final String val1 = TYPES_1;
                  final String val2 = TYPES_1;
                  final String val3 = TYPES_1;
                  TYPES type = TYPES.VALUE;
              }

              """
          )
        );
    }

    @Test
    void fieldNameCollidesWithNewStringLiteral() {
        rewriteRun(
          //language=java
          java(
            """
              package org.foo;
              class A {
                  final String val1 = "value";
                  final String val2 = "value";
                  final String val3 = "value";
                  final int VALUE = 1;
              }
              """,
            """
              package org.foo;
              class A {
                  private static final String VALUE_1 = "value";
                  final String val1 = VALUE_1;
                  final String val2 = VALUE_1;
                  final String val3 = VALUE_1;
                  final int VALUE = 1;
              }
              """
          )
        );
    }

    @Test
    void staticImportCollidesWithNewStringLiteral() {
        rewriteRun(
          //language=java
          java(
            """
              package org.foo;

              import static java.lang.Long.MAX_VALUE;

              class A {
                  final String val1 = "max_value";
                  final String val2 = "max_value";
                  final String val3 = "max_value";
                  final long value = MAX_VALUE;
              }
              """,
            """
              package org.foo;

              import static java.lang.Long.MAX_VALUE;

              class A {
                  private static final String MAX_VALUE_1 = "max_value";
                  final String val1 = MAX_VALUE_1;
                  final String val2 = MAX_VALUE_1;
                  final String val3 = MAX_VALUE_1;
                  final long value = MAX_VALUE;
              }
              """
          )
        );
    }

    @Test
    void generatedNameIsVeryLong() {
        rewriteRun(
          //language=java
          java(
            """
              package org.foo;
              class A {
                  final String val1 = "ThisIsAnUnreasonablyLongVariableNameItGoesOnAndOnForAVeryLongTimeItMightNeverEndWhoIsToKnowHowLongItWillKeepGoingAndGoing";
                  final String val2 = "ThisIsAnUnreasonablyLongVariableNameItGoesOnAndOnForAVeryLongTimeItMightNeverEndWhoIsToKnowHowLongItWillKeepGoingAndGoing";
                  final String val3 = "ThisIsAnUnreasonablyLongVariableNameItGoesOnAndOnForAVeryLongTimeItMightNeverEndWhoIsToKnowHowLongItWillKeepGoingAndGoing";
              }
              """,
            """
              package org.foo;
              class A {
                  private static final String THIS_IS_AN_UNREASONABLY_LONG_VARIABLE = "ThisIsAnUnreasonablyLongVariableNameItGoesOnAndOnForAVeryLongTimeItMightNeverEndWhoIsToKnowHowLongItWillKeepGoingAndGoing";
                  final String val1 = THIS_IS_AN_UNREASONABLY_LONG_VARIABLE;
                  final String val2 = THIS_IS_AN_UNREASONABLY_LONG_VARIABLE;
                  final String val3 = THIS_IS_AN_UNREASONABLY_LONG_VARIABLE;
              }
              """
          )
        );
    }

    @Test
    void replaceRedundantLiteralInMethodInvocation() {
        rewriteRun(
          //language=java
          java(
            """
              class A {
                  String method(String val) {
                      return null;
                  }
                  String val1 = method("value");
                  String val2 = method("value");
                  String val3 = method("value");
              }
              """,
            """
              class A {
                  private static final String VALUE = "value";
                  String method(String val) {
                      return null;
                  }
                  String val1 = method(VALUE);
                  String val2 = method(VALUE);
                  String val3 = method(VALUE);
              }
              """
          )
        );
    }

    @Test
    void replaceRedundantLiteralsInNewClass() {
        rewriteRun(
          //language=java
          java(
            """
              class A {
                  void method() {
                      B b1 = new B("value");
                      B b2 = new B("value");
                      B b3 = new B("value");
                  }
                  private static class B {
                      B(String val) {
                      }
                  }
              }
              """,
            """
              class A {
                  private static final String VALUE = "value";
                  void method() {
                      B b1 = new B(VALUE);
                      B b2 = new B(VALUE);
                      B b3 = new B(VALUE);
                  }
                  private static class B {
                      B(String val) {
                      }
                  }
              }
              """
          )
        );
    }

    @Test
    void multipleRedundantValues() {
        rewriteRun(
          //language=java
          java(
            """
              class A {
                  final String a1 = "value a";
                  final String a2 = "value a";
                  final String a3 = "value a";
                  final String b1 = "value b";
                  final String b2 = "value b";
                  final String b3 = "value b";
              }
              """,
            """
              class A {
                  private static final String VALUE_A = "value a";
                  private static final String VALUE_B = "value b";
                  final String a1 = VALUE_A;
                  final String a2 = VALUE_A;
                  final String a3 = VALUE_A;
                  final String b1 = VALUE_B;
                  final String b2 = VALUE_B;
                  final String b3 = VALUE_B;
              }
              """
          )
        );
    }

    @Test
    void transformStringValue() {
        rewriteRun(
          //language=java
          java(
            """
              class A {
                  final String val1 = "Example,, :: String with `` special __ characters.";
                  final String val2 = "Example,, :: String with `` special __ characters.";
                  final String val3 = "Example,, :: String with `` special __ characters.";
              }
              """,
            """
              class A {
                  private static final String EXAMPLE_STRING_WITH_SPECIAL_CHARACTERS = "Example,, :: String with `` special __ characters.";
                  final String val1 = EXAMPLE_STRING_WITH_SPECIAL_CHARACTERS;
                  final String val2 = EXAMPLE_STRING_WITH_SPECIAL_CHARACTERS;
                  final String val3 = EXAMPLE_STRING_WITH_SPECIAL_CHARACTERS;
              }
              """
          )
        );
    }

    @Test
    void constantAlreadyExists() {
        rewriteRun(
          //language=java
          java(
            """
              class A {
                  private static final String CONSTANT = "value";
                  final String val1 = "value";
                  final String val2 = "value";
                  final String val3 = "value";
              }
              """,
            """
              class A {
                  private static final String CONSTANT = "value";
                  final String val1 = CONSTANT;
                  final String val2 = CONSTANT;
                  final String val3 = CONSTANT;
              }
              """
          )
        );
    }

    @Test
    void constantExistsWithInnerClass() {
        rewriteRun(
          //language=java
          java(
            """
              class A {
                  private static final String CONSTANT = "value";
                  final String val1 = "value";
                  final String val2 = "value";
                  final String val3 = "value";

                  private static class B {
                      // Do not change inner class value.
                      private static final String CONSTANT = "value";
                  }
              }
              """,
            """
              class A {
                  private static final String CONSTANT = "value";
                  final String val1 = CONSTANT;
                  final String val2 = CONSTANT;
                  final String val3 = CONSTANT;

                  private static class B {
                      // Do not change inner class value.
                      private static final String CONSTANT = "value";
                  }
              }
              """
          )
        );
    }

    @Test
    void preventNamespaceShadowingWithNonStringConstant() {
        rewriteRun(
          //language=java
          java(
            """
              package org.foo;
              class A {
                  private static final int VALUE = 1;
                  final String val1 = "value";
                  final String val2 = "value";
                  final String val3 = "value";
              }
              """,
            """
              package org.foo;
              class A {
                  private static final String VALUE_1 = "value";
                  private static final int VALUE = 1;
                  final String val1 = VALUE_1;
                  final String val2 = VALUE_1;
                  final String val3 = VALUE_1;
              }
              """
          )
        );
    }

    @Test
    void preventNamespaceShadowingOnExistingConstant() {
        rewriteRun(
          //language=java
          java(
            """
              class A {
                  // Change field name to prevent potential namespace conflicts.
                  private static final String VALUE = "value";
                  void newScope() {
                      final String VALUE = "name already exists";
                      final String val1 = "value";
                      final String val2 = "value";
                      final String val3 = "value";
                  }
                  void method() {
                      // Change existing method reference.
                      String valueRef = VALUE;
                  }
                  private static class B {
                      // Change existing inner class reference.
                      String innerClass = VALUE;
                  }
              }
              """,
            """
              class A {
                  // Change field name to prevent potential namespace conflicts.
                  private static final String VALUE_1 = "value";
                  void newScope() {
                      final String VALUE = "name already exists";
                      final String val1 = VALUE_1;
                      final String val2 = VALUE_1;
                      final String val3 = VALUE_1;
                  }
                  void method() {
                      // Change existing method reference.
                      String valueRef = VALUE_1;
                  }
                  private static class B {
                      // Change existing inner class reference.
                      String innerClass = VALUE_1;
                  }
              }
              """
          )
        );
    }

    @Test
    void preventNamespaceShadowingOnNewConstant() {
        rewriteRun(
          //language=java
          java(
            """
              class A {
                  final String val1 = "value";
                  final String val2 = "value";
                  final String val3 = "value";
                  final String VALUE = "name space conflict";
              }
              """,
            """
              class A {
                  private static final String VALUE_1 = "value";
                  final String val1 = VALUE_1;
                  final String val2 = VALUE_1;
                  final String val3 = VALUE_1;
                  final String VALUE = "name space conflict";
              }
              """
          )
        );
    }

    @Test
    void multiVariableDeclaration() {
        rewriteRun(
          //language=java
          java(
            """
              class A {
                  final String val1 = "value", val2 = "value", diff = "here";
                  final String val3 = "value";
                  final String VALUE = "name space conflict";
              }
              """,
            """
              class A {
                  private static final String VALUE_1 = "value";
                  final String val1 = VALUE_1, val2 = VALUE_1, diff = "here";
                  final String val3 = VALUE_1;
                  final String VALUE = "name space conflict";
              }
              """
          )
        );
    }

    @Test
    void replaceMixedRedundantLiterals() {
        rewriteRun(
          //language=java
          java(
            """
              class A {
                  final String val1 = "value";
                  void methodA() {
                      methodB("value");
                  }
                  void methodB(String val) {
                      B b = new B("value");
                  }
                  private static class B {
                      B(String val) {
                      }
                  }
              }
              """,
            """
              class A {
                  private static final String VALUE = "value";
                  final String val1 = VALUE;
                  void methodA() {
                      methodB(VALUE);
                  }
                  void methodB(String val) {
                      B b = new B(VALUE);
                  }
                  private static class B {
                      B(String val) {
                      }
                  }
              }
              """
          )
        );
    }

    @Test
    @Issue("https://github.com/openrewrite/rewrite/issues/2329")
    void unicodeCharacterEquivalents() {
        rewriteRun(
          //language=java
          java(
            """
              class A {
                  final String val1 = "āăąēîïĩíĝġńñšŝśûůŷ";
                  final String val2 = "āăąēîïĩíĝġńñšŝśûůŷ";
                  final String val3 = "āăąēîïĩíĝġńñšŝśûůŷ";
              }
              """,
            """
              class A {
                  private static final String AAAEIIIIGGNNSSSUUY = "āăąēîïĩíĝġńñšŝśûůŷ";
                  final String val1 = AAAEIIIIGGNNSSSUUY;
                  final String val2 = AAAEIIIIGGNNSSSUUY;
                  final String val3 = AAAEIIIIGGNNSSSUUY;
              }
              """
          )
        );
    }

    @Test
    @Issue("https://github.com/openrewrite/rewrite/issues/2330")
    void enumDefinition() {
        rewriteRun(
          //language=java
          java(
            """
              enum A {
                  /**/
                  ONE, TWO, THREE;

                  public void example() {
                      final String val1 = "value";
                      final String val2 = "value";
                      final String val3 = "value";
                  }

                  public void bar() {}
              }
              """,
            """
              enum A {
                  /**/
                  ONE, TWO, THREE;
                  private static final String VALUE = "value";

                  public void example() {
                      final String val1 = VALUE;
                      final String val2 = VALUE;
                      final String val3 = VALUE;
                  }

                  public void bar() {}
              }
              """
          )
        );
    }

    @Test
    void enumCannotReplaceConstructorArgument() {
        rewriteRun(
          //language=java
          java(
            """
              enum Scratch {
                  A("value"),
                  B("value"),
                  C("value");
                  Scratch(String s) {
                  }
              }
              """
          )
        );
    }

    @Test
    void interfaceLiteralsCannotBePrivate() {
        rewriteRun(
          //language=java
          java(
            """
              interface A {
                  String val1 = "value";
                  String val2 = "value";
                  String val3 = "value";
              }
              """,
            """
              interface A {
                  String VALUE = "value";
                  String val1 = VALUE;
                  String val2 = VALUE;
                  String val3 = VALUE;
              }
              """
          )
        );
    }

    @Test
    @Issue("https://github.com/openrewrite/rewrite-static-analysis/issues/384")
    void staticWithObjectArray() {
        rewriteRun(
          //language=java
          java(
            """
           class A {
               public void method() {
                   Object[] args = null;
                   args = new Object[] {"value"};
               }
           }
           """
          )
        );
    }

}
