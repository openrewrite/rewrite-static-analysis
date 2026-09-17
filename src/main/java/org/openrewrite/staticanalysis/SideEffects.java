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
import org.openrewrite.Tree;
import org.openrewrite.java.JavaIsoVisitor;
import org.openrewrite.java.tree.Flag;
import org.openrewrite.java.tree.J;
import org.openrewrite.java.tree.JavaType;

import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Whether evaluating an expression might do something observable beyond producing its value; recipes deleting an
 * expression, or evaluating one fewer times, are only correct when this is {@code false}. Deliberately
 * conservative: any invocation, constructor call, assignment or increment counts, as does a {@code volatile} read,
 * which is a synchronization action rather than a side effect. Not {@link
 * org.openrewrite.java.tree.Expression#getSideEffects()}, which reports only the expression's own node type and so
 * misses anything nested inside a ternary or a lambda.
 */
final class SideEffects {

    private SideEffects() {
    }

    static boolean mayHaveSideEffects(@Nullable J tree) {
        if (tree == null) {
            return false;
        }
        return new JavaIsoVisitor<AtomicBoolean>() {
            @Override
            public J.MethodInvocation visitMethodInvocation(J.MethodInvocation method, AtomicBoolean result) {
                result.set(true);
                return method;
            }

            @Override
            public J.Assignment visitAssignment(J.Assignment assignment, AtomicBoolean result) {
                result.set(true);
                return assignment;
            }

            @Override
            public J.AssignmentOperation visitAssignmentOperation(J.AssignmentOperation assignOp, AtomicBoolean result) {
                result.set(true);
                return assignOp;
            }

            @Override
            public J.Unary visitUnary(J.Unary unary, AtomicBoolean result) {
                switch (unary.getOperator()) {
                    case PreIncrement:
                    case PreDecrement:
                    case PostIncrement:
                    case PostDecrement:
                        result.set(true);
                        return unary;
                    default:
                        return super.visitUnary(unary, result);
                }
            }

            @Override
            public J.NewClass visitNewClass(J.NewClass newClass, AtomicBoolean result) {
                result.set(true);
                return newClass;
            }

            @Override
            public J.Identifier visitIdentifier(J.Identifier identifier, AtomicBoolean result) {
                JavaType.Variable fieldType = identifier.getFieldType();
                if (fieldType != null && fieldType.hasFlags(Flag.Volatile)) {
                    result.set(true);
                }
                return identifier;
            }

            @Override
            public @Nullable J visit(@Nullable Tree t, AtomicBoolean result) {
                if (result.get()) {
                    return (J) t;
                }
                return super.visit(t, result);
            }
        }.reduce(tree, new AtomicBoolean(false)).get();
    }
}
