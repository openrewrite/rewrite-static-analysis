/*
 * Copyright 2022 the original author or authors.
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
import org.openrewrite.Tree;
import org.openrewrite.internal.lang.Nullable;
import org.openrewrite.java.JavaVisitor;
import org.openrewrite.java.cleanup.SimplifyBooleanExpression;
import org.openrewrite.java.tree.Expression;
import org.openrewrite.java.tree.J;
import org.openrewrite.java.tree.JLeftPadded;

public class SimplifyCompoundVisitor extends JavaVisitor<ExecutionContext> {
    @Override
    public J visitAssignmentOperation(J.AssignmentOperation assignOp, ExecutionContext ctx) {
        Expression cleanedUpAssignment = cleanupBooleanExpression(assignOp.getAssignment(), ctx);
        if (assignOp.getOperator() == J.AssignmentOperation.Type.BitAnd) {
            if (isLiteralTrue(cleanedUpAssignment)) {
                //noinspection DataFlowIssue
                return null;
            } else if (isLiteralFalse(cleanedUpAssignment)) {
                return maybeAutoFormat(
                        assignOp,
                        new J.Assignment(
                                Tree.randomId(),
                                assignOp.getPrefix(),
                                assignOp.getMarkers(),
                                assignOp.getVariable(),
                                JLeftPadded.build(cleanedUpAssignment),
                                assignOp.getType()
                        ),
                        ctx
                );
            }
        } else if (assignOp.getOperator() == J.AssignmentOperation.Type.BitOr) {
            if (isLiteralFalse(cleanedUpAssignment)) {
                //noinspection DataFlowIssue
                return null;
            } else if (isLiteralTrue(cleanedUpAssignment)) {
                return maybeAutoFormat(
                        assignOp,
                        new J.Assignment(
                                Tree.randomId(),
                                assignOp.getPrefix(),
                                assignOp.getMarkers(),
                                assignOp.getVariable(),
                                JLeftPadded.build(cleanedUpAssignment),
                                assignOp.getType()
                        ),
                        ctx
                );
            }
        }
        return super.visitAssignmentOperation(assignOp, ctx);
    }

    @SuppressWarnings("unchecked")
    private <E extends Expression> E cleanupBooleanExpression(E expression, ExecutionContext ctx) {
        E ex1 = (E) new SimplifyBooleanExpression().getVisitor()
                .visitNonNull(expression, ctx, getCursor().getParentOrThrow());
        return (E) new SimplifyBooleanExpression().getVisitor()
                .visitNonNull(ex1, ctx, getCursor().getParentOrThrow());
    }

    private static boolean isLiteralTrue(@Nullable Expression expression) {
        return expression instanceof J.Literal && ((J.Literal) expression).getValue() == Boolean.valueOf(true);
    }

    private static boolean isLiteralFalse(@Nullable Expression expression) {
        return expression instanceof J.Literal && ((J.Literal) expression).getValue() == Boolean.valueOf(false);
    }
}
