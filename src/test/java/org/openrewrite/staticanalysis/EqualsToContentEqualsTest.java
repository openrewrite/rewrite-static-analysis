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
import org.openrewrite.java.JavaParser;
import org.openrewrite.test.RecipeSpec;
import org.openrewrite.test.RewriteTest;

import static org.openrewrite.java.Assertions.java;

class EqualsToContentEqualsTest implements RewriteTest {
    @Override
    public void defaults(RecipeSpec spec) {
        spec
          .parser(JavaParser.fromJavaVersion())
          .recipe(new EqualsToContentEquals());
    }

    @DocumentExample
    @Test
    void replaceStringBuilder() {
        //language=java
        rewriteRun(
          java(
            """
              class SomeClass {
                  boolean foo(StringBuilder sb) {
                      String str = "example string";
                      return str.equals(sb.toString());
                  }
              }
              """,
            """
              class SomeClass {
                  boolean foo(StringBuilder sb) {
                      String str = "example string";
                      return str.contentEquals(sb);
                  }
              }
              """
          )
        );
    }

    @Test
    void onlyRunsOnCorrectInvocations() {
        //language=java
        rewriteRun(
          java(
            """
              class SomeClass {
                  boolean foo(Integer number, String str) {
                      return str.equals(number.toString());
                  }
              }
              """
          )
        );
    }

    @Test
    void runsOnStringBuffer() {
        //language=java
        rewriteRun(
          java(
            """
              class SomeClass {
                  boolean foo(StringBuffer sb, String str) {
                      return str.equals(sb.toString());
                  }
              }
              """,
            """
              class SomeClass {
                  boolean foo(StringBuffer sb, String str) {
                      return str.contentEquals(sb);
                  }
              }
              """
          )
        );
    }

    @Test
    void runsOnCharSequence() {
        //language=java
        rewriteRun(
          java(
            """
              class SomeClass {
                  boolean foo(CharSequence cs, String str) {
                      return str.equals(cs.toString());
                  }
              }
              """,
            """
              class SomeClass {
                  boolean foo(CharSequence cs, String str) {
                      return str.contentEquals(cs);
                  }
              }
              """
          )
        );
    }

    @Test
    void runsOnNonStringVariablesAlso() {
        //language=java
        rewriteRun(
          java(
            """
              class SomeClass {
                  boolean foo(String str) {
                      return str.equals(getMessage().toString());
                  }
                  
                  StringBuilder getMessage() {
                      return new StringBuilder("message");
                  }
              }
              """,
            """
              class SomeClass {
                  boolean foo(String str) {
                      return str.contentEquals(getMessage());
                  }
                  
                  StringBuilder getMessage() {
                      return new StringBuilder("message");
                  }
              }
              """
          )
        );
    }
}
