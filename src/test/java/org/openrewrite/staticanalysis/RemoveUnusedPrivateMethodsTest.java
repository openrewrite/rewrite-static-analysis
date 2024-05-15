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
import org.openrewrite.Issue;
import org.openrewrite.java.JavaParser;
import org.openrewrite.test.RecipeSpec;
import org.openrewrite.test.RewriteTest;

import static org.openrewrite.java.Assertions.java;

class RemoveUnusedPrivateMethodsTest implements RewriteTest {

    @Override
    public void defaults(RecipeSpec spec) {
        spec.recipe(new RemoveUnusedPrivateMethods())
          .parser(JavaParser.fromJavaVersion().classpath("junit-jupiter-params"));
    }

    @DocumentExample
    @Test
    void removeUnusedPrivateMethods() {
        rewriteRun(
          //language=java
          java(
            """
              class Test {
                  private void unused() {
                  }
                            
                  public void dontRemove() {
                      dontRemove2();
                  }
                  
                  private void dontRemove2() {
                  }
              }
              """,
            """
              class Test {
                            
                  public void dontRemove() {
                      dontRemove2();
                  }
                  
                  private void dontRemove2() {
                  }
              }
              """
          )
        );
    }

    @SuppressWarnings("MissingSerialAnnotation")
    @Test
    void doNotRemoveCustomizedSerialization() {
        rewriteRun(
          //language=java
          java(
            """
              class Test implements java.io.Serializable {
                  private void writeObject(java.io.ObjectOutputStream out) {}
                  private void readObject(java.io.ObjectInputStream in) {}
                  private void readObjectNoData() {}
              }
              """
          )
        );
    }

    @Test
    void doNotRemoveMethodsWithAnnotations() {
        rewriteRun(
          //language=java
          java(
            """
              import org.junit.jupiter.params.provider.MethodSource;
              import java.util.stream.Stream;

              class Test {
                  @MethodSource("sourceExample")
                  void test(String input) {
                  }
                  private Stream<Object> sourceExample() {
                      return null;
                  }
              }
              """
          )
        );
    }

    @Issue("https://github.com/openrewrite/rewrite/issues/1536")
    @Test
    void privateMethodWithBoundedGenericTypes() {
        rewriteRun(
          //language=java
          java(
            """
              public class TestClass {
                  void method() {
                      checkMethodInUse("String", "String");
                  }

                  private static void checkMethodInUse(String arg0, String arg1) {
                  }

                  private static <T> void checkMethodInUse(String arg0, T arg1) {
                  }
              }
              """
          )
        );
    }

    @Test
    @Issue("https://github.com/openrewrite/rewrite/issues/4076")
    void doNotRemoveMethodsOnClassNestedClass() {
        rewriteRun(
          //language=java
          java(
            """
                            import java.util.stream.Stream;
              
              class Test {
                  void test(String input) {
                  }
                  private Stream<Object> unused() {
                      return null;
                  }
              
                  class InnerTest {
                      void test(String input) {
                      }
                      private Stream<Object> unused() {
                          return null;
                      }
                  }
              }
              """,
            """
              import org.junit.jupiter.params.provider.MethodSource;
              import java.util.stream.Stream;
              
              class Test {
                  void test(String input) {
                  }
              
                  class InnerTest {
                      void test(String input) {
                      }
                  }
              }
              """
          )
        );
    }

    @Test
    @Issue("https://github.com/openrewrite/rewrite/issues/4076")
    void doNotRemoveMethodsWithUnusedSuppressWarningsOnClass() {
        rewriteRun(
          //language=java
          java(
            """
              import org.junit.jupiter.params.provider.MethodSource;
              import java.util.stream.Stream;
              
              @SuppressWarnings("unused")
              class Test {
                  void test(String input) {
                  }
                  private Stream<Object> unused() {
                      return null;
                  }
                  private Stream<Object> anotherUnused() {
                      return null;
                  }
              }
              """
          )
        );
    }

    @Test
    @Issue("https://github.com/openrewrite/rewrite/issues/4076")
    void doNotRemoveMethodsWithUnusedSuppressWarningsOnClassNestedClass() {
        rewriteRun(
          //language=java
          java(
            """
              import org.junit.jupiter.params.provider.MethodSource;
              import java.util.stream.Stream;
              
              @SuppressWarnings("unused")
              class Test {
                  void test(String input) {
                  }
                  private Stream<Object> unused() {
                      return null;
                  }
              
                  class InnerTest {
                      void test(String input) {
                      }
                      private Stream<Object> unused() {
                          return null;
                      }
                  }
              }
              """,
            """
              import org.junit.jupiter.params.provider.MethodSource;
              import java.util.stream.Stream;
              
              @SuppressWarnings("unused")
              class Test {
                  void test(String input) {
                  }
                  private Stream<Object> unused() {
                      return null;
                  }
              
                  class InnerTest {
                      void test(String input) {
                      }
                  }
              }
              """
          )
        );
    }

}
