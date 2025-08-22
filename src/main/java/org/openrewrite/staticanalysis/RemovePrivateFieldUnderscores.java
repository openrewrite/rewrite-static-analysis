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

import org.openrewrite.*;
import org.openrewrite.java.JavaVisitor;
import org.openrewrite.java.RenameVariable;
import org.openrewrite.java.tree.J;
import org.openrewrite.java.tree.JLeftPadded;
import org.openrewrite.java.tree.JavaType;
import org.openrewrite.java.tree.Space;
import org.openrewrite.marker.Markers;

import static java.util.Collections.emptyList;

public class RemovePrivateFieldUnderscores extends Recipe {

    @Override
    public String getDisplayName() {
        return "Remove underscores from private class field names";
    }

    @Override
    public String getDescription() {
        return "Removes prefix or suffix underscores from private class field names and adds `this.` to all field accesses for clarity and consistency.";
    }

    @Override
    public TreeVisitor<?, ExecutionContext> getVisitor() {
        return new JavaVisitor<ExecutionContext>() {
            @Override
            public J visitVariableDeclarations(J.VariableDeclarations multiVariable, ExecutionContext ctx) {
                J.VariableDeclarations mv = (J.VariableDeclarations) super.visitVariableDeclarations(multiVariable, ctx);
                if (!mv.hasModifier(J.Modifier.Type.Private)) {
                    return mv;
                }

                Cursor parent = getCursor().getParentTreeCursor();
                if (!(parent.getValue() instanceof J.Block && parent.getParentTreeCursor().getValue() instanceof J.ClassDeclaration)) {
                    return mv;
                }

                for (J.VariableDeclarations.NamedVariable variable : mv.getVariables()) {
                    String oldName = variable.getSimpleName();
                    if (oldName.startsWith("_") || oldName.endsWith("_")) {
                        String newName = oldName.startsWith("_") ? oldName.substring(1) : oldName.substring(0, oldName.length() - 1);
                        doAfterVisit(new AddThisToFieldUses(variable));
                        doAfterVisit(new RenameVariable(variable, newName));
                    }
                }
                return mv;
            }
        };
    }

    private static class AddThisToFieldUses extends JavaVisitor<ExecutionContext> {
        private final JavaType.Variable fieldType;

        public AddThisToFieldUses(J.VariableDeclarations.NamedVariable field) {
            this.fieldType = field.getVariableType();
        }

        @Override
        public J visitIdentifier(J.Identifier identifier, ExecutionContext ctx) {
            J.Identifier id = (J.Identifier) super.visitIdentifier(identifier, ctx);
            if (id.getFieldType() != null && id.getFieldType().equals(this.fieldType)) {
                if (getCursor().getParentTreeCursor().getValue() instanceof J.VariableDeclarations.NamedVariable) {
                    return id;
                }

                if (getCursor().getParentTreeCursor().getValue() instanceof J.FieldAccess) {
                    return id;
                }

                return new J.FieldAccess(
                   Tree.randomId(),
                   identifier.getPrefix(),
                   Markers.EMPTY,
                   new J.Identifier(
                     Tree.randomId(),
                     Space.EMPTY,
                     Markers.EMPTY,
                     emptyList(),
                     "this",
                     identifier.getType(),
                     null
                   ),
                   JLeftPadded.build(identifier.withPrefix(Space.EMPTY).withSimpleName(id.getSimpleName())),
                   identifier.getType()
                 );
            }
            return id;
        }
    }
}
