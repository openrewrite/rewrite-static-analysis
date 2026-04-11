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

import lombok.Getter;
import org.openrewrite.ExecutionContext;
import org.openrewrite.Recipe;
import org.openrewrite.TreeVisitor;
import org.openrewrite.internal.ListUtils;
import org.openrewrite.java.JavaIsoVisitor;
import org.openrewrite.java.tree.Space;
import org.openrewrite.java.tree.TextComment;

import java.time.Duration;
import java.util.Set;

import static java.util.Collections.singleton;

@Getter
public class SingleLineCommentSpacing extends Recipe {

    final String displayName = "Add space after // in single-line comments";

    final String description = "Ensures there is exactly one space after // in single-line comments when missing.";

    final Set<String> tags = singleton("formatting");

    final Duration estimatedEffortPerOccurrence = Duration.ofMinutes(1);

    @Override
    public TreeVisitor<?, ExecutionContext> getVisitor() {
        return new JavaIsoVisitor<ExecutionContext>() {

            @Override
            public Space visitSpace(Space space, Space.Location loc, ExecutionContext ctx) {
                Space s = super.visitSpace(space, loc, ctx);

                return s.withComments(ListUtils.map(s.getComments(), comment -> {
                    if (!(comment instanceof TextComment) || ((TextComment) comment).isMultiline()) {
                        return comment;
                    }
                    TextComment tc = (TextComment) comment;
                    String text = tc.getText();
                    if (text.isEmpty() || text.startsWith(" ") ||
                            text.startsWith("language=") ||
                            text.startsWith("noinspection") ||
                            text.startsWith("region") ||
                            text.startsWith("endregion")) {
                        return comment;
                    }
                    return tc.withText(" " + text);
                }));
            }
        };
    }
}