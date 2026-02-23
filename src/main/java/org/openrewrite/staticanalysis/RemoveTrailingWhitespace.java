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
import org.jspecify.annotations.Nullable;
import org.openrewrite.ExecutionContext;
import org.openrewrite.Recipe;
import org.openrewrite.TreeVisitor;
import org.openrewrite.internal.ListUtils;
import org.openrewrite.java.JavaIsoVisitor;
import org.openrewrite.java.tree.Comment;
import org.openrewrite.java.tree.Space;
import org.openrewrite.java.tree.TextComment;

import java.time.Duration;
import java.util.Collections;
import java.util.Set;
import java.util.regex.Pattern;

@Value
@EqualsAndHashCode(callSuper = false)
public class RemoveTrailingWhitespace extends Recipe {

    private static final Pattern TRAILING_WHITESPACE = Pattern.compile("[ \\t]+(?=\\r?\\n)");

    @Override
    public String getDisplayName() {
        return "Remove trailing whitespace";
    }

    @Override
    public String getDescription() {
        return "Remove trailing whitespace from the end of each line. " +
                "Trailing whitespace is simply useless and should not stay in code. " +
                "It may generate noise when comparing different versions of the same file.";
    }

    @Override
    public Set<String> getTags() {
        return Collections.singleton("RSPEC-S1131");
    }

    @Override
    public Duration getEstimatedEffortPerOccurrence() {
        return Duration.ofMinutes(1);
    }

    @Override
    public TreeVisitor<?, ExecutionContext> getVisitor() {
        return new JavaIsoVisitor<ExecutionContext>() {
            @Override
            @SuppressWarnings("ConstantConditions")
            public Space visitSpace(@Nullable Space space, Space.Location loc, ExecutionContext ctx) {
                Space s = super.visitSpace(space, loc, ctx);
                if (s == null || s == Space.EMPTY) {
                    return s;
                }

                String ws = s.getWhitespace();
                String stripped = stripTrailing(ws);
                if (!ws.equals(stripped)) {
                    s = s.withWhitespace(stripped);
                }

                if (!s.getComments().isEmpty()) {
                    s = s.withComments(ListUtils.map(s.getComments(), RemoveTrailingWhitespace::stripComment));
                }
                return s;
            }
        };
    }

    private static Comment stripComment(Comment c) {
        String suffix = c.getSuffix();
        String strippedSuffix = stripTrailing(suffix);
        if (!suffix.equals(strippedSuffix)) {
            c = c.withSuffix(strippedSuffix);
        }
        if (c instanceof TextComment) {
            TextComment tc = (TextComment) c;
            String text = tc.getText();
            if (tc.isMultiline()) {
                String strippedText = stripTrailing(text);
                if (!text.equals(strippedText)) {
                    c = tc.withText(strippedText);
                }
            } else {
                int end = text.length();
                while (end > 0 && (text.charAt(end - 1) == ' ' || text.charAt(end - 1) == '\t')) {
                    end--;
                }
                if (end != text.length()) {
                    c = tc.withText(text.substring(0, end));
                }
            }
        }
        return c;
    }

    private static String stripTrailing(String s) {
        return TRAILING_WHITESPACE.matcher(s).replaceAll("");
    }
}
