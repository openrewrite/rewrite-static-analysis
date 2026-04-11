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

import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;

import org.openrewrite.ExecutionContext;
import org.openrewrite.Recipe;
import org.openrewrite.TreeVisitor;
import org.openrewrite.java.JavaIsoVisitor;
import org.openrewrite.java.tree.Comment;
import org.openrewrite.java.tree.Space;

public class SingleLineCommentSpacing extends Recipe {

    @Override
    public String getDisplayName() {
        return "Add space after // in single-line comments";
    }

    @Override
    public String getDescription() {
        return "Ensures there is exactly one space after // in single-line comments when missing.";
    }

    @Override
    public Set<String> getTags() {
        return Collections.singleton("formatting");
    }

    @Override
    public Duration getEstimatedEffortPerOccurrence() {
        return Duration.ofMinutes(1);
    }

    @Override
    public TreeVisitor<?, ExecutionContext> getVisitor() {
        return new JavaIsoVisitor<ExecutionContext>() {

            @Override
            public Space visitSpace(Space space, Space.Location loc, ExecutionContext ctx) {
                Space s = super.visitSpace(space, loc, ctx);

                if (s.getComments() == null || s.getComments().isEmpty()) {
                    return s;
                }

                List<Comment> updatedComments = new ArrayList<>();

                for (Comment comment : s.getComments()) {

                    String text = comment.toString();

                    // Check if it's a single-line comment
                    if (text.startsWith("//") && text.length() > 2) {

                        char nextChar = text.charAt(2);

                        // Fix ONLY if there is no space after //
                        if (nextChar != ' ' && !text.startsWith("//language=")) {

                            // Add a space at beginning of suffix
                            String newSuffix = " " + comment.getSuffix();

                            comment = comment.withSuffix(newSuffix);
                        }
                    }

                    updatedComments.add(comment);
                }

                return s.withComments(updatedComments);
            }
        };
    }
}