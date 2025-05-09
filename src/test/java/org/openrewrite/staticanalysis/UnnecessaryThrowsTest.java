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
import org.junitpioneer.jupiter.ExpectedToFail;
import org.openrewrite.DocumentExample;
import org.openrewrite.Issue;
import org.openrewrite.test.RecipeSpec;
import org.openrewrite.test.RewriteTest;
import org.openrewrite.test.TypeValidation;

import static org.openrewrite.java.Assertions.java;

@SuppressWarnings({"RedundantThrows", "resource"})
class UnnecessaryThrowsTest implements RewriteTest {
    @Override
    public void defaults(RecipeSpec spec) {
        spec.recipe(new UnnecessaryThrows());
    }

    @DocumentExample
    @Test
    void unnecessaryThrows() {
        rewriteRun(
          //language=java
          java(
            """
              import java.io.FileInputStream;
              import java.io.FileNotFoundException;
              import java.io.IOException;
              import java.io.UncheckedIOException;
              class Test {
                  private void changed() throws FileNotFoundException, UncheckedIOException {
                  }

                  void unchanged() throws IOException, UncheckedIOException {
                      new FileInputStream("test");
                  }
              }
              """,
            """
              import java.io.FileInputStream;
              import java.io.IOException;
              import java.io.UncheckedIOException;
              class Test {
                  private void changed() throws UncheckedIOException {
                  }

                  void unchanged() throws IOException, UncheckedIOException {
                      new FileInputStream("test");
                  }
              }
              """
          )
        );
    }

    @Issue("https://github.com/openrewrite/rewrite/issues/2144")
    @Test
    void genericException() {
        rewriteRun(
          //language=java
          java(
            """
              class Test {
                  public <E extends Exception> void accept(Class<E> e) throws E {
                  }
              }
              """
          )
        );
    }

    @SuppressWarnings("EmptyTryBlock")
    @Issue("https://github.com/openrewrite/rewrite/issues/631")
    @Test
    void necessaryThrowsFromCloseable() {
        rewriteRun(
          //language=java
          java(
            """
              import java.io.IOException;
              import java.net.URL;
              import java.net.URLClassLoader;

              class Test {
                  public void closeable() throws IOException {
                      // URLClassLoader implements Closeable and throws IOException from its close() method
                      try (URLClassLoader cl = new URLClassLoader(new URL[0])) {
                      }
                  }
              }
              """
          )
        );
    }

    @Test
    void necessaryThrows() {
        rewriteRun(
          //language=java
          java(
            """
              import java.io.IOException;

              class Test {

                  void test() throws IOException {
                      throw new IOException();
                  }
              }
              """
          )
        );
    }

    @Issue("https://github.com/openrewrite/rewrite/issues/519")
    @Test
    void interfaces() {
        rewriteRun(
          //language=java
          java(
            """
              import java.io.IOException;

              interface Test {
                  void test() throws IOException;
              }
              """
          )
        );
    }

    @Issue("https://github.com/openrewrite/rewrite/issues/519")
    @Test
    void abstractMethods() {
        rewriteRun(
          //language=java
          java(
            """
              import java.io.IOException;

              abstract class Test {
                  abstract void test() throws IOException;
              }
              """
          )
        );
    }

    @Issue("https://github.com/openrewrite/rewrite/issues/1059")
    @Test
    void necessaryThrowsFromStaticMethod() {
        rewriteRun(
          //language=java
          java(
            """
              import javax.xml.datatype.DatatypeFactory;

              class Test {
                  void test() throws Exception {
                      DatatypeFactory.newInstance();
                  }
              }
              """
          )
        );
    }

    @Issue("https://github.com/openrewrite/rewrite-static-analysis/issues/443")
    @Test
    void necessaryThrowsFromConstructor() {
        rewriteRun(
          //language=java
          java(
            """
              import java.io.IOException;

              class Test {
                  String str = test();
                  Test() throws IOException {}
                  String test() throws IOException {
                      throw new IOException();
                  }
              }
              """
          )
        );
    }

    @ExpectedToFail("Not yet implemented")
    @Issue("https://github.com/openrewrite/rewrite-static-analysis/issues/443")
    @Test
    void necessaryThrowsFromConstructorWithUnused() {
        rewriteRun(
          //language=java
          java(
            """
              import java.io.IOException;
              import java.util.concurrent.ExecutionException;

              class Test {
                  String str = test();
                  Test() throws IOException, ExecutionException {}
                  String test() throws IOException {
                      throw new IOException();
                  }
              }
              """,
            """
              import java.io.IOException;

              class Test {
                  String str = test();
                  Test() throws IOException {}
                  String test() throws IOException {
                      throw new IOException();
                  }
              }
              """
          )
        );
    }

    @Issue("https://github.com/openrewrite/rewrite/issues/897")
    @Test
    void necessaryThrowsOnInterfaceWithExplicitOverride() {
        rewriteRun(
          //language=java
          java(
            """
              public interface Foo {
                  void bar() throws Exception;
              }
              """
          ),
          //language=java
          java(
            """
              public class FooImpl implements Foo {
                  @Override
                  public void bar() throws Exception {
                      // no-op
                  }
              }
              """
          )
        );
    }

    @Test
    void necessaryThrowsOnInterfaceWithImplicitOverride() {
        rewriteRun(
          //language=java
          java(
            """
              public interface Foo {
                  void bar() throws Exception;
              }
              """
          ),
          //language=java
          java(
            """
              public class FooImpl implements Foo {
                  public void bar() throws Exception {
                      // no-op
                  }
              }
              """
          )
        );
    }

    @Test
    void doNotRemoveDocumentedExceptions() {
        rewriteRun(
          //language=java
          java(
            """
              public class ParentFoo {
                  /**
                   * @throws Exception Throws an exception
                   */
                  public void bar() throws Exception { // this throws should not be removed
                  }
              }

              class Foo extends ParentFoo {
                  @Override
                  public void bar() throws Exception {
                      // no-op
                  }
              }
              """
          )
        );
    }

    @Issue("https://github.com/openrewrite/rewrite/issues/1298")
    @Test
    void doNotRemoveExceptionCoveringOtherExceptions() {
        rewriteRun(
          //language=java
          java(
            """
              package com.yourorg;

              import java.io.IOException;

              class A {
                  void foo() throws Exception {
                      throw new IOException("");
                  }
              }
              """
          )
        );
    }

    @Issue("https://github.com/openrewrite/rewrite/issues/2105")
    @Test
    void preventTransformationIfAnyThrownExceptionHasNullOrUnknownType() {
        rewriteRun(
          spec -> spec.typeValidationOptions(TypeValidation.none()),
          //language=java
          java(
            """
              package com.yourorg;

              import java.io.IOException;

              class A {
                  void foo() throws ExceptionWithUnknownType {
                      someUnknownMethodInvocation();
                  }
              }
              """
          )
        );
    }

    @Issue("https://github.com/openrewrite/rewrite-static-analysis/issues/429")
    @Test
    void interfaceWithGenericTypeThrown() {
        rewriteRun(

          //language=java
          java(
            """
              public interface FooVisitor<T, E extends Exception> {
                  T visit(Foo.Event event) throws E;
              }
              """

          ),
          //language=java
          java(
            """
              public class Foo implements FooVisitor<Void, Foo.MyException> {
                  @Override
                  public Void visit(Event event) throws MyException {
                      return null;
                  }

                  public record Event() { }
                  public static class MyException extends Exception { }
              }
              """,
            """
              public class Foo implements FooVisitor<Void, Foo.MyException> {
                  @Override
                  public Void visit(Event event) {
                      return null;
                  }

                  public record Event() { }
                  public static class MyException extends Exception { }
              }
              """
          )
        );
    }

    @Issue("https://github.com/apache/maven/pull/2291")
    @Test
    void retainProtectedNonOverrides() {
        rewriteRun(
          //language=java
          java(
            """
              import java.io.FileNotFoundException;
              class A {
                  // Someone marked this protected, and added exceptions that implementers can optionally use
                  protected void method() throws FileNotFoundException {
                  }
              }
              """
          ),
          java(
            """
              import java.io.FileNotFoundException;
              class B extends A {
                  @Override
                  protected void method() throws FileNotFoundException {
                      throw new FileNotFoundException();
                  }
              }
              """
          ),
          java(
            """
              import java.io.FileNotFoundException;
              class C extends A {
                  @Override
                  protected void method() throws FileNotFoundException {
                  }
              }
              """,
            // Expected to remove here, as we override, but do not use the exceptions
            """
              class C extends A {
                  @Override
                  protected void method() {
                  }
              }
              """
          )
        );
    }
}
