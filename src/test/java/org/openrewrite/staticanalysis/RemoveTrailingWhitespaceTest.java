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
import org.openrewrite.test.RecipeSpec;
import org.openrewrite.test.RewriteTest;

import static org.openrewrite.test.SourceSpecs.text;

class RemoveTrailingWhitespaceTest implements RewriteTest {

    @Override
    public void defaults(RecipeSpec spec) {
        spec.recipe(new RemoveTrailingWhitespace());
    }

    @DocumentExample
    @Test
    void removeTrailingSpaces() {
        rewriteRun(
          text(
            "Line with trailing spaces   \n" +
            "Line without trailing spaces\n" +
            "Another line with spaces  \n",
            "Line with trailing spaces\n" +
            "Line without trailing spaces\n" +
            "Another line with spaces\n"
          )
        );
    }

    @Test
    void removeTrailingTabs() {
        rewriteRun(
          text(
            "Line with trailing tabs\t\t\n" +
            "Line without trailing tabs\n" +
            "Mixed tabs and spaces\t  \n",
            "Line with trailing tabs\n" +
            "Line without trailing tabs\n" +
            "Mixed tabs and spaces\n"
          )
        );
    }

    @Test
    void removeTrailingWhitespaceFromLastLine() {
        rewriteRun(
          text(
            "First line\n" +
            "Second line  ",
            "First line\n" +
            "Second line"
          )
        );
    }

    @Test
    void preserveEmptyLines() {
        rewriteRun(
          text(
            "Line 1  \n" +
            "\n" +
            "Line 3\t\n" +
            "\n" +
            "Line 5",
            "Line 1\n" +
            "\n" +
            "Line 3\n" +
            "\n" +
            "Line 5"
          )
        );
    }

    @Test
    void handleMultipleConsecutiveWhitespaceTypes() {
        rewriteRun(
          text(
            "Spaces then tabs  \t\t\n" +
            "Tabs then spaces\t\t  \n" +
            "Mixed whitespace \t \t \n",
            "Spaces then tabs\n" +
            "Tabs then spaces\n" +
            "Mixed whitespace\n"
          )
        );
    }

    @Test
    void noChangesWhenNoTrailingWhitespace() {
        rewriteRun(
          text(
            "Clean line 1\n" +
            "Clean line 2\n" +
            "Clean line 3"
          )
        );
    }

    @Test
    void handleEmptyFile() {
        rewriteRun(
          text("")
        );
    }

    @Test
    void handleLinesWithOnlyWhitespace() {
        rewriteRun(
          text(
            "line with content\n" +
            "   \n" +
            "another line",
            "line with content\n" +
            "\n" +
            "another line"
          )
        );
    }

    @Test
    void preserveIndentationWhitespace() {
        rewriteRun(
          text(
            "    Indented line  \n" +
            "\tTab indented line\t\n" +
            "  Mixed indentation\t  ",
            "    Indented line\n" +
            "\tTab indented line\n" +
            "  Mixed indentation"
          )
        );
    }

    @Test
    void handleWindowsLineEndings() {
        rewriteRun(
          text(
            "Windows line 1  \r\n" +
            "Windows line 2\t\r\n" +
            "Windows line 3",
            "Windows line 1\r\n" +
            "Windows line 2\r\n" +
            "Windows line 3"
          )
        );
    }

    @Test
    void handleMacLineEndings() {
        rewriteRun(
          text(
            "Mac line 1  \r" +
            "Mac line 2\t\r" +
            "Mac line 3",
            "Mac line 1\r" +
            "Mac line 2\r" +
            "Mac line 3"
          )
        );
    }

    @Test
    void handleLargeAmountOfTrailingWhitespace() {
        StringBuilder input = new StringBuilder();
        StringBuilder expected = new StringBuilder();

        // Create lines with varying amounts of trailing whitespace
        for (int i = 1; i <= 10; i++) {
            input.append("Line ").append(i);
            expected.append("Line ").append(i);

            // Add i spaces as trailing whitespace
            for (int j = 0; j < i; j++) {
                input.append(" ");
            }

            if (i < 10) {
                input.append("\n");
                expected.append("\n");
            }
        }

        rewriteRun(
          text(
            input.toString(),
            expected.toString()
          )
        );
    }
}
