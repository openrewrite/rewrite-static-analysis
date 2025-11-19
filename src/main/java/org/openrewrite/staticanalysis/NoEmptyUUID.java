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

import org.openrewrite.*;
import org.openrewrite.java.JavaIsoVisitor;
import org.openrewrite.java.JavaTemplate;
import org.openrewrite.java.MethodMatcher;
import org.openrewrite.java.tree.*;

public class NoEmptyUUID extends Recipe {

    @Override
    public String getDisplayName() {
        return "Remove impossible UUID emptiness checks";
    }

    @Override
    public String getDescription() {
        return "UUID.toString() always returns a non-empty string, so isEmpty() and isBlank() always return false.";
    }

    @Override
    public TreeVisitor<?, ExecutionContext> getVisitor() {
        return new JavaIsoVisitor<ExecutionContext>() {
            private final MethodMatcher uuidToStringMatcher = new MethodMatcher("java.util.UUID toString()");
            private final MethodMatcher isEmptyMatcher = new MethodMatcher("java.lang.String isEmpty()");
            private final MethodMatcher isBlankMatcher = new MethodMatcher("java.lang.String isBlank()");

            @Override
            public J.MethodInvocation visitMethodInvocation(J.MethodInvocation method, ExecutionContext ctx) {
                // Skip processing if this is already a replaced literal (not a real method invocation)
                if (method.getMethodType() == null) {
                    return method;
                }

                J.MethodInvocation mi = super.visitMethodInvocation(method, ctx);

                // Check if this is a call to isEmpty() or isBlank()
                if (isEmptyMatcher.matches(mi) || isBlankMatcher.matches(mi)) {
                    Expression select = mi.getSelect();

                    // Check if the select is a UUID.toString() call or chain
                    if (isUUIDToStringCall(select)) {
                        // Replace with false
                        return JavaTemplate.builder("false")
                                .contextSensitive()
                                .build()
                                .apply(getCursor(), mi.getCoordinates().replace());
                    }
                }

                return mi;
            }

            private boolean isUUIDToStringCall(Expression expression) {
                if (expression instanceof J.MethodInvocation) {
                    J.MethodInvocation mi = (J.MethodInvocation) expression;

                    // Direct UUID.toString() call
                    if (uuidToStringMatcher.matches(mi)) {
                        return true;
                    }

                    // Chained calls like UUID.toString().trim()
                    if (mi.getSelect() != null) {
                        return isUUIDToStringCall(mi.getSelect());
                    }
                }

                // Check if this is an identifier that references a UUID string
                if (expression instanceof J.Identifier) {
                    J.Identifier identifier = (J.Identifier) expression;
                    return isUUIDStringVariable(identifier);
                }

                return false;
            }

            private boolean isUUIDStringVariable(J.Identifier identifier) {
                // Look for variable declarations that come from UUID.toString()
                Cursor parent = getCursor().dropParentUntil(is ->
                        is instanceof J.VariableDeclarations || is instanceof J.MethodInvocation);

                if (parent.getValue() instanceof J.VariableDeclarations) {
                    J.VariableDeclarations varDecl = (J.VariableDeclarations) parent.getValue();
                    for (J.VariableDeclarations.NamedVariable variable : varDecl.getVariables()) {
                        if (variable.getName().getSimpleName().equals(identifier.getSimpleName())) {
                            Expression initializer = variable.getInitializer();
                            return initializer != null && isUUIDToStringCall(initializer);
                        }
                    }
                }

                return false;
            }

            @Override
            public J.Lambda visitLambda(J.Lambda lambda, ExecutionContext ctx) {
                J.Lambda l = super.visitLambda(lambda, ctx);

                if (l.getBody() instanceof J.MethodInvocation) {
                    J.MethodInvocation body = (J.MethodInvocation) l.getBody();
                    if ((isEmptyMatcher.matches(body) || isBlankMatcher.matches(body)) &&
                            isUUIDToStringCall(body.getSelect())) {
                        // Replace the lambda body with false
                        l = l.withBody(
                                JavaTemplate.builder("false")
                                        .contextSensitive()
                                        .build()
                                        .apply(getCursor(), body.getCoordinates().replace())
                        );
                    }
                }

                return l;
            }

            @Override
            public J.If visitIf(J.If iff, ExecutionContext ctx) {
                J.If ifStatement = super.visitIf(iff, ctx);

                // Check if the condition contains a UUID emptiness check
                if (ifStatement.getIfCondition() instanceof J.ControlParentheses) {
                    J.ControlParentheses<Expression> controlParens = (J.ControlParentheses<Expression>) ifStatement.getIfCondition();
                    Expression condition = controlParens.getTree();

                    if (condition instanceof J.MethodInvocation) {
                        J.MethodInvocation conditionMi = (J.MethodInvocation) condition;
                        if ((isEmptyMatcher.matches(conditionMi) || isBlankMatcher.matches(conditionMi)) &&
                                isUUIDToStringCall(conditionMi.getSelect())) {
                            // Remove the entire if statement since the condition is always false
                            return null;
                        }
                    }
                }

                return ifStatement;
            }

            @Override
            public J.WhileLoop visitWhileLoop(J.WhileLoop whileLoop, ExecutionContext ctx) {
                J.WhileLoop wl = super.visitWhileLoop(whileLoop, ctx);

                // Check if the condition contains a UUID emptiness check
                if (wl.getCondition() instanceof J.ControlParentheses) {
                    J.ControlParentheses<Expression> controlParens = (J.ControlParentheses<Expression>) wl.getCondition();
                    Expression condition = controlParens.getTree();

                    if (condition instanceof J.MethodInvocation) {
                        J.MethodInvocation conditionMi = (J.MethodInvocation) condition;
                        if ((isEmptyMatcher.matches(conditionMi) || isBlankMatcher.matches(conditionMi)) &&
                                isUUIDToStringCall(conditionMi.getSelect())) {
                            // Replace with while (false)
                            return wl.withCondition(
                                    JavaTemplate.builder("(false)")
                                            .contextSensitive()
                                            .build()
                                            .apply(getCursor(), wl.getCondition().getCoordinates().replace())
                            );
                        }
                    }
                }

                return wl;
            }

            @Override
            public J.DoWhileLoop visitDoWhileLoop(J.DoWhileLoop doWhileLoop, ExecutionContext ctx) {
                J.DoWhileLoop dwl = super.visitDoWhileLoop(doWhileLoop, ctx);

                // Check if the condition contains a UUID emptiness check
                if (dwl.getWhileCondition() instanceof J.ControlParentheses) {
                    J.ControlParentheses<Expression> controlParens = (J.ControlParentheses<Expression>) dwl.getWhileCondition();
                    Expression condition = controlParens.getTree();

                    if (condition instanceof J.MethodInvocation) {
                        J.MethodInvocation conditionMi = (J.MethodInvocation) condition;
                        if ((isEmptyMatcher.matches(conditionMi) || isBlankMatcher.matches(conditionMi)) &&
                                isUUIDToStringCall(conditionMi.getSelect())) {
                            // Replace with while (false)
                            return dwl.withWhileCondition(
                                    JavaTemplate.builder("(false)")
                                            .contextSensitive()
                                            .build()
                                            .apply(getCursor(), dwl.getWhileCondition().getCoordinates().replace())
                            );
                        }
                    }
                }

                return dwl;
            }
        };
    }
}
