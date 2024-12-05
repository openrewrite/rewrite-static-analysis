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

import org.openrewrite.ExecutionContext;
import org.openrewrite.Preconditions;
import org.openrewrite.Recipe;
import org.openrewrite.TreeVisitor;
import org.openrewrite.internal.ListUtils;
import org.openrewrite.java.JavaIsoVisitor;
import org.openrewrite.java.tree.J;
import org.openrewrite.java.tree.Space;
import org.openrewrite.java.tree.TypeUtils;
import org.openrewrite.marker.Markers;
import org.openrewrite.staticanalysis.csharp.CSharpFileChecker;

import java.time.Duration;

import static org.openrewrite.Tree.randomId;

public class UseTargetNewOperator extends Recipe {

    @Override
    public String getDisplayName() {
        return "Use the target-typed new operator";
    }

    @Override
    public String getDescription() {
        return "Replaces full typed objects with the target-typed new operator (new()).";
    }

    @Override
    public Duration getEstimatedEffortPerOccurrence() {
        return Duration.ofMinutes(1);
    }

    @Override
    public TreeVisitor<?, ExecutionContext> getVisitor() {
        return Preconditions.check(new CSharpFileChecker<>(), new JavaIsoVisitor<ExecutionContext>() {
            @Override
            public J.VariableDeclarations visitVariableDeclarations(J.VariableDeclarations multiVariable, ExecutionContext ctx) {
                J.VariableDeclarations varDecls = super.visitVariableDeclarations(multiVariable, ctx);
                if (varDecls.getVariables().size() == 1 && varDecls.getVariables().get(0).getInitializer() != null && varDecls.getTypeExpression() instanceof J.ParameterizedType) {
                    varDecls = varDecls.withVariables(ListUtils.map(varDecls.getVariables(), nv -> {
                        if (nv.getInitializer() instanceof J.NewClass) {
                            nv = nv.withInitializer(maybeRemoveParams((J.NewClass) nv.getInitializer()));
                        }
                        return nv;
                    }));
                }
                return varDecls;
            }

            @Override
            public J.Assignment visitAssignment(J.Assignment assignment, ExecutionContext ctx) {
                J.Assignment asgn = super.visitAssignment(assignment, ctx);
                if (asgn.getAssignment() instanceof J.NewClass && ((J.NewClass) asgn.getAssignment()).getClazz() instanceof J.ParameterizedType) {
                    asgn = asgn.withAssignment(maybeRemoveParams((J.NewClass) asgn.getAssignment()));
                }
                return asgn;
            }

            J.NewClass maybeRemoveParams(J.NewClass newClass) {
                if (newClass.getBody() == null && newClass.getClazz() instanceof J.ParameterizedType) {
                    J.ParameterizedType newClassType = (J.ParameterizedType) newClass.getClazz();
                    if (newClassType.getTypeParameters() != null) {
                        newClassType.getTypeParameters().stream()
                                .map(e -> TypeUtils.asFullyQualified(e.getType()))
                                .forEach(this::maybeRemoveImport);
                        newClass = newClass.withClazz(new J.Empty(randomId(), Space.EMPTY, Markers.EMPTY));
                    }
                }
                return newClass;
            }
        });
    }
}
