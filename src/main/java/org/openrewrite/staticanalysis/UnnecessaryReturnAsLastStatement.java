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

import lombok.Getter;
import org.jspecify.annotations.Nullable;
import org.openrewrite.ExecutionContext;
import org.openrewrite.Recipe;
import org.openrewrite.TreeVisitor;
import org.openrewrite.internal.ListUtils;
import org.openrewrite.java.JavaIsoVisitor;
import org.openrewrite.java.tree.J;
import org.openrewrite.java.tree.JavaType;
import org.openrewrite.java.tree.Space;
import org.openrewrite.java.tree.Statement;
import org.openrewrite.java.tree.TypeUtils;

import java.time.Duration;
import java.util.List;
import java.util.Set;

import static java.util.Collections.singleton;

public class UnnecessaryReturnAsLastStatement extends Recipe {
    @Getter
    final String displayName = "Unnecessary `return` as last statement in void method";

    @Getter
    final String description = "Removes `return` from a `void` method if it's the last " +
            "statement. A trailing `return` in a void method has no effect on " +
            "control flow and is just noise that distracts from the meaningful logic.";

    @Getter
    final Set<String> tags = singleton("RSPEC-S3626");

    @Getter
    final Duration estimatedEffortPerOccurrence = Duration.ofMinutes(1);

    @Override
    public TreeVisitor<?, ExecutionContext> getVisitor() {
        return new JavaIsoVisitor<ExecutionContext>() {
            @Override
            public J.MethodDeclaration visitMethodDeclaration(J.MethodDeclaration method, ExecutionContext ctx) {
                J.MethodDeclaration m = super.visitMethodDeclaration(method, ctx);
                if (TypeUtils.asPrimitive(m.getType()) == JavaType.Primitive.Void && m.getBody() != null) {
                    return m.withBody(trimBlock(m.getBody()));
                }
                return m;
            }

            private J.Block trimBlock(J.Block block) {
                List<Statement> statements = block.getStatements();
                if (statements.isEmpty()) {
                    return block;
                }
                Statement last = statements.get(statements.size() - 1);
                Statement trimmed = maybeRemoveReturn(last);
                if (trimmed == null) {
                    return removeLastStatement(block);
                }
                return trimmed == last ? block : block.withStatements(ListUtils.mapLast(statements, s -> trimmed));
            }

            private @Nullable Statement maybeRemoveReturn(Statement s) {
                if (s instanceof J.Return && ((J.Return) s).getExpression() == null) {
                    return null;
                }
                if (s instanceof J.Block) {
                    return trimBlock((J.Block) s);
                }
                if (s instanceof J.If) {
                    J.If ifStatement = (J.If) s;
                    Statement trimmedThen = maybeRemoveReturn(ifStatement.getThenPart());
                    if (trimmedThen != ifStatement.getThenPart() && trimmedThen != null) {
                        ifStatement = ifStatement.withThenPart(trimmedThen);
                    }
                    if (ifStatement.getElsePart() != null) {
                        Statement trimmedElse = maybeRemoveReturn(ifStatement.getElsePart().getBody());
                        if (trimmedElse == null) {
                            return ifStatement.withElsePart(null);
                        }
                        if (trimmedElse != ifStatement.getElsePart().getBody()) {
                            return ifStatement.withElsePart(ifStatement.getElsePart().withBody(trimmedElse));
                        }
                    }
                    return ifStatement;
                }
                return s;
            }
        };
    }

    /**
     * Removes the last statement of a block, keeping the comments before it at the end of the block.
     */
    static J.Block removeLastStatement(J.Block block) {
        List<Statement> statements = block.getStatements();
        if (statements.isEmpty()) {
            return block;
        }
        Space prefix = statements.get(statements.size() - 1).getPrefix();
        J.Block b = block.withStatements(ListUtils.mapLast(statements, s -> null));
        if (prefix.getComments().isEmpty()) {
            return b;
        }
        Space end = block.getEnd();
        // A comment that trailed the removed statement keeps the line break and indent that preceded the statement
        return b.withEnd(prefix.withComments(end.getComments().isEmpty() ?
                ListUtils.mapLast(prefix.getComments(), c -> c.withSuffix(end.getWhitespace())) :
                ListUtils.concatAll(prefix.getComments(), end.getComments())));
    }
}
