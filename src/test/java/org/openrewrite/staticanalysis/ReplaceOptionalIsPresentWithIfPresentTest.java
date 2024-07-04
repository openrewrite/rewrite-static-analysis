/*
 * Copyright 2023 the original author or authors.
 * <p>
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * <p>
 * https://www.apache.org/licenses/LICENSE-2.0
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
    void doNothingIfIsPresentNotFound() {
        rewriteRun(
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

    @DocumentExample
    @Test
    void ignoreReturnInsideLambda() {
        rewriteRun(
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
    void allowFieldAccess() {
        rewriteRun(
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

    @Disabled("Due to limitation of FinalizeLocalVariables functionality")
    @Test
    void replace2() {
        rewriteRun(
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
}
