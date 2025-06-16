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

import lombok.EqualsAndHashCode;
import lombok.Value;
import org.openrewrite.ExecutionContext;
import org.openrewrite.Recipe;
import org.openrewrite.internal.ListUtils;
import org.openrewrite.java.JavaIsoVisitor;
import org.openrewrite.java.tree.J;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

@Value
@EqualsAndHashCode(callSuper = false)
public class ReorderAnnotations extends Recipe {

    @Override
    public String getDisplayName() {
        return "Reorder annotations alphabetically";
    }

    @Override
    public String getDescription() {
        return "Consistently order annotations by comparing their simple name.";
    }

    private static final Comparator<J.Annotation> comparator = Comparator.comparing(J.Annotation::getSimpleName);

    @Override
    public JavaIsoVisitor<ExecutionContext> getVisitor() {
        return new JavaIsoVisitor<ExecutionContext>() {
            @Override
            public J.ClassDeclaration visitClassDeclaration(J.ClassDeclaration classDecl, ExecutionContext ctx) {
                J.ClassDeclaration cd = super.visitClassDeclaration(classDecl, ctx);
                if (!cd.getLeadingAnnotations().isEmpty()) {
                    List<J.Annotation> sortedAnnotations = new ArrayList<>(cd.getLeadingAnnotations());
                    sortedAnnotations.sort(comparator);
                    if (!sortedAnnotations.equals(cd.getLeadingAnnotations())) {
                        return cd.withLeadingAnnotations(ListUtils.map(sortedAnnotations,
                                (i, a) -> a.withPrefix(cd.getLeadingAnnotations().get(i).getPrefix())));
                    }
                }
                return cd;
            }

            @Override
            public J.VariableDeclarations visitVariableDeclarations(J.VariableDeclarations multiVariable, ExecutionContext ctx) {
                J.VariableDeclarations fd = super.visitVariableDeclarations(multiVariable, ctx);
                if (!fd.getLeadingAnnotations().isEmpty()) {
                    List<J.Annotation> sortedAnnotations = new ArrayList<>(fd.getLeadingAnnotations());
                    sortedAnnotations.sort(comparator);
                    if (!sortedAnnotations.equals(fd.getLeadingAnnotations())) {
                        return fd.withLeadingAnnotations(ListUtils.map(sortedAnnotations,
                                (i, a) -> a.withPrefix(fd.getLeadingAnnotations().get(i).getPrefix())));
                    }
                }
                return fd;
            }

            @Override
            public J.MethodDeclaration visitMethodDeclaration(J.MethodDeclaration method, ExecutionContext ctx) {
                J.MethodDeclaration md = super.visitMethodDeclaration(method, ctx);
                if (!md.getLeadingAnnotations().isEmpty()) {
                    List<J.Annotation> sortedAnnotations = new ArrayList<>(md.getLeadingAnnotations());
                    sortedAnnotations.sort(comparator);
                    if (!sortedAnnotations.equals(md.getLeadingAnnotations())) {
                        return md.withLeadingAnnotations(ListUtils.map(sortedAnnotations,
                                (i, a) -> a.withPrefix(md.getLeadingAnnotations().get(i).getPrefix())));
                    }
                }
                return md;
            }
        };
    }
}
