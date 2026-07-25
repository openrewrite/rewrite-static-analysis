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
import org.openrewrite.java.JavaVisitor;
import org.openrewrite.java.JavadocVisitor;
import org.openrewrite.java.tree.Javadoc;
import org.openrewrite.marker.Markers;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

import static org.openrewrite.Tree.randomId;

/**
 * Reorders Javadoc block tags to match Checkstyle's {@code AtclauseOrder} default /
 * Oracle's recommended tag order:
 * {@code @author}, {@code @version}, {@code @param}, {@code @return}, {@code @throws},
 * {@code @exception}, {@code @see}, {@code @since}, {@code @serial}, {@code @serialField},
 * {@code @serialData}, {@code @deprecated}.
 */
public class JavadocTagOrder extends Recipe {

    @Getter
    final String displayName = "Reorder Javadoc tags";

    @Getter
    final String description = "Reorders Javadoc block tags (`@param`, `@author`, `@since`, etc.) into the " +
                               "order recommended by the Oracle Javadoc documentation and Checkstyle's " +
                               "`AtclauseOrder` check.";

    @Getter
    final Duration estimatedEffortPerOccurrence = Duration.ofMinutes(2);

    @Override
    public TreeVisitor<?, ExecutionContext> getVisitor() {
        return new JavaVisitor<ExecutionContext>() {
            @Override
            protected JavadocVisitor<ExecutionContext> getJavadocVisitor() {
                return new JavadocVisitor<ExecutionContext>(this) {
                    @Override
                    public Javadoc visitDocComment(Javadoc.DocComment javadoc, ExecutionContext ctx) {
                        Javadoc.DocComment dc = (Javadoc.DocComment) super.visitDocComment(javadoc, ctx);
                        List<Javadoc> body = new ArrayList<>(dc.getBody());
                        List<Integer> tagIndexes = new ArrayList<>();
                        for (int i = 0; i < body.size(); i++) {
                            if (isBlockTag(body.get(i))) {
                                tagIndexes.add(i);
                            }
                        }
                        if (tagIndexes.size() < 2) {
                            return dc;
                        }

                        int[] groupStarts = new int[tagIndexes.size() + 1];
                        for (int g = 0; g < tagIndexes.size(); g++) {
                            int tagIdx = tagIndexes.get(g);
                            if (g == 0) {
                                groupStarts[g] = leadingWhitespaceStartForFirstTag(body, tagIdx);
                            } else {
                                int start = tagIdx;
                                int limit = tagIndexes.get(g - 1) + 1;
                                while (start > limit && isLeadingWhitespace(body.get(start - 1))) {
                                    start--;
                                }
                                groupStarts[g] = start;
                            }
                        }
                        // Keep trailing body after the final tag (usually the closing line break before `*/`)
                        // as a suffix so it is not moved when the last tag is reordered.
                        int lastTagIdx = tagIndexes.get(tagIndexes.size() - 1);
                        groupStarts[tagIndexes.size()] = lastTagIdx + 1;

                        List<List<Javadoc>> groups = new ArrayList<>(tagIndexes.size());
                        for (int g = 0; g < tagIndexes.size(); g++) {
                            groups.add(new ArrayList<>(body.subList(groupStarts[g], groupStarts[g + 1])));
                        }
                        List<Javadoc> suffix = new ArrayList<>(body.subList(lastTagIdx + 1, body.size()));

                        if (isAlreadyOrdered(groups)) {
                            return dc;
                        }

                        Javadoc.LineBreak lineBreakProto = findLineBreak(body);

                        List<List<Javadoc>> sorted = new ArrayList<>(groups);
                        sorted.sort(Comparator.comparingInt(group -> tagRank(blockTagIn(group))));

                        List<Javadoc> newBody = new ArrayList<>(body.subList(0, groupStarts[0]));
                        for (List<Javadoc> group : sorted) {
                            newBody.addAll(ensureGroupHasLeadingBreak(group, lineBreakProto, newBody));
                        }
                        newBody.addAll(suffix);
                        return dc.withBody(newBody);
                    }
                };
            }
        };
    }

    /**
     * If a tag group starts with the tag itself (no leading line break), prepend one when the
     * body so far does not already end with a line break.
     */
    private static List<Javadoc> ensureGroupHasLeadingBreak(List<Javadoc> group, Javadoc.LineBreak proto,
                                                            List<Javadoc> newBody) {
        if (proto == null || group.isEmpty() || newBody.isEmpty()) {
            return group;
        }
        if (!isBlockTag(group.get(0))) {
            return group;
        }
        if (newBody.get(newBody.size() - 1) instanceof Javadoc.LineBreak) {
            // Still need the space text that usually sits between `*` and `@tag`
            List<Javadoc> withSpace = new ArrayList<>(group.size() + 1);
            withSpace.add(new Javadoc.Text(randomId(), Markers.EMPTY, " "));
            withSpace.addAll(group);
            return withSpace;
        }
        List<Javadoc> withBreak = new ArrayList<>(group.size() + 2);
        withBreak.add(new Javadoc.LineBreak(randomId(), proto.getMargin(), Markers.EMPTY));
        withBreak.add(new Javadoc.Text(randomId(), Markers.EMPTY, " "));
        withBreak.addAll(group);
        return withBreak;
    }

    private static Javadoc.LineBreak findLineBreak(List<Javadoc> body) {
        for (Javadoc el : body) {
            if (el instanceof Javadoc.LineBreak) {
                return (Javadoc.LineBreak) el;
            }
        }
        return new Javadoc.LineBreak(randomId(), "\n *", Markers.EMPTY);
    }

    /**
     * For the first tag after a description, only take the immediate line break / space that begins
     * the tag line. Extra blank lines stay with the description prefix.
     */
    private static int leadingWhitespaceStartForFirstTag(List<Javadoc> body, int tagIdx) {
        int start = tagIdx;
        if (start > 0 && isBlankText(body.get(start - 1))) {
            start--;
        }
        if (start > 0 && body.get(start - 1) instanceof Javadoc.LineBreak) {
            start--;
        }
        return start;
    }

    private static boolean isAlreadyOrdered(List<List<Javadoc>> groups) {
        int previousRank = Integer.MIN_VALUE;
        for (List<Javadoc> group : groups) {
            int rank = tagRank(blockTagIn(group));
            if (rank < previousRank) {
                return false;
            }
            previousRank = rank;
        }
        return true;
    }

    private static Javadoc blockTagIn(List<Javadoc> group) {
        for (Javadoc element : group) {
            if (isBlockTag(element)) {
                return element;
            }
        }
        throw new IllegalStateException("Tag group did not contain a block tag");
    }

    private static boolean isLeadingWhitespace(Javadoc element) {
        return element instanceof Javadoc.LineBreak || isBlankText(element);
    }

    private static boolean isBlankText(Javadoc element) {
        return element instanceof Javadoc.Text && ((Javadoc.Text) element).getText().trim().isEmpty();
    }

    /**
     * Rank aligned with Checkstyle {@code AtclauseOrder} default tagOrder.
     * Lower values appear earlier. Unknown tags keep a high rank so they stay at the end,
     * preserving their relative order via a stable sort.
     */
    static int tagRank(Javadoc tag) {
        if (tag instanceof Javadoc.Author) {
            return 0;
        }
        if (tag instanceof Javadoc.Version) {
            return 1;
        }
        if (tag instanceof Javadoc.Parameter) {
            return 2;
        }
        if (tag instanceof Javadoc.Return) {
            return 3;
        }
        if (tag instanceof Javadoc.Throws) {
            // @throws before @exception in Checkstyle's default order
            return ((Javadoc.Throws) tag).isThrowsKeyword() ? 4 : 5;
        }
        if (tag instanceof Javadoc.See) {
            return 6;
        }
        if (tag instanceof Javadoc.Since) {
            return 7;
        }
        if (tag instanceof Javadoc.Serial) {
            return 8;
        }
        if (tag instanceof Javadoc.SerialField) {
            return 9;
        }
        if (tag instanceof Javadoc.SerialData) {
            return 10;
        }
        if (tag instanceof Javadoc.Deprecated) {
            return 11;
        }
        if (tag instanceof Javadoc.Hidden) {
            return 12;
        }
        if (tag instanceof Javadoc.Provides) {
            return 13;
        }
        if (tag instanceof Javadoc.Uses) {
            return 14;
        }
        if (tag instanceof Javadoc.UnknownBlock) {
            return 15;
        }
        if (tag instanceof Javadoc.Erroneous) {
            // Empty/malformed @throws often surfaces as Erroneous; keep near throws.
            return 4;
        }
        return 100;
    }

    static boolean isBlockTag(Javadoc element) {
        return element instanceof Javadoc.Author ||
               element instanceof Javadoc.Deprecated ||
               element instanceof Javadoc.Erroneous ||
               element instanceof Javadoc.Hidden ||
               element instanceof Javadoc.Parameter ||
               element instanceof Javadoc.Provides ||
               element instanceof Javadoc.Return ||
               element instanceof Javadoc.See ||
               element instanceof Javadoc.Serial ||
               element instanceof Javadoc.SerialData ||
               element instanceof Javadoc.SerialField ||
               element instanceof Javadoc.Since ||
               element instanceof Javadoc.Throws ||
               element instanceof Javadoc.UnknownBlock ||
               element instanceof Javadoc.Uses ||
               element instanceof Javadoc.Version;
    }
}
