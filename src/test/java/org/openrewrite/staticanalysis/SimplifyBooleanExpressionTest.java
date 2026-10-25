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

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.openrewrite.DocumentExample;
import org.openrewrite.Issue;
import org.openrewrite.test.RecipeSpec;
import org.openrewrite.test.RewriteTest;

import static org.openrewrite.java.Assertions.java;
import static org.openrewrite.kotlin.Assertions.kotlin;
import static org.openrewrite.scala.Assertions.scala;

@SuppressWarnings("ALL")
class SimplifyBooleanExpressionTest implements RewriteTest {

    @Override
    public void defaults(RecipeSpec spec) {
        spec.recipe(new SimplifyBooleanExpression());
    }

    @DocumentExample
    @Test
    void simplifyEqualsLiteralTrueIf() {
        rewriteRun(
          java(
            """
              public class A {
                  boolean a;
                  {
                      if(true == a) {
                      }
                  }
              }
              """,
            """
              public class A {
                  boolean a;
                  {
                      if(a) {
                      }
                  }
              }
              """
          )
        );
    }

    @Test
    void simplifyBooleanExpressionComprehensive() {
        rewriteRun(
          java(
            """
              public class A {
                  {
                      boolean a = !false;
                      boolean b = (a == true);
                      boolean c = b || true;
                      boolean d = c || c;
                      boolean e = d && d;
                      boolean f = (e == true) || e;
                      boolean g = f && false;
                      boolean h = g;
                      boolean i = (a != false);
                  }
              }
              """,
            """
              public class A {
                  {
                      boolean a = true;
                      boolean b = a;
                      boolean c = true;
                      boolean d = c;
                      boolean e = d;
                      boolean f = e;
                      boolean g = false;
                      boolean h = g;
                      boolean i = a;
                  }
              }
              """
          )
        );
    }

    @Test
    void simplifyInvertedBooleanLiteral() {
        rewriteRun(
          java(
            """
              public class A {
                  {
                      boolean a = !false;
                      boolean b = !true;
                  }
              }
              """,
            """
              public class A {
                  {
                      boolean a = true;
                      boolean b = false;
                  }
              }
              """
          )
        );
    }

    @Test
    void simplifyEqualsLiteralTrue() {
        rewriteRun(
          java(
            """
              public class A {
                  {
                      boolean a = true;
                      boolean b = (a == true);
                  }
              }
              """,
            """
              public class A {
                  {
                      boolean a = true;
                      boolean b = a;
                  }
              }
              """
          )
        );
    }

    @Test
    void simplifyOrLiteralTrue() {
        rewriteRun(
          java(
            """
              public class A {
                  {
                      boolean b = true;
                      boolean c = b || true;
                  }
              }
              """,
            """
              public class A {
                  {
                      boolean b = true;
                      boolean c = true;
                  }
              }
              """
          )
        );
    }

    @Test
    void simplifyOrAlwaysTrue() {
        rewriteRun(
          java(
            """
              public class A {
                  {
                      boolean c = true;
                      boolean d = c || c;
                  }
              }
              """,
            """
              public class A {
                  {
                      boolean c = true;
                      boolean d = c;
                  }
              }
              """
          )
        );
    }

    @Test
    void simplifyAndAlwaysTrue() {
        rewriteRun(
          java(
            """
              public class A {
                  {
                      boolean d = true;
                      boolean e = d && d;
                  }
              }
              """,
            """
              public class A {
                  {
                      boolean d = true;
                      boolean e = d;
                  }
              }
              """
          )
        );
    }

    @Test
    void simplifyEqualsLiteralTrueAlwaysTrue() {
        rewriteRun(
          java(
            """
              public class A {
                  {
                      boolean e = true;
                      boolean f = (e == true) || e;
                  }
              }
              """,
            """
              public class A {
                  {
                      boolean e = true;
                      boolean f = e;
                  }
              }
              """
          )
        );
    }

    @Test
    void simplifyLiteralFalseAlwaysFalse() {
        rewriteRun(
          java(
            """
              public class A {
                  {
                      boolean f = true;
                      boolean g = f && false;
                  }
              }
              """,
            """
              public class A {
                  {
                      boolean f = true;
                      boolean g = false;
                  }
              }
              """
          )
        );
    }

    @Test
    void simplifyDoubleNegation() {
        rewriteRun(
          java(
            """
              public class A {
                  public void doubleNegation(boolean g) {
                      boolean h = !!g;
                  }
              }
              """,
            """
              public class A {
                  public void doubleNegation(boolean g) {
                      boolean h = g;
                  }
              }
              """
          )
        );
    }

    @Test
    void simplifyNotEqualsFalse() {
        rewriteRun(
          java(
            """
              public class A {
                  {
                      boolean a = true;
                      boolean i = (a != false);
                  }
              }
              """,
            """
              public class A {
                  {
                      boolean a = true;
                      boolean i = a;
                  }
              }
              """
          )
        );
    }

    @Issue("https://github.com/openrewrite/rewrite/issues/502")
    @Test
    void autoFormatIsConditionallyApplied() {
        rewriteRun(
          java(
            """
              public class A {
                  {
                      boolean a=true;
                      boolean i=a!=true;
                  }
              }
              """,
            """
              public class A {
                  {
                      boolean a=true;
                      boolean i=!a;
                  }
              }
              """
          )
        );
    }

    @Test
    void binaryOrBothFalse() {
        rewriteRun(
          java(
            """
              public class A {
                  {
                      if (!true || !true) {
                          System.out.println("");
                      }
                  }
              }
              """,
            """
              public class A {
                  {
                      if (false) {
                          System.out.println("");
                      }
                  }
              }
              """
          )
        );
    }

    @Test
    void ternary() {
        rewriteRun(
          java(
            """
              public class A {
                  {
                      if (!true || !true) {
                          System.out.println("");
                      }
                  }
              }
              """,
            """
              public class A {
                  {
                      if (false) {
                          System.out.println("");
                      }
                  }
              }
              """
          )
        );
    }

    @Test
    void nullCheck() {
        rewriteRun(
          spec -> spec
            .recipes(
              new SimplifyBooleanReturn(),
              new SimplifyBooleanExpression()
            ),
          //language=java
          java(
                """
              class A {
                  String name;
                  boolean notOne(A a) {
                      if (a != null ? !name.equals(a.name) : a.name != null) return false;
                      return true;
                  }
              }
              """,
            """
              class A {
                  String name;
                  boolean notOne(A a) {
                      return a != null ? name.equals(a.name) : a.name == null;
                  }
              }
              """
            )
        );
    }

    @Issue("https://github.com/openrewrite/rewrite-static-analysis/issues/276")
    @Test
    void doNotNegateConditionOfTernaryWhenBranchesAreNotSwapped() {
        rewriteRun(
          java(
            """
              class A {
                  boolean foo(String name) {
                      return !(name != null ? !name.equals(System.out.toString()) : false);
                  }
              }
              """,
            """
              class A {
                  boolean foo(String name) {
                      return name != null ? name.equals(System.out.toString()) : true;
                  }
              }
              """
          )
        );
    }

    @Test
    void correctlySimplifyNegatedTernaryEqualsNull() {
        rewriteRun(
          java(
            """
              class A {
                  void doSome(String o1, String o2) {
                      if (!(o1 == null ? o2 == null : o1.equals(o2))) {
                      }
                  }
              }
              """,
            """
              class A {
                  void doSome(String o1, String o2) {
                      if (o1 == null ? o2 != null : !o1.equals(o2)) {
                      }
                  }
              }
              """
          )
        );
    }

    @Test
    void scalaAndTrue() {
        rewriteRun(
          //language=scala
          scala(
            """
              class A {
                def foo(a: Boolean): Boolean = a && true
              }
              """,
            """
              class A {
                def foo(a: Boolean): Boolean = a
              }
              """
          )
        );
    }

    @Nested
    class Kotlin {
        @DocumentExample
        @Test
        void regular() {
            rewriteRun(
              kotlin(
                """
                  fun getSymbol() : String? {
                      return null
                  }
                  """
              ),
              kotlin(
                """
                  val isPositive = getSymbol().equals("+") == true
                  """,
                """
                  val isPositive = getSymbol().equals("+")
                  """
              )
            );
        }

        @Test
        void doNotChangeWithNullable() {
            rewriteRun(
              kotlin(
                """
                  fun getSymbol() : String? {
                      return null
                  }

                  val isPositive = getSymbol()?.equals("+") == true
                  """
              )
            );
        }

        @Test
        void nullableChain() {
            rewriteRun(
              kotlin(
                """
                  fun getSymbol() : String? {
                      return null
                  }
                  """
              ),
              kotlin(
                """
                  val isPositive1 = getSymbol()?.plus("").equals("+") == true
                  val isPositive2 = getSymbol()?.plus("")?.equals("+") == true
                  """,
                """
                  val isPositive1 = getSymbol()?.plus("").equals("+")
                  val isPositive2 = getSymbol()?.plus("")?.equals("+") == true
                  """
              )
            );
        }

        @Test
        void nullableVariable() {
            rewriteRun(
              kotlin(
                """
                  fun main() {
                      val name : String? = null
                      val isPositive1 = name?.equals("+") == true
                      val isPositive2 = name.equals("+") == true
                  }
                  """,
                """
                  fun main() {
                      val name : String? = null
                      val isPositive1 = name?.equals("+") == true
                      val isPositive2 = name.equals("+")
                  }
                  """
              )
            );
        }

        @Issue("https://github.com/openrewrite/rewrite-static-analysis/issues/303")
        @Test
        void nullableBooleanReceiverEqualsTrue() {
            rewriteRun(
              kotlin(
                """
                  fun Boolean?.toLegacyFlag() = if (this == true) "1" else "0"
                  """
              )
            );
        }

        @Issue("https://github.com/openrewrite/rewrite-static-analysis/issues/303")
        @Test
        void nullableBooleanFieldEqualsTrue() {
            rewriteRun(
              kotlin(
                """
                  data class Todo(val completed: Boolean?)
                  fun main() {
                      val todo = Todo(null)
                      val isCompleted: Boolean = todo.completed == true
                  }
                  """
              )
            );
        }

        @Test
        void nullableBooleanFunctionEqualsTrue() {
            rewriteRun(
              kotlin(
                """
                  fun flag(): Boolean? = null

                  fun check() {
                      if (flag() == true) println("true")
                  }
                  """
              )
            );
        }

        @Issue("https://github.com/openrewrite/rewrite-static-analysis/issues/303")
        @Test
        void nullableBooleanParameterEqualsFalse() {
            rewriteRun(
              kotlin(
                """
                  fun check(b: Boolean?) {
                      if (b == false) println("false or null")
                  }
                  """
              )
            );
        }

        @Issue("https://github.com/openrewrite/rewrite-static-analysis/issues/303")
        @Test
        void nullableBooleanParameterNotEqualTrue() {
            rewriteRun(
              kotlin(
                """
                  fun check(b: Boolean?) {
                      if (b != true) println("not true")
                  }
                  """
              )
            );
        }
    }
}
