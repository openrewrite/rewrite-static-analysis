/*
 * Copyright 2022 the original author or authors.
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

import org.openrewrite.*;
import org.openrewrite.java.JavaVisitor;
import org.openrewrite.java.tree.*;

import java.time.Duration;
import java.util.Collections;
import java.util.List;
import java.util.Set;

@Incubating(since = "7.23.0")
public class RemoveRedundantTypeCast extends Recipe {
    @Override
    public String getDisplayName() {
        return "Remove redundant casts";
    }

    @Override
    public String getDescription() {
        return "Removes unnecessary type casts. Does not currently check casts in lambdas, class constructors, and method invocations.";
    }

    @Override
    public Duration getEstimatedEffortPerOccurrence() {
        return Duration.ofMinutes(2);
    }

    @Override
    public Set<String> getTags() {
        return Collections.singleton("RSPEC-1905");
    }

    @Override
    public TreeVisitor<?, ExecutionContext> getVisitor() {
        return new JavaVisitor<ExecutionContext>() {
            @Override
            public J visitTypeCast(J.TypeCast typeCast, ExecutionContext ctx) {
                J visited = super.visitTypeCast(typeCast, ctx);
                if (!(visited instanceof J.TypeCast)) {
                    return visited;
                }

                Cursor parent = getCursor().dropParentUntil(is -> is instanceof J.VariableDeclarations ||
                                                                  is instanceof J.Lambda ||
                                                                  is instanceof J.Return ||
                                                                  is instanceof MethodCall ||
                                                                  is instanceof J.MethodDeclaration ||
                                                                  is instanceof J.ClassDeclaration ||
                                                                  is instanceof JavaSourceFile);

                J parentValue = parent.getValue();

                JavaType targetType = null;
                if (parentValue instanceof J.VariableDeclarations) {
                    targetType = ((J.VariableDeclarations) parentValue).getVariables().get(0).getType();
                } else if (parentValue instanceof MethodCall) {
                    MethodCall methodCall = (MethodCall) parentValue;
                    JavaType.Method methodType = methodCall.getMethodType();
                    if (methodType != null && !methodType.getParameterTypes().isEmpty()) {
                        List<Expression> arguments = methodCall.getArguments();
                        for (int i = 0; i < arguments.size(); i++) {
                            Expression arg = arguments.get(i);
                            if (arg == typeCast) {
                                targetType = methodType.getParameterTypes().get(i);
                                break;
                            }
                        }
                    }
                } else if (parentValue instanceof J.Return && ((J.Return) parentValue).getExpression() == typeCast) {
                    parent = parent.dropParentUntil(is -> is instanceof J.Lambda ||
                                                          is instanceof J.MethodDeclaration ||
                                                          is instanceof J.ClassDeclaration ||
                                                          is instanceof JavaSourceFile);
                    if (parent.getValue() instanceof J.MethodDeclaration && ((J.MethodDeclaration) parent.getValue()).getMethodType() != null) {
                        targetType = ((J.MethodDeclaration) parent.getValue()).getMethodType().getReturnType();
                    }
                }

                J.TypeCast visitedTypeCast = (J.TypeCast) visited;
                JavaType expressionType = visitedTypeCast.getExpression().getType();

                if (targetType == null) {
                    // Not currently supported, this will be more accurate with dataflow analysis.
                    return visitedTypeCast;
                } else if (!(targetType instanceof JavaType.Array) && TypeUtils.isOfClassType(targetType, "java.lang.Object") ||
                           TypeUtils.isOfType(targetType, expressionType) ||
                           TypeUtils.isAssignableTo(targetType, expressionType)) {
                    return visitedTypeCast.getExpression().withPrefix(visitedTypeCast.getPrefix());
                }
                return visitedTypeCast;
            }
        };
    }
}
