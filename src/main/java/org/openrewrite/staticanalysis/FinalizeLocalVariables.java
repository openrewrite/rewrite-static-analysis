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
import org.openrewrite.java.tree.J;
import org.openrewrite.java.tree.Space;
import org.openrewrite.marker.Markers;

import static java.util.Collections.emptyList;

public class FinalizeLocalVariables extends Recipe {

    @Getter
    final String displayName = "Finalize local variables";

    @Getter
    final String description = "Adds the `final` modifier keyword to local variables which are not reassigned.";

    @Override
    public TreeVisitor<?, ExecutionContext> getVisitor() {
        return new JavaIsoVisitor<ExecutionContext>() {

            @Override
            public J.VariableDeclarations visitVariableDeclarations(J.VariableDeclarations multiVariable, ExecutionContext ctx) {
                J.VariableDeclarations mv = super.visitVariableDeclarations(multiVariable, ctx);

                if (mv.hasModifier(J.Modifier.Type.Final)) {
                    return mv;
                }

                if (mv.getVariables().stream().anyMatch(nv -> nv.getInitializer() == null)) {
                    return mv;
                }

                // Skip known cases where a `final` modifier looks out of place
                J parentTreeCursor = getCursor().getParentTreeCursor().getValue();
                if (parentTreeCursor instanceof J.ForLoop.Control ||
                        parentTreeCursor instanceof J.Try.Resource) {
                    return mv;
                }

                if (mv.getVariables().stream().anyMatch(v -> v.isField(getCursor()))) {
                    return mv;
                }

                // ignores anonymous class fields, contributed code for issue #181
                Object parentScope = this.getCursorToParentScope(this.getCursor()).getValue();
                if (parentScope instanceof J.NewClass || parentScope instanceof SourceFile) {
                    return mv;
                }

                if (mv.getVariables().stream()
                        .noneMatch(v -> {
                            Cursor declaringCursor = v.getDeclaringScope(getCursor());
                            return FindAssignmentReferencesToVariable.find(declaringCursor.getValue(), v).get();
                        })) {
                    mv = autoFormat(
                            mv.withModifiers(
                                    ListUtils.concat(mv.getModifiers(), new J.Modifier(Tree.randomId(), Space.EMPTY, Markers.EMPTY, null, J.Modifier.Type.Final, emptyList()))
                            ), ctx);
                }

                return mv;
            }

            private Cursor getCursorToParentScope(final Cursor cursor) {
                return cursor.dropParentUntil(is -> is instanceof J.NewClass || is instanceof J.ClassDeclaration || is instanceof SourceFile);
            }
        };
    }
}
