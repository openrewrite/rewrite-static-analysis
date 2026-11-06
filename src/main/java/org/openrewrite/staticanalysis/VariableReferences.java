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

import org.jspecify.annotations.Nullable;
import org.openrewrite.Cursor;
import org.openrewrite.Tree;
import org.openrewrite.java.JavaIsoVisitor;
import org.openrewrite.java.search.SemanticallyEqual;
import org.openrewrite.java.tree.*;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Predicate;

import static java.util.Collections.emptyList;
import static java.util.Collections.singletonList;
import static java.util.stream.Collectors.toList;

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
        if (parent == null) {
            return false;
        }
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
                return grandParent instanceof Expression || grandParent instanceof J.Return;
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
        return new JavaIsoVisitor<List<J>>() {
            @Override
            public J.Identifier visitIdentifier(J.Identifier identifier, List<J> ctx) {
                if (SemanticallyEqual.areEqual(identifier, target) && isRhsValue(getCursor())) {
                    ctx.add(identifier);
                }
                return super.visitIdentifier(identifier, ctx);
            }
        }.reduce(j, new ArrayList<>());
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
                if (assignment.getVariable() instanceof J.Identifier && SemanticallyEqual.areEqual(assignment.getVariable(), target)) {
                    ctx.add(assignment);
                }
                return super.visitAssignment(assignment, ctx);
            }

            @Override
            public J.AssignmentOperation visitAssignmentOperation(J.AssignmentOperation assignOp, List<Statement> ctx) {
                if (assignOp.getVariable() instanceof J.Identifier && SemanticallyEqual.areEqual(assignOp.getVariable(), target)) {
                    ctx.add(assignOp);
                }
                return super.visitAssignmentOperation(assignOp, ctx);
            }

            @Override
            public J.Unary visitUnary(J.Unary unary, List<Statement> ctx) {
                if (unary.getOperator().isModifying() && unary.getExpression() instanceof J.Identifier && SemanticallyEqual.areEqual(unary.getExpression(), target)) {
                    ctx.add(unary);
                }
                return super.visitUnary(unary, ctx);
            }
        };

        return visitor.reduce(j, new ArrayList<>());
    }

    /**
     * Like {@link #isEffectivelyFinal(Cursor, J.VariableDeclarations.NamedVariable)}, for the local variable or
     * parameter that an identifier refers to. The cursor must reach up to the compilation unit, since the declaration
     * is looked up in the enclosing scopes. An identifier without type attribution gives {@code false}, and so does
     * one whose declaration the lookup misses, such as a declaration in an earlier switch case.
     */
    public static boolean isEffectivelyFinal(Cursor identifier) {
        J.Identifier id = identifier.getValue();
        if (id.getFieldType() == null || !(id.getFieldType().getOwner() instanceof JavaType.Method)) {
            return false;
        }
        J child = id;
        for (Cursor c = identifier.getParent(); c != null; c = c.getParent()) {
            if (!(c.getValue() instanceof J)) {
                continue;
            }
            J scope = c.getValue();
            List<?> candidates = emptyList();
            if (scope instanceof J.Block || scope instanceof J.Case) {
                List<Statement> statements = scope instanceof J.Block ? ((J.Block) scope).getStatements() : ((J.Case) scope).getStatements();
                candidates = statements.subList(0, statements.indexOf(child) + 1);
            } else if (scope instanceof J.MethodDeclaration) {
                candidates = ((J.MethodDeclaration) scope).getParameters();
            } else if (scope instanceof J.Lambda) {
                candidates = ((J.Lambda) scope).getParameters().getParameters();
            } else if (scope instanceof J.ForLoop) {
                candidates = ((J.ForLoop) scope).getControl().getInit();
            } else if (scope instanceof J.ForEachLoop) {
                candidates = singletonList(((J.ForEachLoop) scope).getControl().getVariable());
            } else if (scope instanceof J.Try && ((J.Try) scope).getResources() != null) {
                candidates = ((J.Try) scope).getResources().stream().map(J.Try.Resource::getVariableDeclarations).collect(toList());
            } else if (scope instanceof J.Try.Catch) {
                candidates = singletonList(((J.Try.Catch) scope).getParameter().getTree());
            }
            for (Object candidate : candidates) {
                if (candidate instanceof J.VariableDeclarations) {
                    for (J.VariableDeclarations.NamedVariable variable : ((J.VariableDeclarations) candidate).getVariables()) {
                        if (variable.getSimpleName().equals(id.getSimpleName())) {
                            // The cursor skips holders such as J.Lambda.Parameters. That changes neither the declaring scope
                            // nor whether the parent is a block, case, record or pattern, which is all the other method reads.
                            return isEffectivelyFinal(new Cursor(new Cursor(c, candidate), variable), variable);
                        }
                    }
                }
            }
            child = scope;
        }
        return false;
    }

    /**
     * Whether a local variable or parameter is final or effectively final (JLS 4.12.4), so that a lambda may capture it.
     * Errors go one way only. This may return {@code false} for a variable javac accepts, but never {@code true} for one it rejects.
     * A local without an initializer counts only when a single plain assignment writes it and no loop sits between the two,
     * so assignments in separate branches give {@code false}. Fields and pattern variables give {@code false}.
     * This needs no type attribution, because fields are told apart by where they are declared.
     *
     * @param cursor a cursor at or below the {@link J.VariableDeclarations} that holds {@code variable}
     */
    public static boolean isEffectivelyFinal(Cursor cursor, J.VariableDeclarations.NamedVariable variable) {
        Cursor declarations = cursor;
        while (!(declarations.getValue() instanceof J.VariableDeclarations) ||
               !((J.VariableDeclarations) declarations.getValue()).getVariables().contains(variable)) {
            declarations = declarations.getParentOrThrow();
        }
        Object position = declarations.getParentTreeCursor().getValue();
        Cursor scope = variable.getDeclaringScope(declarations);
        if (scope.getValue() == Cursor.ROOT_VALUE || position instanceof J.ClassDeclaration ||
            scope.getParentTreeCursor().getValue() instanceof J.ClassDeclaration || scope.getParentTreeCursor().getValue() instanceof J.NewClass) {
            return false;
        }
        if (position instanceof J.DeconstructionPattern ||
            position instanceof J.Case && !((J.Case) position).getStatements().contains(declarations.<J.VariableDeclarations>getValue())) {
            return false;
        }
        boolean blankLocal = variable.getInitializer() == null && (position instanceof J.Block || position instanceof J.Case);

        // Matching by name also counts writes to a same-named variable in a nested class, which only errs towards false.
        List<Cursor> writes = new JavaIsoVisitor<List<Cursor>>() {
            @Override
            public J.Assignment visitAssignment(J.Assignment assignment, List<Cursor> writes) {
                record(assignment.getVariable(), writes);
                return super.visitAssignment(assignment, writes);
            }

            @Override
            public J.AssignmentOperation visitAssignmentOperation(J.AssignmentOperation assignOp, List<Cursor> writes) {
                record(assignOp.getVariable(), writes);
                return super.visitAssignmentOperation(assignOp, writes);
            }

            @Override
            public J.Unary visitUnary(J.Unary unary, List<Cursor> writes) {
                if (unary.getOperator().isModifying()) {
                    record(unary.getExpression(), writes);
                }
                return super.visitUnary(unary, writes);
            }

            private void record(Expression target, List<Cursor> writes) {
                Expression unwrapped = target.unwrap();
                if (unwrapped instanceof J.Identifier && ((J.Identifier) unwrapped).getSimpleName().equals(variable.getSimpleName())) {
                    writes.add(getCursor());
                }
            }
        }.reduce(scope.getValue(), new ArrayList<>(), scope.getParentOrThrow());

        if (writes.isEmpty()) {
            return true;
        }
        if (!blankLocal || writes.size() > 1 || !(writes.get(0).getValue() instanceof J.Assignment)) {
            return false;
        }
        for (Cursor c = writes.get(0); c.getValue() != scope.getValue(); c = c.getParentOrThrow()) {
            if (c.getValue() instanceof Loop) {
                return false;
            }
        }
        return true;
    }
}
