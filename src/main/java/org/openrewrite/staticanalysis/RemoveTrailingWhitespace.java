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

import lombok.EqualsAndHashCode;
import lombok.Value;
import org.openrewrite.ExecutionContext;
import org.openrewrite.Recipe;
import org.openrewrite.TreeVisitor;
import org.openrewrite.text.PlainText;
import org.openrewrite.text.PlainTextVisitor;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

@EqualsAndHashCode(callSuper = false)
@Value
public class RemoveTrailingWhitespace extends Recipe {

    private static final Pattern TRAILING_WHITESPACE = Pattern.compile("[ \\t]+(?=\\r?\\n|$)", Pattern.MULTILINE);

    @Override
    public String getDisplayName() {
        return "Remove trailing whitespace from text files";
    }

    @Override
    public String getDescription() {
        return "Removes trailing whitespace (spaces and tabs) from the end of lines in text files. " +
               "This helps maintain clean code formatting and prevents unnecessary whitespace in version control.";
    }

    @Override
    public TreeVisitor<?, ExecutionContext> getVisitor() {
        return new PlainTextVisitor<ExecutionContext>() {
            @Override
            public PlainText visitText(PlainText text, ExecutionContext ctx) {
                String content = text.getText();
                Matcher matcher = TRAILING_WHITESPACE.matcher(content);
                if (matcher.find()) {
                    String cleanedContent = matcher.replaceAll("");
                    return text.withText(cleanedContent);
                }
                return text;
            }
        };
    }
}
