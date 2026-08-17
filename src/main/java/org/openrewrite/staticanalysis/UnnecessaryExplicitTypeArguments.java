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

import java.util.*;

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
                        // Cannot remove type parameters if it would introduce ambiguity about which method should be called
                        if (enclosingMethod.getMethodType() == null) {
                            return m;
                        }
                        // The enclosing method's type parameters may be interdependent (e.g. `<T, S extends T>`).
                        // Inference then resolves them jointly against the target type, so this argument's witness
                        // can be load-bearing even though this call's own type variables are inferable from its
                        // arguments. Retain it rather than reason about the enclosing method's inference.
                        if (hasInterdependentTypeParameters(enclosingMethod.getMethodType())) {
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
                    Cursor enclosingFnCursor = getCursor().dropParentUntil(p -> p instanceof J.MethodDeclaration || p instanceof J.Lambda || Cursor.ROOT_VALUE.equals(p));
                    Object e = enclosingFnCursor.getValue();
                    if (e instanceof J.MethodDeclaration) {
                        J.MethodDeclaration methodDeclaration = (J.MethodDeclaration) e;
                        if (methodDeclaration.getReturnTypeExpression() != null) {
                            inferredType = methodDeclaration.getReturnTypeExpression().getType();
                        }
                    } else if (e instanceof J.Lambda) {
                        // A lambda passed as a call argument has the same inference circularity as the
                        // select/argument branches above; guard it the same way.
                        Object lambdaEnclosing = enclosingFnCursor.getParentTreeCursor().getValue();
                        if (lambdaEnclosing instanceof J.MethodInvocation && !canInferTypeArgumentsFromArguments(methodType)) {
                            return m;
                        }
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
                // methodType is already substituted (no GenericTypeVariable left) and
                // getDeclaredFormalTypeNames() is unreliable; look up the real declared signature instead.
                JavaType.Method declared = findDeclaredSignature(methodType);
                if (declared == null) {
                    return false;
                }
                Map<String, JavaType.GenericTypeVariable> returnTypeVariables = new HashMap<>();
                collectGenericTypeVariables(declared.getReturnType(), returnTypeVariables);
                if (returnTypeVariables.isEmpty()) {
                    return true;
                }
                Map<String, JavaType.GenericTypeVariable> parameterTypeVariables = new HashMap<>();
                for (JavaType paramType : declared.getParameterTypes()) {
                    collectGenericTypeVariables(paramType, parameterTypeVariables);
                }
                return parameterTypeVariables.keySet().containsAll(returnTypeVariables.keySet());
            }

            private boolean hasInterdependentTypeParameters(JavaType.Method methodType) {
                JavaType.Method declared = findDeclaredSignature(methodType);
                if (declared == null) {
                    return false;
                }
                Map<String, JavaType.GenericTypeVariable> typeVariables = new HashMap<>();
                collectGenericTypeVariables(declared.getReturnType(), typeVariables);
                for (JavaType paramType : declared.getParameterTypes()) {
                    collectGenericTypeVariables(paramType, typeVariables);
                }
                for (Map.Entry<String, JavaType.GenericTypeVariable> typeVariable : typeVariables.entrySet()) {
                    Set<String> boundNames = new HashSet<>();
                    for (JavaType bound : typeVariable.getValue().getBounds()) {
                        Map<String, JavaType.GenericTypeVariable> inBound = new HashMap<>();
                        collectGenericTypeVariables(bound, inBound);
                        boundNames.addAll(inBound.keySet());
                    }
                    // A self-referential F-bound such as `<E extends Enum<E>>` is not a dependency on
                    // another type parameter, and is far too common to retain witnesses for.
                    boundNames.remove(typeVariable.getKey());
                    boundNames.retainAll(typeVariables.keySet());
                    if (!boundNames.isEmpty()) {
                        return true;
                    }
                }
                return false;
            }

            private JavaType.@Nullable Method findDeclaredSignature(JavaType.Method methodType) {
                if (!(methodType.getDeclaringType() instanceof JavaType.Class)) {
                    return null;
                }
                JavaType.Class declaringClass = (JavaType.Class) methodType.getDeclaringType();
                JavaType.Method match = null;
                for (JavaType.Method candidate : declaringClass.getMethods()) {
                    if (candidate.getName().equals(methodType.getName()) &&
                            candidate.getParameterTypes().size() == methodType.getParameterTypes().size()) {
                        if (match != null) {
                            return null; // ambiguous same-arity overload
                        }
                        match = candidate;
                    }
                }
                return match;
            }

            private void collectGenericTypeVariables(@Nullable JavaType type, Map<String, JavaType.GenericTypeVariable> into) {
                if (type instanceof JavaType.GenericTypeVariable) {
                    into.putIfAbsent(((JavaType.GenericTypeVariable) type).getName(), (JavaType.GenericTypeVariable) type);
                } else if (type instanceof JavaType.Parameterized) {
                    for (JavaType typeParameter : ((JavaType.Parameterized) type).getTypeParameters()) {
                        collectGenericTypeVariables(typeParameter, into);
                    }
                } else if (type instanceof JavaType.Array) {
                    collectGenericTypeVariables(((JavaType.Array) type).getElemType(), into);
                }
            }
        });
    }
}
