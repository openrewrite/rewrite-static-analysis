/*
 * Copyright 2021 the original author or authors.
 * <p>
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * <p>
 * https://www.apache.org/licenses/LICENSE-2.0
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
        return new LinkedHashSet<>(Arrays.asList("RSPEC-1116", "RSPEC-2959"));
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
            public J.Block visitBlock(final J.Block block, final ExecutionContext executionContext) {
                final Iterator<Statement> iterator = block.getStatements().iterator();
                final List<Statement> result = new ArrayList<>();
                while (iterator.hasNext()) {
                    Statement statement = iterator.next();
                    if (statement instanceof J.Empty) {
                        nextNonEmptyAggregatedWithComments(statement, iterator)
                                .ifPresent(nextLine -> {
                                    String semicolonPrefixWhitespace = statement.getPrefix().getWhitespace();
                                    if (semicolonPrefixWhitespace.isEmpty()) {
                                        result.add(nextLine);
                                    } else {
                                        result.add(nextLine.withPrefix(nextLine.getPrefix()
                                                .withWhitespace(semicolonPrefixWhitespace)));
                                    }
                                });
                    } else {
                        result.add(statement);
                    }
                }
                return super.visitBlock(block.withStatements(result), executionContext);
            }

            @Override
            public J.Try.Resource visitTryResource(J.Try.Resource tr, ExecutionContext executionContext) {
                J.Try _try = getCursor().dropParentUntil(J.Try.class::isInstance).getValue();
                if (_try.getResources().isEmpty() ||
                        _try.getResources().get(_try.getResources().size() - 1) != tr ||
                        !_try.getResources().get(_try.getResources().size() - 1).isTerminatedWithSemicolon()) {
                    return tr;
                }
                return tr.withTerminatedWithSemicolon(false);
            }

            @Override
            public J.EnumValueSet visitEnumValueSet(J.EnumValueSet enums, ExecutionContext executionContext) {
                J.EnumValueSet e = super.visitEnumValueSet(enums, executionContext);
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
