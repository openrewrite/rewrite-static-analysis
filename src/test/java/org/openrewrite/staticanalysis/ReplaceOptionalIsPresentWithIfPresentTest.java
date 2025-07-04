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

import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.openrewrite.DocumentExample;
import org.openrewrite.Issue;
import org.openrewrite.java.JavaParser;
import org.openrewrite.test.RecipeSpec;
import org.openrewrite.test.RewriteTest;

import static org.openrewrite.java.Assertions.java;

@SuppressWarnings({"OptionalIsPresent", "ConstantValue", "OptionalUsedAsFieldOrParameterType", "UnusedAssignment", "UnnecessaryLocalVariable", "CodeBlock2Expr", "Convert2MethodRef", "SuspiciousNameCombination", "Convert2Lambda", "Anonymous2MethodRef"})
class ReplaceOptionalIsPresentWithIfPresentTest implements RewriteTest {
    @Override
    public void defaults(RecipeSpec spec) {
        spec
          .parser(JavaParser.fromJavaVersion()
            .logCompilationWarningsAndErrors(true))
          .recipe(new ReplaceOptionalIsPresentWithIfPresent());
    }

    @Test
    @DocumentExample
    void ignoreReturnInsideLambda() {
        rewriteRun(
          //language=java
          java(
            """
              import java.util.Optional;
              import java.util.function.Supplier;
              public class A {
                  Supplier<Integer> s = () -> { return 1; };
                  int method(Optional<Integer> o) {
                      if (o.isPresent()) {
                          s = () -> {
                              return 2;
                          };
                      }
                      return s.get();
                  }
              }
              """,
            """
              import java.util.Optional;
              import java.util.function.Supplier;
              public class A {
                  Supplier<Integer> s = () -> { return 1; };
                  int method(Optional<Integer> o) {
                      o.ifPresent(obj -> {
                          s = () -> {
                              return 2;
                          };
                      });
                      return s.get();
                  }
              }
              """
          )
        );
    }

    @Test
    void doNothingIfIsPresentNotFound() {
        rewriteRun(
          //language=java
          java(
            """
              import java.util.Optional;
              import java.util.ArrayList;
              import java.util.List;
              public class A {
                  void method() {
                      List<Integer> list = new ArrayList<>();
                      Optional<Integer> o = Optional.of(2);
                  }
              }
              """
          )
        );
    }

    @Test
    void doNothingIfPresentPartOfElseIf() {
        rewriteRun(
          //language=java
          java(
            """
              import java.util.Optional;
              import java.util.ArrayList;
              import java.util.List;
              public class A {
                  void method() {
                      boolean x=false;
                      List<Integer> list = new ArrayList<>();
                      Optional<Integer> o = Optional.of(2);
                      if(x){
                          System.out.println("hello");
                      }else if(o.isPresent()){
                          list.add(o.get());
                      }
                  }
              }
              """
          )
        );
    }

    @Test
    void doNothingIfElsePartPresent() {
        rewriteRun(
          //language=java
          java(
            """
              import java.util.Optional;
              import java.util.ArrayList;
              import java.util.List;
              public class A {
                  void method() {
                      List<Integer> list = new ArrayList<>();
                      Optional<Integer> o = Optional.of(2);
                      if(o.isPresent()){
                          list.add(o.get());
                      }else{
                          System.out.println("else part");
                      }
                  }
              }
              """
          )
        );
    }

    @Test
    void doNothingIfContainsReturn() {
        rewriteRun(
          //language=java
          java(
            """
              import java.util.Optional;
              public class A {
                  Integer method(Optional<Integer> o) {
                      if (o.isPresent()){
                          return o.get();
                      }
                      return -1;
                  }
              }
              """
          )
        );
    }

    @Test
    void allowFieldAccess() {
        rewriteRun(
          //language=java
          java(
            """
              import java.util.Optional;
              import java.util.function.Supplier;
              public class A {
                  int method(Optional<Integer> o) {
                      if (o.isPresent()) {
                          System.out.println(o.get());
                      }
                      return 1;
                  }
              }
              """,
            """
              import java.util.Optional;
              import java.util.function.Supplier;
              public class A {
                  int method(Optional<Integer> o) {
                      o.ifPresent(obj -> {
                          System.out.println(obj);
                      });
                      return 1;
                  }
              }
              """
          )
        );
    }

    @Test
    void ignoreReturnInsideAnonymousSubclass() {
        rewriteRun(
          //language=java
          java(
            """
              import java.util.Optional;
              import java.util.function.Supplier;
              public class A {
                  Supplier<Integer> s = () -> { return 1; };
                  int method(Optional<Integer> o) {
                      if (o.isPresent()) {
                          s = new Supplier<>() {
                              @Override
                              public Integer get() {
                                  return o.get();
                              }
                          };
                      }
                      return s.get();
                  }
              }
              """,
            """
              import java.util.Optional;
              import java.util.function.Supplier;
              public class A {
                  Supplier<Integer> s = () -> { return 1; };
                  int method(Optional<Integer> o) {
                      o.ifPresent(obj -> {
                          s = new Supplier<>() {
                              @Override
                              public Integer get() {
                                  return obj;
                              }
                          };
                      });
                      return s.get();
                  }
              }
              """
          )
        );
    }

    @Test
    void doNothingIfContainsThrow() {
        rewriteRun(
          //language=java
          java(
            """
              import java.util.Optional;
              public class A {
                  Integer method(Optional<Integer> o) {
                      if (o.isPresent()){
                          throw new RuntimeException();
                      }
                      return -1;
                  }
              }
              """
          )
        );
    }

    @Test
    void doNothingIfLocalVariableAssignedInsideIfBlock() {
        rewriteRun(
          //language=java
          java(
            """
              import java.util.Optional;
              import java.util.ArrayList;
              import java.util.List;
              public class A {
                  void method() {
                      int x;
                      List<Integer> list = new ArrayList<>();
                      Optional<Integer> o = Optional.of(2);
                      if(o.isPresent()){
                          x=o.get();
                          list.add(o.get());
                      }
                  }
              }
              """
          )
        );
    }

    @Test
    void doNothingIfNonEffectivelyFinalVariableAccessed() {
        rewriteRun(
          //language=java
          java(
            """
              import java.util.Optional;
              import java.util.ArrayList;
              import java.util.List;
              public class A {
                  void method() {
                      int x=10;
                      List<Integer> list = new ArrayList<>();
                      Optional<Integer> o = Optional.of(2);
                      if(o.isPresent()){
                          int y=x;
                          list.add(o.get());
                      }
                      x=20;
                  }
              }
              """
          ),
          //language=java
          java(
            """
              import java.util.Optional;
              import java.util.ArrayList;
              import java.util.List;
              public class B {
                  void method() {
                      int x;
                      x=10;
                      List<Integer> list = new ArrayList<>();
                      Optional<Integer> o = Optional.of(2);
                      if(o.isPresent()){
                          int y=x;
                          list.add(o.get());
                      }
                      x=20;
                  }
              }
              """
          )
        );
    }

    @Test
    void nestedOptionalsUnlessHandledCorrectly() {
        rewriteRun(
          //language=java
          java(
            """
              import java.util.Optional;
              public class A {
                  Integer method(Optional<Integer> a, Optional<Integer> b, Optional<Integer> c) {
                      if (a.isPresent()) {
                          if (b.isPresent()) {
                              if (c.isPresent()) {
                                  int x = a.get() + b.get() + c.get();
                              }
                          }
                      }
                      return -1;
                  }
              }
              """,
            """
              import java.util.Optional;
              public class A {
                  Integer method(Optional<Integer> a, Optional<Integer> b, Optional<Integer> c) {
                      a.ifPresent(obj2 -> {
                          b.ifPresent(obj1 -> {
                              c.ifPresent(obj -> {
                                  int x = obj2 + obj1 + obj;
                              });
                          });
                      });
                      return -1;
                  }
              }
              """
          )
        );
    }

    @Test
    void replace() {
        rewriteRun(
          //language=java
          java(
            """
              import java.util.Optional;
              import java.util.ArrayList;
              import java.util.List;
              public class A {
                  void method() {
                      List<Integer> list = new ArrayList<>();
                      Optional<Integer> o = Optional.of(2);
                      if(o.isPresent()){
                          list.add(o.get());
                      }
                  }
              }
              """,
            """
              import java.util.Optional;
              import java.util.ArrayList;
              import java.util.List;
              public class A {
                  void method() {
                      List<Integer> list = new ArrayList<>();
                      Optional<Integer> o = Optional.of(2);
                      o.ifPresent(obj -> {
                          list.add(obj);
                      });
                  }
              }
              """
          )
        );
    }

    @Test
    @Disabled("Due to limitation of FinalizeLocalVariables functionality")
    void replace2() {
        rewriteRun(
          //language=java
          java(
            """
              import java.util.Optional;
              import java.util.ArrayList;
              import java.util.List;
              public class B {
                  void method() {
                      final int z=12;
                      int x;
                      x=10;
                      List<Integer> list = new ArrayList<>();
                      Optional<Integer> o = Optional.of(2);
                      if(o.isPresent()){
                          int y = x;
                          list.add(o.get());
                          System.out.println(o.get() + y + z);
                      }
                  }
              }
              """,
            """
              import java.util.Optional;
              import java.util.ArrayList;
              import java.util.List;
              public class B {
                  void method() {
                      final int z=12;
                      int x;
                      x=10;
                      List<Integer> list = new ArrayList<>();
                      Optional<Integer> o = Optional.of(2);
                      o.ifPresent(obj -> {
                          int y = x;
                          list.add(obj);
                          System.out.println(obj + y + z);
                      });
                  }
              }
              """
          )
        );
    }

    @Test
    void replaceIfStaticVariableAccessedORAssigned() {
        rewriteRun(
          //language=java
          java(
            """
              import java.util.Optional;
              import java.util.ArrayList;
              import java.util.List;
              public class A {
                  static int z = 10;
                  void method() {
                      z=20;
                      List<Integer> list = new ArrayList<>();
                      Optional<Integer> o = Optional.of(2);
                      if(o.isPresent()){
                          z = 30;
                          list.add(o.get());
                      }
                  }
              }
              """,
            """
              import java.util.Optional;
              import java.util.ArrayList;
              import java.util.List;
              public class A {
                  static int z = 10;
                  void method() {
                      z=20;
                      List<Integer> list = new ArrayList<>();
                      Optional<Integer> o = Optional.of(2);
                      o.ifPresent(obj -> {
                          z = 30;
                          list.add(obj);
                      });
                  }
              }
              """
          ),
          //language=java
          java(
            """
              import java.util.Optional;
              import java.util.ArrayList;
              import java.util.List;
              public class B {
                  static int z = 10;
                  void method() {
                      z=20;
                      List<Integer> list = new ArrayList<>();
                      Optional<Integer> o = Optional.of(2);
                      if(o.isPresent()){
                          list.add(o.get() + z);
                      }
                  }
              }
              """,
            """
              import java.util.Optional;
              import java.util.ArrayList;
              import java.util.List;
              public class B {
                  static int z = 10;
                  void method() {
                      z=20;
                      List<Integer> list = new ArrayList<>();
                      Optional<Integer> o = Optional.of(2);
                      o.ifPresent(obj -> {
                          list.add(obj + z);
                      });
                  }
              }
              """
          )
        );
    }

    @Test
    void replaceIfInstanceVariableAssignedORAccessed() {
        rewriteRun(
          //language=java
          java(
            """
              import java.util.Optional;
              import java.util.ArrayList;
              import java.util.List;
              public class A {
                  int z = 10;
                  void method() {
                      z=20;
                      List<Integer> list = new ArrayList<>();
                      Optional<Integer> o = Optional.of(2);
                      if(o.isPresent()){
                          z = 30;
                          list.add(o.get());
                      }
                  }
              }
              """,
            """
              import java.util.Optional;
              import java.util.ArrayList;
              import java.util.List;
              public class A {
                  int z = 10;
                  void method() {
                      z=20;
                      List<Integer> list = new ArrayList<>();
                      Optional<Integer> o = Optional.of(2);
                      o.ifPresent(obj -> {
                          z = 30;
                          list.add(obj);
                      });
                  }
              }
              """
          ),
          //language=java
          java(
            """
              import java.util.Optional;
              import java.util.ArrayList;
              import java.util.List;
              public class B {
                  int z = 10;
                  void method() {
                      z=20;
                      List<Integer> list = new ArrayList<>();
                      Optional<Integer> o = Optional.of(2);
                      if(o.isPresent()){
                          list.add(o.get() + z);
                      }
                  }
              }
              """,
            """
              import java.util.Optional;
              import java.util.ArrayList;
              import java.util.List;
              public class B {
                  int z = 10;
                  void method() {
                      z=20;
                      List<Integer> list = new ArrayList<>();
                      Optional<Integer> o = Optional.of(2);
                      o.ifPresent(obj -> {
                          list.add(obj + z);
                      });
                  }
              }
              """
          )
        );
    }

    @Test
    void replaceNestedIf() {
        rewriteRun(
          //language=java
          java(
            """
              import java.util.Optional;
              import java.util.ArrayList;
              import java.util.List;
              public class A {
                  void method() {
                      boolean x = true;
                      List<Integer> list = new ArrayList<>();
                      Optional<Integer> o = Optional.of(2);
                      if(x){
                          if(o.isPresent()){
                              list.add(o.get());
                          }
                      }
                  }
              }
              """,
            """
              import java.util.Optional;
              import java.util.ArrayList;
              import java.util.List;
              public class A {
                  void method() {
                      boolean x = true;
                      List<Integer> list = new ArrayList<>();
                      Optional<Integer> o = Optional.of(2);
                      if(x){
                          o.ifPresent(obj -> {
                              list.add(obj);
                          });
                      }
                  }
              }
              """
          )
        );
    }

    @Test
    void replaceAndHandleDifferentOptionalsPresent() {
        rewriteRun(
          //language=java
          java(
            """
              import java.util.Optional;
              import java.util.ArrayList;
              import java.util.List;
              public class A {
                  void method() {
                      List<Integer> list = new ArrayList<>();
                      Optional<Integer> o = Optional.of(2);
                      Optional<Integer> o2 = Optional.of(3);
                      if(o.isPresent()){
                          list.add(o.get());
                          list.add(o2.get());
                      }
                  }
              }
              """,
            """
              import java.util.Optional;
              import java.util.ArrayList;
              import java.util.List;
              public class A {
                  void method() {
                      List<Integer> list = new ArrayList<>();
                      Optional<Integer> o = Optional.of(2);
                      Optional<Integer> o2 = Optional.of(3);
                      o.ifPresent(obj -> {
                          list.add(obj);
                          list.add(o2.get());
                      });
                  }
              }
              """
          )
        );
    }

    @Test
    void doNothingIfIsPresentOnMethodInvocation() {
        rewriteRun(
          //language=java
          java(
            """
              import java.util.Optional;

              public class Foo {
                  public static void main(String[] args) {
                      if (next().isPresent()) {
                          System.out.println("Message: " + next().get());
                      }
                  }
                  private static Optional<String> next() {
                      // not guaranteed to return the same value every time, so best not to change above code
                      return Optional.of("foo");
                  }
              }
              """
          )
        );
    }

    @Test
    @Issue("https://github.com/openrewrite/rewrite-static-analysis/issues/435")
    void doNothingIfMethodThrowsException() {
        rewriteRun(
          //language=java
          java(
            """
              import java.util.Optional;
              public class Foo {
                  public void foo() throws Exception {
                      Optional<String> optional = Optional.empty();
                      if (optional.isPresent()) {
                          bar(optional.get());
                      }
                  }

                  private void bar(String s) throws Exception {
                      throw new Exception("bar");
                  }
              }
              """
          )
        );
    }
}
