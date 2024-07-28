/*
 * Copyright 2024 the original author or authors.
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
import org.openrewrite.Recipe;
import org.openrewrite.Tree;
import org.openrewrite.TreeVisitor;
import org.openrewrite.internal.ListUtils;
import org.openrewrite.internal.lang.Nullable;
import org.openrewrite.java.JavaIsoVisitor;
import org.openrewrite.java.tree.J;
import org.openrewrite.java.tree.Space;
import org.openrewrite.marker.Markers;

import java.util.Collections;

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
                return annotated(Nullable.class)
                        .lower(getCursor())
                        .findFirst()
                        .map(nullable -> {
                            if (nullable.getCursor().getParentTreeCursor().getValue() != m) {
                                return m;
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
                        .orElse(m);
            }
        };
    }
}
