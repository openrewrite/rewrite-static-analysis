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

import org.openrewrite.ExecutionContext;
import org.openrewrite.Recipe;
import org.openrewrite.TreeVisitor;
import org.openrewrite.internal.StringUtils;
import org.openrewrite.java.AnnotationMatcher;
import org.openrewrite.java.JavaVisitor;
import org.openrewrite.java.JavadocVisitor;
import org.openrewrite.java.tree.Javadoc;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

public class RemoveEmptyDeprecatedJavadoc extends Recipe {

    private static final AnnotationMatcher DEPRECATED_ANNOTATION = new AnnotationMatcher("@java.lang.Deprecated");

    @Override
    public String getDisplayName() {
        return "Remove empty `@deprecated` javadoc when `@Deprecated` annotation is present";
    }

    @Override
    public String getDescription() {
        return "Removes empty `@deprecated` javadoc tags from classes and methods when the `@Deprecated` annotation is present. " +
                "The javadoc tag is only useful when providing a description explaining why the element was deprecated or suggesting an alternative.";
    }

    @Override
    public Duration getEstimatedEffortPerOccurrence() {
        return Duration.ofMinutes(2);
    }

    @Override
    public TreeVisitor<?, ExecutionContext> getVisitor() {
        return new JavaVisitor<ExecutionContext>() {
            @Override
            protected JavadocVisitor<ExecutionContext> getJavadocVisitor() {
                return new JavadocVisitor<ExecutionContext>(this) {
                    @Override
                    public Javadoc visitDocComment(Javadoc.DocComment javadoc, ExecutionContext ctx) {
                        // Check if the parent element has @Deprecated annotation
                        if (!hasDeprecatedAnnotation()) {
                            return javadoc;
                        }

                        Javadoc.DocComment dc = (Javadoc.DocComment) super.visitDocComment(javadoc, ctx);
                        List<Javadoc> newBody = new ArrayList<>();
                        boolean isChanged = false;

                        List<Javadoc> body = new ArrayList<>(javadoc.getBody());
                        // Add a trailing null element for look-ahead processing
                        body.add(0, null);

                        for (int i = 0; i < body.size(); i++) {
                            Javadoc currentDoc = body.get(i);
                            if (i + 1 < body.size()) {
                                Javadoc nextDoc = body.get(i + 1);
                                if (nextDoc instanceof Javadoc.Deprecated) {
                                    Javadoc.Deprecated deprecatedTag = (Javadoc.Deprecated) nextDoc;
                                    if (isEmptyDeprecated(deprecatedTag)) {
                                        // The @deprecated being removed is the last item in the JavaDoc body
                                        if (i + 1 == body.size() - 1) {
                                            // If we have a previous LineBreak we need to remove it before adding the new one
                                            if (!newBody.isEmpty() && newBody.get(newBody.size() - 1) instanceof Javadoc.LineBreak) {
                                                newBody.remove(newBody.size() - 1);
                                            }
                                            if (!deprecatedTag.getDescription().isEmpty()) {
                                                newBody.add(deprecatedTag.getDescription().get(0));
                                            }
                                        }

                                        // Skip the next element (the deprecated tag)
                                        i += 1;
                                        isChanged = true;
                                        currentDoc = null;
                                    }
                                }
                            }

                            if (currentDoc != null) {
                                newBody.add(currentDoc);
                            }
                        }

                        if (isChanged) {
                            trim(newBody);
                            if (newBody.isEmpty() || RemoveJavaDocAuthorTag.isBlank(getCursor(), newBody)) {
                                return null;
                            }
                            dc = dc.withBody(newBody);
                        }
                        return dc;
                    }

                    private void trim(List<Javadoc> body) {
                        Javadoc currentDoc;
                        Javadoc.LineBreak firstLineBreak = null;
                        while (!body.isEmpty()) {
                            currentDoc = body.get(body.size() - 1);
                            boolean isLineBreak = currentDoc instanceof Javadoc.LineBreak;
                            if (isLineBreak && firstLineBreak == null) {
                                firstLineBreak = (Javadoc.LineBreak) currentDoc;
                            }
                            boolean isEmptyText = false;
                            if (currentDoc instanceof Javadoc.Text) {
                                String currentText = ((Javadoc.Text) currentDoc).getText().trim();
                                isEmptyText = StringUtils.isBlank(currentText);
                            }
                            if (!isLineBreak && !isEmptyText) {
                                break;
                            }
                            body.remove(body.size() - 1);
                        }
                        if (!body.isEmpty() && firstLineBreak != null) {
                            // ensure proper "ending" of JavaDoc including OS-specific newlines
                            String margin = firstLineBreak.getMargin();
                            if (margin.endsWith("*")) {
                                firstLineBreak = firstLineBreak.withMargin(margin.substring(0, margin.length() - 1));
                            }
                            body.add(firstLineBreak);
                        }
                    }

                    private boolean hasDeprecatedAnnotation() {
                        // Navigate up to find the class or method declaration
                        org.openrewrite.Cursor cursor = getCursor().getParentTreeCursor();
                        while (cursor != null) {
                            Object value = cursor.getValue();
                            if (value instanceof org.openrewrite.java.tree.J.ClassDeclaration) {
                                org.openrewrite.java.tree.J.ClassDeclaration cd = (org.openrewrite.java.tree.J.ClassDeclaration) value;
                                return cd.getLeadingAnnotations().stream().anyMatch(DEPRECATED_ANNOTATION::matches);
                            } else if (value instanceof org.openrewrite.java.tree.J.MethodDeclaration) {
                                org.openrewrite.java.tree.J.MethodDeclaration md = (org.openrewrite.java.tree.J.MethodDeclaration) value;
                                return md.getLeadingAnnotations().stream().anyMatch(DEPRECATED_ANNOTATION::matches);
                            } else if (value instanceof org.openrewrite.java.tree.J.VariableDeclarations) {
                                org.openrewrite.java.tree.J.VariableDeclarations vd = (org.openrewrite.java.tree.J.VariableDeclarations) value;
                                return vd.getLeadingAnnotations().stream().anyMatch(DEPRECATED_ANNOTATION::matches);
                            }
                            cursor = cursor.getParentTreeCursor();
                        }
                        return false;
                    }

                    private boolean isEmptyDeprecated(Javadoc.Deprecated deprecated) {
                        // Check if the deprecated tag has no description or only whitespace
                        return deprecated.getDescription().stream()
                                .allMatch(jd -> {
                                    if (jd instanceof Javadoc.LineBreak) {
                                        return true;
                                    }
                                    if (jd instanceof Javadoc.Text) {
                                        String text = ((Javadoc.Text) jd).getText().trim();
                                        return StringUtils.isBlank(text);
                                    }
                                    return false;
                                });
                    }
                };
            }
        };
    }
}
