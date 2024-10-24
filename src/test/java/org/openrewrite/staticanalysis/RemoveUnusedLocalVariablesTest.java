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

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junitpioneer.jupiter.ExpectedToFail;
import org.openrewrite.DocumentExample;
import org.openrewrite.Issue;
import org.openrewrite.test.RecipeSpec;
import org.openrewrite.test.RewriteTest;

import static org.openrewrite.java.Assertions.java;
import static org.openrewrite.java.Assertions.version;
import static org.openrewrite.kotlin.Assertions.kotlin;

@SuppressWarnings({
  "ConstantConditions",
  "StatementWithEmptyBody",
  "EmptyTryBlock",
  "UnusedAssignment",
  "ResultOfMethodCallIgnored",
  "BooleanMethodNameMustStartWithQuestion",
  "PointlessBooleanExpression",
  "UseOfObsoleteCollectionType",
  "UnnecessaryLocalVariable",
  "EmptyFinallyBlock",
  "ClassInitializerMayBeStatic",
  "FunctionName",
  "ParameterCanBeLocal"
})
class RemoveUnusedLocalVariablesTest implements RewriteTest {

    @Override
    public void defaults(RecipeSpec spec) {
        spec.recipe(new RemoveUnusedLocalVariables(new String[0], null));
    }

    @Test
    @Issue("https://github.com/openrewrite/rewrite/issues/841")
    void ignoreSuppressWarnings() {
        rewriteRun(
          //language=java
          java(
            """
              class Test {
                  static int method(int x) {
                      int a = 0;
                      @SuppressWarnings("unused") int b = 0;
                      return a + 1;
                  }
              }
              """
          )
        );
    }

    @Test
    @Issue("https://github.com/openrewrite/rewrite/issues/1332")
    @SuppressWarnings("MethodMayBeStatic")
    void ignoreVariablesNamed() {
        rewriteRun(
          spec -> spec.recipe(new RemoveUnusedLocalVariables(new String[]{"unused"}, null)),
          //language=java
          java(
            """
              class Test {
                  void method(Object someData) {
                      int unused = 123;
                      int removed = 123;
                  }
              }
              """,
            """
              class Test {
                  void method(Object someData) {
                      int unused = 123;
                  }
              }
              """
          )
        );
    }

    @Test
    @Issue("https://github.com/openrewrite/rewrite/issues/1278")
    @SuppressWarnings("MethodMayBeStatic")
    void keepRightHandSideStatement() {
        rewriteRun(
          //language=java
          java(
            """
              class Test {
                  void method(Object someData) {
                      int doNotRemoveMe = writeDataToTheDB(someData);
                      int doNotRemoveMeEither = 1 + writeDataToTheDB(someData);
                  }

                  int writeDataToTheDB(Object save) {
                      return 1;
                  }
              }
              """
          )
        );
    }

    @Test
    void keepStatementWhenSideEffectInInitialization() {
        rewriteRun(
          //language=java
          java(
            """
              class Test {
                  void method(Object someData) {
                      // Don't write code like this.... Please
                      int a = null == (someData = null) ? 0 : 9;
                  }
              }
              """
          )
        );
    }

    @Test
    void keepStatementWhenSideEffectInAccess() {
        rewriteRun(
          //language=java
          java(
            """
              class Test {
                  void method(java.util.Scanner reader, Object someData) {
                      String a = "";
                      while((a = reader.nextLine()) != null) {
                          System.out.println(a);
                      }
                  }
              }
              """
          )
        );
    }

    @DocumentExample
    @Test
    void removeUnusedLocalVariables() {
        rewriteRun(
          //language=java
          java(
            """
              class Test {
                  static int method(int x) {
                      int a = 0;
                      int b = 0;
                      return a;
                  }
              }
              """,
            """
              class Test {
                  static int method(int x) {
                      int a = 0;
                      return a;
                  }
              }
              """
          )
        );
    }

    @Issue("https://github.com/openrewrite/rewrite/issues/1742")
    @Test
    void preserveComment() {
        rewriteRun(
          //language=java
          java(
            """
              class Test {
                  Long method() {
                      // Keep comment
                      String foo;
                      return Long.parseLong("123");
                  }
              }
              """,
            """
              class Test {
                  Long method() {
                      // Keep comment
                      return Long.parseLong("123");
                  }
              }
              """
          )
        );
    }

    @Test
    void removeUnusedLocalVariablesReassignedButNeverUsed() {
        rewriteRun(
          //language=java
          java(
            """
              class Test {
                  static int method() {
                      int isRead = -1;
                      int notRead = 0;
                      notRead = 1;
                      notRead += 1;
                      notRead = isRead + 1;
                      return isRead + 1;
                  }
              }
              """,
            """
              class Test {
                  static int method() {
                      int isRead = -1;
                      return isRead + 1;
                  }
              }
              """
          )
        );
    }

    @Test
    void ignoreClassFields() {
        rewriteRun(
          //language=java
          java(
            """
              class Test {
                  int a = 0;
                  int b = 1;

                  int method(int x) {
                      b = 2;
                      return x + 1;
                  }
              }
              """
          )
        );
    }

    @Test
    void removeUnusedLocalVariablesInClassInitializer() {
        rewriteRun(
          //language=java
          java(
            """
              class Test {
                  static {
                      int unused = 0;
                      int used = 1;
                      System.out.println(used);
                  }

                  {
                      int unused = 0;
                      int used = 1;
                      System.out.println(used);
                  }
              }
              """,
            """
              class Test {
                  static {
                      int used = 1;
                      System.out.println(used);
                  }

                  {
                      int used = 1;
                      System.out.println(used);
                  }
              }
              """
          )
        );
    }

    @Test
    void handleLocalVariablesShadowingClassFields() {
        rewriteRun(
          //language=java
          java(
            """
              class Test {
                  int a = 0;
                  int unused = 1;

                  static int method(int x) {
                      int unused = 2;
                      return x + 1;
                  }
              }
              """,
            """
              class Test {
                  int a = 0;
                  int unused = 1;

                  static int method(int x) {
                      return x + 1;
                  }
              }
              """
          )
        );
    }

    @Test
    void localVariableUnusedIncrementOperation() {
        rewriteRun(
          //language=java
          java(
            """
              class Test {
                  static boolean isTrue() {
                      return false;
                  }

                  static int method(int x) {
                      int a = 0;
                      int b = 99;
                      a++;
                      for (int i = 0; isTrue(); i++) {
                          a++;
                      }
                      return b++;
                  }
              }
              """,
            """
              class Test {
                  static boolean isTrue() {
                      return false;
                  }

                  static int method(int x) {
                      int b = 99;
                      for (int i = 0; isTrue(); i++) {
                      }
                      return b++;
                  }
              }
              """
          )
        );
    }

    @Test
    @Issue("https://github.com/apache/dubbo/blob/747282cdf851c144af562d3f92e10349cc315e36/dubbo-metadata/dubbo-metadata-definition-protobuf/src/test/java/org/apache/dubbo/metadata/definition/protobuf/model/GooglePB.java#L938-L944")
    void keepLocalVariablesAssignmentOperationToOtherVariables() {
        rewriteRun(
          //language=java
          java(
            """
              class Test {
                  static int method() {
                      int dataSize = 0;
                      int size = 0;
                      for (int j = 0; j < 10; j++) {
                          dataSize += 1;
                      }
                      size += dataSize;
                      return size;
                  }
              }
              """
          )
        );
    }

    @Test
    @Issue("https://github.com/openrewrite/rewrite/blob/706a172ed5449214a4a08637a27dbe768fb4eecd/rewrite-core/src/main/java/org/openrewrite/internal/StringUtils.java#L55-L65")
    void keepLocalVariableAssignmentOperation() {
        rewriteRun(
          //language=java
          java(
            """
              class Test {
                  static boolean method() {
                      boolean a = false;
                      return a |= false;
                  }
              }
              """
          )
        );
    }

    @Test
    @Issue("https://github.com/openrewrite/rewrite/blob/706a172ed5449214a4a08637a27dbe768fb4eecd/rewrite-core/src/main/java/org/openrewrite/internal/StringUtils.java#L55-L65")
    void removeUnusedLocalVariableBitwiseAssignmentOperation() {
        rewriteRun(
          //language=java
          java(
            """
              class Test {
                  static boolean method() {
                      boolean a = false;
                      boolean b = false;
                      b &= true;
                      return a |= false;
                  }
              }
              """,
            """
              class Test {
                  static boolean method() {
                      boolean a = false;
                      return a |= false;
                  }
              }
              """
          )
        );
    }

    @Test
    @Issue("https://github.com/openrewrite/rewrite/blob/706a172ed5449214a4a08637a27dbe768fb4eecd/rewrite-core/src/main/java/org/openrewrite/internal/StringUtils.java#L55-L65")
    void keepLocalVariableBitwiseAssignmentOperationWithinExpression() {
        rewriteRun(
          //language=java
          java(
            """
              class Test {
                  static boolean method(String string) {
                      boolean a = false;
                      for (char c : string.toCharArray()) {
                          if (false || (a |= !Character.isWhitespace(c))) {
                              break;
                          }
                      }
                      return a;
                  }
              }
              """
          )
        );
    }

    @Test
    @Issue("https://github.com/openrewrite/rewrite/blob/706a172ed5449214a4a08637a27dbe768fb4eecd/rewrite-core/src/main/java/org/openrewrite/internal/StringUtils.java#L55-L65")
    void handleInstanceOf() {
        rewriteRun(
          //language=java
          java(
            """
              import java.util.Stack;

              class Test {
                  static boolean method(Stack<Object> typeStack) {
                      for (Object e = typeStack.pop(); ; e = typeStack.pop()) {
                          if (e instanceof String) {
                              break;
                          }
                      }
                      return true;
                  }
              }
              """
          )
        );
    }

    @Test
    void removeUnusedLocalVariablesFromMultiVariablesDeclaration() {
        rewriteRun(
          //language=java
          java(
            """
              class Test {
                  static int method(int x) {
                      int a = 0, b = 0, c = 0, d = 0;
                      return b + c;
                  }
              }
              """,
            """
              class Test {
                  static int method(int x) {
                      int b = 0, c = 0;
                      return b + c;
                  }
              }
              """
          )
        );
    }

    @Test
    void keepLocalVariablesWhenUsedAsMethodInvocationArgument() {
        rewriteRun(
          //language=java
          java(
            """
              class Test {
                  static void method() {
                      int a = 0;
                      System.out.println(a);
                  }
              }
              """
          )
        );
    }

    @Test
    void keepLocalVariablesWhenMethodInvocationsCalledOnThem() {
        rewriteRun(
          //language=java
          java(
            """
              class Test {
                  void method() {
                      Worker worker = new Worker();
                      worker.doWork();
                  }

                  class Worker {
                      void doWork() {
                          //
                      }
                  }
              }
              """
          )
        );
    }

    @Test
    void ignoreClassVariables() {
        rewriteRun(
          //language=java
          java(
            """
              class Test {
                  static int someClassVariable = 0;
                  int someInstanceVariable = 0;

                  static void method() {
                      // do nothing
                  }
              }
              """
          )
        );
    }

    @SuppressWarnings("All")
    @Test
    void ignoreAnonymousClassVariables() {
        rewriteRun(
          //language=java
          java(
            """
              import java.io.File;

              class Test {
                  static File method(File dir) {
                      final File src = new File(dir, "child") {
                          private static final long serialVersionUID = 1L;
                      };
                      return src;
                  }
              }
              """
          )
        );
    }

    @Test
    @Issue("https://github.com/apache/dubbo/blob/747282cdf851c144af562d3f92e10349cc315e36/dubbo-rpc/dubbo-rpc-api/src/main/java/org/apache/dubbo/rpc/RpcStatus.java#L108-L118")
    void forLoopWithExternalIncrementLogic() {
        rewriteRun(
          //language=java
          java(
            """
              class Test {
                  static void method() {
                      for (int i; ; ) {
                          i = 41;
                          if (i == 42) {
                              break;
                          }
                      }
                  }
              }
              """
          )
        );
    }

    @Test
    void forLoopIncrementVariableReadInEvaluationCondition() {
        rewriteRun(
          //language=java
          java(
            """
              class Test {
                  static void method() {
                      for (int j = 0; j < 10; j++) {
                      }
                  }
              }
              """
          )
        );
    }

    @Test
    void removeUnusedLocalVariablesWithinTryCatch() {
        rewriteRun(
          //language=java
          java(
            """
              class Test {
                  static void method() {
                      try {
                          int a = 0;
                          int b = 1;
                          System.out.println(b);
                      } catch (Exception e) {
                          int a = 3;
                          int b = 4;
                          System.out.println(a);
                      } finally {
                          int a = 5;
                          int b = 6;
                      }
                  }
              }
              """,
            """
              class Test {
                  static void method() {
                      try {
                          int b = 1;
                          System.out.println(b);
                      } catch (Exception e) {
                          int a = 3;
                          System.out.println(a);
                      } finally {
                      }
                  }
              }
              """
          )
        );
    }

    @Test
    void ignoreTryWithResourceUnusedVariables() {
        rewriteRun(
          //language=java
          java(
            """
              import java.util.stream.Stream;

              class Test {
                  static void method() {
                      try (Stream<Object> unused = Stream.of()) {
                          // do nothing
                      } catch (Exception e) {
                          // do nothing
                      }
                  }
              }
              """
          )
        );
    }

    @Test
    void handleVariablesReadWithinTry() {
        rewriteRun(
          //language=java
          java(
            """
              class Test {
                  static void assertEquals(Object expected, Object actual) {
                      // do nothing
                  }

                  static void method() {
                      Object used, unused;
                      try {
                          used = new Object();
                          assertEquals(used, null);
                          unused = new Object();
                      } catch (Exception e) {
                          // do nothing
                      }
                  }
              }
              """,
            """
              class Test {
                  static void assertEquals(Object expected, Object actual) {
                      // do nothing
                  }

                  static void method() {
                      Object used;
                      try {
                          used = new Object();
                          assertEquals(used, null);
                      } catch (Exception e) {
                          // do nothing
                      }
                  }
              }
              """
          )
        );
    }

    @Test
    void ignoreUnusedTryCatchExceptionVariableDeclaration() {
        rewriteRun(
          //language=java
          java(
            """
              class Test {
                  static void method() {
                      try {
                          // do nothing
                      } catch (Exception e) {
                          // do nothing
                      }
                  }
              }
              """
          )
        );
    }

    @Test
    void ignoreUnusedLambdaExpressionParameters() {
        rewriteRun(
          //language=java
          java(
            """
              import java.util.function.BinaryOperator;
              import java.util.function.UnaryOperator;

              class Test {
                  static BinaryOperator<UnaryOperator<Object>> method() {
                      return (a, b) -> input -> {
                          Object o = a.apply(input);
                          o.toString();
                          return o;
                      };
                  }
              }
              """
          )
        );
    }

    @Test
    void ignoreUnusedLambdaExpressionParametersForEach() {
        rewriteRun(
          //language=java
          java(
            """
              import java.util.List;

              class Test {
                  static void method(List<Object> list) {
                      list.forEach(item -> {
                          // do nothing with "item"
                      });
                  }
              }
              """
          )
        );
    }

    @Test
    @Issue("https://github.com/txazo/spring-cloud-sourcecode/blob/5ffe615558e76f3bb37f19026ece5cbaff4d0404/eureka-client/src/main/java/com/netflix/discovery/converters/jackson/builder/StringInterningAmazonInfoBuilder.java#L114-L124")
    void recognizeUsedVariableWithinWhileLoop() {
        rewriteRun(
          //language=java
          java(
            """
              class Test {
                  TestToken testToken;

                  static void method(Test tp) {
                      int isUsed = 0;
                      TestToken token = tp.nextToken();
                      while ((token = tp.nextToken()) != TestToken.END_TOKEN) {
                          // do anything except read the value of "token"
                          tp.nextToken();
                          int unused = 11;
                          unused = isUsed;
                          System.out.println(isUsed);
                      }
                  }

                  TestToken nextToken() {
                      return this.testToken;
                  }

                  enum TestToken {
                      START_TOKEN,
                      END_TOKEN
                  }
              }
              """,
            """
              class Test {
                  TestToken testToken;

                  static void method(Test tp) {
                      int isUsed = 0;
                      TestToken token = tp.nextToken();
                      while ((token = tp.nextToken()) != TestToken.END_TOKEN) {
                          // do anything except read the value of "token"
                          tp.nextToken();
                          System.out.println(isUsed);
                      }
                  }

                  TestToken nextToken() {
                      return this.testToken;
                  }

                  enum TestToken {
                      START_TOKEN,
                      END_TOKEN
                  }
              }
              """
          )
        );
    }

    @Test
    @Issue("This still causes SonarQube to warn, but there isn't much that can be done in these cases. Maybe change to a while loop?")
    void ignoreForLoopIncrementVariableNeverRead() {
        rewriteRun(
          //language=java
          java(
            """
              class Test {
                  static boolean isTrue() {
                      return true;
                  }

                  static void method() {
                      for (int j = 0; isTrue(); j++) {
                      }
                  }
              }
              """
          )
        );
    }

    @Test
    @Issue("This still causes SonarQube to warn, but there isn't much that can be done in these cases. Maybe change to a forEach?")
    void ignoreEnhancedForLoops() {
        rewriteRun(
          //language=java
          java(
            """
              import java.util.List;

              class Test {
                  static void method(List<String> list) {
                      for (String s : list) {
                          // do nothing
                      }
                  }
              }
              """
          )
        );
    }

    @Test
    void removeFileGetterMethodsAsTheyDoNotHaveSideEffects() {
        rewriteRun(
          //language=java
          java(
            """
              import java.io.File;

              class Test {
                  static void method(File file) {
                      String canonicalPath = file.getCanonicalPath();
                  }
              }
              """,
            """
              import java.io.File;

              class Test {
                  static void method(File file) {
                  }
              }
              """
          )
        );
    }

    @Test
    void removeFileGetterMethodsWhenChainedAsTheyDoNotHaveSideEffects() {
        rewriteRun(
          //language=java
          java(
            """
              import java.io.File;

              class Test {
                  static void method(File file) {
                      String canonicalPath = file.getParentFile().getCanonicalPath();
                  }
              }
              """,
            """
              import java.io.File;

              class Test {
                  static void method(File file) {
                  }
              }
              """
          )
        );
    }

    @Issue("https://github.com/openrewrite/rewrite/issues/1743")
    @Test
    void assignmentWithinExpression() {
        rewriteRun(
          //language=java
          java(
            """
              class A {
                  void foo() {
                      String foo;
                      Long.parseLong(foo = "123");
                  }
              }
              """,
            """
              class A {
                  void foo() {
                      Long.parseLong("123");
                  }
              }
              """
          )
        );
    }

    @Issue("https://github.com/openrewrite/rewrite/issues/2509")
    @Test
    void recordCompactConstructor() {
        rewriteRun(
          version(java(
            """
              public record MyRecord(
                 boolean bar,
                 String foo
              ) {
                 public MyRecord {
                    if (foo == null) {
                        foo = "defaultValue";
                    }
                }
              }
              """
          ), 17)
        );
    }

    @Test
    void removeKotlinUnusedLocalVariable() {
        rewriteRun(
          kotlin(
            """
              class A (val b: String) {
                  fun foo() {
                      val bar = b;
                  }
              }
              """,
            """
              class A (val b: String) {
                  fun foo() {
                  }
              }
              """
          )
        );
    }

    @Test
    void retainJavaUnusedLocalVariableWithNewClass() {
        rewriteRun(
          //language=java
          java(
            """
              class A {}
              class B {
                void foo() {
                  A a = new A();
                }
              }
              """
          )
        );
    }

    @Test
    @Issue("https://github.com/openrewrite/rewrite-static-analysis/issues/152")
    void retainUnusedInsideCase() {
        rewriteRun(
          //language=java
          java(
            """
              class Test {
                  static void method() {
                      int x = 10;
                      char y = 20;
                      switch (x) {
                          case 10:
                              byte unused;
                              break;
                      }
                  }
              }
              """,
            """
              class Test {
                  static void method() {
                      int x = 10;
                      switch (x) {
                          case 10:
                              byte unused;
                              break;
                      }
                  }
              }
              """
          )
        );
    }

    @Test
    @Issue("https://github.com/openrewrite/rewrite-feature-flags/pull/35")
    void removeDespiteSideEffects() {
        rewriteRun(
          spec -> spec.recipe(new RemoveUnusedLocalVariables(null, true)),
          //language=java
          java(
            """
              class Test {
                  int sideEffect() { return 123; }
                  void method(Object someData) {
                      int unused = sideEffect();
                  }
              }
              """,
            """
              class Test {
                  int sideEffect() { return 123; }
                  void method(Object someData) {
                  }
              }
              """
          )
        );
    }

    @Nested
    class Kotlin {

        @Test
        void retainUnusedLocalVariableWithNewClass() {
            rewriteRun(
              kotlin(
                """
                  class A {}
                  class B {
                    fun foo() {
                      val a = A();
                    }
                  }
                  """
              )
            );
        }

        @Test
        @ExpectedToFail("Not yet implemented")
        void retainUnusedLocalVariableConst() {
            rewriteRun(
              //language=kotlin
              kotlin(
                """
                  package constants
                  const val FOO = "bar"
                  """
              ),
              //language=kotlin
              kotlin(
                """
                  package config
                  import constants.FOO
                  fun baz() {
                    val foo = FOO
                    println(foo)
                  }
                  """
              )
            );
        }

    }
}
