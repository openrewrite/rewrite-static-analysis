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
import org.openrewrite.java.tree.TextComment;

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

                List<Comment> comments = s.getComments();
                if (comments.isEmpty()) {
                    return s;
                }

                boolean changed = false;
                List<Comment> updatedComments = new ArrayList<>(comments.size());

                for (Comment comment : comments) {
                    Comment updated = comment;

                    if (comment instanceof TextComment) {
                        TextComment tc = (TextComment) comment;
                        String text = tc.getText();

                        // Skip empty comments
                        if (text.isEmpty()) {
                            updatedComments.add(comment);
                            continue;
                        }

                        // Skip special comments like //language=java
                        if (text.startsWith("language=")) {
                            updatedComments.add(comment);
                            continue;
                        }

                        // If already has space after //
                        if (text.startsWith(" ")) {
                            updatedComments.add(comment);
                            continue;
                        }

                        // Add space after //
                        String newText = " " + text;

                        if (!newText.equals(text)) {
                            updated = tc.withText(newText);
                            changed = true;
                        }
                    }

                    updatedComments.add(updated);
                }

                // Only return new object if something changed (important for single-cycle rule)
                return changed ? s.withComments(updatedComments) : s;
            }
        };
    }
}