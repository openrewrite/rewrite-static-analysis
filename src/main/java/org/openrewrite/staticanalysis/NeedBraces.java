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

import org.openrewrite.*;
import org.openrewrite.internal.ListUtils;
import org.openrewrite.internal.lang.Nullable;
import org.openrewrite.java.JavaIsoVisitor;
import org.openrewrite.java.style.Checkstyle;
import org.openrewrite.java.style.NeedBracesStyle;
import org.openrewrite.java.tree.*;
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
        NeedBracesStyle needBracesStyle;

        /**
         * A {@link J.Block} implies the section of code is implicitly surrounded in braces.
         * We can use that to our advantage by saying if you aren't a block (e.g. a single {@link Statement}, etc.),
         * then we're going to make this into a block. That's how we'll get the code bodies surrounded in braces.
         */
        private <T extends Statement> J.Block buildBlock(Statement owner, T element) {
            J j = getCursor().getParentTreeCursor().getValue();
            Space end = Space.EMPTY;
            if (j instanceof J.Block) {
                J.Block block = (J.Block) j;
                List<Statement> statements = block.getStatements();
                int i = statements.indexOf(owner);
                boolean last = i == statements.size() - 1;
                Space trailingSpace = last ? block.getEnd() : statements.get(i + 1).getPrefix();
                if (!trailingSpace.getComments().isEmpty() && trailingSpace.getWhitespace().indexOf('\n') == -1) {
                    end = trailingSpace;
                    getCursor().getParentTreeCursor().<List<Integer>>computeMessageIfAbsent("replaced", k -> new ArrayList<>()).add(i);
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
        public J visit(@Nullable Tree tree, ExecutionContext ctx) {
            if (tree instanceof JavaSourceFile) {
                SourceFile cu = (SourceFile) requireNonNull(tree);
                needBracesStyle = cu.getStyle(NeedBracesStyle.class) == null ? Checkstyle.needBracesStyle() : cu.getStyle(NeedBracesStyle.class);
            }
            return super.visit(tree, ctx);
        }

        @Override
        public J.Block visitBlock(J.Block block, ExecutionContext ctx) {
            J.Block bl = super.visitBlock(block, ctx);
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
                J.Block b = buildBlock(elem, elem.getThenPart());
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
                J.Block b = buildBlock(getCursor().getParentTreeCursor().getValue(), elem.getBody());
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
                J.Block b = buildBlock(elem, elem.getBody());
                elem = maybeAutoFormat(elem, elem.withBody(b), ctx);
            } else if (!needBracesStyle.getAllowSingleLineStatement() && !hasAllowableBodyType) {
                J.Block b = buildBlock(elem, elem.getBody());
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
                J.Block b = buildBlock(elem, elem.getBody());
                elem = maybeAutoFormat(elem, elem.withBody(b), ctx);
            } else if (!needBracesStyle.getAllowSingleLineStatement() && !hasAllowableBodyType) {
                J.Block b = buildBlock(elem, elem.getBody());
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
                J.Block b = buildBlock(elem, elem.getBody());
                elem = maybeAutoFormat(elem, elem.withBody(b), ctx);
            } else if (!needBracesStyle.getAllowSingleLineStatement() && !hasAllowableBodyType) {
                J.Block b = buildBlock(elem, elem.getBody());
                elem = maybeAutoFormat(elem, elem.withBody(b), ctx);
            }
            return elem;
        }
    }

    ;
}
