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

class JavadocParagraphTest implements RewriteTest {

    @Override
    public void defaults(RecipeSpec spec) {
        spec.recipe(new JavadocParagraph());
    }

    @DocumentExample
    @Issue("https://github.com/openrewrite/rewrite-static-analysis/issues/545")
    @Test
    void addMissingParagraphTag() {
        rewriteRun(
          //language=java
          java(
            """
              /**
               * The first paragraph.
               *
               * The second paragraph.
               */
              class Test {}
              """,
            """
              /**
               * The first paragraph.
               *
               * <p>The second paragraph.
               */
              class Test {}
              """
          )
        );
    }

    @Test
    void addTagsToMultipleLaterParagraphs() {
        rewriteRun(
          //language=java
          java(
            """
              /**
               * First.
               *
               * Second.
               *
               * Third.
               */
              class Test {}
              """,
            """
              /**
               * First.
               *
               * <p>Second.
               *
               * <p>Third.
               */
              class Test {}
              """
          )
        );
    }

    @Test
    void doNotChangeSingleFirstParagraph() {
        rewriteRun(
          //language=java
          java(
            """
              /**
               * First.
               */
              class Test {}
              """
          )
        );
    }

    @Test
    void doNotChangeExistingParagraphTag() {
        rewriteRun(
          //language=java
          java(
            """
              /**
               * First.
               *
               * <p>Second.
               */
              class Test {}
              """
          )
        );
    }

    @Test
    void doNotAddTagToBlockTagSection() {
        rewriteRun(
          //language=java
          java(
            """
              class Test {
                  /**
                   * Description.
                   *
                   * @param value first line
                   *
                   *              second line
                   */
                  void method(String value) {}
              }
              """
          )
        );
    }

    @Test
    void paragraphMayBeginWithInlineMarkup() {
        rewriteRun(
          //language=java
          java(
            """
              /**
               * First.
               *
               * {@link String} begins the second paragraph.
               */
              class Test {}
              """,
            """
              /**
               * First.
               *
               * <p>{@link String} begins the second paragraph.
               */
              class Test {}
              """
          )
        );
    }

    @Test
    void preserveMultipleBlankLines() {
        rewriteRun(
          //language=java
          java(
            """
              /**
               * First.
               *
               *
               * Second.
               */
              class Test {}
              """,
            """
              /**
               * First.
               *
               *
               * <p>Second.
               */
              class Test {}
              """
          )
        );
    }

    @Test
    void preserveWindowsLineEndings() {
        rewriteRun(
          //language=java
          java(
            """
              /**
               * First.
               *
               * Second.
               */
              class Test {}
              """.replace("\n", "\r\n"),
            """
              /**
               * First.
               *
               * <p>Second.
               */
              class Test {}
              """.replace("\n", "\r\n")
          )
        );
    }

    @Test
    void doNotInsertParagraphInsidePreBlock() {
        rewriteRun(
          //language=java
          java(
            """
              /**
               * First.
               *
               * <pre>
               * line one
               *
               * line two
               * </pre>
               */
              class Test {}
              """
          )
        );
    }

    @Test
    void doNotInsertParagraphBeforeBlockLevelHtml() {
        rewriteRun(
          //language=java
          java(
            """
              /**
               * First.
               *
               * <ul>
               *   <li>one</li>
               *   <li>two</li>
               * </ul>
               */
              class Test {}
              """
          )
        );
    }

    @Test
    void addParagraphAfterPreBlock() {
        rewriteRun(
          //language=java
          java(
            """
              /**
               * First.
               *
               * <pre>
               * code
               * </pre>
               *
               * Trailing prose.
               */
              class Test {}
              """,
            """
              /**
               * First.
               *
               * <pre>
               * code
               * </pre>
               *
               * <p>Trailing prose.
               */
              class Test {}
              """
          )
        );
    }
}
