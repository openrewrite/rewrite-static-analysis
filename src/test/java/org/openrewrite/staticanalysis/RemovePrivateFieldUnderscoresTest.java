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

class RemovePrivateFieldUnderscoresTest implements RewriteTest {

    @Override
    public void defaults(RecipeSpec spec) {
        spec.recipe(new RemovePrivateFieldUnderscores());
    }

    @DocumentExample
    @Test
    void removesPrefixUnderscore() {
        rewriteRun(
          //language=java
          java(
            """
              public class ParseLocation {
                  private String _ruleName;

                  public String getRuleName() {
                      return _ruleName;
                  }

                  public void setRuleName(String ruleName) {
                      _ruleName = ruleName;
                  }
              }
              """,
            """
              public class ParseLocation {
                  private String ruleName;

                  public String getRuleName() {
                      return ruleName;
                  }

                  public void setRuleName(String ruleName) {
                      this.ruleName = ruleName;
                  }
              }
              """
          )
        );
    }

    @Test
    void removesSuffixUnderscore() {
        rewriteRun(
          //language=java
          java(
            """
              public class ParseLocation {
                  private String ruleName_;

                  public String getRuleName() {
                      return ruleName_;
                  }

                  public void setRuleName(String ruleName) {
                      ruleName_ = ruleName;
                  }
              }
              """,
            """
              public class ParseLocation {
                  private String ruleName;

                  public String getRuleName() {
                      return ruleName;
                  }

                  public void setRuleName(String ruleName) {
                      this.ruleName = ruleName;
                  }
              }
              """
          )
        );
    }

    @Test
    void doesNotAddThisWhenNoAmbiguity() {
        rewriteRun(
          //language=java
          java(
            """
              public class Calculator {
                  private int _operand1;
                  private int operand2_;

                  public Calculator(int a, int b) {
                      _operand1 = a;
                      operand2_ = b;
                  }

                  public int sum() {
                      return _operand1 + operand2_;
                  }
              }
              """,
            """
              public class Calculator {
                  private int operand1;
                  private int operand2;

                  public Calculator(int a, int b) {
                      operand1 = a;
                      operand2 = b;
                  }

                  public int sum() {
                      return operand1 + operand2;
                  }
              }
              """
          )
        );
    }

    @Test
    void addsThisWhenFieldIsShadowedByLocalVariable() {
        rewriteRun(
          //language=java
          java(
            """
              public class Calculator {
                  private int _operand1;
                  private int operand2_;

                  public Calculator(int a, int b) {
                      int operand1 = 10;
                      // ... do something else with operand1 ...
                      _operand1 = a;
                      operand2_ = b;
                  }

                  public int sum() {
                      int operand2 = 10;
                      // ... do something else with operand2 ...
                      return _operand1 + operand2_;
                  }
              }
              """,
            """
              public class Calculator {
                  private int operand1;
                  private int operand2;

                  public Calculator(int a, int b) {
                      int operand1 = 10;
                      // ... do something else with operand1 ...
                      this.operand1 = a;
                      operand2 = b;
                  }

                  public int sum() {
                      int operand2 = 10;
                      // ... do something else with operand2 ...
                      return operand1 + this.operand2;
                  }
              }
              """
          )
        );
    }

    @Test
    void handlesFieldsAlreadyQualifiedWithThis() {
        rewriteRun(
          //language=java
          java(
            """
              public class ParseLocation {
                  private String _ruleName;

                  public String getRuleName() {
                      return this._ruleName;
                  }

                  public void setRuleName(String ruleName) {
                      this._ruleName = ruleName;
                  }
              }
              """,
            """
              public class ParseLocation {
                  private String ruleName;

                  public String getRuleName() {
                      return this.ruleName;
                  }

                  public void setRuleName(String ruleName) {
                      this.ruleName = ruleName;
                  }
              }
              """
          )
        );
    }

    @Test
    void doesNotChangeNonPrivateFields() {
        rewriteRun(
          //language=java
          java(
            """
              public class MyClass {
                  public String _publicField;
                  protected String _protectedField;
                  String packagePrivateField_;
              }
              """
          )
        );
    }

    @Test
    void doesNotChangeLocalVariables() {
        rewriteRun(
          //language=java
          java(
            """
              public class MyClass {
                  public String myMethod(String str) {
                      String _str = bang(str);
                      String str_ = "str_";
                      return _str;
                  }

                  private String bang(String s) {
                      return s;
                  }
              }
              """
          )
        );
    }

    @Test
    void doesNotChangeReservedKeyword() {
        rewriteRun(
          //language=java
          java(
            """
              public class MyClass {
                  private String class_;
                  private String _class;
              }
              """
          )
        );
    }
}
