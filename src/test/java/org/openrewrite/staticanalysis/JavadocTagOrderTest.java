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

@SuppressWarnings("JavadocDeclaration")
class JavadocTagOrderTest implements RewriteTest {

    @Override
    public void defaults(RecipeSpec spec) {
        spec.recipe(new JavadocTagOrder());
    }

    @DocumentExample
    @Issue("https://github.com/openrewrite/rewrite-static-analysis/issues/283")
    @Test
    void reorderAuthorParamSince() {
        rewriteRun(
          //language=java
          java(
            """
              /**
               * @param <T>
               *          type of product
               * @author John
               * @since 2020.1.0
               */
              class Test<T> {
              }
              """,
            """
              /**
               * @author John
               * @param <T>
               *          type of product
               * @since 2020.1.0
               */
              class Test<T> {
              }
              """
          )
        );
    }

    @Test
    void alreadyOrderedUnchanged() {
        rewriteRun(
          //language=java
          java(
            """
              /**
               * @author John
               * @param <T> type of product
               * @since 2020.1.0
               */
              class Test<T> {
              }
              """
          )
        );
    }

    @Test
    void reorderMethodTags() {
        rewriteRun(
          //language=java
          java(
            """
              class Test {
                  /**
                   * Does something.
                   * @return the value
                   * @param x input
                   * @throws IllegalArgumentException when invalid
                   */
                  int method(int x) {
                      return x;
                  }
              }
              """,
            """
              class Test {
                  /**
                   * Does something.
                   * @param x input
                   * @return the value
                   * @throws IllegalArgumentException when invalid
                   */
                  int method(int x) {
                      return x;
                  }
              }
              """
          )
        );
    }

    @Test
    void preserveRelativeOrderOfSameTagType() {
        rewriteRun(
          //language=java
          java(
            """
              class Test {
                  /**
                   * @return result
                   * @param b second
                   * @param a first
                   */
                  int method(int a, int b) {
                      return a + b;
                  }
              }
              """,
            """
              class Test {
                  /**
                   * @param b second
                   * @param a first
                   * @return result
                   */
                  int method(int a, int b) {
                      return a + b;
                  }
              }
              """
          )
        );
    }

    @Test
    void deprecatedAfterSince() {
        rewriteRun(
          //language=java
          java(
            """
              /**
               * @deprecated use other
               * @author Jane
               * @since 1.0
               */
              class Test {
              }
              """,
            """
              /**
               * @author Jane
               * @since 1.0
               * @deprecated use other
               */
              class Test {
              }
              """
          )
        );
    }
}
