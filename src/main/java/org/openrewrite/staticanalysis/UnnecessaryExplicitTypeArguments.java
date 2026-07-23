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
import org.jspecify.annotations.Nullable;
import org.openrewrite.*;
import org.openrewrite.java.JavaIsoVisitor;
import org.openrewrite.java.tree.*;
import org.openrewrite.staticanalysis.java.JavaFileChecker;

import java.util.ArrayList;
import java.util.List;

public class UnnecessaryExplicitTypeArguments extends Recipe {

    @Getter
    final String displayName = "Unnecessary explicit type arguments";

    @Getter
    final String description = "When explicit type arguments are inferable by the compiler, they may be removed.";

    @Override
    public TreeVisitor<?, ExecutionContext> getVisitor() {
        return Preconditions.check(new JavaFileChecker<>(), new JavaIsoVisitor<ExecutionContext>() {
            @Override
            public J.MethodInvocation visitMethodInvocation(J.MethodInvocation method, ExecutionContext ctx) {
                J.MethodInvocation m = super.visitMethodInvocation(method, ctx);

                JavaType.Method methodType = m.getMethodType();
                if (methodType == null || m.getTypeParameters() == null || m.getTypeParameters().isEmpty()) {
                    return m;
                }

                Object enclosing = getCursor().getParentTreeCursor().getValue();

                if (enclosing instanceof J.Ternary) {
                    return m; // may be necessary for type inference
                }

                JavaType inferredType = null;
                if (enclosing instanceof J.MethodInvocation) {
                    J.MethodInvocation enclosingMethod = (J.MethodInvocation) enclosing;
                    if (enclosingMethod.getSelect() == method) {
                        // This invocation is the select (receiver) of the enclosing invocation, so the
                        // enclosing call provides no target type to drive inference of this call's type
                        // variables. Retain the witness unless those type variables can be inferred from
                        // this call's own arguments.
                        if (!canInferTypeArgumentsFromArguments(methodType)) {
                            return m;
                        }
                    } else {
                        // As above, retain unless inferable from this call's own arguments (static or not).
                        if (!canInferTypeArgumentsFromArguments(methodType)) {
                            return m;
                        }
                        // If the enclosing method has dependent type parameters (e.g., <T, S extends T>),
                        // removing the explicit type argument from this call can prevent the compiler from
                        // correctly inferring the dependent type parameters. For example:
                        //   lexicographical(Comparator.<Integer>naturalOrder())
                        // where lexicographical is declared as <T, S extends T>, the explicit <Integer>
                        // is required so the compiler can bind both T and S correctly.
                        if (hasDependentTypeParameters(enclosingMethod.getMethodType())) {
                            return m;
                        }
                        // Cannot remove type parameters if it would introduce ambiguity about which method should be called
                        if (enclosingMethod.getMethodType() == null) {
                            return m;
                        }
                        if (!(enclosingMethod.getMethodType().getDeclaringType() instanceof JavaType.Class)) {
                            return m;
                        }
                        JavaType.Class declaringClass = (JavaType.Class) enclosingMethod.getMethodType().getDeclaringType();
                        // If there's another method on the class with the same name, skip removing type parameters
                        // More nuanced detection of ambiguity introduction is possible
                        if (declaringClass.getMethods().stream()
                                .filter(it -> it.getName().equals(enclosingMethod.getSimpleName()))
                                .count() > 1) {
                            return m;
                        }
                    }
                    inferredType = methodType.getReturnType();
                } else if (enclosing instanceof Expression) {
                    inferredType = ((Expression) enclosing).getType();
                } else if (enclosing instanceof NameTree) {
                    if (enclosing instanceof J.VariableDeclarations.NamedVariable) {
                        J.VariableDeclarations decl = getCursor().getParentTreeCursor().getParentTreeCursor().getValue();
                        if (decl.getTypeExpression() instanceof J.Identifier && "var".equals(((J.Identifier) decl.getTypeExpression()).getSimpleName())) {
                            return m;
                        }
                    }
                    inferredType = ((NameTree) enclosing).getType();
                } else if (enclosing instanceof J.Return) {
                    Object e = getCursor().dropParentUntil(p -> p instanceof J.MethodDeclaration || p instanceof J.Lambda || Cursor.ROOT_VALUE.equals(p)).getValue();
                    if (e instanceof J.MethodDeclaration) {
                        J.MethodDeclaration methodDeclaration = (J.MethodDeclaration) e;
                        if (methodDeclaration.getReturnTypeExpression() != null) {
                            inferredType = methodDeclaration.getReturnTypeExpression().getType();
                        }
                    } else if (e instanceof J.Lambda) {
                        inferredType = getLambdaReturnType(((J.Lambda) e).getType());
                    }
                }

                if (inferredType != null && TypeUtils.isOfType(inferredType, methodType.getReturnType())) {
                    m = m.withTypeParameters(null);
                }

                return m;
            }

            private JavaType.@Nullable Method findMethodIfUnambiguous(JavaType.FullyQualified type) {
                JavaType.Method sam = null;
                for (JavaType.Method candidate : type.getMethods()) {
                    if (candidate.hasFlags(Flag.Default) || candidate.hasFlags(Flag.Static)) {
                        continue;
                    }
                    if (sam != null) {
                        return null;
                    }
                    sam = candidate;
                }
                return sam;
            }

            private @Nullable JavaType getLambdaReturnType(@Nullable JavaType lambdaType) {
                JavaType.Parameterized parameterized = TypeUtils.asParameterized(lambdaType);
                if (parameterized == null) {
                    return null;
                }
                JavaType.Method sam = findMethodIfUnambiguous(parameterized);
                if (sam == null) {
                    return null;
                }
                JavaType samReturn = sam.getReturnType();
                if (samReturn instanceof JavaType.GenericTypeVariable) {
                    String name = ((JavaType.GenericTypeVariable) samReturn).getName();
                    List<JavaType> formalParams = parameterized.getType().getTypeParameters();
                    List<JavaType> actualParams = parameterized.getTypeParameters();
                    for (int i = 0; i < formalParams.size() && i < actualParams.size(); i++) {
                        JavaType formal = formalParams.get(i);
                        if (formal instanceof JavaType.GenericTypeVariable &&
                                name.equals(((JavaType.GenericTypeVariable) formal).getName())) {
                            return actualParams.get(i);
                        }
                    }
                    return null;
                }
                return samReturn;
            }

            private boolean canInferTypeArgumentsFromArguments(JavaType.Method methodType) {
                // Without arguments, the type parameters cannot be inferred from call-site arguments.
                if (methodType.getParameterTypes().isEmpty()) {
                    return false;
                }
                List<String> formalTypeNames = new ArrayList<>(methodType.getDeclaredFormalTypeNames());
                methodType.getParameterTypes().stream()
                        .filter(p -> p instanceof JavaType.Parameterized)
                        .flatMap(p -> ((JavaType.Parameterized) p).getTypeParameters().stream())
                        .filter(t -> t instanceof JavaType.GenericTypeVariable)
                        .forEach(it -> formalTypeNames.remove(((JavaType.GenericTypeVariable) it).getName()));
                return formalTypeNames.isEmpty();
            }

            /**
             * Returns true if the given method has dependent type parameters — that is, at least one
             * type parameter has a bound that references another type parameter declared by the same
             * method (e.g., {@code <T, S extends T>}).
             * <p>
             * When such dependencies exist, removing explicit type arguments from an argument expression
             * can prevent the compiler from resolving the dependent type parameters correctly.
             */
            private boolean hasDependentTypeParameters(JavaType.@Nullable Method enclosingMethodType) {
                if (enclosingMethodType == null) {
                    return false;
                }
                List<String> formalNames = enclosingMethodType.getDeclaredFormalTypeNames();
                if (formalNames.size() < 2) {
                    // Need at least two type parameters for one to bound another
                    return false;
                }
                List<JavaType> typesToSearch = new ArrayList<>(enclosingMethodType.getParameterTypes());
                if (enclosingMethodType.getReturnType() != null) {
                    typesToSearch.add(enclosingMethodType.getReturnType());
                }
                return typesToSearch.stream()
                        .anyMatch(t -> hasGenericWithFormalParamBound(t, formalNames));
            }

            /**
             * Recursively searches {@code type} for a {@link JavaType.GenericTypeVariable} whose name
             * is among {@code formalNames} AND whose bounds directly reference another name in
             * {@code formalNames}. Such a variable represents a dependent type parameter.
             */
            private boolean hasGenericWithFormalParamBound(@Nullable JavaType type, List<String> formalNames) {
                if (type instanceof JavaType.GenericTypeVariable) {
                    JavaType.GenericTypeVariable gtv = (JavaType.GenericTypeVariable) type;
                    if (formalNames.contains(gtv.getName())) {
                        for (JavaType bound : gtv.getBounds()) {
                            if (referencesAnyFormalName(bound, formalNames)) {
                                return true;
                            }
                        }
                    }
                    return false;
                }
                if (type instanceof JavaType.Parameterized) {
                    return ((JavaType.Parameterized) type).getTypeParameters().stream()
                            .anyMatch(t -> hasGenericWithFormalParamBound(t, formalNames));
                }
                if (type instanceof JavaType.Array) {
                    return hasGenericWithFormalParamBound(((JavaType.Array) type).getElemType(), formalNames);
                }
                return false;
            }

            /**
             * Returns true if {@code type} is, or transitively contains, a
             * {@link JavaType.GenericTypeVariable} whose name is in {@code formalNames}.
             */
            private boolean referencesAnyFormalName(@Nullable JavaType type, List<String> formalNames) {
                if (type instanceof JavaType.GenericTypeVariable) {
                    return formalNames.contains(((JavaType.GenericTypeVariable) type).getName());
                }
                if (type instanceof JavaType.Parameterized) {
                    return ((JavaType.Parameterized) type).getTypeParameters().stream()
                            .anyMatch(t -> referencesAnyFormalName(t, formalNames));
                }
                return false;
            }
        });
    }
}
