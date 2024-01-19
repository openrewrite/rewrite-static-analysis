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

import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

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
                                        return l.withBody(expression);
                                    } else {
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
                        if (hasLambdaArgument(method) && hasAmbiguousMethodOverloading(method)) {
                            return method;
                        }
                        return super.visitMethodInvocation(method, ctx);
                    }
                }
        );
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

    // Check whether a method has overloading methods in the declaring class
    private static boolean hasAmbiguousMethodOverloading(J.MethodInvocation method) {
        JavaType.Method methodType = method.getMethodType();
        String methodName = methodType.getName();
        return methodType != null && hasAmbiguousMethodOverloading(methodType, method.getArguments(), methodName);
    }

    static boolean hasAmbiguousMethodOverloading(MethodCall method) {
        JavaType.Method methodType = method.getMethodType();
        String methodName = methodType.getName();
        return methodType != null && hasAmbiguousMethodOverloading(methodType, method.getArguments(), methodName);
    }

    // TODO this is actually more complex in the presence of generics and inheritance
    static boolean hasAmbiguousMethodOverloading(JavaType.Method methodType, List<Expression> arguments, String methodName) {
        int numberOfArguments = arguments.size();

        //all methods of the given type
        List<JavaType.Method> methodsOfType = Optional.of(methodType)
                .map(JavaType.Method::getDeclaringType)
                .filter(JavaType.Class.class::isInstance)
                .map(JavaType.Class.class::cast)
                .map(JavaType.Class::getMethods)
                .orElse(Collections.emptyList());

        List<JavaType.Method> potentiallyOverLoadedMethods = methodsOfType.stream()
                .filter(dm -> dm.getName().equals(methodName))
                .filter(dm -> dm.getParameterTypes().size() == numberOfArguments)
                .collect(Collectors.toList());

        //if there are less than 2 such methods, then there is no ambiguity
        if(potentiallyOverLoadedMethods.size() <= 1) {
            return false;
        }

        //if there is a position where
        //  - the argument is a lambda
        //  - the parameters of all potential methods have the same type
        // then there is no ambiguity
        for (int i = 0; i < numberOfArguments; i++) {
            int finalI = i;
            if (arguments.get(i) instanceof J.Lambda) {
                long distinctElementsCount = potentiallyOverLoadedMethods.stream()
                    .map(m -> m.getParameterTypes().get(finalI))
                    .distinct().count();
                if (distinctElementsCount == 1) {
                    return false;
                }
            }
        }
        //otherwise, there must be ambiguity
        return true;
    }

}
