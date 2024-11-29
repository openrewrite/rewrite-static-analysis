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

import org.jspecify.annotations.Nullable;
import org.openrewrite.Cursor;
import org.openrewrite.Tree;
import org.openrewrite.java.JavaIsoVisitor;
import org.openrewrite.java.tree.Expression;
import org.openrewrite.java.tree.J;
import org.openrewrite.java.tree.Statement;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Predicate;

public class VariableReferences {
    private static boolean isIncrementKind(Cursor tree) {
        return tree.getValue() instanceof J.Unary && ((J.Unary) tree.getValue()).getOperator().isModifying();
    }

    private static @Nullable Cursor dropParentWhile(Predicate<Object> valuePredicate, Cursor cursor) {
        while (cursor != null && valuePredicate.test(cursor.getValue())) {
            cursor = cursor.getParent();
        }
        return cursor;
    }

    private static @Nullable Cursor dropParentUntil(Predicate<Object> valuePredicate, Cursor cursor) {
        while (cursor != null && !valuePredicate.test(cursor.getValue())) {
            cursor = cursor.getParent();
        }
        return cursor;
    }

    private static boolean isRhsValue(Cursor tree) {
        if (!(tree.getValue() instanceof J.Identifier)) {
            return false;
        }

        Cursor parent = dropParentWhile(J.Parentheses.class::isInstance, tree.getParent());
        assert parent != null;
        if (parent.getValue() instanceof J.Assignment) {
            if (dropParentUntil(J.ControlParentheses.class::isInstance, parent) != null) {
                return true;
            }
            J.Assignment assignment = parent.getValue();
            return assignment.getVariable() != tree.getValue();
        }

        if (parent.getValue() instanceof J.VariableDeclarations.NamedVariable) {
            J.VariableDeclarations.NamedVariable namedVariable = parent.getValue();
            return namedVariable.getName() != tree.getValue();
        }

        if (parent.getValue() instanceof J.AssignmentOperation) {
            J.AssignmentOperation assignmentOperation = parent.getValue();
            if (assignmentOperation.getVariable() == tree.getValue()) {
                Tree grandParent = parent.getParentTreeCursor().getValue();
                return (grandParent instanceof Expression || grandParent instanceof J.Return);
            }
        }

        return !(isIncrementKind(parent) && parent.getParentTreeCursor().getValue() instanceof J.Block);
    }

    /**
     * An identifier is considered a right-hand side ("rhs") read operation if it is not used as the left operand
     * of an assignment, nor as the operand of a stand-alone increment.
     *
     * @param j      The subtree to search.
     * @param target A {@link J.Identifier} to check for usages.
     * @return found {@link J} locations of "right-hand" read calls.
     */
    public static List<J> findRhsReferences(J j, J.Identifier target) {
        final List<J> refs = new ArrayList<>();
        new JavaIsoVisitor<List<J>>() {
            @Override
            public J.Identifier visitIdentifier(J.Identifier identifier, List<J> ctx) {
                if (identifier.getSimpleName().equals(target.getSimpleName()) && isRhsValue(getCursor())) {
                    ctx.add(identifier);
                }
                return super.visitIdentifier(identifier, ctx);
            }
        }.visit(j, refs);
        return refs;
    }

    /**
     * @param j      The subtree to search.
     * @param target A {@link J.Identifier} to check for usages.
     * @return found {@link Statement} locations of "left-hand" assignment write calls.
     */
    public static List<Statement> findLhsReferences(J j, J.Identifier target) {
        JavaIsoVisitor<List<Statement>> visitor = new JavaIsoVisitor<List<Statement>>() {
            @Override
            public J.Assignment visitAssignment(J.Assignment assignment, List<Statement> ctx) {
                if (assignment.getVariable() instanceof J.Identifier) {
                    J.Identifier i = (J.Identifier) assignment.getVariable();
                    if (i.getSimpleName().equals(target.getSimpleName())) {
                        ctx.add(assignment);
                    }
                }
                return super.visitAssignment(assignment, ctx);
            }

            @Override
            public J.AssignmentOperation visitAssignmentOperation(J.AssignmentOperation assignOp, List<Statement> ctx) {
                if (assignOp.getVariable() instanceof J.Identifier) {
                    J.Identifier i = (J.Identifier) assignOp.getVariable();
                    if (i.getSimpleName().equals(target.getSimpleName())) {
                        ctx.add(assignOp);
                    }
                }
                return super.visitAssignmentOperation(assignOp, ctx);
            }

            @Override
            public J.Unary visitUnary(J.Unary unary, List<Statement> ctx) {
                if (unary.getExpression() instanceof J.Identifier) {
                    J.Identifier i = (J.Identifier) unary.getExpression();
                    if (i.getSimpleName().equals(target.getSimpleName())) {
                        ctx.add(unary);
                    }
                }
                return super.visitUnary(unary, ctx);
            }
        };

        List<Statement> refs = new ArrayList<>();
        visitor.visit(j, refs);
        return refs;
    }
}
