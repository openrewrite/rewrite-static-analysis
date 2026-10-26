/*
 * Copyright 2026 the original author or authors.
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

import org.jspecify.annotations.Nullable;
import org.openrewrite.Cursor;
import org.openrewrite.SourceFile;
import org.openrewrite.java.tree.J;
import org.openrewrite.java.tree.JavaType;
import org.openrewrite.java.tree.Space;
import org.openrewrite.java.tree.Statement;

/**
 * What a visitor deleting a statement should return in its place: {@code null} where the statement is an element
 * of a statement list, an empty block where it is the sole body of a brace-less {@code if}, {@code else}, loop,
 * label, arrow {@code case} or void lambda, and otherwise the statement itself, as an enclosing expression may
 * use its value.
 */
final class StatementRemoval {

    private StatementRemoval() {
    }

    static @Nullable J remove(Cursor cursor, Statement statement) {
        Cursor parentCursor = cursor.getParentTreeCursor();
        Object parent = parentCursor.getValue();
        Object original = cursor.getValue();
        if (parent instanceof J.Block || parent instanceof SourceFile ||
                parent instanceof J.Case && ((J.Case) parent).getStatements().contains(original) ||
                parent instanceof J.ForLoop.Control && (((J.ForLoop.Control) parent).getInit().contains(original) ||
                        ((J.ForLoop.Control) parent).getUpdate().contains(original))) {
            return null;
        }
        if (parent instanceof J.If || parent instanceof J.If.Else || parent instanceof J.WhileLoop ||
                parent instanceof J.DoWhileLoop || parent instanceof J.ForLoop || parent instanceof J.ForEachLoop ||
                parent instanceof J.Label ||
                parent instanceof J.Case && ((J.Case) parent).getBody() == original &&
                        !(parentCursor.getParentTreeCursor().getParentTreeCursor().getValue() instanceof J.SwitchExpression) ||
                parent instanceof J.Lambda && statement instanceof J.MethodInvocation &&
                        ((J.MethodInvocation) statement).getType() == JavaType.Primitive.Void) {
            Space prefix = statement.getPrefix();
            return J.Block.createEmptyBlock().withPrefix(prefix.getComments().isEmpty() ? Space.SINGLE_SPACE : prefix);
        }
        return statement;
    }
}
