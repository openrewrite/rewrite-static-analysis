/*
 * Copyright 2024 the original author or authors.
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
import org.jspecify.annotations.Nullable;
import org.openrewrite.ExecutionContext;
import org.openrewrite.Preconditions;
import org.openrewrite.Recipe;
import org.openrewrite.TreeVisitor;
import org.openrewrite.internal.ListUtils;
import org.openrewrite.java.JavaIsoVisitor;
import org.openrewrite.java.search.FindRepeatableAnnotations;
import org.openrewrite.java.tree.Expression;
import org.openrewrite.java.tree.J;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import static java.util.Collections.singleton;
import static java.util.Collections.singletonList;

public class UnwrapRepeatableAnnotations extends Recipe {
    @Getter
    final String displayName = "Unwrap `@Repeatable` annotations";

    @Getter
    final String description = "Java 8 introduced the concept of `@Repeatable` annotations, " +
            "making the wrapper annotation unnecessary. " +
            "Using the repeatable form directly reduces nesting and makes the individual annotations easier to scan.";

    @Getter
    final Set<String> tags = singleton("RSPEC-S1710");

    @Override
    public TreeVisitor<?, ExecutionContext> getVisitor() {
        return Preconditions.check(new FindRepeatableAnnotations(), new JavaIsoVisitor<ExecutionContext>() {
            @Override
            public J.MethodDeclaration visitMethodDeclaration(J.MethodDeclaration method, ExecutionContext ctx) {
                J.MethodDeclaration m = super.visitMethodDeclaration(method, ctx);
                List<J.Annotation> ann = unwrap(m.getLeadingAnnotations());
                if (ann.isEmpty()) {
                    return m;
                }
                return maybeAutoFormat(m, m.withLeadingAnnotations(ann), ann.get(ann.size() - 1), ctx,
                        getCursor().getParentOrThrow());
            }

            @Override
            public J.ClassDeclaration visitClassDeclaration(J.ClassDeclaration classDecl, ExecutionContext ctx) {
                J.ClassDeclaration c = super.visitClassDeclaration(classDecl, ctx);
                List<J.Annotation> ann = unwrap(c.getLeadingAnnotations());
                if (ann.isEmpty()) {
                    return c;
                }
                return maybeAutoFormat(c, c.withLeadingAnnotations(ann), ann.get(ann.size() - 1), ctx,
                        getCursor().getParentOrThrow());
            }

            @Override
            public J.VariableDeclarations visitVariableDeclarations(J.VariableDeclarations multiVariable, ExecutionContext ctx) {
                J.VariableDeclarations v = super.visitVariableDeclarations(multiVariable, ctx);
                List<J.Annotation> ann = unwrap(v.getLeadingAnnotations());
                if (ann.isEmpty()) {
                    return v;
                }
                return maybeAutoFormat(v, v.withLeadingAnnotations(ann), ann.get(ann.size() - 1), ctx,
                        getCursor().getParentOrThrow());
            }

            private List<J.Annotation> unwrap(List<J.Annotation> annotations) {
                return ListUtils.flatMap(annotations, a -> {
                    List<J.Annotation> unwrapped = repeatablesWrappedBy(a);
                    return unwrapped == null ? a : unwrapped;
                });
            }

            /**
             * A container annotation holds nothing but the repeatable annotations it wraps, as in
             * {@code @Annotations({@Annotation, @Annotation})}. An annotation that merely happens to take a
             * repeatable annotation as one of several elements, such as
             * {@code @CollectionTable(name = "T", joinColumns = @JoinColumn(name = "id"))}, is not a container,
             * and replacing it with the annotation nested inside would discard its remaining elements.
             *
             * @param annotation The annotation to inspect.
             * @return The wrapped repeatable annotations, or {@code null} if this is not a container annotation.
             */
            private @Nullable List<J.Annotation> repeatablesWrappedBy(J.Annotation annotation) {
                List<Expression> arguments = annotation.getArguments();
                if (arguments == null || arguments.size() != 1) {
                    return null;
                }
                Expression argument = arguments.get(0);
                if (argument instanceof J.Assignment) {
                    J.Assignment assignment = (J.Assignment) argument;
                    if (!(assignment.getVariable() instanceof J.Identifier) ||
                            !"value".equals(((J.Identifier) assignment.getVariable()).getSimpleName())) {
                        return null;
                    }
                    argument = assignment.getAssignment();
                }
                List<Expression> elements = argument instanceof J.NewArray ?
                        ((J.NewArray) argument).getInitializer() : singletonList(argument);
                if (elements == null || elements.isEmpty()) {
                    return null;
                }
                List<J.Annotation> repeatables = new ArrayList<>(elements.size());
                for (Expression element : elements) {
                    if (!(element instanceof J.Annotation) ||
                            !FindRepeatableAnnotations.isRepeatable(element.getType())) {
                        return null;
                    }
                    repeatables.add((J.Annotation) element);
                }
                return repeatables;
            }
        });
    }
}
