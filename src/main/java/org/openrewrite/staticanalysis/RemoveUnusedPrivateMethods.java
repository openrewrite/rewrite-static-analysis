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

import org.jspecify.annotations.Nullable;
import org.openrewrite.*;
import org.openrewrite.java.JavaIsoVisitor;
import org.openrewrite.java.NoMissingTypes;
import org.openrewrite.java.RemoveUnusedImports;
import org.openrewrite.java.search.FindAnnotations;
import org.openrewrite.java.tree.*;

import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;

import static java.util.Collections.singleton;

public class RemoveUnusedPrivateMethods extends Recipe {

    @Override
    public String getDisplayName() {
        return "Remove unused private methods";
    }

    @Override
    public String getDescription() {
        return "`private` methods that are never executed are dead code and should be removed.";
    }

    @Override
    public Set<String> getTags() {
        return singleton("RSPEC-S1144");
    }

    @Override
    public TreeVisitor<?, ExecutionContext> getVisitor() {
        JavaIsoVisitor<ExecutionContext> visitor = new JavaIsoVisitor<ExecutionContext>() {
            @Override
            public J.ClassDeclaration visitClassDeclaration(J.ClassDeclaration classDeclaration, ExecutionContext ctx) {
                if (unusedWarningsSuppressed(classDeclaration)) {
                    return classDeclaration;
                }
                return super.visitClassDeclaration(classDeclaration, ctx);
            }

            private boolean unusedWarningsSuppressed(J classDeclaration) {
                for (J.Annotation annotation : FindAnnotations.find(classDeclaration, "java.lang.SuppressWarnings")) {
                    List<Expression> arguments = annotation.getArguments();
                    if (arguments != null) {
                        for (Expression argument : arguments) {
                            if (J.Literal.isLiteralValue(argument, "all") ||
                                    J.Literal.isLiteralValue(argument, "unused")) {
                                return true;
                            }
                        }
                    }
                }
                return false;
            }

            @Override
            public J.@Nullable MethodDeclaration visitMethodDeclaration(J.MethodDeclaration method, ExecutionContext ctx) {
                J.MethodDeclaration m = super.visitMethodDeclaration(method, ctx);

                JavaType.Method methodType = method.getMethodType();
                if (methodType == null) {
                    return m;
                }

                // Only consider private, non-constructors.
                if (!methodType.hasFlags(Flag.Private) || method.isConstructor()) {
                    return m;
                }

                // Serialization hooks & similar: do not remove even if private.
                switch (m.getSimpleName()) {
                    case "readObject":
                    case "readObjectNoData":
                    case "readResolve":
                    case "writeObject":
                    case "writeReplace":
                        return m;
                }

                // If we're missing the enclosing class or CU, bail out.
                J.ClassDeclaration classDeclaration = getCursor().firstEnclosing(J.ClassDeclaration.class);
                if (classDeclaration == null) {
                    return m;
                }
                JavaSourceFile cu = getCursor().firstEnclosingOrThrow(JavaSourceFile.class);

                // If referenced anywhere in TypesInUse (e.g., via other classes), keep it.
                for (JavaType.Method usedMethodType : cu.getTypesInUse().getUsedMethods()) {
                    if (methodType.equals(usedMethodType)) {
                        return m;
                    }
                }

                // Do not touch if the compilation unit references JUnit's MethodSource (reflective use).
                for (JavaType javaType : cu.getTypesInUse().getTypesInUse()) {
                    if (TypeUtils.isOfClassType(javaType, "org.junit.jupiter.params.provider.MethodSource")) {
                        return m;
                    }
                }

                // Temporary stop-gap until we have DFA:
                // If the declared method shows generic type artifacts, be conservative and keep it.
                for (JavaType.Method declared : cu.getTypesInUse().getDeclaredMethods()) {
                    if (methodType.equals(declared) && m.toString().contains("Generic{")) {
                        return m;
                    }
                }

                // Scan the enclosing class for in-class usages (method calls or method references).
                // IMPORTANT: self-recursion alone does NOT count as usage.
                AtomicBoolean usedInClass = new AtomicBoolean(false);

                new JavaIsoVisitor<ExecutionContext>() {
                    @Override
                    public J.MethodInvocation visitMethodInvocation(J.MethodInvocation call, ExecutionContext ctx2) {
                        J.MethodInvocation c = super.visitMethodInvocation(call, ctx2);
                        JavaType.Method calledType = c.getMethodType();
                        if (calledType != null && calledType.equals(methodType)) {
                            // Ignore self-recursion: only count if the enclosing method is NOT the same declaration.
                            J.MethodDeclaration enclosing = getCursor().firstEnclosing(J.MethodDeclaration.class);
                            if (enclosing == null || enclosing != method) {
                                usedInClass.set(true);
                            }
                        }
                        return c;
                    }

                    @Override
                    public J.MemberReference visitMemberReference(J.MemberReference ref, ExecutionContext ctx2) {
                        J.MemberReference r = super.visitMemberReference(ref, ctx2);
                        JavaType.Method refType = r.getMethodType();
                        if (refType != null && refType.equals(methodType)) {
                            J.MethodDeclaration enclosing = getCursor().firstEnclosing(J.MethodDeclaration.class);
                            if (enclosing == null || enclosing != method) {
                                usedInClass.set(true);
                            }
                        }
                        return r;
                    }
                }.visit(classDeclaration, ctx);

                if (usedInClass.get()) {
                    return m;
                }

                // No external nor internal usages -> remove it.
                doAfterVisit(new RemoveUnusedImports().getVisitor());
                //noinspection ConstantConditions
                return null;
            }
        };

        return Preconditions.check(new NoMissingTypes(), Repeat.repeatUntilStable(visitor));
    }
}
