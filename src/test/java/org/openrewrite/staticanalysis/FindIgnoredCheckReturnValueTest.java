/*
 * Copyright 2026 the original author or authors.
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
import org.openrewrite.java.JavaParser;
import org.openrewrite.test.RecipeSpec;
import org.openrewrite.test.RewriteTest;

import java.util.List;

import static org.openrewrite.java.Assertions.java;

@SuppressWarnings({"ResultOfMethodCallIgnored", "unused"})
class FindIgnoredCheckReturnValueTest implements RewriteTest {

    @Override
    public void defaults(RecipeSpec spec) {
        spec.recipe(new FindIgnoredCheckReturnValue(null))
          .parser(JavaParser.fromJavaVersion()
            .classpath("junit-jupiter-api", "assertj-core")
            .dependsOn(
              //language=java
              """
                package com.google.errorprone.annotations;
                import java.lang.annotation.*;
                @Retention(RetentionPolicy.CLASS)
                public @interface CheckReturnValue {}
                """,
              //language=java
              """
                package com.google.errorprone.annotations;
                import java.lang.annotation.*;
                @Retention(RetentionPolicy.CLASS)
                public @interface CanIgnoreReturnValue {}
                """,
              //language=java
              """
                package com.example;
                import com.google.errorprone.annotations.*;
                @CheckReturnValue
                public final class Point {
                    public Point withX(int x) { return this; }
                    @CanIgnoreReturnValue
                    public Point validate() { return this; }
                    public void print() {}
                }
                """,
              //language=java
              """
                package org.mockito;
                public class Mockito {
                    public static <T> T verify(T mock) { return mock; }
                }
                """
            ));
    }

    @DocumentExample
    @Test
    void ignoredResultOfAnnotatedClass() {
        rewriteRun(
          java(
            """
              import com.example.Point;
              class Test {
                  Point move(Point p) {
                      p.withX(1);
                      p.validate();
                      p.print();
                      return p.withX(2);
                  }
              }
              """,
            """
              import com.example.Point;
              class Test {
                  Point move(Point p) {
                      /*~~(Result of `withX` is ignored, but `@CheckReturnValue` on class `Point` requires using it. Use the returned value, remove the call, or annotate the method with `@CanIgnoreReturnValue` if ignoring the result is intended.)~~>*/p.withX(1);
                      p.validate();
                      p.print();
                      return p.withX(2);
                  }
              }
              """
          )
        );
    }

    @Test
    void annotatedMethodOnly() {
        rewriteRun(
          java(
            """
              import com.google.errorprone.annotations.CheckReturnValue;
              class Test {
                  @CheckReturnValue
                  String name() { return ""; }
                  String other() { return ""; }
                  void use() {
                      name();
                      other();
                      String n = name();
                  }
              }
              """,
            """
              import com.google.errorprone.annotations.CheckReturnValue;
              class Test {
                  @CheckReturnValue
                  String name() { return ""; }
                  String other() { return ""; }
                  void use() {
                      /*~~(Result of `name` is ignored, but `@CheckReturnValue` on the method requires using it. Use the returned value, remove the call, or annotate the method with `@CanIgnoreReturnValue` if ignoring the result is intended.)~~>*/name();
                      other();
                      String n = name();
                  }
              }
              """
          )
        );
    }

    @Test
    void canIgnoreOnNestedClassOverridesOuterClass() {
        rewriteRun(
          java(
            """
              import com.google.errorprone.annotations.*;
              @CheckReturnValue
              class Outer {
                  static class Checked {
                      String value() { return ""; }
                  }
                  @CanIgnoreReturnValue
                  static class Ignorable {
                      String value() { return ""; }
                  }
                  void use(Checked c, Ignorable i) {
                      c.value();
                      i.value();
                  }
              }
              """,
            """
              import com.google.errorprone.annotations.*;
              @CheckReturnValue
              class Outer {
                  static class Checked {
                      String value() { return ""; }
                  }
                  @CanIgnoreReturnValue
                  static class Ignorable {
                      String value() { return ""; }
                  }
                  void use(Checked c, Ignorable i) {
                      /*~~(Result of `value` is ignored, but `@CheckReturnValue` on class `Outer` requires using it. Use the returned value, remove the call, or annotate the method with `@CanIgnoreReturnValue` if ignoring the result is intended.)~~>*/c.value();
                      i.value();
                  }
              }
              """
          )
        );
    }

    @Test
    void classRetentionAnnotationFromClasspath() {
        rewriteRun(
          java(
            """
              import static org.assertj.core.api.Assertions.assertThat;
              class Test {
                  void check(String s) {
                      assertThat(s);
                      assertThat(s).isNotEmpty();
                  }
              }
              """,
            """
              import static org.assertj.core.api.Assertions.assertThat;
              class Test {
                  void check(String s) {
                      /*~~(Result of `assertThat` is ignored, but `@CheckReturnValue` on class `Assertions` requires using it. Use the returned value, remove the call, or annotate the method with `@CanIgnoreReturnValue` if ignoring the result is intended.)~~>*/assertThat(s);
                      assertThat(s).isNotEmpty();
                  }
              }
              """
          )
        );
    }

    @Test
    void statementPositions() {
        rewriteRun(
          java(
            """
              import com.example.Point;
              class Test {
                  void positions(Point p, boolean b, int n) {
                      if (b) p.withX(1);
                      else p.withX(2);
                      while (b) p.withX(3);
                      for (p.withX(4); b; p.withX(5)) {
                      }
                      label: p.withX(6);
                      switch (n) {
                          case 1:
                              p.withX(7);
                      }
                      switch (n) {
                          case 1 -> p.withX(8);
                          default -> {}
                      }
                  }
              }
              """,
            """
              import com.example.Point;
              class Test {
                  void positions(Point p, boolean b, int n) {
                      if (b) /*~~(Result of `withX` is ignored, but `@CheckReturnValue` on class `Point` requires using it. Use the returned value, remove the call, or annotate the method with `@CanIgnoreReturnValue` if ignoring the result is intended.)~~>*/p.withX(1);
                      else /*~~(Result of `withX` is ignored, but `@CheckReturnValue` on class `Point` requires using it. Use the returned value, remove the call, or annotate the method with `@CanIgnoreReturnValue` if ignoring the result is intended.)~~>*/p.withX(2);
                      while (b) /*~~(Result of `withX` is ignored, but `@CheckReturnValue` on class `Point` requires using it. Use the returned value, remove the call, or annotate the method with `@CanIgnoreReturnValue` if ignoring the result is intended.)~~>*/p.withX(3);
                      for (/*~~(Result of `withX` is ignored, but `@CheckReturnValue` on class `Point` requires using it. Use the returned value, remove the call, or annotate the method with `@CanIgnoreReturnValue` if ignoring the result is intended.)~~>*/p.withX(4); b; /*~~(Result of `withX` is ignored, but `@CheckReturnValue` on class `Point` requires using it. Use the returned value, remove the call, or annotate the method with `@CanIgnoreReturnValue` if ignoring the result is intended.)~~>*/p.withX(5)) {
                      }
                      label: /*~~(Result of `withX` is ignored, but `@CheckReturnValue` on class `Point` requires using it. Use the returned value, remove the call, or annotate the method with `@CanIgnoreReturnValue` if ignoring the result is intended.)~~>*/p.withX(6);
                      switch (n) {
                          case 1:
                              /*~~(Result of `withX` is ignored, but `@CheckReturnValue` on class `Point` requires using it. Use the returned value, remove the call, or annotate the method with `@CanIgnoreReturnValue` if ignoring the result is intended.)~~>*/p.withX(7);
                      }
                      switch (n) {
                          case 1 -> /*~~(Result of `withX` is ignored, but `@CheckReturnValue` on class `Point` requires using it. Use the returned value, remove the call, or annotate the method with `@CanIgnoreReturnValue` if ignoring the result is intended.)~~>*/p.withX(8);
                          default -> {}
                      }
                  }
              }
              """
          )
        );
    }

    @Test
    void unchangedWhenResultIsUsed() {
        rewriteRun(
          java(
            """
              import com.example.Point;
              import java.util.Objects;
              import java.util.function.Function;
              import java.util.function.Supplier;
              class Test {
                  Point use(Point p, int n) {
                      Objects.requireNonNull(p.withX(1));
                      Supplier<Point> s = () -> p.withX(2);
                      Function<Integer, Point> f = p::withX;
                      Point q = switch (n) {
                          case 1 -> p.withX(3);
                          default -> p;
                      };
                      for (; p.withX(4) != null; ) {
                          break;
                      }
                      return q;
                  }
              }
              """
          )
        );
    }

    @Test
    void voidFunctionalInterfaces() {
        rewriteRun(
          java(
            """
              import com.example.Point;
              import java.util.List;
              class Test {
                  void use(List<Point> points, List<Integer> xs, Point p) {
                      points.forEach(q -> q.withX(1));
                      xs.forEach(p::withX);
                      Runnable r = () -> p.withX(2);
                  }
              }
              """,
            """
              import com.example.Point;
              import java.util.List;
              class Test {
                  void use(List<Point> points, List<Integer> xs, Point p) {
                      points.forEach(q -> /*~~(Result of `withX` is ignored, but `@CheckReturnValue` on class `Point` requires using it. Use the returned value, remove the call, or annotate the method with `@CanIgnoreReturnValue` if ignoring the result is intended.)~~>*/q.withX(1));
                      xs.forEach(/*~~(Result of `withX` is ignored, but `@CheckReturnValue` on class `Point` requires using it. Use the returned value, remove the call, or annotate the method with `@CanIgnoreReturnValue` if ignoring the result is intended.)~~>*/p::withX);
                      Runnable r = () -> /*~~(Result of `withX` is ignored, but `@CheckReturnValue` on class `Point` requires using it. Use the returned value, remove the call, or annotate the method with `@CanIgnoreReturnValue` if ignoring the result is intended.)~~>*/p.withX(2);
                  }
              }
              """
          )
        );
    }

    @Test
    void constructorOfAnnotatedClass() {
        rewriteRun(
          java(
            """
              import com.google.errorprone.annotations.CheckReturnValue;
              @CheckReturnValue
              class Money {
                  Money(int cents) {}
                  Money() {
                      this(0);
                  }
                  static void use() {
                      new Money(1);
                      Money m = new Money(2);
                  }
              }
              """,
            """
              import com.google.errorprone.annotations.CheckReturnValue;
              @CheckReturnValue
              class Money {
                  Money(int cents) {}
                  Money() {
                      this(0);
                  }
                  static void use() {
                      /*~~(Result of `new Money` is ignored, but `@CheckReturnValue` on class `Money` requires using it. Use the returned value, remove the call, or annotate the method with `@CanIgnoreReturnValue` if ignoring the result is intended.)~~>*/new Money(1);
                      Money m = new Money(2);
                  }
              }
              """
          )
        );
    }

    @Test
    void anonymousClassDiscardedByLambda() {
        rewriteRun(
          java(
            """
              import com.google.errorprone.annotations.CheckReturnValue;
              import java.util.function.Consumer;
              @CheckReturnValue
              class Test {
                  abstract static class Visitor {
                      abstract void visit(String s);
                  }
                  Consumer<String> afterRecipe = s -> new Visitor() {
                      @Override
                      void visit(String s) {
                      }
                  };
              }
              """,
            """
              import com.google.errorprone.annotations.CheckReturnValue;
              import java.util.function.Consumer;
              @CheckReturnValue
              class Test {
                  abstract static class Visitor {
                      abstract void visit(String s);
                  }
                  Consumer<String> afterRecipe = s -> /*~~(Result of `new Test.Visitor() {...}` is ignored, but `@CheckReturnValue` on class `Test` requires using it. Use the returned value, remove the call, or annotate the method with `@CanIgnoreReturnValue` if ignoring the result is intended.)~~>*/new Visitor() {
                      @Override
                      void visit(String s) {
                      }
                  };
              }
              """
          )
        );
    }

    @Test
    void localClassInAnnotatedClass() {
        rewriteRun(
          java(
            """
              import com.google.errorprone.annotations.CheckReturnValue;
              @CheckReturnValue
              class Test {
                  void use() {
                      class Local {
                          String value() { return ""; }
                          void self() {
                              value();
                          }
                      }
                      new Local().value();
                  }
              }
              """,
            """
              import com.google.errorprone.annotations.CheckReturnValue;
              @CheckReturnValue
              class Test {
                  void use() {
                      class Local {
                          String value() { return ""; }
                          void self() {
                              /*~~(Result of `value` is ignored, but `@CheckReturnValue` on class `Test` requires using it. Use the returned value, remove the call, or annotate the method with `@CanIgnoreReturnValue` if ignoring the result is intended.)~~>*/value();
                          }
                      }
                      /*~~(Result of `value` is ignored, but `@CheckReturnValue` on class `Test` requires using it. Use the returned value, remove the call, or annotate the method with `@CanIgnoreReturnValue` if ignoring the result is intended.)~~>*/new Local().value();
                  }
              }
              """
          )
        );
    }

    @Test
    void localClassInsideAnonymousClass() {
        rewriteRun(
          java(
            """
              import com.google.errorprone.annotations.CheckReturnValue;
              @CheckReturnValue
              class Test {
                  Runnable task = new Runnable() {
                      @Override
                      public void run() {
                          class Local {
                              String value() { return ""; }
                              void self() {
                                  value();
                              }
                          }
                      }
                  };
              }
              """,
            """
              import com.google.errorprone.annotations.CheckReturnValue;
              @CheckReturnValue
              class Test {
                  Runnable task = new Runnable() {
                      @Override
                      public void run() {
                          class Local {
                              String value() { return ""; }
                              void self() {
                                  /*~~(Result of `value` is ignored, but `@CheckReturnValue` on class `Test` requires using it. Use the returned value, remove the call, or annotate the method with `@CanIgnoreReturnValue` if ignoring the result is intended.)~~>*/value();
                              }
                          }
                      }
                  };
              }
              """
          )
        );
    }

    @Test
    void unchangedForSuperConstructorCall() {
        rewriteRun(
          java(
            """
              import com.google.errorprone.annotations.CheckReturnValue;
              @CheckReturnValue
              class Base {
                  Base(int cents) {}
              }
              class Derived extends Base {
                  Derived() {
                      super(0);
                  }
              }
              """
          )
        );
    }

    @Test
    void packageInfoInRepository() {
        rewriteRun(
          java(
            """
              @CheckReturnValue
              package com.example.immutable;
              import com.google.errorprone.annotations.CheckReturnValue;
              """,
            spec -> spec.path("com/example/immutable/package-info.java")
          ),
          java(
            """
              package com.example.immutable;
              public class Name {
                  public Name trim() { return this; }
                  void use() {
                      trim();
                  }
              }
              """,
            """
              package com.example.immutable;
              public class Name {
                  public Name trim() { return this; }
                  void use() {
                      /*~~(Result of `trim` is ignored, but `@CheckReturnValue` on package `com.example.immutable` requires using it. Use the returned value, remove the call, or annotate the method with `@CanIgnoreReturnValue` if ignoring the result is intended.)~~>*/trim();
                  }
              }
              """
          )
        );
    }

    @Test
    void packagesFromOption() {
        rewriteRun(
          spec -> spec.recipe(new FindIgnoredCheckReturnValue(List.of("com.example.immutable"))),
          java(
            """
              package com.example.immutable;
              public class Name {
                  public Name trim() { return this; }
              }
              """
          ),
          java(
            """
              import com.example.immutable.Name;
              class Test {
                  void use(Name n) {
                      n.trim();
                  }
              }
              """,
            """
              import com.example.immutable.Name;
              class Test {
                  void use(Name n) {
                      /*~~(Result of `trim` is ignored, but `@CheckReturnValue` on package `com.example.immutable` requires using it. Use the returned value, remove the call, or annotate the method with `@CanIgnoreReturnValue` if ignoring the result is intended.)~~>*/n.trim();
                  }
              }
              """
          )
        );
    }

    @Test
    void unchangedWhenExpectedToThrow() {
        rewriteRun(
          java(
            """
              import com.example.Point;
              import static org.assertj.core.api.Assertions.assertThatThrownBy;
              import static org.junit.jupiter.api.Assertions.assertThrows;
              import static org.junit.jupiter.api.Assertions.fail;
              class Test {
                  void expectations(Point p) {
                      assertThrows(IllegalArgumentException.class, () -> p.withX(-1));
                      assertThatThrownBy(() -> p.withX(-2)).isInstanceOf(IllegalArgumentException.class);
                      try {
                          p.withX(-3);
                          fail();
                      } catch (IllegalArgumentException expected) {
                      }
                  }
              }
              """
          )
        );
    }

    @Test
    void unchangedOnMockitoVerification() {
        rewriteRun(
          java(
            """
              import com.example.Point;
              import static org.mockito.Mockito.verify;
              class Test {
                  void verification(Point p) {
                      verify(p).withX(1);
                  }
              }
              """
          )
        );
    }
}
