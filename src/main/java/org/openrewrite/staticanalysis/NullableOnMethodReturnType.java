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
import org.openrewrite.*;
import org.openrewrite.internal.ListUtils;
import org.openrewrite.java.JavaIsoVisitor;
import org.openrewrite.java.search.UsesType;
import org.openrewrite.java.trait.Annotated;
import org.openrewrite.java.tree.J;
import org.openrewrite.java.tree.Space;
import org.openrewrite.marker.Markers;

import static java.util.Collections.singletonList;
import static java.util.Objects.requireNonNull;

public class NullableOnMethodReturnType extends Recipe {

    @Getter
    final String displayName = "Move `@Nullable` method annotations to the return type";

    @Getter
    final String description = "This is the way the cool kids do it.";

    @Override
    public TreeVisitor<?, ExecutionContext> getVisitor() {
        JavaIsoVisitor<ExecutionContext> visitor = new JavaIsoVisitor<ExecutionContext>() {
            @Override
            public J.MethodDeclaration visitMethodDeclaration(J.MethodDeclaration method, ExecutionContext ctx) {
                J.MethodDeclaration m = super.visitMethodDeclaration(method, ctx);
                // For package-private methods, the annotation is on the method, not the return type
                if (m.getModifiers().isEmpty()) {
                    return m;
                }
                return requireNonNull(new Annotated.Matcher("*..Nullable")
                        .lower(getCursor())
                        .findFirst()
                        .map(nullable -> {
                            if (nullable.getCursor().getParentTreeCursor().getValue() != m) {
                                return m;
                            }
                            J.MethodDeclaration m2 = m;
                            m2 = m2.withLeadingAnnotations(ListUtils.map(m2.getLeadingAnnotations(),
                                    a -> a == nullable.getTree() ? null : a));
                            if (m2 != m && m2.getReturnTypeExpression() != null) {
                                // For array types, annotate the array brackets, not the element type
                                if (m2.getReturnTypeExpression() instanceof J.ArrayType) {
                                    // For type-use annotations on arrays (JSpecify style), the annotation should be on the outermost array dimension
                                    // This creates: elementType @Nullable []
                                    // We do this by wrapping the ENTIRE array type in an AnnotatedType, but with proper spacing
                                    m2 = m2.withReturnTypeExpression(((J.ArrayType) m2.getReturnTypeExpression())
                                            .withAnnotations(singletonList(nullable.getTree().withPrefix(Space.SINGLE_SPACE))));
                                } else {
                                    m2 = m2.withReturnTypeExpression(new J.AnnotatedType(
                                            Tree.randomId(),
                                            Space.SINGLE_SPACE,
                                            Markers.EMPTY,
                                            singletonList(nullable.getTree().withPrefix(Space.EMPTY)),
                                            m2.getReturnTypeExpression()
                                    ));
                                }
                                m2 = autoFormat(m2, m2.getReturnTypeExpression(), ctx, getCursor().getParentOrThrow());
                                m2 = m2.withPrefix(m2.getPrefix().withWhitespace(m2.getPrefix().getWhitespace().replace("\n\n\n", "\n\n")));
                            }
                            return m2;
                        })
                        .orElse(m));
            }
        };
        return Preconditions.check(new UsesType<>("*..Nullable", false), visitor);
    }
}
