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
import org.junit.jupiter.params.provider.MethodSource;
import org.openrewrite.DocumentExample;
import org.openrewrite.ExecutionContext;
import org.openrewrite.InMemoryExecutionContext;
import org.openrewrite.java.JavaIsoVisitor;
import org.openrewrite.java.JavaParser;
import org.openrewrite.java.tree.Expression;
import org.openrewrite.java.tree.J;
import org.openrewrite.java.tree.JavaType;
import org.openrewrite.test.RecipeSpec;
import org.openrewrite.test.RewriteTest;

import java.util.List;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.openrewrite.groovy.Assertions.groovy;
import static org.openrewrite.java.Assertions.java;

@SuppressWarnings({"BooleanConstructorCall", "CachedNumberConstructorCall"})
class NoPrimitiveWrappersForToStringOrCompareToTest implements RewriteTest {

    @Override
    public void defaults(RecipeSpec spec) {
        spec.recipe(new NoPrimitiveWrappersForToStringOrCompareTo());
    }

    @Test
    void preserveDoubleConstructorForToString() {
        rewriteRun(
          java(
            """
              class T {
                  String primitive(double value) {
                      return new Float(value).toString();
                  }

                  String boxed(Double value) {
                      return new Float(value).toString();
                  }

                  String parenthesized(double value) {
                      return new Float((value)).toString();
                  }

                  String literal() {
                      return new Float(1.25d).toString();
                  }
              }
              """
          )
        );
    }

    @Test
    void preserveDoubleConstructorForCompareTo() {
        rewriteRun(
          java(
            """
              class T {
                  int primitive(double value, Float other) {
                      return new Float(value).compareTo(other);
                  }

                  int boxed(Double value, Float other) {
                      return new Float(value).compareTo(other);
                  }

                  int parenthesized(double value, Float other) {
                      return new Float((value)).compareTo(other);
                  }

                  int literal(Float other) {
                      return new Float(1.25d).compareTo(other);
                  }
              }
              """
          )
        );
    }

    @Test
    void preserveBoundedAndEffectfulDoubleArguments() {
        rewriteRun(
          java(
            """
              class T {
                  <N extends Double> String bounded(N value) {
                      return new Float(value).toString();
                  }

                  int effectful() {
                      return new Float(nextDouble()).compareTo(nextFloat());
                  }

                  Double nextDouble() { return null; }
                  Float nextFloat() { return 0f; }
              }
              """
          )
        );
    }

    @Test
    void preserveDoubleConstructorInGroovy() {
        rewriteRun(
          groovy(
            """
              class T {
                  String text(double value) {
                      return new Float(value).toString()
                  }

                  int comparison(Double value, Float other) {
                      return new Float(value).compareTo(other)
                  }
              }
              """
          )
        );
    }

    static Stream<JavaType> missingArgumentTypes() {
        return Stream.of(null, JavaType.Unknown.getInstance());
    }

    @ParameterizedTest
    @MethodSource("missingArgumentTypes")
    void preserveDoubleConstructorWithMissingArgumentType(JavaType missingType) {
        ExecutionContext ctx = new InMemoryExecutionContext(t -> { throw new AssertionError(t); });
        J.CompilationUnit parsed = (J.CompilationUnit) JavaParser.fromJavaVersion().build().parse(ctx,
          "class T { String text(Double value) { return new Float(value).toString(); } " +
          "int comparison(Double value, Float other) { return new Float(value).compareTo(other); } }")
          .findFirst().orElseThrow();
        // Remove argument attribution only in this visitor contract test. RewriteTest
        // cases retain normal type validation and prove the compiler-attributed overloads.
        J.CompilationUnit unattributed = (J.CompilationUnit) new JavaIsoVisitor<ExecutionContext>() {
            @Override
            public J.NewClass visitNewClass(J.NewClass newClass, ExecutionContext executionContext) {
                Expression argument = newClass.getArguments().get(0).withType(missingType);
                return newClass.withArguments(List.of(argument));
            }
        }.visit(parsed, ctx);
        J.CompilationUnit result = (J.CompilationUnit) new NoPrimitiveWrappersForToStringOrCompareTo()
          .getVisitor().visit(unattributed, ctx);
        assertThat(result).isNotNull();
        assertThat(result.printAll()).isEqualTo(unattributed.printAll());
    }

    @Test
    void preserveConstructorWithMissingAttribution() {
        ExecutionContext ctx = new InMemoryExecutionContext(t -> { throw new AssertionError(t); });
        J.CompilationUnit parsed = (J.CompilationUnit) JavaParser.fromJavaVersion().build().parse(ctx,
          "class T { String text(double value) { return new Float(value).toString(); } " +
          "int comparison(double value, Float other) { return new Float(value).compareTo(other); } }")
          .findFirst().orElseThrow();
        J.CompilationUnit unattributed = (J.CompilationUnit) new JavaIsoVisitor<ExecutionContext>() {
            @Override
            public J.NewClass visitNewClass(J.NewClass newClass, ExecutionContext executionContext) {
                return newClass.withConstructorType(null);
            }
        }.visit(parsed, ctx);
        J.CompilationUnit result = (J.CompilationUnit) new NoPrimitiveWrappersForToStringOrCompareTo()
          .getVisitor().visit(unattributed, ctx);
        assertThat(result).isNotNull();
        assertThat(result.printAll()).isEqualTo(unattributed.printAll());
    }

    @Test
    void stillMigrateFloatArgumentsAndWideningConversions() {
        rewriteRun(
          java(
            """
              class T {
                  String primitive(float value) {
                      return new Float(value).toString();
                  }

                  String boxed(Float value) {
                      return new Float(value).toString();
                  }

                  int compare(float value, Float other) {
                      return new Float(value).compareTo(other);
                  }

                  String integral(int value) {
                      return new Float(value).toString();
                  }

                  int wideIntegral(long value, Float other) {
                      return new Float(value).compareTo(other);
                  }

                  String explicitNarrowing(double value) {
                      return new Float((float) value).toString();
                  }

                  String valueOf(float value) {
                      return Float.valueOf(value).toString();
                  }
              }
              """,
            """
              class T {
                  String primitive(float value) {
                      return Float.toString(value);
                  }

                  String boxed(Float value) {
                      return Float.toString(value);
                  }

                  int compare(float value, Float other) {
                      return Float.compare(value, other);
                  }

                  String integral(int value) {
                      return Float.toString(value);
                  }

                  int wideIntegral(long value, Float other) {
                      return Float.compare(value, other);
                  }

                  String explicitNarrowing(double value) {
                      return Float.toString((float) value);
                  }

                  String valueOf(float value) {
                      return Float.toString(value);
                  }
              }
              """
          )
        );
    }

    @DocumentExample
    @SuppressWarnings({"removal", "UnnecessaryTemporaryOnConversionToString"})
    @Test
    void noPrimitiveWrapperForToString() {
        rewriteRun(
          //language=java
          java(
            """
              class T {
                   String a = new Integer(3).toString();
                   String b = Long.valueOf(3).toString();
                   String c = Double.valueOf(3.0).toString();
                   String d = Float.valueOf("4").toString();
                   String e = new Float("3").toString();
                   String f = Boolean.valueOf(true).toString();
                   String G = Boolean.valueOf("true").toString();
              }
              """,
            """
              class T {
                   String a = Integer.toString(3);
                   String b = Long.toString(3);
                   String c = Double.toString(3.0);
                   String d = Float.valueOf("4").toString();
                   String e = new Float("3").toString();
                   String f = Boolean.toString(true);
                   String G = Boolean.valueOf("true").toString();
              }
              """
          )
        );
    }

    @SuppressWarnings("UseCompareMethod")
    @Test
    void noPrimitiveWrapperForCompareTo() {
        rewriteRun(
          //language=java
          java(
            """
              class T {
                  int a = new Boolean(true).compareTo(false);
                  int b = Boolean.valueOf(true).compareTo(false);
                  int c = new Integer(3).compareTo(4);
                  int d = new Long(4L).compareTo(5L);
              }
              """,
            """
              class T {
                  int a = Boolean.compare(true, false);
                  int b = Boolean.compare(true, false);
                  int c = Integer.compare(3, 4);
                  int d = Long.compare(4L, 5L);
              }
              """
          )
        );
    }
}
