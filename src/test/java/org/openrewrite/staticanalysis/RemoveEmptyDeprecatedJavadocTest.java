/*
 * Copyright 2025 the original author or authors.
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

class RemoveEmptyDeprecatedJavadocTest implements RewriteTest {
    @Override
    public void defaults(RecipeSpec spec) {
        spec.recipe(new RemoveEmptyDeprecatedJavadoc());
    }

    @DocumentExample
    @Issue("https://github.com/openrewrite/rewrite/issues/2801")
    @Test
    void removeEmptyDeprecatedFromClass() {
        rewriteRun(
          //language=java
          java(
            """
              /**
               * This is a deprecated class.
               * @deprecated
               */
              @Deprecated
              class Test {}
              """,
            """
              /**
               * This is a deprecated class.
               */
              @Deprecated
              class Test {}
              """
          )
        );
    }

    @Issue("https://github.com/openrewrite/rewrite/issues/2801")
    @Test
    void removeEmptyDeprecatedFromMethod() {
        rewriteRun(
          //language=java
          java(
            """
              class Test {
                  /**
                   * This method is deprecated.
                   * @deprecated
                   */
                  @Deprecated
                  void oldMethod() {
                  }
              }
              """,
            """
              class Test {
                  /**
                   * This method is deprecated.
                   */
                  @Deprecated
                  void oldMethod() {
                  }
              }
              """
          )
        );
    }

    @Issue("https://github.com/openrewrite/rewrite/issues/2801")
    @Test
    void removeDeprecatedWhenOnlyTag() {
        rewriteRun(
          //language=java
          java(
            """
              /**
               * @deprecated
               */
              @Deprecated
              class Test {}
              """,
            """
              @Deprecated
              class Test {}
              """
          )
        );
    }

    @Issue("https://github.com/openrewrite/rewrite/issues/2801")
    @Test
    void doNotRemoveDeprecatedWithDescription() {
        rewriteRun(
          //language=java
          java(
            """
              /**
               * This is a deprecated class.
               * @deprecated Use NewClass instead
               */
              @Deprecated
              class Test {}
              """
          )
        );
    }

    @Issue("https://github.com/openrewrite/rewrite/issues/2801")
    @Test
    void doNotRemoveWhenNoDeprecatedAnnotation() {
        rewriteRun(
          //language=java
          java(
            """
              /**
               * This is a class.
               * @deprecated
               */
              class Test {}
              """
          )
        );
    }

    @Issue("https://github.com/openrewrite/rewrite/issues/2801")
    @Test
    void removeEmptyDeprecatedWithWhitespace() {
        rewriteRun(
          //language=java
          java(
            """
              /**
               * This is a deprecated class.
               * @deprecated
               */
              @Deprecated
              class Test {}
              """,
            """
              /**
               * This is a deprecated class.
               */
              @Deprecated
              class Test {}
              """
          )
        );
    }

    @Issue("https://github.com/openrewrite/rewrite/issues/2801")
    @Test
    void removeDeprecatedFromField() {
        rewriteRun(
          //language=java
          java(
            """
              class Test {
                  /**
                   * Old field.
                   * @deprecated
                   */
                  @Deprecated
                  String oldField;
              }
              """,
            """
              class Test {
                  /**
                   * Old field.
                   */
                  @Deprecated
                  String oldField;
              }
              """
          )
        );
    }

    @Issue("https://github.com/openrewrite/rewrite/issues/2801")
    @Test
    void preserveOtherJavadocTags() {
        rewriteRun(
          //language=java
          java(
            """
              class Test {
                  /**
                   * This method is deprecated.
                   * @param value the input value
                   * @return the result
                   * @deprecated
                   */
                  @Deprecated
                  String oldMethod(String value) {
                      return value;
                  }
              }
              """,
            """
              class Test {
                  /**
                   * This method is deprecated.
                   * @param value the input value
                   * @return the result
                   */
                  @Deprecated
                  String oldMethod(String value) {
                      return value;
                  }
              }
              """
          )
        );
    }
}
