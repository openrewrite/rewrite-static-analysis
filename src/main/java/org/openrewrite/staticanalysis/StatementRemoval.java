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
import org.openrewrite.java.tree.TypeUtils;

/**
 * What a visitor may put in place of a statement it deletes or replaces. Deleting an element of a statement list
 * leaves nothing. Deleting the sole body of a brace-less {@code if}, {@code else}, loop, label, arrow {@code case}
 * or void lambda leaves an empty block. Anywhere else the statement stays, as an enclosing expression may use its value.
 */
final class StatementRemoval {

    private StatementRemoval() {
    }

    static @Nullable J remove(Cursor cursor, Statement statement) {
        if (isListElement(cursor)) {
            return null;
        }
        if (isSoleBody(cursor) || cursor.getParentTreeCursor().getValue() instanceof J.Lambda &&
                statement instanceof J.MethodInvocation && ((J.MethodInvocation) statement).getType() == JavaType.Primitive.Void) {
            Space prefix = statement.getPrefix();
            return J.Block.createEmptyBlock().withPrefix(prefix.getComments().isEmpty() ? Space.SINGLE_SPACE : prefix);
        }
        return statement;
    }

    /**
     * Whether only a statement expression, such as a method invocation, may replace the Java expression at the cursor.
     * That holds where it stands as a statement, as nothing uses its value. The body of a lambda whose functional
     * interface returns void, or is unknown, stands as a statement. Kotlin, Groovy and Scala accept any expression there.
     */
    static boolean needsStatementExpression(Cursor cursor) {
        SourceFile sourceFile = cursor.firstEnclosing(SourceFile.class);
        if (sourceFile != null && !(sourceFile instanceof J.CompilationUnit)) {
            return false;
        }
        Object parent = cursor.getParentTreeCursor().getValue();
        if (parent instanceof J.Lambda) {
            JavaType.FullyQualified functionalInterface = TypeUtils.asFullyQualified(((J.Lambda) parent).getType());
            JavaType.Method sam = functionalInterface == null ? null : UseLambdaForFunctionalInterface.functionalInterfaceMethod(functionalInterface);
            return sam == null || sam.getReturnType() == JavaType.Primitive.Void;
        }
        return isListElement(cursor) || isSoleBody(cursor);
    }

    private static boolean isListElement(Cursor cursor) {
        Object parent = cursor.getParentTreeCursor().getValue();
        Object original = cursor.getValue();
        return parent instanceof J.Block || parent instanceof SourceFile ||
                parent instanceof J.Case && ((J.Case) parent).getStatements().contains(original) ||
                parent instanceof J.ForLoop.Control && (((J.ForLoop.Control) parent).getInit().contains(original) ||
                        ((J.ForLoop.Control) parent).getUpdate().contains(original));
    }

    private static boolean isSoleBody(Cursor cursor) {
        Cursor parentCursor = cursor.getParentTreeCursor();
        Object parent = parentCursor.getValue();
        return parent instanceof J.If || parent instanceof J.If.Else || parent instanceof J.WhileLoop ||
                parent instanceof J.DoWhileLoop || parent instanceof J.ForLoop || parent instanceof J.ForEachLoop ||
                parent instanceof J.Label ||
                parent instanceof J.Case && ((J.Case) parent).getBody() == cursor.getValue() &&
                        !(parentCursor.getParentTreeCursor().getParentTreeCursor().getValue() instanceof J.SwitchExpression);
    }
}
