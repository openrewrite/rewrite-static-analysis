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
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.openrewrite.DocumentExample;
import org.openrewrite.ExecutionContext;
import org.openrewrite.InMemoryExecutionContext;
import org.openrewrite.java.JavaIsoVisitor;
import org.openrewrite.java.JavaParser;
import org.openrewrite.java.tree.J;
import org.openrewrite.java.tree.JavaType;
import org.openrewrite.test.RecipeSpec;
import org.openrewrite.test.RewriteTest;

import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.openrewrite.java.Assertions.java;
import static org.openrewrite.python.Assertions.python;
import static org.openrewrite.scala.Assertions.scala;

@SuppressWarnings("DoubleNegation")
class BooleanChecksNotInvertedTest implements RewriteTest {

    @Override
    public void defaults(RecipeSpec spec) {
        spec.recipe(new BooleanChecksNotInverted());
    }

    @DocumentExample
    @SuppressWarnings("StatementWithEmptyBody")
    @Test
    void rspec1940() {
        rewriteRun(
          //language=java
          java(
            """
              public class Test {
                  int i;
                  int a;
                  void test() {
                      if ( !(a == 2)) {
                      }
                      boolean b = !(i < 10);
                  }
              }
              """,
            """
              public class Test {
                  int i;
                  int a;
                  void test() {
                      if ( a != 2) {
                      }
                      boolean b = i >= 10;
                  }
              }
              """
          )
        );
    }

    @Test
    void doubleNegation() {
        rewriteRun(
          //language=java
          java(
            """
              public class Test {
                  boolean test(boolean b) {
                      return !(!b);
                  }
              }
              """,
            """
              public class Test {
                  boolean test(boolean b) {
                      return b;
                  }
              }
              """
          )
        );
    }

    @Test
    void doNotChangePythonChainedComparison() {
        rewriteRun(
          //language=python
          python(
            """
              def test(value):
                  if not (1 <= value <= 6):
                      pass
              """
          )
        );
    }

    @Test
    void scalaInvertedEquals() {
        rewriteRun(
          //language=scala
          scala(
            """
              class A {
                def foo(a: Int, b: Int): Boolean = !(a == b)
              }
              """,
            """
              class A {
                def foo(a: Int, b: Int): Boolean = a != b
              }
              """
          )
        );
    }

    static Stream<Arguments> floatingRelationalComparisons() {
        return Stream.of("double", "float", "Double", "Float")
          .flatMap(type -> Stream.of("<", ">", "<=", ">=")
            .flatMap(operator -> Stream.of(false, true)
              .map(reversed -> Arguments.of(type, operator, reversed))));
    }

    @ParameterizedTest
    @MethodSource("floatingRelationalComparisons")
    void preserveFloatingRelationalComparison(String type, String operator, boolean reversed) {
        String comparison = reversed ? "0 " + operator + " value" : "value " + operator + " 0";
        rewriteRun(
          spec -> spec.cycles(3).expectedCyclesThatMakeChanges(0),
          java(
            """
              class Test {
                  boolean test(%s value) {
                      return !(%s);
                  }
              }
              """.formatted(type, comparison)
          )
        );
    }

    @ParameterizedTest
    @CsvSource({
      "int, <, >=",
      "long, >, <=",
      "char, <=, >",
      "byte, >=, <",
      "short, <, >=",
      "Integer, <, >=",
      "Long, >, <=",
      "Character, <=, >",
      "Byte, >=, <",
      "Short, <, >="
    })
    void continueInvertingIntegralComparisons(String type, String beforeOperator, String afterOperator) {
        rewriteRun(
          spec -> spec.cycles(3).expectedCyclesThatMakeChanges(1),
          java(
            """
              class Test {
                  boolean test(%s value) {
                      return !(value %s 0);
                  }
              }
              """.formatted(type, beforeOperator),
            """
              class Test {
                  boolean test(%s value) {
                      return value %s 0;
                  }
              }
              """.formatted(type, afterOperator)
          )
        );
    }

    @ParameterizedTest
    @CsvSource({
      "double, ==, !=",
      "float, !=, ==",
      "Double, ==, !=",
      "Float, !=, =="
    })
    void continueInvertingFloatingEquality(String type, String beforeOperator, String afterOperator) {
        rewriteRun(
          spec -> spec.cycles(3).expectedCyclesThatMakeChanges(1),
          java(
            """
              class Test {
                  boolean test(%s value) {
                      return !(value %s 0);
                  }
              }
              """.formatted(type, beforeOperator),
            """
              class Test {
                  boolean test(%s value) {
                      return value %s 0;
                  }
              }
              """.formatted(type, afterOperator)
          )
        );
    }

    @Test
    void preserveEffectfulFloatingOperand() {
        rewriteRun(
          java(
            """
              class Test {
                  int calls;
                  double next() {
                      calls++;
                      return Double.NaN;
                  }
                  boolean test() {
                      return !(next() > 0);
                  }
              }
              """
          )
        );
    }

    @ParameterizedTest
    @ValueSource(strings = {"Double", "Float"})
    void preserveGenericFloatingBounds(String type) {
        rewriteRun(
          java(
            """
              class Test {
                  <T extends %s> boolean test(T value) {
                      return !(value > 0);
                  }
              }
              """.formatted(type)
          )
        );
    }

    static Stream<Arguments> missingOperandTypes() {
        return Stream.of(false, true)
          .flatMap(right -> Stream.<JavaType>of(null, JavaType.Unknown.getInstance())
            .map(type -> Arguments.of(type, right)));
    }

    @ParameterizedTest
    @MethodSource("missingOperandTypes")
    void preserveComparisonWithMissingOperandType(JavaType missingType, boolean right) {
        ExecutionContext ctx = new InMemoryExecutionContext(t -> {
            throw new AssertionError(t);
        });
        J.CompilationUnit parsed = (J.CompilationUnit) JavaParser.fromJavaVersion().build().parse(ctx,
          "class Test { boolean test(int left, int right) { return !(left > right); } }")
          .findFirst().orElseThrow();
        // Deliberately remove only operand attribution in this visitor contract test.
        // The RewriteTest cases above retain their normal type validation.
        J.CompilationUnit unattributed = (J.CompilationUnit) new JavaIsoVisitor<ExecutionContext>() {
            @Override
            public J.Binary visitBinary(J.Binary binary, ExecutionContext executionContext) {
                return right ? binary.withRight(binary.getRight().withType(missingType)) :
                  binary.withLeft(binary.getLeft().withType(missingType));
            }
        }.visit(parsed, ctx);
        J.CompilationUnit result = (J.CompilationUnit) new BooleanChecksNotInverted()
          .getVisitor().visit(unattributed, ctx);
        assertThat(result).isNotNull();
        assertThat(result.printAll()).isEqualTo(unattributed.printAll());
    }

}
