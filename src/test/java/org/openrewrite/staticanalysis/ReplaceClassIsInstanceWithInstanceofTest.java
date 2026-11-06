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
import org.openrewrite.InMemoryExecutionContext;
import org.openrewrite.Issue;
import org.openrewrite.groovy.tree.G;
import org.openrewrite.java.JavaIsoVisitor;
import org.openrewrite.java.JavaParser;
import org.openrewrite.java.tree.Expression;
import org.openrewrite.java.tree.J;
import org.openrewrite.java.tree.JavaType;

import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.openrewrite.groovy.Assertions.groovy;
import org.openrewrite.test.RecipeSpec;
import org.openrewrite.test.RewriteTest;

import static org.openrewrite.java.Assertions.java;
import static org.openrewrite.java.Assertions.javaVersion;

@SuppressWarnings({"RedundantClassCall", "ConstantValue", "UnusedAssignment"})
class ReplaceClassIsInstanceWithInstanceofTest implements RewriteTest {

    @Override
    public void defaults(RecipeSpec spec) {
        spec.recipe(new ReplaceClassIsInstanceWithInstanceof());
    }

    @Test
    void nestedWildcardOperand() {
        AtomicReference<Expression> original = new AtomicReference<>();
        rewriteRun(
          java(
            """
              interface Column<T> {}
              interface Table<C extends Column<?>, T> {}
              class BoolTable implements Table<Column<Boolean>, Boolean> {}
              class Test {
                  boolean test(Table<? extends Column<?>, ?> value) {
                      return BoolTable.class.isInstance(value);
                  }
              }
              """,
            """
              interface Column<T> {}
              interface Table<C extends Column<?>, T> {}
              class BoolTable implements Table<Column<Boolean>, Boolean> {}
              class Test {
                  boolean test(Table<? extends Column<?>, ?> value) {
                      return value instanceof BoolTable;
                  }
              }
              """,
            spec -> spec.mapBeforeRecipe(cu -> (J.CompilationUnit) new JavaIsoVisitor<Integer>() {
                @Override
                public J.MethodInvocation visitMethodInvocation(J.MethodInvocation method, Integer p) {
                    J.MethodInvocation m = super.visitMethodInvocation(method, p);
                    if (!"isInstance".equals(m.getSimpleName())) {
                        return m;
                    }
                    J.Identifier operand = (J.Identifier) m.getArguments().get(0);
                    JavaType.Parameterized table = (JavaType.Parameterized) operand.getType();
                    JavaType.GenericTypeVariable columnBound = (JavaType.GenericTypeVariable) table.getTypeParameters().get(0);
                    JavaType.Parameterized column = (JavaType.Parameterized) columnBound.getBounds().get(0);
                    JavaType.GenericTypeVariable wildcard = (JavaType.GenericTypeVariable) column.getTypeParameters().get(0);
                    // Legacy LSTs retain a captured wildcard as the bound of another wildcard.
                    JavaType captured = new JavaType.GenericTypeVariable(null, "?",
                      JavaType.GenericTypeVariable.Variance.COVARIANT, List.of(wildcard));
                    JavaType type = table.withTypeParameters(List.of(columnBound.withBounds(
                      List.of(column.withTypeParameters(List.of(captured)))), table.getTypeParameters().get(1)));
                    original.set(operand.withType(type));
                    return m.withArguments(List.of(original.get()));
                }
            }.visit(cu, 0)).afterRecipe(cu -> new JavaIsoVisitor<Integer>() {
                @Override
                public J.InstanceOf visitInstanceOf(J.InstanceOf instanceOf, Integer p) {
                    assertThat(instanceOf.getExpression().getId()).isEqualTo(original.get().getId());
                    assertThat(instanceOf.getExpression().getType()).isSameAs(original.get().getType());
                    return super.visitInstanceOf(instanceOf, p);
                }
            }.visit(cu, 0))
          )
        );
    }

    @Test
    void groovyStatementPositions() {
        rewriteRun(
          groovy(
            """
              class Test {
                  boolean check(Object value) {
                      // retain the implicit return and label
                      expect:
                      /* check */ String.class.isInstance(value)
                  }
                  boolean block(Object value) {
                      String.class.isInstance(value)
                  }
                  void body(Object value) {
                      if (value != null) String.class.isInstance(value)
                  }
              }
              """,
            """
              class Test {
                  boolean check(Object value) {
                      // retain the implicit return and label
                      expect:
                      /* check */ value instanceof String
                  }
                  boolean block(Object value) {
                      value instanceof String
                  }
                  void body(Object value) {
                      if (value != null) value instanceof String
                  }
              }
              """,
            spec -> spec.mapBeforeRecipe(ReplaceClassIsInstanceWithInstanceofTest::attributeClassIsInstance)
              .afterRecipe(cu -> new JavaIsoVisitor<Integer>() {
                  @Override
                  public J.Label visitLabel(J.Label label, Integer p) {
                      assertThat(label.getStatement()).isInstanceOf(G.ExpressionStatement.class);
                      return super.visitLabel(label, p);
                  }
              }.visit(cu, 0))
          )
        );
    }

    @Test
    void groovyExpressionPositions() {
        rewriteRun(
          groovy(
            """
              class Test {
                  boolean check(Object value) {
                      boolean result = String.class.isInstance(value)
                      return String.class.isInstance(value)
                  }
              }
              """,
            """
              class Test {
                  boolean check(Object value) {
                      boolean result = value instanceof String
                      return value instanceof String
                  }
              }
              """,
            spec -> spec.mapBeforeRecipe(ReplaceClassIsInstanceWithInstanceofTest::attributeClassIsInstance)
          )
        );
    }

    // Standalone Groovy parsing does not resolve Class.isInstance as the compiler LST does.
    // Supply its real Java method and class-literal types while retaining the native Groovy statement tree.
    private static G.CompilationUnit attributeClassIsInstance(G.CompilationUnit cu) {
        var ctx = new InMemoryExecutionContext();
        J.MethodInvocation[] attributed = new J.MethodInvocation[1];
        new JavaIsoVisitor<Integer>() {
            @Override
            public J.MethodInvocation visitMethodInvocation(J.MethodInvocation method, Integer p) {
                attributed[0] = method;
                return method;
            }
        }.visit(JavaParser.fromJavaVersion().build().parse(ctx,
          "class Attribution { boolean check(Object value) { return String.class.isInstance(value); } }")
          .findFirst().orElseThrow(), 0);
        assertThat(attributed[0].getMethodType()).isNotNull();
        return (G.CompilationUnit) new JavaIsoVisitor<Integer>() {
            @Override
            public J.MethodInvocation visitMethodInvocation(J.MethodInvocation method, Integer p) {
                J.MethodInvocation m = super.visitMethodInvocation(method, p);
                if ("isInstance".equals(m.getSimpleName())) {
                    J.FieldAccess select = (J.FieldAccess) m.getSelect();
                    J.FieldAccess typed = (J.FieldAccess) attributed[0].getSelect();
                    return m.withMethodType(attributed[0].getMethodType())
                      .withSelect(select.withTarget(select.getTarget().withType(typed.getTarget().getType()))
                        .withType(typed.getType()));
                }
                return m;
            }
        }.visitNonNull(cu, 0);
    }

    @DocumentExample
    @Test
    void changeInstanceOf() {
        rewriteRun(
          //language=java
          java(
            """
              class A {
                  void foo() {
                      String s = "";
                      boolean result = String.class.isInstance(s);
                  }
              }
              """,
            """
              class A {
                  void foo() {
                      String s = "";
                      boolean result = s instanceof String;
                  }
              }
              """
          )
        );
    }

    @Issue("https://github.com/openrewrite/rewrite-static-analysis/issues/1116")
    @Test
    void doNotChangeWhenInstanceofWouldNotCompile() {
        rewriteRun(
          //language=java
          java(
            """
              import java.util.List;

              class A<N extends Number> {
                  void foo(String s, Object o, int i, int[] arr, Number num, N n, List<? extends Number> nums) {
                      boolean b;
                      // Unrelated classes
                      b = Integer.class.isInstance(s);
                      b = String.class.isInstance(num);
                      // Primitive operands
                      b = String.class.isInstance(o.hashCode());
                      b = Integer.class.isInstance(i);
                      // A final class that doesn't implement the interface
                      b = Runnable.class.isInstance(s);
                      // An array is only an `Object`, `Cloneable` or `Serializable`
                      b = String.class.isInstance(arr);
                      // A type variable or wildcard capture is checked against its bound
                      b = String.class.isInstance(n);
                      b = String.class.isInstance(nums.get(0));
                  }
              }
              """
          )
        );
    }

    @Issue("https://github.com/openrewrite/rewrite-static-analysis/issues/1116")
    @Test
    void changeWhenTypesMayOverlap() {
        rewriteRun(
          //language=java
          java(
            """
              import java.io.Serializable;
              import java.util.ArrayList;
              import java.util.List;

              class A<T, N extends Number> {
                  void foo(Number num, List<?> list, CharSequence cs, Runnable r, Integer boxed, T t, N n, int[] arr, List<? super Integer> ints) {
                      boolean b;
                      b = Runnable.class.isInstance(num);
                      b = ArrayList.class.isInstance(list);
                      b = String.class.isInstance(cs);
                      b = List.class.isInstance(r);
                      b = Number.class.isInstance(boxed);
                      b = Integer.class.isInstance(num);
                      b = String.class.isInstance(t);
                      b = Integer.class.isInstance(n);
                      b = Serializable.class.isInstance(arr);
                      b = String.class.isInstance(ints.get(0));
                  }
              }
              """,
            """
              import java.io.Serializable;
              import java.util.ArrayList;
              import java.util.List;

              class A<T, N extends Number> {
                  void foo(Number num, List<?> list, CharSequence cs, Runnable r, Integer boxed, T t, N n, int[] arr, List<? super Integer> ints) {
                      boolean b;
                      b = num instanceof Runnable;
                      b = list instanceof ArrayList;
                      b = cs instanceof String;
                      b = r instanceof List;
                      b = boxed instanceof Number;
                      b = num instanceof Integer;
                      b = t instanceof String;
                      b = n instanceof Integer;
                      b = arr instanceof Serializable;
                      b = ints.get(0) instanceof String;
                  }
              }
              """
          )
        );
    }

    @Test
    void doNotChangeWhenAlreadyInstanceOf() {
        rewriteRun(
          //language=java
          java(
            """
              class A {
                  boolean foo() {
                    String s = "";
                    return s instanceof String;
                  }
              }
              """
          )
        );
    }

    @Test
    void doNotChangeWhenVariable() {
        rewriteRun(
          //language=java
          java(
            """
              class A {
                  void foo(Class<?> clazz) {
                      String s = "";
                      boolean result = clazz.isInstance(s);
                  }
              }
              """
          )
        );
    }

    @Test
    void doNotChangeInstanceOfWithVariable() {
        rewriteRun(
          //language=java
          java(
            """
              class A {
                  String foo(Object obj) {
                      if (obj instanceof String s) {
                          return s;
                      }
                      return null;
                  }
              }
              """,
            spec -> spec.markers(javaVersion(17))
          )
        );
    }

    @Test
    void methodInvocation() {
        rewriteRun(
          //language=java
          java(
            """
              import java.util.List;

              class A {
                  boolean foo(List<Object> list) {
                      return String.class.isInstance(list.get(0));
                  }
              }
              """,
            """
              import java.util.List;

              class A {
                  boolean foo(List<Object> list) {
                      return list.get(0) instanceof String;
                  }
              }
              """
          )
        );
    }

    @Test
    void fieldAccess() {
        rewriteRun(
          //language=java
          java(
            """
              class A {
                  Object content;
                  boolean foo(Object obj) {
                      return String.class.isInstance(this.content);
                  }
              }
              """,
            """
              class A {
                  Object content;
                  boolean foo(Object obj) {
                      return this.content instanceof String;
                  }
              }
              """
          )
        );
    }

    @Test
    void imported() {
        rewriteRun(
          //language=java
          java(
            """
              import java.util.Map;

              class A {
                  boolean foo(Object obj) {
                      return Map.class.isInstance(obj);
                  }
              }
              """,
            """
              import java.util.Map;

              class A {
                  boolean foo(Object obj) {
                      return obj instanceof Map;
                  }
              }
              """
          )
        );
    }

    @Test
    void importedNested() {
        rewriteRun(
          //language=java
          java(
            """
              import java.util.Map.Entry;

              class A {
                  boolean foo(Object obj) {
                      return Entry.class.isInstance(obj);
                  }
              }
              """,
            """
              import java.util.Map.Entry;

              class A {
                  boolean foo(Object obj) {
                      return obj instanceof Entry;
                  }
              }
              """
          )
        );
    }

    @Test
    void typeFromSourcePath() {
        rewriteRun(
          //language=java
          java(
            """
              package a;

              class A {
                  boolean foo(Object obj) {
                      return A.class.isInstance(obj);
                  }
              }
              """,
            """
              package a;

              class A {
                  boolean foo(Object obj) {
                      return obj instanceof A;
                  }
              }
              """
          )
        );
    }

    @Test
    void defaultPackage() {
        rewriteRun(
          //language=java
          java(
            """
              class A {
                  boolean foo(Object obj) {
                      return A.class.isInstance(obj);
                  }
              }
              """,
            """
              class A {
                  boolean foo(Object obj) {
                      return obj instanceof A;
                  }
              }
              """
          )
        );
    }

    @Test
    void typeVariable() {
        rewriteRun(
          //language=java
          java(
            """
              class A {
                  Class<?> clazz;
                  boolean foo(Object obj) {
                      return this.clazz.isInstance(obj);
                  }
              }
              """
          )
        );
    }

    @Test
    void parensAroundInstanceOf() {
        rewriteRun(
          //language=java
          java(
            """
            class A {
                boolean foo(Object one, Object two) {
                    if (one == null || !String.class.isInstance(two)) {
                        return false;
                    }
                    return true;
                }
            }
            """,
            """
            class A {
                boolean foo(Object one, Object two) {
                    if (one == null || !(two instanceof String)) {
                        return false;
                    }
                    return true;
                }
            }
            """
          )
        );
    }

    @SuppressWarnings("ResultOfMethodCallIgnored")
    @Test
    void doNotChangeExpressionStatement() {
        rewriteRun(
          //language=java
          java(
            """
              class A {
                  void foo(Object o) {
                      String.class.isInstance(o);
                  }
              }
              """
          )
        );
    }
}
