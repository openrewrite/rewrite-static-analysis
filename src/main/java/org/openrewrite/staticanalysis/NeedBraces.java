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

import org.jspecify.annotations.Nullable;
import org.openrewrite.*;
import org.openrewrite.internal.ListUtils;
import org.openrewrite.java.JavaIsoVisitor;
import org.openrewrite.java.style.Checkstyle;
import org.openrewrite.java.style.NeedBracesStyle;
import org.openrewrite.java.tree.J;
import org.openrewrite.java.tree.JRightPadded;
import org.openrewrite.java.tree.Space;
import org.openrewrite.java.tree.Statement;
import org.openrewrite.kotlin.tree.K;
import org.openrewrite.marker.Markers;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;

import static java.util.Objects.requireNonNull;

public class NeedBraces extends Recipe {
    @Override
    public String getDisplayName() {
        return "Fix missing braces";
    }

    @Override
    public String getDescription() {
        return "Adds missing braces around code such as single-line `if`, `for`, `while`, and `do-while` block bodies.";
    }

    @Override
    public Set<String> getTags() {
        return Collections.singleton("RSPEC-S121");
    }

    @Override
    public Duration getEstimatedEffortPerOccurrence() {
        return Duration.ofMinutes(2);
    }

    @Override
    public TreeVisitor<?, ExecutionContext> getVisitor() {
        return new NeedBracesVisitor();
    }

    private static class NeedBracesVisitor extends JavaIsoVisitor<ExecutionContext> {

        @SuppressWarnings("NotNullFieldNotInitialized")
        NeedBracesStyle needBracesStyle;

        /**
         * A {@link J.Block} implies the section of code is implicitly surrounded in braces.
         * We can use that to our advantage by saying if you aren't a block (e.g. a single {@link Statement}, etc.),
         * then we're going to make this into a block. That's how we'll get the code bodies surrounded in braces.
         */
        private <T extends Statement> J.Block buildBlock(T element) {
            J rootElement = null;
            Space end = Space.EMPTY;

            Cursor currentCursor = getCursor();
            while (
                    currentCursor != null &&
                            currentCursor.getParent() != null &&
                            !(currentCursor.getParent().getValue() instanceof J.Block)
            ) {
                currentCursor = currentCursor.getParent();
            }

            if (currentCursor != null && currentCursor.getValue() instanceof JRightPadded) {
                JRightPadded<J> paddedIf = currentCursor.getValue();
                rootElement = paddedIf.getElement();
            }

            // Move comments
            if (rootElement instanceof Statement && !(rootElement instanceof J.DoWhileLoop)) {
                Cursor blockParentCursor = currentCursor.getParent();
                J.Block block = blockParentCursor.getValue();
                List<Statement> statements = block.getStatements();
                int currentIndex = statements.indexOf(rootElement);
                boolean last = currentIndex == statements.size() - 1;
                Space trailingComment = last ? block.getEnd() : statements.get(currentIndex + 1).getPrefix();

                if (!trailingComment.isEmpty() && !trailingComment.getWhitespace().contains("\n")) {
                    end = trailingComment;
                    if (last) {
                        blockParentCursor.putMessage("removeEndComments", true);
                    } else {
                        blockParentCursor.<List<Integer>>computeMessageIfAbsent("replaced", k -> new ArrayList<>()).add(currentIndex);
                    }
                }
            }

            return new J.Block(
                    Tree.randomId(),
                    Space.EMPTY,
                    Markers.EMPTY,
                    JRightPadded.build(false),
                    element instanceof J.Empty ? Collections.emptyList() : Collections.singletonList(JRightPadded.build(element)),
                    end
            );
        }

        @Override
        public @Nullable J visit(@Nullable Tree tree, ExecutionContext ctx) {
            if (tree instanceof SourceFile) {
                SourceFile cu = (SourceFile) requireNonNull(tree);
                // Python don't need none of your curly braces
                if (cu.getSourcePath().toString().endsWith(".py")) {
                    return (J) tree;
                }
                needBracesStyle = cu.getStyle(NeedBracesStyle.class) == null ?
                        Checkstyle.needBracesStyle() :
                        cu.getStyle(NeedBracesStyle.class, new NeedBracesStyle(false, false));
            }
            return super.visit(tree, ctx);
        }

        @Override
        public J.Block visitBlock(J.Block block, ExecutionContext ctx) {
            J.Block bl = super.visitBlock(block, ctx);
            if (Boolean.TRUE.equals(getCursor().pollMessage("removeEndComments"))) {
                bl = bl.withEnd(bl.getEnd().withComments(Collections.emptyList()));
                bl = maybeAutoFormat(block, bl, ctx);
            }
            List<Integer> indexes = getCursor().pollMessage("replaced");
            if (indexes != null) {
                for (int index : indexes) {
                    boolean last = index == bl.getPadding().getStatements().size() - 1;
                    if (!last) {
                        bl = bl.withStatements(ListUtils.map(bl.getStatements(), (i, stmt) -> {
                            if (i == index + 1) {
                                return stmt.withPrefix(Space.EMPTY);
                            }
                            return stmt;
                        }));
                    } else {
                        bl = bl.withEnd(bl.getEnd().withComments(Collections.emptyList()));
                    }
                }
                bl = maybeAutoFormat(block, bl, ctx);
            }
            return bl;
        }

        @Override
        public J.If visitIf(J.If iff, ExecutionContext ctx) {
            if (usedAsExpression()) {
                // Kotlin has no dedicated ternary operator
                return iff;
            }
            J.If elem = super.visitIf(iff, ctx);
            boolean hasAllowableBodyType = elem.getThenPart() instanceof J.Block;
            if (!needBracesStyle.getAllowSingleLineStatement() && !hasAllowableBodyType) {
                J.Block b;
                if (elem.getElsePart() != null && !elem.getElsePart().getPrefix().getComments().isEmpty()) {
                    Space end = elem.getElsePart().getPrefix();
                    elem = elem.withElsePart(elem.getElsePart().withPrefix(Space.EMPTY));
                    b = buildBlock(elem.getThenPart()).withEnd(end);
                } else {
                    b = buildBlock(elem.getThenPart());
                }

                elem = maybeAutoFormat(elem, elem.withThenPart(b), ctx);
            }
            return elem;
        }

        private boolean usedAsExpression() {
            return getCursor().getParentOrThrow().getValue() instanceof K.StatementExpression;
        }

        @Override
        public J.If.Else visitElse(J.If.Else else_, ExecutionContext ctx) {
            J.If.Else elem = super.visitElse(else_, ctx);
            boolean hasAllowableBodyType = elem.getBody() instanceof J.Block || elem.getBody() instanceof J.If;
            if (!needBracesStyle.getAllowSingleLineStatement() && !hasAllowableBodyType) {
                Space prefix = elem.getPrefix();
                Statement body = elem.getBody();

                if (!prefix.getComments().isEmpty() && prefix.getWhitespace().contains("\n")) {
                    body = body.withPrefix(prefix);
                }

                J.Block b = buildBlock(body);
                elem = maybeAutoFormat(elem, elem.withBody(b), ctx);
            }
            return elem;
        }

        @Override
        public J.WhileLoop visitWhileLoop(J.WhileLoop whileLoop, ExecutionContext ctx) {
            J.WhileLoop elem = super.visitWhileLoop(whileLoop, ctx);
            boolean hasAllowableBodyType = needBracesStyle.getAllowEmptyLoopBody() ?
                    elem.getBody() instanceof J.Block || elem.getBody() instanceof J.Empty :
                    elem.getBody() instanceof J.Block;
            if (!needBracesStyle.getAllowEmptyLoopBody() && elem.getBody() instanceof J.Empty) {
                J.Block b = buildBlock(elem.getBody());
                elem = maybeAutoFormat(elem, elem.withBody(b), ctx);
            } else if (!needBracesStyle.getAllowSingleLineStatement() && !hasAllowableBodyType) {
                J.Block b = buildBlock(elem.getBody());
                elem = maybeAutoFormat(elem, elem.withBody(b), ctx);
            }
            return elem;
        }

        @Override
        public J.DoWhileLoop visitDoWhileLoop(J.DoWhileLoop doWhileLoop, ExecutionContext ctx) {
            J.DoWhileLoop elem = super.visitDoWhileLoop(doWhileLoop, ctx);
            boolean hasAllowableBodyType = needBracesStyle.getAllowEmptyLoopBody() ?
                    elem.getBody() instanceof J.Block || elem.getBody() instanceof J.Empty :
                    elem.getBody() instanceof J.Block;
            if (!needBracesStyle.getAllowEmptyLoopBody() && elem.getBody() instanceof J.Empty) {
                J.Block b = buildBlock(elem.getBody());
                elem = maybeAutoFormat(elem, elem.withBody(b), ctx);
            } else if (!needBracesStyle.getAllowSingleLineStatement() && !hasAllowableBodyType) {
                J.Block b = buildBlock(elem.getBody());
                elem = maybeAutoFormat(elem, elem.withBody(b), ctx);
            }
            return elem;
        }

        @Override
        public J.ForLoop visitForLoop(J.ForLoop forLoop, ExecutionContext ctx) {
            J.ForLoop elem = super.visitForLoop(forLoop, ctx);
            boolean hasAllowableBodyType = needBracesStyle.getAllowEmptyLoopBody() ?
                    elem.getBody() instanceof J.Block || elem.getBody() instanceof J.Empty :
                    elem.getBody() instanceof J.Block;
            if (!needBracesStyle.getAllowEmptyLoopBody() && elem.getBody() instanceof J.Empty) {
                J.Block b = buildBlock(elem.getBody());
                elem = maybeAutoFormat(elem, elem.withBody(b), ctx);
            } else if (!needBracesStyle.getAllowSingleLineStatement() && !hasAllowableBodyType) {
                J.Block b = buildBlock(elem.getBody());
                elem = maybeAutoFormat(elem, elem.withBody(b), ctx);
            }
            return elem;
        }
    }
}
