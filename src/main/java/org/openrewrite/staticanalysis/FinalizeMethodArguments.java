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
import org.openrewrite.ExecutionContext;
import org.openrewrite.Preconditions;
import org.openrewrite.Recipe;
import org.openrewrite.Tree;
import org.openrewrite.TreeVisitor;
import org.openrewrite.internal.ListUtils;
import org.openrewrite.java.JavaIsoVisitor;
import org.openrewrite.java.tree.J;
import org.openrewrite.java.tree.JavaType;
import org.openrewrite.java.tree.Space;
import org.openrewrite.java.tree.Statement;
import org.openrewrite.marker.Markers;
import org.openrewrite.staticanalysis.groovy.GroovyFileChecker;
import org.openrewrite.staticanalysis.java.JavaFileChecker;

import java.util.List;
import java.util.Optional;

import static java.util.Collections.emptyList;

public class FinalizeMethodArguments extends Recipe {

    @Getter
    final String displayName = "Finalize method arguments";

    @Getter
    final String description = "Adds the `final` modifier keyword to method parameters.";

    @Override
    public TreeVisitor<?, ExecutionContext> getVisitor() {
        return Preconditions.check(Preconditions.or(new JavaFileChecker<>(), new GroovyFileChecker<>()), new JavaIsoVisitor<ExecutionContext>() {
            @Override
            public J.MethodDeclaration visitMethodDeclaration(J.MethodDeclaration methodDeclaration, ExecutionContext ctx) {
                J.MethodDeclaration declarations = super.visitMethodDeclaration(methodDeclaration, ctx);

                if (isWrongKind(methodDeclaration) ||
                    isEmpty(declarations.getParameters()) ||
                    hasFinalModifiers(declarations.getParameters()) ||
                    isAbstractMethod(methodDeclaration)) {
                    return declarations;
                }

                J enclosing = getCursor().getParentTreeCursor().getValue();
                if (methodDeclaration.getParameters().stream().anyMatch(p -> p instanceof J.VariableDeclarations &&
                        ((J.VariableDeclarations) p).getVariables().stream()
                                .anyMatch(v -> FindAssignmentReferencesToVariable.find(enclosing, v).get()))) {
                    return declarations;
                }

                List<Statement> parameters = ListUtils.map(declarations.getParameters(), FinalizeMethodArguments::updateParam);
                return declarations.withParameters(parameters);
            }
        });
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

    private static Statement updateParam(final Statement p) {
        if (p instanceof J.VariableDeclarations) {
            J.VariableDeclarations variableDeclarations = (J.VariableDeclarations) p;
            if (variableDeclarations.getModifiers().isEmpty()) {
                variableDeclarations = updateModifiers(variableDeclarations, !((J.VariableDeclarations) p).getLeadingAnnotations().isEmpty());
                return updateDeclarations(variableDeclarations);
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
