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

@SuppressWarnings({
  "UnnecessaryBoxing", "BooleanConstructorCall", "ConstantConditions",
  "StringOperationCanBeSimplified", "CachedNumberConstructorCall"
})
class PrimitiveWrapperClassConstructorToValueOfTest implements RewriteTest {
    @Override
    public void defaults(RecipeSpec spec) {
        spec.recipe(new PrimitiveWrapperClassConstructorToValueOf());
    }

    @DocumentExample
    @Test
    void newClassToValueOf() {
        rewriteRun(
          //language=java
          java(
            """
              class A {
                  Boolean bool = new Boolean(true);
                  Byte b = new Byte("1");
                  Character c = new Character('c');
                  Double d = new Double(1.0);
                  Float f = new Float(1.1f);
                  Long l = new Long(1);
                  Short sh = new Short("12");
                  short s3 = 3;
                  Short sh3 = new Short(s3);
                  Integer i = new Integer(1);
              }
              """,
            """
              class A {
                  Boolean bool = Boolean.valueOf(true);
                  Byte b = Byte.valueOf("1");
                  Character c = Character.valueOf('c');
                  Double d = Double.valueOf(1.0);
                  Float f = Float.valueOf(1.1f);
                  Long l = Long.valueOf(1);
                  Short sh = Short.valueOf("12");
                  short s3 = 3;
                  Short sh3 = Short.valueOf(s3);
                  Integer i = Integer.valueOf(1);
              }
              """
          )
        );
    }

    @Issue("https://github.com/openrewrite/rewrite/issues/2945")
    @Test
    void ternaryWithBinary() {
        rewriteRun(
          //language=java
          java(
            """
              import java.util.concurrent.TimeUnit;
              class A {
                  void method(Long time) {
                      Long timeoutValue = (time == null)
                          ? new Long(0)
                          : time + TimeUnit.MICROSECONDS.convert(60, TimeUnit.MINUTES);
                  }
              }
              """,
            """
              import java.util.concurrent.TimeUnit;
              class A {
                  void method(Long time) {
                      Long timeoutValue = (time == null)
                          ? Long.valueOf(0)
                          : time + TimeUnit.MICROSECONDS.convert(60, TimeUnit.MINUTES);
                  }
              }
              """
          )
        );
    }

    @Test
    void integerValueOf() {
        rewriteRun(
          //language=java
          java(
            """
              class A {
                  Integer i = Integer.valueOf(1);
                  String hello = new String("Hello" + " world " + i);
                  Long l = 11L;
              }
              """
          )
        );
    }

    @Test
    void newIntegerToValueOfValueRef() {
        rewriteRun(
          //language=java
          java(
            """
              class A {
                  boolean fls = true;
                  Boolean b2 = new Boolean(fls);
                  char ch = 'c';
                  Character c = new Character(ch);
                  double d1 = 1.1;
                  Double d = new Double(d1);
                  int k = 1;
                  Integer k2 = new Integer(k);
              }
              """,
            """
              class A {
                  boolean fls = true;
                  Boolean b2 = Boolean.valueOf(fls);
                  char ch = 'c';
                  Character c = Character.valueOf(ch);
                  double d1 = 1.1;
                  Double d = Double.valueOf(d1);
                  int k = 1;
                  Integer k2 = Integer.valueOf(k);
              }
              """
          )
        );
    }

    @Issue("https://github.com/openrewrite/rewrite/issues/901")
    @Test
    void templateIsNewClassArgumentForNewClass() {
        rewriteRun(
          //language=java
          java(
            """
              import java.util.Date;
              public class A {
                  public static void main(String[] args) {
                      Date d = new Date(new Long(0));
                      Long l = new Long(new Integer(0));
                  }
              }
              """,
            """
              import java.util.Date;
              public class A {
                  public static void main(String[] args) {
                      Date d = new Date(Long.valueOf(0));
                      Long l = Long.valueOf(Integer.valueOf(0));
                  }
              }
              """
          )
        );
    }

    @Test
    void doubleToFloat() {
        rewriteRun(
          //language=java
          java(
            """
              class T {
                  Double d1 = Double.valueOf(1.0);
                  double d2 = 2.0d;
                  void makeFloats() {
                      Float f = new Float(2.0d);
                      Float f2 = new Float(getD());
                      Float f3 = new Float(d1);
                      Float f4 = new Float(d2);
                  }
                  Double getD() {
                      return Double.valueOf(2.0d);
                  }
              }
              """,
            """
              class T {
                  Double d1 = Double.valueOf(1.0);
                  double d2 = 2.0d;
                  void makeFloats() {
                      Float f = Float.valueOf((float) 2.0d);
                      Float f2 = Float.valueOf(getD().floatValue());
                      Float f3 = Float.valueOf(d1.floatValue());
                      Float f4 = Float.valueOf((float) d2);
                  }
                  Double getD() {
                      return Double.valueOf(2.0d);
                  }
              }
              """
          )
        );
    }

    @Test
    void doubleLiteralToFloatKeepsBinary64Rounding() {
        // `new Float(double)` is `(float) value`, rounding through binary64 to 0x3f800000, where
        // `Float.valueOf(String)` rounds the decimal straight to binary32 and yields 0x3f800001
        rewriteRun(
          //language=java
          java(
            """
              class T {
                  Float value = new Float(1.0000000596046448);
              }
              """,
            """
              class T {
                  Float value = Float.valueOf((float) 1.0000000596046448);
              }
              """
          )
        );
    }

    @Test
    void doubleLiteralToFloatKeepsSourceForm() {
        rewriteRun(
          //language=java
          java(
            """
              class T {
                  Float hex = new Float(0x1.0000002p0);
                  Float suffixed = new Float(1.0000000596046448D);
                  Float subnormal = new Float(4.9E-324);
              }
              """,
            """
              class T {
                  Float hex = Float.valueOf((float) 0x1.0000002p0);
                  Float suffixed = Float.valueOf((float) 1.0000000596046448D);
                  Float subnormal = Float.valueOf((float) 4.9E-324);
              }
              """
          )
        );
    }

    @Test
    void doubleExpressionToFloatUsesCast() {
        rewriteRun(
          //language=java
          java(
            """
              class T {
                  Float negativeZero = new Float(-0.0);
                  Float overflowing = new Float(Double.MAX_VALUE);
                  Float parenthesized = new Float((1.0000000596046448));
              }
              """,
            """
              class T {
                  Float negativeZero = Float.valueOf((float) -0.0);
                  Float overflowing = Float.valueOf((float) Double.MAX_VALUE);
                  Float parenthesized = Float.valueOf((float) (1.0000000596046448));
              }
              """
          )
        );
    }

    @Test
    void compoundDoubleExpressionToFloatIsParenthesized() {
        rewriteRun(
          //language=java
          java(
            """
              class T {
                  double d1 = 1.0;
                  double d2 = 2.0;
                  void makeFloats() {
                      Float sum = new Float(d1 + d2);
                      Float ternary = new Float(d1 > d2 ? d1 : d2);
                      Float assigned = new Float(d1 = 2.0);
                      Float compound = new Float(d1 += 2.0);
                  }
              }
              """,
            """
              class T {
                  double d1 = 1.0;
                  double d2 = 2.0;
                  void makeFloats() {
                      Float sum = Float.valueOf((float) (d1 + d2));
                      Float ternary = Float.valueOf((float) (d1 > d2 ? d1 : d2));
                      Float assigned = Float.valueOf((float) (d1 = 2.0));
                      Float compound = Float.valueOf((float) (d1 += 2.0));
                  }
              }
              """
          )
        );
    }

    @Test
    void floatLiteralUnchangedByDoubleHandling() {
        rewriteRun(
          //language=java
          java(
            """
              class T {
                  Float f = new Float(1.1f);
                  Float hex = new Float(0x1.0000002p0f);
              }
              """,
            """
              class T {
                  Float f = Float.valueOf(1.1f);
                  Float hex = Float.valueOf(0x1.0000002p0f);
              }
              """
          )
        );
    }

    @Test
    void withinEnum() {
        rewriteRun(
          //language=java
          java(
            """
              public enum Options {

                  JAR("instance.jar.file"),
                  JVM_ARGUMENTS("instance.vm.args"),
                  QUICKSTART_OPTIONS("instance.options"),
                  INSTALLATIONS("instance.installations"),
                  START_TIMEOUT("instance.timeout");

                  private String name;

                  Options(String name) {
                      this.name = name;
                  }

                  public String asString() {
                      return System.getProperty(name);
                  }

                  public Integer asInteger(int defaultValue) {
                      String string  = asString();

                      if (string == null) {
                          return defaultValue;
                      }

                      return new Integer(asString());
                  }

              }
              """,
            """
              public enum Options {

                  JAR("instance.jar.file"),
                  JVM_ARGUMENTS("instance.vm.args"),
                  QUICKSTART_OPTIONS("instance.options"),
                  INSTALLATIONS("instance.installations"),
                  START_TIMEOUT("instance.timeout");

                  private String name;

                  Options(String name) {
                      this.name = name;
                  }

                  public String asString() {
                      return System.getProperty(name);
                  }

                  public Integer asInteger(int defaultValue) {
                      String string  = asString();

                      if (string == null) {
                          return defaultValue;
                      }

                      return Integer.valueOf(asString());
                  }

              }
              """
          )
        );
    }
}
