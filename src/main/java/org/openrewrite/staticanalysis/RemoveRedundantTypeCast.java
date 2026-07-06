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
import org.openrewrite.*;
import org.openrewrite.java.JavaVisitor;
import org.openrewrite.java.tree.*;
import org.openrewrite.staticanalysis.kotlin.KotlinFileChecker;

import java.time.Duration;
import java.util.List;
import java.util.Set;

import static java.util.Collections.singleton;
import static org.openrewrite.staticanalysis.LambdaBlockToExpression.hasMethodOverloading;

@Incubating(since = "7.23.0")
public class RemoveRedundantTypeCast extends Recipe {
    private static final String REMOVE_UNNECESSARY_PARENTHESES = "removeUnnecessaryParentheses";

    @Getter
    final String displayName = "Remove redundant casts";

    @Getter
    final String description = "Removes unnecessary type casts. Does not currently check casts in lambdas " +
            "and class constructors. Redundant casts add visual noise and can obscure the actual type " +
            "relationships in the code, making it harder to follow the data flow.";

    @Getter
    final Duration estimatedEffortPerOccurrence = Duration.ofMinutes(2);

    @Getter
    final Set<String> tags = singleton("RSPEC-S1905");

    @Override
    public TreeVisitor<?, ExecutionContext> getVisitor() {
        return Preconditions.check(Preconditions.not(new KotlinFileChecker<>()), new JavaVisitor<ExecutionContext>() {
            @Override
            public J visitTypeCast(J.TypeCast typeCast, ExecutionContext ctx) {
                J visited = super.visitTypeCast(typeCast, ctx);
                if (!(visited instanceof J.TypeCast)) {
                    return visited;
                }

                // A cast nested inside another cast typically bridges an unchecked generic
                // conversion (e.g. `(List<String>) (Object) values`); preserve it.
                Cursor parentTreeCursor = getCursor().getParentTreeCursor();
                while (parentTreeCursor.getValue() instanceof J.Parentheses ||
                        parentTreeCursor.getValue() instanceof J.ControlParentheses) {
                    parentTreeCursor = parentTreeCursor.getParentTreeCursor();
                }
                if (parentTreeCursor.getValue() instanceof J.TypeCast) {
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

                // A cast on a generic method invocation pins the inferred return type so
                // an overloaded outer call (e.g. `StringBuilder.append`) resolves unambiguously.
                if (parentValue instanceof MethodCall) {
                    JavaType.Method parentMethodType = ((MethodCall) parentValue).getMethodType();
                    if ((parentMethodType == null || hasMethodOverloading(parentMethodType)) &&
                            returnsDeclaredTypeParameter(typeCast.getExpression())) {
                        return visited;
                    }
                }

                // When the cast is an argument to an overloaded method, removing it can
                // make resolution ambiguous (e.g. `log(String, Object...)` vs
                // `log(String, Throwable)` selected by `(Object) value`). Bail out before
                // any other branch decides the cast is redundant.
                if (parentValue instanceof MethodCall) {
                    JavaType.Method methodType = ((MethodCall) parentValue).getMethodType();
                    if (methodType == null || hasMethodOverloading(methodType)) {
                        return visited;
                    }
                }

                // A raw cast on a parameterized expression (e.g. `(B) b` where `b` is `B<T>`,
                // or `(Collection) set` where `set` is `Set<? extends G>`) is an intentional
                // unchecked conversion; removing it changes overload and type-inference behavior
                // on parameterized arguments, which can break compilation.
                JavaType.Parameterized exprParameterized = TypeUtils.asParameterized(expressionType);
                if (parentValue instanceof MethodCall && exprParameterized != null &&
                        TypeUtils.asFullyQualified(castType) != null &&
                        TypeUtils.asParameterized(castType) == null &&
                        TypeUtils.isAssignableTo(castType, expressionType)) {
                    return visited;
                }

                JavaType targetType = null;
                if (castType.equals(expressionType)) {
                    targetType = castType;
                } else if (parentValue instanceof J.VariableDeclarations) {
                    targetType = ((J.VariableDeclarations) parentValue).getVariables().get(0).getType();
                } else if (parentValue instanceof MethodCall) {
                    MethodCall methodCall = (MethodCall) parentValue;
                    JavaType.Method methodType = methodCall.getMethodType();
                    if (!methodType.getParameterTypes().isEmpty()) {
                        List<Expression> arguments = methodCall.getArguments();
                        for (int i = 0; i < arguments.size(); i++) {
                            Expression arg = arguments.get(i);
                            if (arg == typeCast) {
                                // A `null` literal cast at the last position of a (potential) varargs call
                                // disambiguates between passing `null` as the array vs. a single null element.
                                if (J.Literal.isLiteralValue(typeCast.getExpression(), null) &&
                                        i == methodType.getParameterTypes().size() - 1 &&
                                        i == arguments.size() - 1 &&
                                        methodType.getParameterTypes().get(i) instanceof JavaType.Array) {
                                    return visited;
                                }
                                // A cast to a non-array type (e.g. `(Object)`) on an array-typed argument at the
                                // varargs position marks the array as a single varargs element rather than being
                                // spread, and silences Error Prone's `PrimitiveArrayPassedToVarargsMethod`.
                                // Removing it changes argument semantics, so preserve the cast.
                                if (i == methodType.getParameterTypes().size() - 1 &&
                                        i == arguments.size() - 1 &&
                                        methodType.getParameterTypes().get(i) instanceof JavaType.Array &&
                                        typeCast.getExpression().getType() instanceof JavaType.Array &&
                                        !(castType instanceof JavaType.Array)) {
                                    return visited;
                                }
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
                J castExpression = typeCast.getExpression();
                while (castExpression instanceof J.Parentheses || castExpression instanceof J.ControlParentheses) {
                    castExpression = castExpression instanceof J.Parentheses ?
                            ((J.Parentheses<?>) castExpression).getTree() :
                            ((J.ControlParentheses<?>) castExpression).getTree();
                }
                if (castExpression instanceof J.Lambda || castExpression instanceof J.MemberReference) {
                    // Not currently supported, this will be more accurate with dataflow analysis.
                    // The cast supplies the target type for the lambda or method reference; removing it
                    // can break compilation, e.g. when assigned to a `var` local.
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

                // A cast inside a lambda body may pin the inferred return type of a generic method call
                // so outer inference (e.g. Optional.map -> ifPresent) doesn't lose it to the type bound.
                if (parentValue instanceof J.Lambda && returnsDeclaredTypeParameter(typeCast.getExpression())) {
                    return visitedTypeCast;
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

            private boolean returnsDeclaredTypeParameter(Expression expression) {
                if (!(expression instanceof J.MethodInvocation)) {
                    return false;
                }
                JavaType.Method invokedMethod = ((J.MethodInvocation) expression).getMethodType();
                if (invokedMethod == null || invokedMethod.getDeclaringType() == null) {
                    return false;
                }
                for (JavaType.Method declared : invokedMethod.getDeclaringType().getMethods()) {
                    if (declared.getName().equals(invokedMethod.getName()) &&
                            declared.getParameterTypes().size() == invokedMethod.getParameterTypes().size() &&
                            declared.getReturnType() instanceof JavaType.GenericTypeVariable &&
                            declared.getDeclaredFormalTypeNames().contains(
                                    ((JavaType.GenericTypeVariable) declared.getReturnType()).getName())) {
                        return true;
                    }
                }
                return false;
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
        });
    }
}
