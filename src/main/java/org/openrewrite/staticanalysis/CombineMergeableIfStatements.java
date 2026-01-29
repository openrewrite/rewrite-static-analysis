/*
 * Copyright 2025 the original author or authors.
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
import lombok.RequiredArgsConstructor;
import org.jspecify.annotations.Nullable;
import org.openrewrite.ExecutionContext;
import org.openrewrite.Recipe;
import org.openrewrite.Tree;
import org.openrewrite.TreeVisitor;
import org.openrewrite.internal.ListUtils;
import org.openrewrite.java.JavaIsoVisitor;
import org.openrewrite.java.JavaTemplate;
import org.openrewrite.java.style.IntelliJ;
import org.openrewrite.java.style.TabsAndIndentsStyle;
import org.openrewrite.java.tree.Comment;
import org.openrewrite.java.tree.Expression;
import org.openrewrite.java.tree.J;
import org.openrewrite.java.tree.JavaSourceFile;
import org.openrewrite.java.tree.Space;
import org.openrewrite.java.tree.Statement;
import org.openrewrite.java.tree.TextComment;
import org.openrewrite.style.Style;

import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static java.util.Collections.emptyList;
import static java.util.Collections.singleton;
import static java.util.Objects.requireNonNull;
import static org.openrewrite.java.format.ShiftFormat.indent;

public class CombineMergeableIfStatements extends Recipe {

    private static final String CONTINUATION_KEY = "continuationAfterLogicalAnd";

    @Getter
    final String displayName = "Mergeable `if` statements should be combined";
    @Getter
    final String description = "Mergeable `if` statements should be combined.";
    @Getter
    final Set<String> tags = singleton("RSPEC-S1066");
        return singleton("RSPEC-S1066");
    }

    @Override
    public TreeVisitor<?, ExecutionContext> getVisitor() {
        return new JavaIsoVisitor<ExecutionContext>() {
            @Override
            public J.If visitIf(J.If iff, ExecutionContext ctx) {
                J.If outerIf = super.visitIf(iff, ctx);

                if (outerIf.getElsePart() == null) {
                    // thenPart is either a single if or a block with a single if
                    J.Block outerBlock = null;
                    J.If innerIf = null;
                    if (outerIf.getThenPart() instanceof J.If) {
                        innerIf = (J.If) outerIf.getThenPart();
                    } else if (outerIf.getThenPart() instanceof J.Block) {
                        outerBlock = (J.Block) outerIf.getThenPart();
                        List<Statement> statements = outerBlock.getStatements();
                        if (statements.size() == 1 && statements.get(0) instanceof J.If) {
                            innerIf = (J.If) statements.get(0);
                        }
                    }

                    if (innerIf != null && innerIf.getElsePart() == null) {
                        // thenPart of outer if is replaced with thenPart of innerIf
                        // combine conditions with logical AND : correct parenthesizing is handled by JavaTemplate
                        Expression outerCondition = outerIf.getIfCondition().getTree();
                        Expression innerCondition = innerIf.getIfCondition().getTree();

                        UUID innerIfId = Tree.randomId();
                        getCursor().getRoot().putMessage(innerIfId.toString(), innerIf.getComments());
                        UUID outerBlockId = Tree.randomId();
                        getCursor().getRoot().putMessage(outerBlockId.toString(),
                                Optional.ofNullable(outerBlock).map(J::getComments).orElse(emptyList()));

                        doAfterVisit(new MergedConditionalVisitor<>());
                        return JavaTemplate.<J.If>apply(
                                String.format("#{any()} /*%s,%s,%s*/&& #{any()}", CONTINUATION_KEY, innerIfId, outerBlockId),
                                getCursor(),
                                outerCondition.getCoordinates().replace(),
                                outerCondition,
                                innerCondition)
                                .withThenPart(indent(innerIf.getThenPart(), getCursor(), -1));
                    }
                }

                return outerIf;
            }
        };
    }

    @RequiredArgsConstructor
    private static class MergedConditionalVisitor<P> extends JavaIsoVisitor<P> {

        @Nullable
        private TabsAndIndentsStyle tabsAndIndentsStyle;

        @Override
        public @Nullable J visit(@Nullable Tree tree, P p) {
            if (tree instanceof JavaSourceFile) {
                JavaSourceFile cu = (JavaSourceFile) requireNonNull(tree);
                tabsAndIndentsStyle = Style.from(TabsAndIndentsStyle.class, cu, IntelliJ::tabsAndIndents);
            }
            return super.visit(tree, p);
        }

        @Override
        public Space visitSpace(@Nullable Space space, Space.Location loc, P p) {
            Space s = super.visitSpace(space, loc, p);
            if (s.getComments().size() == 1 &&
                    s.getComments().get(0) instanceof TextComment) {
                TextComment onlyComment = (TextComment) s.getComments().get(0);
                if (onlyComment.isMultiline() &&
                        onlyComment.getText().startsWith(CONTINUATION_KEY) &&
                        getCursor().firstEnclosingOrThrow(J.Binary.class).getOperator() == J.Binary.Type.And) {
                    final String[] arr = onlyComment.getText().split(",");
                    final String innerIfId = arr[1];
                    final String outerBlockId = arr[2];
                    List<Comment> innerIfComments = Optional.ofNullable(getCursor().getRoot().<List<Comment>>pollMessage(innerIfId)).orElse(emptyList());
                    List<Comment> outerBlockComments = Optional.ofNullable(getCursor().getRoot().<List<Comment>>pollMessage(outerBlockId)).orElse(emptyList());

                    getCursor().putMessageOnFirstEnclosing(J.Binary.class, CONTINUATION_KEY, innerIfComments);
                    s = s.withComments(outerBlockComments);
                }
            }

            return s;
        }

        @Override
        public J.Binary visitBinary(J.Binary binary, P p) {
            J.Binary b = super.visitBinary(binary, p);

            List<Comment> comments = getCursor().pollMessage(CONTINUATION_KEY);
            if (comments != null) {
                final String outerIfIndent = getCursor().firstEnclosingOrThrow(J.If.class).getPrefix().getIndent();
                final String continuationIndent = continuationIndent(requireNonNull(tabsAndIndentsStyle), outerIfIndent);

                if (comments.isEmpty()) {
                    b = b.withRight(b.getRight()
                            .withPrefix(Space.format("\n" + continuationIndent)));
                } else {
                    b = b.withRight(b.getRight()
                            .withComments(ListUtils.map(comments, c -> replaceIndent(c, continuationIndent))));
                }
            }

            return b;
        }

        private Comment replaceIndent(Comment comment, String newIndent) {
            Comment c = comment.withSuffix(replaceLastLineWithIndent(comment.getSuffix(), newIndent));
            if (c.isMultiline() && c instanceof TextComment) {
                TextComment tc = (TextComment) c;
                c = tc.withText(replaceTextIndent(tc.getText(), newIndent));
            }
            return c;
        }

        private String replaceTextIndent(final String text, final String newIndent) {
            final StringBuilder sb = new StringBuilder();
            boolean found = false;
            for (final char ch : text.toCharArray()) {
                if (ch == ' ' || ch == '\t') {
                    if (!found) {
                        sb.append(ch);
                    }
                } else if (ch == '\r' || ch == '\n') {
                    sb.append(ch);
                    found = true;
                } else {
                    if (found) {
                        sb.append(newIndent);
                        if (ch == '*') {
                            sb.append(' ');
                        }
                    }
                    sb.append(ch);
                    found = false;
                }
            }
            if (found) {
                sb.append(newIndent);
                sb.append(' ');
            }
            return sb.toString();
        }

        private String replaceLastLineWithIndent(String whitespace, String indent) {
            int idx = whitespace.length() - 1;
            while (idx >= 0) {
                char c = whitespace.charAt(idx);
                if (c == '\r' || c == '\n') {
                    break;
                }
                idx--;
            }
            if (idx >= 0) {
                return whitespace.substring(0, idx + 1) + indent;
            }
            return whitespace;
        }

        private String continuationIndent(TabsAndIndentsStyle tabsAndIndents, String currentIndent) {
            char c;
            int len;
            if (tabsAndIndents.getUseTabCharacter()) {
                c = '\t';
                len = tabsAndIndents.getContinuationIndent() / tabsAndIndents.getTabSize();
            } else {
                c = ' ';
                len = tabsAndIndents.getContinuationIndent();
            }

            StringBuilder sb = new StringBuilder(currentIndent);
            for (int i = 0; i < len; i++) {
                sb.append(c);
            }
            return sb.toString();
        }
    }
}
