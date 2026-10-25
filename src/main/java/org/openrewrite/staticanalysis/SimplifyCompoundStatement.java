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
import org.openrewrite.Cursor;
import org.openrewrite.ExecutionContext;
import org.openrewrite.Recipe;
import org.openrewrite.Tree;
import org.openrewrite.TreeVisitor;
import org.openrewrite.java.JavaVisitor;
import org.openrewrite.java.cleanup.SimplifyBooleanExpressionVisitor;
import org.openrewrite.java.tree.Expression;
import org.openrewrite.java.tree.J;
import org.openrewrite.java.tree.JLeftPadded;

public class SimplifyCompoundStatement extends Recipe {
    @Getter
    final String displayName = "Simplify compound statement";

    @Getter
    final String description = "Fixes or removes useless compound statements. For example, removing `b &= true`, and replacing `b &= false` with `b = false`.";

    @Override
    public TreeVisitor<?, ExecutionContext> getVisitor() {
        return new JavaVisitor<ExecutionContext>() {
            @Override
            public @Nullable J visitAssignmentOperation(J.AssignmentOperation assignOp, ExecutionContext ctx) {
                J.AssignmentOperation.Type operator = assignOp.getOperator();
                if (operator == J.AssignmentOperation.Type.BitAnd || operator == J.AssignmentOperation.Type.BitOr) {
                    // `b &= true` and `b |= false` leave `b` unchanged
                    boolean identity = operator == J.AssignmentOperation.Type.BitAnd;
                    Cursor parent = getCursor().getParentOrThrow();
                    Expression simplified = (Expression) new SimplifyBooleanExpressionVisitor()
                            .visitNonNull(assignOp.getAssignment(), ctx, parent);
                    simplified = (Expression) new SimplifyBooleanExpressionVisitor()
                            .visitNonNull(simplified, ctx, parent);
                    if (J.Literal.isLiteralValue(simplified, identity)) {
                        return null;
                    }
                    if (J.Literal.isLiteralValue(simplified, !identity)) {
                        return maybeAutoFormat(
                                assignOp,
                                new J.Assignment(
                                        Tree.randomId(),
                                        assignOp.getPrefix(),
                                        assignOp.getMarkers(),
                                        assignOp.getVariable(),
                                        JLeftPadded.build(simplified),
                                        assignOp.getType()
                                ),
                                ctx
                        );
                    }
                }
                return super.visitAssignmentOperation(assignOp, ctx);
            }
        };
    }
}
