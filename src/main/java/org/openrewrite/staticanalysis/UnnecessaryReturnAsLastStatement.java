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
import org.openrewrite.ExecutionContext;
import org.openrewrite.Recipe;
import org.openrewrite.TreeVisitor;
import org.openrewrite.internal.ListUtils;
import org.openrewrite.java.JavaIsoVisitor;
import org.openrewrite.java.tree.J;
import org.openrewrite.java.tree.JavaType;
import org.openrewrite.java.tree.Statement;
import org.openrewrite.java.tree.TypeUtils;

public class UnnecessaryReturnAsLastStatement extends Recipe {
    @Override
    public String getDisplayName() {
        return "Unnecessary `return` as last statement in void method";
    }

    @Override
    public String getDescription() {
        return "Removes `return` from a `void` method if it's the last statement.";
    }

    @Override
    public TreeVisitor<?, ExecutionContext> getVisitor() {
        return new JavaIsoVisitor<ExecutionContext>() {
            @Override
            public J.MethodDeclaration visitMethodDeclaration(J.MethodDeclaration method, ExecutionContext ctx) {
                J.MethodDeclaration m = super.visitMethodDeclaration(method, ctx);
                if (TypeUtils.asPrimitive(m.getType()) == JavaType.Primitive.Void) {
                    return m.withBody(maybeRemoveReturnAsLastStatement(m.getBody()));
                }
                return m;
            }

            private J.@Nullable Block maybeRemoveReturnAsLastStatement(J.@Nullable Block b) {
                if (b == null) {
                    return null;
                }

                return b.withStatements(ListUtils.mapLast(b.getStatements(), lastStatement -> {
                    if (lastStatement instanceof J.Return && ((J.Return) lastStatement).getExpression() == null) {
                        return null;
                    } else if (lastStatement instanceof J.If) {
                        J.If ifStatement = (J.If) lastStatement;
                        ifStatement = ifStatement.withThenPart(maybeRemoveReturnAsLastStatement(ifStatement.getThenPart()));
                        J.If.Else elze = ifStatement.getElsePart();
                        if (elze != null) {
                            return ifStatement.withElsePart(elze.withBody(maybeRemoveReturnAsLastStatement(elze.getBody())));
                        }
                        return ifStatement;
                    } else {
                        return lastStatement;
                    }
                }));
            }

            private Statement maybeRemoveReturnAsLastStatement(Statement s) {
                return s instanceof J.Block ? maybeRemoveReturnAsLastStatement((J.Block) s) : s;
            }
        };
    }
}
