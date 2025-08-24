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
import org.openrewrite.java.tree.*;
import org.openrewrite.staticanalysis.java.JavaFileChecker;

import java.util.ArrayList;
import java.util.List;

public class UnnecessaryExplicitTypeArguments extends Recipe {

    @Override
    public String getDisplayName() {
        return "Unnecessary explicit type arguments";
    }

    @Override
    public String getDescription() {
        return "When explicit type arguments are inferable by the compiler, they may be removed.";
    }

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
                    if (shouldRetainOnStaticMethod(methodType)) {
                        return m;
                    }
                    // Cannot remove type parameters if it would introduce ambiguity about which method should be called
                    J.MethodInvocation enclosingMethod = (J.MethodInvocation) enclosing;
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
                        inferredType = ((J.Lambda) e).getType();
                    }
                }

                if (inferredType != null && TypeUtils.isOfType(inferredType, methodType.getReturnType())) {
                    m = m.withTypeParameters(null);
                }

                return m;
            }

            private boolean shouldRetainOnStaticMethod(JavaType.Method methodType) {
                if (!methodType.hasFlags(Flag.Static)) {
                    return false;
                }
                List<String> formalTypeNames = new ArrayList<>(methodType.getDeclaredFormalTypeNames());
                methodType.getParameterTypes().stream()
                        .filter(p -> p instanceof JavaType.Parameterized)
                        .flatMap(p -> ((JavaType.Parameterized) p).getTypeParameters().stream())
                        .filter(t -> t instanceof JavaType.GenericTypeVariable)
                        .forEach(it -> formalTypeNames.remove(((JavaType.GenericTypeVariable) it).getName()));
                return !formalTypeNames.isEmpty();
            }
        });
    }
}
