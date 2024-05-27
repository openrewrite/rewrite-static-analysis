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
import org.openrewrite.internal.StringUtils;
import org.openrewrite.java.JavaVisitor;
import org.openrewrite.java.JavadocVisitor;
import org.openrewrite.java.tree.Javadoc;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class RemoveJavaDocAuthorTag extends Recipe {

    @Override
    public String getDisplayName() {
        return "Remove author tags from JavaDocs";
    }

    @Override
    public String getDescription() {
        return "Removes author tags from JavaDocs to reduce code maintenance.";
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
                        Javadoc.DocComment dc = (Javadoc.DocComment) super.visitDocComment(javadoc, ctx);
                        List<Javadoc> newBody = new ArrayList<>();
                        boolean isChanged = false;
                        boolean removeNextLineBreak = false;
                        for (int i = javadoc.getBody().size() - 1; i >= 0; i--) {
                            Javadoc doc = javadoc.getBody().get(i);
                            if (removeNextLineBreak) {
                                if (doc instanceof Javadoc.LineBreak) {
                                    removeNextLineBreak = false;
                                }
                            } else if (doc instanceof Javadoc.Author) {
                                isChanged = true;
                                removeNextLineBreak = true;
                            } else {
                                newBody.add(doc);
                            }
                        }

                        if (isChanged) {
                            if (isBlank(getCursor(), newBody)) {
                                return null;
                            }
                            Collections.reverse(newBody);
                            dc = dc.withBody(newBody);
                        }
                        return dc;
                    }
                };
            }
        };
    }

    static boolean isBlank(Cursor cursor, List<Javadoc> newBody) {
        return newBody.stream().allMatch(jd -> {
            PrintOutputCapture<Object> p = new PrintOutputCapture<>(null);
            jd.printer(cursor).visit(jd, p);
            String currentLine = p.getOut().trim();
            return StringUtils.isBlank(currentLine) || "*".equals(currentLine);
        });
    }
}
