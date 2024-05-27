/*
 * Copyright 2022 the original author or authors.
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

import org.openrewrite.ExecutionContext;
import org.openrewrite.Incubating;
import org.openrewrite.Recipe;
import org.openrewrite.TreeVisitor;
import org.openrewrite.internal.ListUtils;
import org.openrewrite.java.JavaIsoVisitor;
import org.openrewrite.java.JavadocVisitor;
import org.openrewrite.java.tree.Comment;
import org.openrewrite.java.tree.J;
import org.openrewrite.java.tree.Javadoc;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

@Incubating(since = "7.33.0")
public class RemoveEmptyJavaDocParameters extends Recipe {

    @Override
    public String getDisplayName() {
        return "Remove JavaDoc `@param`, `@return`, and `@throws` with no description";
    }

    @Override
    public String getDescription() {
        return "Removes `@param`, `@return`, and `@throws` with no description from JavaDocs.";
    }

    @Override
    public Duration getEstimatedEffortPerOccurrence() {
        return Duration.ofMinutes(2);
    }

    @Override
    public TreeVisitor<?, ExecutionContext> getVisitor() {
        return new JavaIsoVisitor<ExecutionContext>() {
            final RemoveEmptyParamVisitor removeEmptyParamVisitor = new RemoveEmptyParamVisitor();

            @Override
            public J.MethodDeclaration visitMethodDeclaration(J.MethodDeclaration method, ExecutionContext ctx) {
                J.MethodDeclaration md = super.visitMethodDeclaration(method, ctx);
                if (md.getComments().stream().anyMatch(Javadoc.DocComment.class::isInstance)) {
                    md = md.withComments(ListUtils.map(md.getComments(), it -> {
                        if (it instanceof Javadoc.DocComment) {
                            Javadoc.DocComment docComment = (Javadoc.DocComment) it;
                            return (Comment) removeEmptyParamVisitor.visitDocComment(docComment, ctx);
                        }
                        return it;
                    }));
                }
                return md;
            }

            class RemoveEmptyParamVisitor extends JavadocVisitor<ExecutionContext> {
                public RemoveEmptyParamVisitor() {
                    super(new JavaIsoVisitor<>());
                }

                @Override
                public Javadoc visitDocComment(Javadoc.DocComment javadoc, ExecutionContext ctx) {
                    List<Javadoc> newBody = new ArrayList<>(javadoc.getBody().size());
                    boolean useNewBody = false;

                    List<Javadoc> body = new ArrayList<>(javadoc.getBody());
                    // We add a trailing element, to fix elements on the first line without space
                    // We can use null since this element is never going to be read, and nulls get filtered later on.
                    body.add(0, null);

                    for (int i = 0; i < body.size(); i++) {
                        // JavaDocs require a look ahead, because the current element may be an element that exists on the same line as a parameter.
                        // I.E. the space that precedes `* @param` will be a `Javadoc.Text` and needs to be removed along with the empty `@param`.
                        // A `Javadoc` will always precede a parameter even if there is empty space like `*@param`.
                        Javadoc currentDoc = body.get(i);
                        if (i + 1 < body.size()) {
                            Javadoc nextDoc = body.get(i + 1);
                            if (nextDoc instanceof Javadoc.Parameter) {
                                Javadoc.Parameter nextParameter = (Javadoc.Parameter) nextDoc;
                                if (isEmptyParameter(nextParameter)) {
                                    // The `@param` being removed is the last item in the JavaDoc body, and contains
                                    // relevant whitespace via the JavaDoc.LineBreak.
                                    if (i + 1 == body.size() - 1) {
                                        // If we have a previous LineBreak we need to remove it before adding the new one
                                        if (!newBody.isEmpty() && newBody.get(newBody.size() - 1) instanceof Javadoc.LineBreak) {
                                            newBody.remove(newBody.size() - 1);
                                        }
                                        if (!nextParameter.getDescription().isEmpty()) {
                                            newBody.add(nextParameter.getDescription().get(0));
                                        }
                                    }

                                    // No need to reprocess the next element.
                                    i += 1;

                                    useNewBody = true;
                                    currentDoc = null;
                                }
                            } else if (nextDoc instanceof Javadoc.Return) {
                                Javadoc.Return nextReturn = (Javadoc.Return) nextDoc;
                                if (isEmptyReturn(nextReturn)) {
                                    // The `@return` being removed is the last item in the JavaDoc body, and contains
                                    // relevant whitespace via the JavaDoc.LineBreak.
                                    if (i + 1 == body.size() - 1) {
                                        // If we have a previous LineBreak we need to remove it before adding the new one
                                        if (!newBody.isEmpty() && newBody.get(newBody.size() - 1) instanceof Javadoc.LineBreak) {
                                            newBody.remove(newBody.size() - 1);
                                        }
                                        if (!nextReturn.getDescription().isEmpty()) {
                                            newBody.add(nextReturn.getDescription().get(0));
                                        }
                                    }

                                    // No need to reprocess the next element.
                                    i += 1;

                                    useNewBody = true;
                                    currentDoc = null;
                                }
                            } else if (nextDoc instanceof Javadoc.Erroneous) {
                                Javadoc.Erroneous nextErroneous = (Javadoc.Erroneous) nextDoc;
                                if (isEmptyErroneous(nextErroneous)) {
                                    if (!newBody.isEmpty() && newBody.get(newBody.size() - 1) instanceof Javadoc.LineBreak) {
                                        newBody.remove(newBody.size() - 1);
                                    }

                                    // No need to reprocess the next element.
                                    i += 1;

                                    useNewBody = true;
                                    currentDoc = null;
                                }
                            }
                        }

                        if (currentDoc != null) {
                            newBody.add(currentDoc);
                        }
                    }

                    if (useNewBody) {
                        if (RemoveJavaDocAuthorTag.isBlank(getCursor(), newBody)) {
                            return null;
                        }
                        javadoc = javadoc.withBody(newBody);
                    }
                    // No need to call super visitor, already covered all cases by adding an empty first element when needed.
                    return javadoc;
                }

                public boolean isEmptyParameter(Javadoc.Parameter parameter) {
                    return parameter.getDescription().stream().allMatch(Javadoc.LineBreak.class::isInstance);
                }

                public boolean isEmptyReturn(Javadoc.Return aReturn) {
                    return aReturn.getDescription().stream().allMatch(Javadoc.LineBreak.class::isInstance);
                }

                public boolean isEmptyErroneous(Javadoc.Erroneous erroneous) {
                    // Empty throws result in an Erroneous type.
                    return erroneous.getText().size() == 1 &&
                           erroneous.getText().get(0) instanceof Javadoc.Text &&
                           "@throws".equals(((Javadoc.Text) erroneous.getText().get(0)).getText());
                }
            }
        };
    }
}
