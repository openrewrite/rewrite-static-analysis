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
import org.openrewrite.Preconditions;
import org.openrewrite.Recipe;
import org.openrewrite.TreeVisitor;
import org.openrewrite.internal.ListUtils;
import org.openrewrite.java.JavaIsoVisitor;
import org.openrewrite.java.tree.Comment;
import org.openrewrite.java.tree.J;
import org.openrewrite.java.tree.Space;
import org.openrewrite.java.tree.Statement;
import org.openrewrite.staticanalysis.java.JavaFileChecker;

import java.time.Duration;
import java.util.List;
import java.util.Set;
import java.util.function.Function;

import static java.util.Collections.singleton;

public class RemoveNoArgumentSuperConstructorCall extends Recipe {
    @Getter
    final String displayName = "Remove no argument `super()` constructor calls";

    @Getter
    final String description = "The compiler inserts a call to the no argument constructor of the super class " +
            "when a constructor does not start with an explicit `this()` or `super(..)` call, " +
            "which makes writing out `super();` redundant.";

    @Getter
    final Set<String> tags = singleton("RSPEC-S3253");

    @Getter
    final Duration estimatedEffortPerOccurrence = Duration.ofMinutes(2);

    @Override
    public TreeVisitor<?, ExecutionContext> getVisitor() {
        return Preconditions.check(new JavaFileChecker<>(), new JavaIsoVisitor<ExecutionContext>() {
            @Override
            public J.MethodDeclaration visitMethodDeclaration(J.MethodDeclaration method, ExecutionContext ctx) {
                J.MethodDeclaration md = super.visitMethodDeclaration(method, ctx);
                J.Block body = md.getBody();
                if (!md.isConstructor() || body == null || body.getStatements().isEmpty()) {
                    return md;
                }

                Statement first = body.getStatements().get(0);
                if (!(first instanceof J.MethodInvocation)) {
                    return md;
                }
                J.MethodInvocation superCall = (J.MethodInvocation) first;
                if (!"super".equals(superCall.getSimpleName()) || superCall.getSelect() != null ||
                        superCall.getTypeParameters() != null ||
                        superCall.getArguments().size() != 1 || !(superCall.getArguments().get(0) instanceof J.Empty)) {
                    return md;
                }

                Space firstPrefix = first.getPrefix();
                List<Comment> comments = firstPrefix.getComments();
                List<Statement> remaining = ListUtils.mapFirst(body.getStatements(), (Function<Statement, Statement>) statement -> null);
                if (remaining.isEmpty()) {
                    Space end = body.getEnd();
                    if (comments.isEmpty()) {
                        return md.withBody(body.withStatements(remaining));
                    }
                    List<Comment> moved = end.getComments().isEmpty() ?
                            ListUtils.mapLast(comments, comment -> comment.withSuffix(end.getWhitespace())) : comments;
                    return md.withBody(body.withStatements(remaining).withEnd(end
                            .withWhitespace(firstPrefix.getWhitespace())
                            .withComments(ListUtils.concatAll(moved, end.getComments()))));
                }
                return md.withBody(body.withStatements(ListUtils.mapFirst(remaining, next -> next.withPrefix(next.getPrefix()
                        .withWhitespace(firstPrefix.getWhitespace())
                        .withComments(ListUtils.concatAll(comments, next.getComments()))))));
            }
        });
    }
}
