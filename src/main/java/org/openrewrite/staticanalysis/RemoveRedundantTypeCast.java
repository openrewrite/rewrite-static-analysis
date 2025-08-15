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
import org.openrewrite.java.JavaVisitor;
import org.openrewrite.java.tree.*;

import java.time.Duration;
import java.util.List;
import java.util.Set;

import static java.util.Collections.singleton;
import static org.openrewrite.staticanalysis.LambdaBlockToExpression.hasMethodOverloading;

@Incubating(since = "7.23.0")
public class RemoveRedundantTypeCast extends Recipe {
    private static final String REMOVE_UNNECESSARY_PARENTHESES = "removeUnnecessaryParentheses";

    @Override
    public String getDisplayName() {
        return "Remove redundant casts";
    }

    @Override
    public String getDescription() {
        return "Removes unnecessary type casts. Does not currently check casts in lambdas and class constructors.";
    }

    @Override
    public Duration getEstimatedEffortPerOccurrence() {
        return Duration.ofMinutes(2);
    }

    @Override
    public Set<String> getTags() {
        return singleton("RSPEC-S1905");
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

                J.TypeCast visitedTypeCast = (J.TypeCast) visited;
                JavaType expressionType = visitedTypeCast.getExpression().getType();
                JavaType castType = visitedTypeCast.getType();

                JavaType targetType = null;
                // Null-check; because for Kotlin's Gradle files, we don't always have all type information available
                if (castType != null && castType.equals(expressionType)) {
                    targetType = castType;
                } else if (parentValue instanceof J.VariableDeclarations) {
                    targetType = ((J.VariableDeclarations) parentValue).getVariables().get(0).getType();
                } else if (parentValue instanceof MethodCall) {
                    MethodCall methodCall = (MethodCall) parentValue;
                    JavaType.Method methodType = methodCall.getMethodType();
                    if (methodType == null || hasMethodOverloading(methodType)) {
                        return visited;
                    }
                    if (!methodType.getParameterTypes().isEmpty()) {
                        List<Expression> arguments = methodCall.getArguments();
                        for (int i = 0; i < arguments.size(); i++) {
                            Expression arg = arguments.get(i);
                            if (arg == typeCast) {
                                targetType = getParameterType(methodType, i);
                                break;
                            }
                        }
                    }
                    if (TypeUtils.isAssignableTo(castType, expressionType)) {
                        targetType = castType;
                    }
                } else if (parentValue instanceof J.Return && expressionIsTypeCast((J.Return) parentValue, typeCast)) {
                    parent = parent.dropParentUntil(is -> is instanceof J.Lambda ||
                                                          is instanceof J.MethodDeclaration ||
                                                          is instanceof J.ClassDeclaration ||
                                                          is instanceof JavaSourceFile);
                    if (parent.getValue() instanceof J.MethodDeclaration && ((J.MethodDeclaration) parent.getValue()).getMethodType() != null) {
                        JavaType.Method methodType = ((J.MethodDeclaration) parent.getValue()).getMethodType();
                        targetType = methodType.getReturnType();
                    }
                }

                if (targetType == null) {
                    return visitedTypeCast;
                }
                if ((targetType instanceof JavaType.Primitive || castType instanceof JavaType.Primitive) && castType != expressionType) {
                    return visitedTypeCast;
                }
                if (typeCast.getExpression() instanceof J.Lambda || typeCast.getExpression() instanceof J.MemberReference) {
                    // Not currently supported, this will be more accurate with dataflow analysis.
                    return visitedTypeCast;
                }

                // Special case: if this cast is in a generic method call that's part of a method chain,
                // the cast might be necessary to control generic type inference
                if (parentValue instanceof J.MethodInvocation &&
                        TypeUtils.isAssignableTo(castType, expressionType) &&
                        !castType.equals(expressionType)) {
                    // Check if the method returns a generic type
                    JavaType.Method methodType = ((J.MethodInvocation) parentValue).getMethodType();
                    if (methodType != null && methodType.getReturnType() instanceof JavaType.Parameterized) {
                        // This cast is widening the type (e.g., BarImpl to Bar) in a generic context
                        // which might affect how the generic type is inferred in method chains
                        // Keep the cast to be safe
                        return visitedTypeCast;
                    }
                }

                if (!(targetType instanceof JavaType.Array) && TypeUtils.isOfClassType(targetType, "java.lang.Object") ||
                    TypeUtils.isOfType(targetType, expressionType) ||
                    TypeUtils.isAssignableTo(targetType, expressionType)) {
                    JavaType.FullyQualified fullyQualified = TypeUtils.asFullyQualified(castType);
                    if (fullyQualified != null) {
                        maybeRemoveImport(fullyQualified.getFullyQualifiedName());
                    }
                    Cursor directParent = getCursor().getParent();
                    if (directParent != null && directParent.getParent() != null && directParent.getParent().getValue() instanceof J.Parentheses) {
                        directParent.getParent().putMessage(REMOVE_UNNECESSARY_PARENTHESES, true);
                    }
                    return visitedTypeCast.getExpression().withPrefix(visitedTypeCast.getPrefix());
                }
                return visitedTypeCast;
            }

            @Override
            public <T extends J> J visitParentheses(J.Parentheses<T> parens, ExecutionContext ctx) {
                J.Parentheses<T> parentheses = (J.Parentheses<T>) super.visitParentheses(parens, ctx);
                if (getCursor().getMessage(REMOVE_UNNECESSARY_PARENTHESES, false)) {
                    return parentheses.getTree().withPrefix(parentheses.getPrefix());
                }
                return parentheses;
            }

            private JavaType getParameterType(JavaType.Method method, int arg) {
                List<JavaType> parameterTypes = method.getParameterTypes();
                if (parameterTypes.size() > arg) {
                    return parameterTypes.get(arg);
                }
                // varargs?
                JavaType type = parameterTypes.get(parameterTypes.size() - 1);
                return type instanceof JavaType.Array ? ((JavaType.Array) type).getElemType() : type;
            }

            private boolean expressionIsTypeCast(J.Return return_, J.TypeCast typeCast) {
                if (return_.getExpression() instanceof J.Parentheses<?>) {
                    return expressionIsTypeCast((J.Parentheses<?>) return_.getExpression(), typeCast);
                }
                return return_.getExpression() == typeCast;
            }

            private boolean expressionIsTypeCast(J.Parentheses<?> parentheses, J.TypeCast typeCast) {
                if (parentheses.getTree() instanceof J.Parentheses) {
                    return expressionIsTypeCast((J.Parentheses<?> ) parentheses.getTree(), typeCast);
                }
               return parentheses.getTree() == typeCast;
            }
        };
    }
}
