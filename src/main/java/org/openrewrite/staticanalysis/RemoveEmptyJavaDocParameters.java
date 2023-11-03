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

import org.openrewrite.*;
import org.openrewrite.internal.ListUtils;
import org.openrewrite.internal.StringUtils;
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
                    boolean removeTrailingLineBreaks = false;

                    List<Javadoc> body = javadoc.getBody();

                    for (int i = 0; i < body.size(); i++) {
                        Javadoc currentDoc = body.get(i);

                        if (removeTrailingLineBreaks && i != body.size() - 1) {
                            if (currentDoc instanceof Javadoc.LineBreak && !(body.get(i + 1) instanceof Javadoc.LineBreak)) {
                                removeTrailingLineBreaks = false;
                            } else {
                                continue;
                            }
                        }

                        if (currentDoc instanceof Javadoc.Parameter) {
                            Javadoc.Parameter parameter = (Javadoc.Parameter) currentDoc;
                            if (isEmptyParameter(parameter)) {
                                removeSpaceAndLinebreaks(newBody);
                                // Add last LineBreak which may be '\n    ' or '\n    *' fixes '**/' at the end of docs after removal of empty @param
                                List<Javadoc> descriptions = ((Javadoc.Parameter) body.get(i)).getDescription();
                                if (!descriptions.isEmpty()) {
                                    newBody.add(descriptions.get(descriptions.size() - 1));
                                }

                                useNewBody = true;
                            } else {
                                visitParameter(parameter, ctx);
                                newBody.add(currentDoc);
                            }
                        } else if (currentDoc instanceof Javadoc.Return) {
                            Javadoc.Return aReturn = (Javadoc.Return) currentDoc;
                            if (isEmptyReturn(aReturn)) {
                                removeSpaceAndLinebreaks(newBody);
                                // Add last LineBreak which may be '\n    ' or '\n    *' fixes '**/' at the end of docs after removal of empty @return
                                List<Javadoc> descriptions = ((Javadoc.Return) body.get(i)).getDescription();
                                if (!descriptions.isEmpty()) {
                                    newBody.add(descriptions.get(descriptions.size() - 1));
                                }

                                useNewBody = true;
                            } else {
                                visitReturn(aReturn, ctx);
                                newBody.add(currentDoc);
                            }
                        } else if (currentDoc instanceof Javadoc.Throws) {
                            Javadoc.Throws aThrows = (Javadoc.Throws) currentDoc;
                            if (isEmptyThrows(aThrows)) {
                                removeSpaceAndLinebreaks(newBody);
                                // Add last LineBreak which may be '\n    ' or '\n    *' fixes '**/' at the end of docs after removal of empty @throws
                                List<Javadoc> descriptions = ((Javadoc.Throws) body.get(i)).getDescription();
                                if (!descriptions.isEmpty()) {
                                    newBody.add(descriptions.get(descriptions.size() - 1));
                                }

                                useNewBody = true;
                            } else {
                                visitThrows(aThrows, ctx);
                                newBody.add(currentDoc);
                            }
                        } else if (currentDoc instanceof Javadoc.Erroneous) {
                            Javadoc.Erroneous erroneous = (Javadoc.Erroneous) currentDoc;
                            if (isEmptyErroneous(erroneous)) {
                                removeSpaceAndLinebreaks(newBody);

                                useNewBody = true;
                                // Erroneous doesn't have list of descriptions, so have to skip trailing LineBreaks manually.
                                removeTrailingLineBreaks = true;
                            } else {
                                visitErroneous(erroneous, ctx);
                                newBody.add(currentDoc);
                            }
                        } else {
                            newBody.add(currentDoc);
                        }
                    }

                    // If after "cleaning" JavaDocs is empty remove it.
                    if (isWholeDescriptionBlank(getCursor(), newBody)) {
                        return null;
                    }

                    if (useNewBody) {
                        javadoc = javadoc.withBody(newBody);
                    }
                    return super.visitDocComment(javadoc, ctx);
                }


                public boolean isEmptyParameter(Javadoc.Parameter parameter) {
                    return parameter.getDescription().stream().allMatch(Javadoc.LineBreak.class::isInstance);
                }

                public boolean isEmptyReturn(Javadoc.Return aReturn) {
                    return aReturn.getDescription().stream().allMatch(Javadoc.LineBreak.class::isInstance);
                }

                public boolean isEmptyThrows(Javadoc.Throws aThrows) {
                    return aThrows.getDescription().stream().allMatch(Javadoc.LineBreak.class::isInstance);
                }

                public boolean isEmptyErroneous(Javadoc.Erroneous erroneous) {
                    if (erroneous.getText().size() == 1 && erroneous.getText().get(0) instanceof Javadoc.Text) {
                        Javadoc.Text text = (Javadoc.Text) erroneous.getText().get(0);
                        // Empty param/return/throws result in an Erroneous type.
                        return "@param".equals(text.getText()) || "@return".equals(text.getText()) || "@throws".equals(text.getText());
                    }
                    return false;
                }
            }
        };
    }

    private boolean isWholeDescriptionBlank(Cursor cursor, List<Javadoc> newBody) {
        return newBody.stream().allMatch(jd -> {
            PrintOutputCapture<Object> p = new PrintOutputCapture<>(null);
            jd.printer(cursor).visit(jd, p);
            return StringUtils.isBlank(p.getOut());
        });
    }

    private void removeSpaceAndLinebreaks(List<Javadoc> newBody) {
        // The space that precedes `* @param`/`* @return`/`* @throws` will be a `Javadoc.Text` and needs to be removed along with the empty `@param`/`@return`/`@throws`.
        if (!newBody.isEmpty() && newBody.get(newBody.size() - 1) instanceof Javadoc.Text) {
            if (StringUtils.isBlank(((Javadoc.Text) newBody.get(newBody.size() - 1)).getText())) {
                newBody.remove(newBody.size() - 1);
            }
        }
        // Last added LineBreak which is in the same line as currentDoc.
        if (!newBody.isEmpty() && newBody.get(newBody.size() - 1) instanceof Javadoc.LineBreak) {
            newBody.remove(newBody.size() - 1);
        }
    }
}