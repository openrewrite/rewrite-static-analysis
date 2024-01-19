/*
 * Copyright 2021 the original author or authors.
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

import org.junit.jupiter.api.Test;
import org.openrewrite.DocumentExample;
import org.openrewrite.InMemoryExecutionContext;
import org.openrewrite.Issue;
import org.openrewrite.java.JavaParser;
import org.openrewrite.test.RecipeSpec;
import org.openrewrite.test.RewriteTest;

import static org.openrewrite.java.Assertions.java;

class LambdaBlockToExpressionTest implements RewriteTest {
    @Override
    public void defaults(RecipeSpec spec) {
        spec.recipe(new LambdaBlockToExpression())
            .parser(JavaParser.fromJavaVersion()
              .classpathFromResources(new InMemoryExecutionContext(), "junit-jupiter-api-5.10")
              .logCompilationWarningsAndErrors(true));
    }

    @DocumentExample
    @SuppressWarnings("CodeBlock2Expr")
    @Test
    void simplifyLambdaBlockToExpression() {
        rewriteRun(
          //language=java
          java(
            """
              import java.util.function.Function;
              class Test {
                  Function<Integer, Integer> f = n -> {
                      return n+1;
                  };
              }
              """,
            """
              import java.util.function.Function;
              class Test {
                  Function<Integer, Integer> f = n -> n+1;
              }
              """
          )
        );
    }

    @Test
    @Issue("https://github.com/openrewrite/rewrite-static-analysis/issues/1")
    void simplifyLambdaBlockToExpressionWithComments() {
        rewriteRun(
          //language=java
          java(
            """
              import java.util.function.Function;
              class Test {
                  Function<Integer, Integer> f = n -> {
                      // The buttonType will always be "cancel", even if we pressed one of the entry type buttons
                      return n + 1;
                  };
              }
              """,
            """
              import java.util.function.Function;
              class Test {
                  Function<Integer, Integer> f = n ->
                      // The buttonType will always be "cancel", even if we pressed one of the entry type buttons
                      n + 1;
              }
              """
          )
        );
    }

    @Issue("https://github.com/openrewrite/rewrite-static-analysis/issues/162")
    @Test
    void noChangeIfLambdaBlockWithAmbiguousMethod() {
        //language=java
        rewriteRun(
          java(
            """
              import java.util.function.Function;
              import java.util.function.Consumer;
              class A {
                  void aMethod(Consumer<Integer> consumer) {}
                  void aMethod(Function<Integer,String> function) {}
              }
              """
          ),
          java(
            """
              class Test {
                  void doTest() {
                      A a = new A();
                      a.aMethod(value -> {
                        return value.toString();
                      });
                  }
              }
              """
          )
        );
    }

    @Issue("https://github.com/openrewrite/rewrite-static-analysis/issues/236")
    @Test
    void simplifyLambdaBlockReturningVoidAsWellSimple() {
        //language=java
        rewriteRun(
          java(
            """
              public class Main {
              
                public void run() {
                  Runnable runHelloWorld = () -> {
                      System.out.println("Hello world!");
                  };
                  runHelloWorld.run();
                }
              }
              """,
            """
              public class Main {
              
                public void run() {
                  Runnable runHelloWorld = () ->
                      System.out.println("Hello world!");
                  runHelloWorld.run();
                }
              }
              """
          )
        );
    }

    @Issue("https://github.com/openrewrite/rewrite-static-analysis/issues/236")
    @Test
    void simplifyLambdaBlockReturningVoidAsWellAssertThrows() {
        //language=java
        rewriteRun(
          java(
            """
              import static org.junit.jupiter.api.Assertions.assertThrows;
              
              import java.math.BigDecimal;
              import org.junit.jupiter.api.Test;
              
              public class Main {
                @Test
                public void test() {
                  assertThrows(ArithmeticException.class, () -> {
                    BigDecimal.ONE.divide(BigDecimal.ZERO);
                  });
                }
              }
              """,
            """
              import static org.junit.jupiter.api.Assertions.assertThrows;
              
              import java.math.BigDecimal;
              import org.junit.jupiter.api.Test;
              
              public class Main {
                @Test
                public void test() {
                  assertThrows(ArithmeticException.class, () ->
                    BigDecimal.ONE.divide(BigDecimal.ZERO));
                }
              }
              """
          )
        );
    }
}
