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
import org.openrewrite.Issue;
import org.openrewrite.test.RecipeSpec;
import org.openrewrite.test.RewriteTest;

import static org.openrewrite.java.Assertions.java;

class SimplifyTernaryMethodInvocationTest implements RewriteTest {
    @Override
    public void defaults(RecipeSpec spec) {
        spec.recipe(new SimplifyTernaryMethodInvocation());
    }

    @Issue("https://github.com/openrewrite/rewrite-static-analysis/issues/695")
    @DocumentExample
    @Test
    void movesToStringOutOfTernary() {
        rewriteRun(
          //language=java
          java(
            """
              class Test {
                  String shorter(StringBuilder s1, StringBuilder s2) {
                      return s2.length() < s1.length() ? s2.toString() : s1.toString();
                  }
              }
              """,
            """
              class Test {
                  String shorter(StringBuilder s1, StringBuilder s2) {
                      return (s2.length() < s1.length() ? s2 : s1).toString();
                  }
              }
              """
          )
        );
    }

    @Test
    void worksForDifferentReceiversWithSharedInterfaceAndIdenticalArguments() {
        rewriteRun(
          //language=java
          java(
            """
              class Test {
                  interface Renderer { String render(int value); }
                  static class Left implements Renderer {
                      public String render(int value) { return "left" + value; }
                  }
                  static class Right implements Renderer {
                      public String render(int value) { return "right" + value; }
                  }

                  int next() { return 1; }

                  String render(boolean condition, Left left, Right right) {
                      return condition ? left.render(next()) : right.render(next());
                  }
              }
              """,
            """
              class Test {
                  interface Renderer { String render(int value); }
                  static class Left implements Renderer {
                      public String render(int value) { return "left" + value; }
                  }
                  static class Right implements Renderer {
                      public String render(int value) { return "right" + value; }
                  }

                  int next() { return 1; }

                  String render(boolean condition, Left left, Right right) {
                      return (condition ? left : right).render(next());
                  }
              }
              """
          )
        );
    }

    @Test
    void preservesMultipleArguments() {
        rewriteRun(
          //language=java
          java(
            """
              class Test {
                  String middle(boolean condition, String left, String right) {
                      return condition ? left.substring(1, 3) : right.substring(1, 3);
                  }
              }
              """,
            """
              class Test {
                  String middle(boolean condition, String left, String right) {
                      return (condition ? left : right).substring(1, 3);
                  }
              }
              """
          )
        );
    }

    @Test
    void doesNotUseMethodsFromUnrelatedInterfaces() {
        rewriteRun(
          //language=java
          java(
            """
              interface Hotel { int roomNumber(); }
              interface Apartment { int roomNumber(); }

              class Test {
                  int count(Object venue) {
                      return venue instanceof Hotel ? ((Hotel) venue).roomNumber() : ((Apartment) venue).roomNumber();
                  }
              }
              """
          )
        );
    }

    @Test
    void doesNotChangeOverloadOrReturnType() {
        rewriteRun(
          //language=java
          java(
            """
              class Test {
                  static class Base {
                      public String render(Object value) { return "base"; }
                      public Object result() { return "base"; }
                  }
                  static class Left extends Base {
                      public String render(String value) { return "left"; }
                      @Override public String result() { return "left"; }
                  }
                  static class Right extends Base {
                      public String render(String value) { return "right"; }
                      @Override public String result() { return "right"; }
                  }

                  String render(boolean condition, Left left, Right right) {
                      return condition ? left.render("x") : right.render("x");
                  }

                  String result(boolean condition, Left left, Right right) {
                      return condition ? left.result() : right.result();
                  }
              }
              """
          )
        );
    }

    @Test
    void doesNotWidenCheckedExceptions() {
        rewriteRun(
          //language=java
          java(
            """
              import java.io.IOException;

              class Test {
                  interface Base { String value() throws IOException; }
                  static class Left implements Base {
                      public String value() { return "left"; }
                  }
                  static class Right implements Base {
                      public String value() { return "right"; }
                  }

                  String value(boolean condition, Left left, Right right) {
                      return condition ? left.value() : right.value();
                  }
              }
              """
          )
        );
    }

    @Test
    void preservesDifferentArgumentsAndComments() {
        rewriteRun(
          //language=java
          java(
            """
              class Test {
                  String render(boolean condition, String left, String right) {
                      String a = condition ? left.substring(1) : right.substring(2);
                      String b = condition ? left.substring(/* keep */ 1) : right.substring(1);
                      return a + b;
                  }
              }
              """
          )
        );
    }

    @Test
    void leavesStaticGenericAndVarargsCallsAlone() {
        rewriteRun(
          //language=java
          java(
            """
              class Test {
                  static class Api {
                      public static String version() { return "v1"; }
                      public <T> T identity(T value) { return value; }
                      public String join(String... values) { return String.join(",", values); }
                  }

                  String value(boolean condition, Api left, Api right) {
                      String a = condition ? left.version() : right.version();
                      String b = condition ? left.identity("x") : right.identity("x");
                      String c = condition ? left.join("x") : right.join("x");
                      return a + b + c;
                  }
              }
              """
          )
        );
    }
}
