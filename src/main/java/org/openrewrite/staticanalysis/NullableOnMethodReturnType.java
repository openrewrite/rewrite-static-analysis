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

import org.openrewrite.ExecutionContext;
import org.openrewrite.Recipe;
import org.openrewrite.Tree;
import org.openrewrite.TreeVisitor;
import org.openrewrite.internal.ListUtils;
import org.openrewrite.java.JavaIsoVisitor;
import org.openrewrite.java.tree.J;
import org.openrewrite.java.tree.JavaType;
import org.openrewrite.java.tree.NameTree;
import org.openrewrite.java.tree.Space;
import org.openrewrite.marker.Markers;

import java.util.Collections;

import static java.util.Objects.requireNonNull;
import static org.openrewrite.java.trait.Traits.annotated;

public class NullableOnMethodReturnType extends Recipe {

    @Override
    public String getDisplayName() {
        return "Move `@Nullable` method annotations to the return type";
    }

    @Override
    public String getDescription() {
        return "This is the way the cool kids do it.";
    }

    @Override
    public TreeVisitor<?, ExecutionContext> getVisitor() {
        return new JavaIsoVisitor<ExecutionContext>() {
            @Override
            public J.MethodDeclaration visitMethodDeclaration(J.MethodDeclaration method, ExecutionContext ctx) {
                J.MethodDeclaration m = super.visitMethodDeclaration(method, ctx);
                return requireNonNull(annotated("*..Nullable")
                        .lower(getCursor())
                        .findFirst()
                        .map(nullable -> {
                            if (nullable.getCursor().getParentTreeCursor().getValue() != m) {
                                return m;
                            }
                            if (!m.getLeadingAnnotations().isEmpty()) {
                                for (J.Annotation a : m.getLeadingAnnotations()) {
                                    JavaType annotationType = a.getAnnotationType().getType();
                                    if (annotationType instanceof JavaType.FullyQualified && ((JavaType.FullyQualified) annotationType).getFullyQualifiedName().equals("org.jspecify.annotations.Nullable")) {
                                        return m;
                                    }
                                }
                            }
                            J.MethodDeclaration m2 = m;
                            m2 = m2.withLeadingAnnotations(ListUtils.map(m2.getLeadingAnnotations(),
                                    a -> a == nullable.getTree() ? null : a));
                            if (m2 != m) {
                                m2 = m2.withReturnTypeExpression(new J.AnnotatedType(
                                        Tree.randomId(),
                                        Space.SINGLE_SPACE,
                                        Markers.EMPTY,
                                        Collections.singletonList(nullable.getTree().withPrefix(Space.EMPTY)),
                                        m2.getReturnTypeExpression()
                                ));
                                m2 = autoFormat(m2, m2.getReturnTypeExpression(), ctx, getCursor().getParentOrThrow());
                                m2 = m2.withPrefix(m2.getPrefix().withWhitespace(m2.getPrefix().getWhitespace().replace("\n\n\n", "\n\n")));
                            }
                            return m2;
                        })
                        .orElse(m));
            }
        };
    }
}
