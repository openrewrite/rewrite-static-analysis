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
import org.openrewrite.Cursor;
import org.openrewrite.ExecutionContext;
import org.openrewrite.Recipe;
import org.openrewrite.Tree;
import org.openrewrite.TreeVisitor;
import org.openrewrite.internal.ListUtils;
import org.openrewrite.java.JavaVisitor;
import org.openrewrite.java.tree.J;
import org.openrewrite.java.tree.Space;
import org.openrewrite.java.tree.Statement;

import java.time.Duration;
import java.util.Comparator;
import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;

import static java.util.Collections.singleton;

public class ForLoopIncrementInUpdate extends Recipe {

    @Getter
    final String displayName = "`for` loop counters incremented in update";

    @Getter
    final String description = "The increment should be moved to the loop's increment clause " +
            "if possible. Placing the counter update in the loop body rather than " +
            "the update clause obscures the loop's control flow and makes it " +
            "harder to reason about termination.";

    @Getter
    final Set<String> tags = singleton("RSPEC-S1994");

    @Getter
    final Duration estimatedEffortPerOccurrence = Duration.ofMinutes(20);

    @Override
    public TreeVisitor<?, ExecutionContext> getVisitor() {
        return new JavaVisitor<ExecutionContext>() {
            @Override
            public J visitForLoop(J.ForLoop forLoop, ExecutionContext ctx) {
                Statement init = forLoop.getControl().getInit().get(0);
                if (init instanceof J.VariableDeclarations) {
                    J.VariableDeclarations initVars = (J.VariableDeclarations) init;

                    Statement body = forLoop.getBody();
                    Statement lastStatement;
                    if (body instanceof J.Block) {
                        List<Statement> statements = ((J.Block) body).getStatements();
                        if (statements.isEmpty()) {
                            return super.visitForLoop(forLoop, ctx);
                        }
                        lastStatement = statements.get(statements.size() - 1);
                    } else {
                        return super.visitForLoop(forLoop, ctx);
                    }

                    if (lastStatement instanceof J.Unary) {
                        J.Unary unary = (J.Unary) lastStatement;
                        if (unary.getExpression() instanceof J.Identifier) {
                            String unaryTarget = ((J.Identifier) unary.getExpression()).getSimpleName();
                            for (J.VariableDeclarations.NamedVariable initVar : initVars.getVariables()) {
                                if (initVar.getSimpleName().equals(unaryTarget)) {
                                    // A `continue` skips the trailing increment but not the update clause, so
                                    // hoisting it would run the increment on iterations that currently skip it.
                                    // https://github.com/openrewrite/rewrite-static-analysis/issues/1061
                                    if (continuesThisLoop(forLoop)) {
                                        return super.visitForLoop(forLoop, ctx);
                                    }
                                    J.ForLoop f = forLoop.withControl(forLoop.getControl().withUpdate(ListUtils.insertInOrder(
                                            ListUtils.map(forLoop.getControl().getUpdate(), u -> u instanceof J.Empty ? null : u),
                                            unary.withPrefix(Space.format(" ")),
                                            Comparator.comparing(s -> s.printTrimmed(getCursor()), Comparator.naturalOrder())
                                    )));

                                    return f.withBody((Statement) new JavaVisitor<ExecutionContext>() {

                                        @Override
                                        public @Nullable J visit(@Nullable Tree tree, ExecutionContext ctx) {
                                            return tree == unary ? null : super.visit(tree, ctx);
                                        }
                                    }.visit(f.getBody(), ctx));
                                }
                            }
                        }
                    }
                }

                return super.visitForLoop(forLoop, ctx);
            }

            private boolean continuesThisLoop(J.ForLoop forLoop) {
                Object parent = getCursor().getParentTreeCursor().getValue();
                String label = parent instanceof J.Label ? ((J.Label) parent).getLabel().getSimpleName() : null;
                return new JavaVisitor<AtomicBoolean>() {
                    @Override
                    public J visitContinue(J.Continue continueStatement, AtomicBoolean found) {
                        if (continueStatement.getLabel() == null) {
                            // An unlabeled continue targets the innermost loop, which is only this one if no
                            // other loop sits between them.
                            boolean nested = false;
                            for (Cursor c = getCursor().getParent(); c != null; c = c.getParent()) {
                                Object value = c.getValue();
                                if (value instanceof J.ForLoop || value instanceof J.ForEachLoop ||
                                        value instanceof J.WhileLoop || value instanceof J.DoWhileLoop) {
                                    nested = true;
                                    break;
                                }
                            }
                            if (!nested) {
                                found.set(true);
                            }
                        } else if (continueStatement.getLabel().getSimpleName().equals(label)) {
                            found.set(true);
                        }
                        return super.visitContinue(continueStatement, found);
                    }
                }.reduce(forLoop.getBody(), new AtomicBoolean(false)).get();
            }
        };
    }
}
