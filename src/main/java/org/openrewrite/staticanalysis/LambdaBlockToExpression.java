/*
 * Copyright 2021 the original author or authors.
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
import org.openrewrite.java.JavaIsoVisitor;
import org.openrewrite.java.tree.*;
import org.openrewrite.staticanalysis.java.JavaFileChecker;

import java.util.List;
import java.util.Optional;

public class LambdaBlockToExpression extends Recipe {
    @Override
    public String getDisplayName() {
        return "Simplify lambda blocks to expressions";
    }

    @Override
    public String getDescription() {
        return "Single-line statement lambdas returning a value can be replaced with expression lambdas.";
    }

    @Override
    public TreeVisitor<?, ExecutionContext> getVisitor() {
        return Preconditions.check(new JavaFileChecker<>(),
                new JavaIsoVisitor<ExecutionContext>() {
                    @Override
                    public J.Lambda visitLambda(J.Lambda lambda, ExecutionContext ctx) {
                        J.Lambda l = super.visitLambda(lambda, ctx);
                        if (lambda.getBody() instanceof J.Block) {
                            List<Statement> statements = ((J.Block) lambda.getBody()).getStatements();
                            if (statements.size() == 1) {
                                Statement statement = statements.get(0);
                                Space prefix = statement.getPrefix();
                                if (statement instanceof J.Return) {
                                    Expression expression = ((J.Return) statement).getExpression();
                                        if (prefix.getComments().isEmpty()) {
                                            //noinspection DataFlowIssue
                                            return l.withBody(expression);
                                        } else if (expression != null) {
                                            return l.withBody(expression.withPrefix(prefix));
                                        }
                                } else if (statement instanceof J.MethodInvocation) {
                                    if (prefix.getComments().isEmpty()) {
                                        return l.withBody(statement);
                                    } else {
                                        return l.withBody(statement.withPrefix(prefix));
                                    }
                                }
                            }
                        }
                        return l;
                    }

                    @Override
                    public J.MethodInvocation visitMethodInvocation(J.MethodInvocation method, ExecutionContext ctx) {
                        if (hasLambdaArgument(method) && hasMethodOverloading(method)) {
                            return method;
                        }
                        return super.visitMethodInvocation(method, ctx);
                    }
                }
        );
    }

    // Check whether a method has overloading methods in the declaring class
    private static boolean hasMethodOverloading(J.MethodInvocation method) {
        JavaType.Method methodType = method.getMethodType();
        return methodType != null && hasMethodOverloading(methodType);
    }

    // TODO this is actually more complex in the presence of generics and inheritance
    static boolean hasMethodOverloading(JavaType.Method methodType) {
        String methodName = methodType.getName();
        int numberOfParameters = methodType.getParameterNames().size();
        return Optional.of(methodType)
                .map(JavaType.Method::getDeclaringType)
                .filter(JavaType.Class.class::isInstance)
                .map(JavaType.Class.class::cast)
                .map(JavaType.Class::getMethods)
                .map(methods -> {
                    int overloadingCount = 0;
                    for (JavaType.Method dm : methods) {
                        if (methodName.equals(dm.getName()) &&
                            numberOfParameters == dm.getParameterNames().size()) {
                            if (++overloadingCount > 1) {
                                return true;
                            }
                        }
                    }
                    return false;
                })
                .orElse(false);
    }

    private static boolean hasLambdaArgument(J.MethodInvocation method) {
        boolean hasLambdaArgument = false;
        for (Expression arg : method.getArguments()) {
            if (arg instanceof J.Lambda) {
                hasLambdaArgument = true;
                break;
            }
        }
        return hasLambdaArgument;
    }
}
