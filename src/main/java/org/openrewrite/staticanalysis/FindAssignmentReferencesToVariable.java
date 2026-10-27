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

import lombok.EqualsAndHashCode;
import lombok.Value;
import org.openrewrite.java.JavaIsoVisitor;
import org.openrewrite.java.tree.J;

import java.util.concurrent.atomic.AtomicBoolean;

@EqualsAndHashCode(callSuper = false)
@Value
class FindAssignmentReferencesToVariable extends JavaIsoVisitor<AtomicBoolean> {

    J.VariableDeclarations.NamedVariable variable;

    static AtomicBoolean find(J j, J.VariableDeclarations.NamedVariable variable) {
        return new FindAssignmentReferencesToVariable(variable)
                .reduce(j, new AtomicBoolean());
    }

    @Override
    public J.Assignment visitAssignment(J.Assignment assignment, AtomicBoolean hasAssignment) {
        if (hasAssignment.get()) {
            return assignment;
        }
        J.Assignment a = super.visitAssignment(assignment, hasAssignment);
        if (a.getVariable() instanceof J.Identifier) {
            J.Identifier i = (J.Identifier) a.getVariable();
            if (i.getSimpleName().equals(variable.getSimpleName())) {
                hasAssignment.set(true);
            }
        }
        return a;
    }

    @Override
    public J.AssignmentOperation visitAssignmentOperation(J.AssignmentOperation assignOp, AtomicBoolean hasAssignment) {
        if (hasAssignment.get()) {
            return assignOp;
        }

        J.AssignmentOperation a = super.visitAssignmentOperation(assignOp, hasAssignment);
        if (a.getVariable() instanceof J.Identifier) {
            J.Identifier i = (J.Identifier) a.getVariable();
            if (i.getSimpleName().equals(variable.getSimpleName())) {
                hasAssignment.set(true);
            }
        }
        return a;
    }

    @Override
    public J.Unary visitUnary(J.Unary unary, AtomicBoolean hasAssignment) {
        if (hasAssignment.get()) {
            return unary;
        }

        J.Unary u = super.visitUnary(unary, hasAssignment);
        if (u.getOperator().isModifying() && u.getExpression() instanceof J.Identifier) {
            J.Identifier i = (J.Identifier) u.getExpression();
            if (i.getSimpleName().equals(variable.getSimpleName())) {
                hasAssignment.set(true);
            }
        }
        return u;
    }
}
