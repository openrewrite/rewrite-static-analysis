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
import org.openrewrite.java.tree.JavaType;
import org.openrewrite.java.tree.TypeUtils;

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

    private static final Comparator<J.Annotation> comparator = Comparator
            .comparing((J.Annotation a) -> {
                // If the annotation is a type use annotation, it should be ordered last
                if (a.getType() instanceof JavaType.Class) {
                    for (JavaType.FullyQualified fq : ((JavaType.Class) a.getType()).getAnnotations()) {
                        if (TypeUtils.isOfClassType(fq, "java.lang.annotation.Target")) {
                            for (JavaType.Annotation.ElementValue elementValue : ((JavaType.Annotation) fq).getValues()) {
                                Object value = elementValue.getValue();
                                if (value instanceof List) {
                                    for (Object item : (List<?>) value) {
                                        if (item instanceof JavaType.Variable &&
                                                "TYPE_USE".equals(((JavaType.Variable) item).getName())) {
                                            return 1;
                                        }
                                    }
                                } else if (value instanceof JavaType.Variable &&
                                        "TYPE_USE".equals(((JavaType.Variable) value).getName())) {
                                    return 1;
                                }
                            }
                        }
                    }
                }
                return 0;
            })
            .thenComparing(J.Annotation::getSimpleName);

    @Override
    public JavaIsoVisitor<ExecutionContext> getVisitor() {
        return new JavaIsoVisitor<ExecutionContext>() {
            @Override
            public J.ClassDeclaration visitClassDeclaration(J.ClassDeclaration classDecl, ExecutionContext ctx) {
                J.ClassDeclaration d = super.visitClassDeclaration(classDecl, ctx);
                if (1 < d.getLeadingAnnotations().size()) {
                    List<J.Annotation> sortedAnnotations = new ArrayList<>(d.getLeadingAnnotations());
                    sortedAnnotations.sort(comparator);
                    if (!sortedAnnotations.equals(d.getLeadingAnnotations())) {
                        return d.withLeadingAnnotations(ListUtils.map(sortedAnnotations,
                                (i, a) -> a.withPrefix(d.getLeadingAnnotations().get(i).getPrefix())));
                    }
                }
                return d;
            }

            @Override
            public J.VariableDeclarations visitVariableDeclarations(J.VariableDeclarations multiVariable, ExecutionContext ctx) {
                J.VariableDeclarations d = super.visitVariableDeclarations(multiVariable, ctx);
                if (1 < d.getLeadingAnnotations().size()) {
                    List<J.Annotation> sortedAnnotations = new ArrayList<>(d.getLeadingAnnotations());
                    sortedAnnotations.sort(comparator);
                    if (!sortedAnnotations.equals(d.getLeadingAnnotations())) {
                        return d.withLeadingAnnotations(ListUtils.map(sortedAnnotations,
                                (i, a) -> a.withPrefix(d.getLeadingAnnotations().get(i).getPrefix())));
                    }
                }
                return d;
            }

            @Override
            public J.MethodDeclaration visitMethodDeclaration(J.MethodDeclaration method, ExecutionContext ctx) {
                J.MethodDeclaration d = super.visitMethodDeclaration(method, ctx);
                if (1 < d.getLeadingAnnotations().size()) {
                    List<J.Annotation> sortedAnnotations = new ArrayList<>(d.getLeadingAnnotations());
                    sortedAnnotations.sort(comparator);
                    if (!sortedAnnotations.equals(d.getLeadingAnnotations())) {
                        return d.withLeadingAnnotations(ListUtils.map(sortedAnnotations,
                                (i, a) -> a.withPrefix(d.getLeadingAnnotations().get(i).getPrefix())));
                    }
                }
                return d;
            }
        };
    }
}
