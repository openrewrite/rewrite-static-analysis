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
import org.openrewrite.java.JavaIsoVisitor;
import org.openrewrite.internal.ListUtils;
import org.openrewrite.java.JavadocVisitor;
import org.openrewrite.java.tree.Javadoc;
import org.openrewrite.marker.Markers;

import java.time.Duration;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

import static org.openrewrite.Tree.randomId;

public class JavadocParagraph extends Recipe {

    /**
     * HTML elements that render as their own block; a {@code <p>} must not be injected before them,
     * nor should paragraph detection run inside them (notably {@code <pre>}, which is verbatim).
     */
    private static final Set<String> BLOCK_LEVEL_HTML = new HashSet<>(Arrays.asList(
            "address", "article", "aside", "blockquote", "dd", "details", "div", "dl", "dt",
            "fieldset", "figcaption", "figure", "footer", "form", "h1", "h2", "h3", "h4", "h5",
            "h6", "header", "hr", "li", "main", "nav", "ol", "p", "pre", "section", "table",
            "tbody", "td", "tfoot", "th", "thead", "tr", "ul"));

    @Getter
    final String displayName = "Add missing Javadoc paragraph tags";

    @Getter
    final String description = "Adds missing `<p>` tags to prose paragraphs separated by blank lines " +
            "in Javadocs.";

    @Getter
    final Duration estimatedEffortPerOccurrence = Duration.ofMinutes(1);

    @Override
    public TreeVisitor<?, ExecutionContext> getVisitor() {
        return new JavaIsoVisitor<ExecutionContext>() {
            @Override
            protected JavadocVisitor<ExecutionContext> getJavadocVisitor() {
                return new JavadocVisitor<ExecutionContext>(this) {
                    private boolean seenParagraphContent;
                    private boolean inBlockTags;
                    private boolean inPre;
                    private int consecutiveLineBreaks;

                    @Override
                    public Javadoc visitDocComment(Javadoc.DocComment javadoc, ExecutionContext ctx) {
                        Javadoc.DocComment docComment =
                                (Javadoc.DocComment) super.visitDocComment(javadoc, ctx);
                        seenParagraphContent = false;
                        inBlockTags = false;
                        inPre = false;
                        consecutiveLineBreaks = 0;
                        return docComment.withBody(ListUtils.flatMap(docComment.getBody(), element -> {
                            if (inBlockTags) {
                                return element;
                            }
                            // Content inside a <pre> block is verbatim: pass it through untouched and
                            // do not count its blank lines as paragraph boundaries.
                            if (inPre) {
                                if (element instanceof Javadoc.EndElement &&
                                    "pre".equalsIgnoreCase(((Javadoc.EndElement) element).getName())) {
                                    inPre = false;
                                    seenParagraphContent = true;
                                    consecutiveLineBreaks = 0;
                                }
                                return element;
                            }
                            if (isBlockTag(element)) {
                                inBlockTags = true;
                                return element;
                            }
                            if (element instanceof Javadoc.LineBreak) {
                                consecutiveLineBreaks++;
                                return element;
                            }
                            if (element instanceof Javadoc.Text &&
                                ((Javadoc.Text) element).getText().trim().isEmpty()) {
                                return element;
                            }

                            // Block-level HTML elements render as their own block, so no <p> is added
                            // before them (e.g. avoid producing "<p><pre>" or "<p><ul>").
                            boolean paragraphBoundary = seenParagraphContent && consecutiveLineBreaks >= 2;
                            Object mapped = element;
                            if (paragraphBoundary && !isParagraphTag(element) && !isBlockLevelHtml(element)) {
                                mapped = element instanceof Javadoc.Text ?
                                        addParagraphTag((Javadoc.Text) element) :
                                        Arrays.asList(new Javadoc.Text(randomId(), Markers.EMPTY, "<p>"), element);
                            }

                            seenParagraphContent = true;
                            consecutiveLineBreaks = 0;
                            if (element instanceof Javadoc.StartElement &&
                                "pre".equalsIgnoreCase(((Javadoc.StartElement) element).getName())) {
                                inPre = true;
                            }
                            return mapped;
                        }));
                    }

                    private Javadoc.Text addParagraphTag(Javadoc.Text text) {
                        String value = text.getText();
                        int contentStart = 0;
                        while (contentStart < value.length() &&
                               Character.isWhitespace(value.charAt(contentStart))) {
                            contentStart++;
                        }
                        return text.withText(value.substring(0, contentStart) + "<p>" +
                                             value.substring(contentStart));
                    }

                    private boolean isParagraphTag(Javadoc element) {
                        if (element instanceof Javadoc.StartElement) {
                            return "p".equalsIgnoreCase(((Javadoc.StartElement) element).getName());
                        }
                        if (element instanceof Javadoc.Text) {
                            String text = ((Javadoc.Text) element).getText().trim();
                            return text.length() >= 3 && text.regionMatches(true, 0, "<p>", 0, 3);
                        }
                        return false;
                    }

                    private boolean isBlockLevelHtml(Javadoc element) {
                        return element instanceof Javadoc.StartElement &&
                               BLOCK_LEVEL_HTML.contains(((Javadoc.StartElement) element).getName().toLowerCase());
                    }

                    private boolean isBlockTag(Javadoc element) {
                        return element instanceof Javadoc.Author ||
                               element instanceof Javadoc.Deprecated ||
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
                };
            }
        };
    }
}
