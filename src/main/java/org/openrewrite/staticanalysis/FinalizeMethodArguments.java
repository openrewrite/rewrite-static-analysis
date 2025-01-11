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

import lombok.EqualsAndHashCode;
import lombok.Value;
import org.openrewrite.*;
import org.openrewrite.internal.ListUtils;
import org.openrewrite.java.JavaIsoVisitor;
import org.openrewrite.java.tree.*;
import org.openrewrite.marker.Markers;

import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicBoolean;

import static java.util.Collections.emptyList;

/**
 * Finalize method arguments v2
 */
public class FinalizeMethodArguments extends Recipe {

    @Override
    public String getDisplayName() {
        return "Finalize method arguments";
    }

    @Override
    public String getDescription() {
        return "Adds the `final` modifier keyword to method parameters.";
    }

    @Override
    public TreeVisitor<?, ExecutionContext> getVisitor() {
        return new JavaIsoVisitor<ExecutionContext>() {
            @Override
            public J.MethodDeclaration visitMethodDeclaration(J.MethodDeclaration methodDeclaration, ExecutionContext ctx) {
                J.MethodDeclaration declarations = super.visitMethodDeclaration(methodDeclaration, ctx);

                if (isWrongKind(methodDeclaration) ||
                    isEmpty(declarations.getParameters()) ||
                    hasFinalModifiers(declarations.getParameters()) ||
                    isAbstractMethod(methodDeclaration)) {
                    return declarations;
                }

                final AtomicBoolean assigned = new AtomicBoolean(false);

                methodDeclaration.getParameters().forEach(p -> checkIfAssigned(assigned, p));
                if (assigned.get()) {
                    return declarations;
                }

                List<Statement> parameters = ListUtils.map(declarations.getParameters(), FinalizeMethodArguments::updateParam);
                declarations = declarations.withParameters(parameters);
                return declarations;
            }

            private void checkIfAssigned(final AtomicBoolean assigned, final Statement p) {
                if (p instanceof J.VariableDeclarations) {
                    J.VariableDeclarations variableDeclarations = (J.VariableDeclarations) p;
                    if (variableDeclarations.getVariables().stream()
                            .anyMatch(namedVariable ->
                                    FindAssignmentReferencesToVariable.find(getCursor()
                                                            .getParentTreeCursor()
                                                            .getValue(),
                                                    namedVariable)
                                            .get())) {
                        assigned.set(true);
                    }
                }
            }
        };
    }

    private static boolean isWrongKind(final J.MethodDeclaration methodDeclaration) {
        return Optional.ofNullable(methodDeclaration.getMethodType())
                .map(JavaType.Method::getDeclaringType)
                .map(JavaType.FullyQualified::getKind)
                .filter(JavaType.FullyQualified.Kind.Interface::equals)
                .isPresent();
    }

    private static boolean isAbstractMethod(J.MethodDeclaration method) {
        return method.getModifiers().stream().anyMatch(modifier -> modifier.getType() == J.Modifier.Type.Abstract);
    }

    @Value
    @EqualsAndHashCode(callSuper = false)
    private static class FindAssignmentReferencesToVariable extends JavaIsoVisitor<AtomicBoolean> {

        J.VariableDeclarations.NamedVariable variable;

        /**
         * @param subtree  The subtree to search.
         * @param variable A {@link J.VariableDeclarations.NamedVariable} to check for any reassignment calls.
         * @return An {@link AtomicBoolean} that is true if the variable has been reassigned and false otherwise.
         */
        static AtomicBoolean find(J subtree, J.VariableDeclarations.NamedVariable variable) {
            return new FindAssignmentReferencesToVariable(variable)
                    .reduce(subtree, new AtomicBoolean());
        }

        @Override
        public J.Assignment visitAssignment(J.Assignment a, AtomicBoolean hasAssignment) {
            // Return quickly if the variable has been reassigned before
            if (hasAssignment.get()) {
                return a;
            }

            J.Assignment assignment = super.visitAssignment(a, hasAssignment);

            if (assignment.getVariable() instanceof J.Identifier) {
                J.Identifier identifier = (J.Identifier) assignment.getVariable();

                if (identifier.getSimpleName().equals(variable.getSimpleName())) {
                    hasAssignment.set(true);
                }
            }

            return assignment;
        }

        @Override
        public J.Unary visitUnary(final J.Unary unary, final AtomicBoolean hasAssignment) {
            if (hasAssignment.get()) {
                return unary;
            }

            final J.Unary u = super.visitUnary(unary, hasAssignment);
            if (u.getOperator().isModifying() && u.getExpression() instanceof J.Identifier) {
                final J.Identifier i = (J.Identifier) u.getExpression();
                if (i.getSimpleName().equals(this.variable.getSimpleName())) {
                    hasAssignment.set(true);
                }
            }
            return u;
        }

        @Override
        public J.AssignmentOperation visitAssignmentOperation(final J.AssignmentOperation assignOp, final AtomicBoolean hasAssignment) {
            if (hasAssignment.get()) {
                return assignOp;
            }

            final J.AssignmentOperation a = super.visitAssignmentOperation(assignOp, hasAssignment);
            if (a.getVariable() instanceof J.Identifier) {
                final J.Identifier i = (J.Identifier) a.getVariable();
                if (i.getSimpleName().equals(this.variable.getSimpleName())) {
                    hasAssignment.set(true);
                }
            }
            return a;
        }
    }

    private static Statement updateParam(final Statement p) {
        if (p instanceof J.VariableDeclarations) {
            J.VariableDeclarations variableDeclarations = (J.VariableDeclarations) p;
            if (variableDeclarations.getModifiers().isEmpty()) {
                variableDeclarations = updateModifiers(variableDeclarations, !((J.VariableDeclarations) p).getLeadingAnnotations().isEmpty());
                variableDeclarations = updateDeclarations(variableDeclarations);
                return variableDeclarations;
            }
        }
        return p;
    }

    private static J.VariableDeclarations updateDeclarations(final J.VariableDeclarations variableDeclarations) {
        return variableDeclarations.withTypeExpression(variableDeclarations.getTypeExpression() != null ?
                variableDeclarations.getTypeExpression().withPrefix(Space.SINGLE_SPACE) : null);
    }

    private static J.VariableDeclarations updateModifiers(final J.VariableDeclarations variableDeclarations, final boolean leadingAnnotations) {
        List<J.Modifier> modifiers = variableDeclarations.getModifiers();
        J.Modifier finalModifier = new J.Modifier(Tree.randomId(),
                Space.EMPTY,
                Markers.EMPTY,
                null,
                J.Modifier.Type.Final,
                emptyList());
        if (leadingAnnotations) {
            finalModifier = finalModifier.withPrefix(Space.SINGLE_SPACE);
        }
        return variableDeclarations.withModifiers(ListUtils.concat(finalModifier, modifiers));
    }

    private boolean hasFinalModifiers(final List<Statement> parameters) {
        return parameters.stream().allMatch(p -> {
            if (p instanceof J.VariableDeclarations) {
                final List<J.Modifier> modifiers = ((J.VariableDeclarations) p).getModifiers();
                return !modifiers.isEmpty() &&
                       modifiers.stream()
                               .allMatch(m -> m.getType() == J.Modifier.Type.Final);
            }
            return false;
        });
    }

    private boolean isEmpty(final List<Statement> parameters) {
        return parameters.size() == 1 && (parameters.get(0) instanceof J.Empty);
    }
}
