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
import org.openrewrite.ExecutionContext;
import org.openrewrite.Incubating;
import org.openrewrite.Recipe;
import org.openrewrite.TreeVisitor;
import org.openrewrite.internal.ListUtils;
import org.openrewrite.java.JavaVisitor;
import org.openrewrite.java.tree.J;
import org.openrewrite.java.tree.JRightPadded;
import org.openrewrite.java.tree.Statement;

import java.util.List;
import java.util.Set;

import static java.util.Collections.singleton;

@Incubating(since = "7.21.0")
public class RemoveUnneededBlock extends Recipe {
    @Getter
    final String displayName = "Remove unneeded block";

    @Getter
    final String description = "Flatten blocks into inline statements when possible. " +
            "Unnecessary nested blocks add indentation and scope boundaries that obscure " +
            "the control flow, often indicating code that should be extracted into its own method.";

    @Getter
    final Set<String> tags = singleton("RSPEC-S1199");

    @Override
    public TreeVisitor<?, ExecutionContext> getVisitor() {
        return new RemoveUnneededBlockStatementVisitor();
    }

    static class RemoveUnneededBlockStatementVisitor extends JavaVisitor<ExecutionContext> {

        @Override
        public J.Block visitBlock(J.Block block, ExecutionContext ctx) {
            J.Block bl = (J.Block) super.visitBlock(block, ctx);
            J directParent = getCursor().getParentTreeCursor().getValue();
            if (directParent instanceof J.NewClass || directParent instanceof J.ClassDeclaration) {
                // If the direct parent is an initializer block or a static block, skip it
                return bl;
            }

            return maybeInlineBlock(bl, ctx);
        }

        private J.Block maybeInlineBlock(J.Block block, ExecutionContext ctx) {
            List<Statement> statements = block.getStatements();
            if (statements.isEmpty()) {
                // Removal handled by `EmptyBlock`
                return block;
            }

            // Inline at the padding level so each statement keeps its `JRightPadded`: the JS/TS
            // `Semicolon` marker lives on the padding, not the statement, and is otherwise dropped.
            Statement lastStatement = statements.get(statements.size() - 1);
            List<JRightPadded<Statement>> padded = block.getPadding().getStatements();
            J.Block flattened = block.getPadding().withStatements(ListUtils.flatMap(padded, (i, rp) -> {
                Statement stmt = rp.getElement();
                J.Block nested;
                if (stmt instanceof J.Try) {
                    J.Try _try = (J.Try) stmt;
                    if (_try.getResources() != null || !_try.getCatches().isEmpty() || _try.getFinally() == null || !_try.getFinally().getStatements().isEmpty()) {
                        return rp;
                    }
                    nested = _try.getBody();
                } else if (stmt instanceof J.Block) {
                    nested = (J.Block) stmt;
                } else {
                    return rp;
                }

                // blocks are relevant for scoping, so don't flatten them if they contain variable declarations unless they also have returns
                if (i < padded.size() - 1 &&
                        nested.getStatements().stream().anyMatch(J.VariableDeclarations.class::isInstance) &&
                        nested.getStatements().stream().noneMatch(J.Return.class::isInstance)) {
                    return rp;
                }

                return ListUtils.map(nested.getPadding().getStatements(), (j, innerRp) -> {
                    Statement inlinedStmt = innerRp.getElement();
                    if (j == 0) {
                        inlinedStmt = inlinedStmt.withPrefix(inlinedStmt.getPrefix()
                                .withComments(ListUtils.concatAll(nested.getComments(), inlinedStmt.getComments())));
                    }
                    return innerRp.withElement(autoFormat(inlinedStmt, ctx, getCursor()));
                });
            }));

            if (flattened == block) {
                return block;
            }
            if (lastStatement instanceof J.Block) {
                flattened = flattened.withEnd(flattened.getEnd()
                        .withComments(ListUtils.concatAll(((J.Block) lastStatement).getEnd().getComments(), flattened.getEnd().getComments())));
            }
            return flattened;
        }
    }
}
