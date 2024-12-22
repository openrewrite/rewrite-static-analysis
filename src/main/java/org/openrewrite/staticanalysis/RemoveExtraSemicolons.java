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

import org.openrewrite.ExecutionContext;
import org.openrewrite.Recipe;
import org.openrewrite.TreeVisitor;
import org.openrewrite.java.JavaIsoVisitor;
import org.openrewrite.java.tree.Comment;
import org.openrewrite.java.tree.J;
import org.openrewrite.java.tree.Space;
import org.openrewrite.java.tree.Statement;

import java.time.Duration;
import java.util.*;

public class RemoveExtraSemicolons extends Recipe {

    @Override
    public String getDisplayName() {
        return "Remove extra semicolons";
    }

    @Override
    public String getDescription() {
        return "Optional semicolons at the end of try-with-resources are also removed.";
    }

    @Override
    public Set<String> getTags() {
        return new LinkedHashSet<>(Arrays.asList("RSPEC-S1116", "RSPEC-S2959"));
    }

    @Override
    public Duration getEstimatedEffortPerOccurrence() {
        return Duration.ofMinutes(1);
    }

    @SuppressWarnings("ConstantConditions")
    @Override
    public TreeVisitor<?, ExecutionContext> getVisitor() {
        return new JavaIsoVisitor<ExecutionContext>() {

            @Override
            public J.Block visitBlock(final J.Block block, final ExecutionContext ctx) {
                final Iterator<Statement> iterator = block.getStatements().iterator();
                final List<Statement> result = new ArrayList<>();
                while (iterator.hasNext()) {
                    Statement statement = iterator.next();
                    if (statement instanceof J.Empty) {
                        nextNonEmptyAggregatedWithComments(statement, iterator)
                                .ifPresent(nextLine -> {
                                    String whitespace = statement.getPrefix().getWhitespace();
                                    if (!whitespace.contains("\n") && nextLine.getComments().isEmpty())  {
                                        result.add(nextLine);
                                    } else {
                                        Space updatedPrefix = nextLine.getPrefix().withWhitespace(whitespace);
                                        result.add(nextLine.withPrefix(updatedPrefix));
                                    }
                                });
                    } else {
                        result.add(statement);
                    }
                }
                return super.visitBlock(block.withStatements(result), ctx);
            }

            @Override
            public J.Try.Resource visitTryResource(J.Try.Resource tr, ExecutionContext ctx) {
                J.Try _try = getCursor().dropParentUntil(J.Try.class::isInstance).getValue();
                if (_try.getResources().isEmpty() ||
                        _try.getResources().get(_try.getResources().size() - 1) != tr ||
                        !_try.getResources().get(_try.getResources().size() - 1).isTerminatedWithSemicolon()) {
                    return tr;
                }
                return tr.withTerminatedWithSemicolon(false);
            }

            @Override
            public J.EnumValueSet visitEnumValueSet(J.EnumValueSet enums, ExecutionContext ctx) {
                J.EnumValueSet e = super.visitEnumValueSet(enums, ctx);
                if (getCursor().firstEnclosing(J.Block.class).getStatements().size() == 1) {
                    e = e.withTerminatedWithSemicolon(false);
                }
                return e;
            }
        };
    }

    private Optional<Statement> nextNonEmptyAggregatedWithComments(Statement current, Iterator<Statement> iterator) {
        List<Comment> comments = new ArrayList<>(current.getComments());
        while (iterator.hasNext()) {
            Statement statement = iterator.next();
            comments.addAll(statement.getComments());
            if (!(statement instanceof J.Empty)) {
                return Optional.of(statement.withComments(comments));
            }
        }
        return Optional.empty();
    }
}
