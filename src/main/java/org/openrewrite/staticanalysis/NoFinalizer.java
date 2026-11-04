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
import org.openrewrite.ExecutionContext;
import org.openrewrite.Preconditions;
import org.openrewrite.ScanningRecipe;
import org.openrewrite.TreeVisitor;
import org.openrewrite.internal.ListUtils;
import org.openrewrite.java.JavaIsoVisitor;
import org.openrewrite.java.MethodMatcher;
import org.openrewrite.java.search.DeclaresMethod;
import org.openrewrite.java.tree.J;
import org.openrewrite.java.tree.JavaType;
import org.openrewrite.java.tree.TypeUtils;

import java.time.Duration;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import static java.util.Collections.singleton;

public class NoFinalizer extends ScanningRecipe<NoFinalizer.Calls> {

    private static final MethodMatcher FINALIZER = new MethodMatcher("java.lang.Object finalize()", true);

    @Getter
    final String displayName = "Remove `finalize()` method";

    @Getter
    final String description = "Finalizers are deprecated. Use of `finalize()` can lead to performance issues, deadlocks, hangs, and other undesirable behavior.";

    @Getter
    final Set<String> tags = singleton("RSPEC-S1113");

    @Getter
    final Duration estimatedEffortPerOccurrence = Duration.ofMinutes(20);

    static class Calls {
        final Set<JavaType.FullyQualified> explicit = new HashSet<>();
        final Map<JavaType.FullyQualified, Set<JavaType.FullyQualified>> superCalls = new HashMap<>();

        boolean isCalled(JavaType.FullyQualified type) {
            for (JavaType.FullyQualified called : explicit) {
                if (TypeUtils.isAssignableTo(called.getFullyQualifiedName(), type)) {
                    return true;
                }
            }
            return false;
        }
    }

    @Override
    public Calls getInitialValue(ExecutionContext ctx) {
        return new Calls();
    }

    @Override
    public TreeVisitor<?, ExecutionContext> getScanner(Calls calls) {
        return new JavaIsoVisitor<ExecutionContext>() {
            @Override
            public J.MethodInvocation visitMethodInvocation(J.MethodInvocation method, ExecutionContext ctx) {
                if (FINALIZER.matches(method) && method.getMethodType() != null) {
                    JavaType.FullyQualified target = method.getMethodType().getDeclaringType();
                    J.MethodDeclaration enclosing = getCursor().firstEnclosing(J.MethodDeclaration.class);
                    boolean superCall = method.getSelect() instanceof J.Identifier &&
                                        "super".equals(((J.Identifier) method.getSelect()).getSimpleName());
                    if (superCall && "java.lang.Object".equals(target.getFullyQualifiedName())) {
                        return super.visitMethodInvocation(method, ctx);
                    }
                    if (superCall && enclosing != null && FINALIZER.matches(enclosing.getMethodType())) {
                        // Cleanup chaining alone must not keep an otherwise unused finalizer.
                        calls.superCalls.computeIfAbsent(enclosing.getMethodType().getDeclaringType(),
                                k -> new HashSet<>()).add(target);
                    } else {
                        calls.explicit.add(target);
                    }
                }
                return super.visitMethodInvocation(method, ctx);
            }

            @Override
            public J.MemberReference visitMemberReference(J.MemberReference reference, ExecutionContext ctx) {
                if (FINALIZER.matches(reference.getMethodType())) {
                    calls.explicit.add(reference.getMethodType().getDeclaringType());
                }
                return super.visitMemberReference(reference, ctx);
            }
        };
    }

    @Override
    public TreeVisitor<?, ExecutionContext> getVisitor(Calls calls) {
        // A retained override still needs the implementations it delegates to.
        boolean changed;
        do {
            changed = false;
            for (Map.Entry<JavaType.FullyQualified, Set<JavaType.FullyQualified>> entry : calls.superCalls.entrySet()) {
                if (calls.isCalled(entry.getKey())) {
                    for (JavaType.FullyQualified target : entry.getValue()) {
                        if (!"java.lang.Object".equals(target.getFullyQualifiedName())) {
                            changed |= calls.explicit.add(target);
                        }
                    }
                }
            }
        } while (changed);
        return Preconditions.check(new DeclaresMethod<>(FINALIZER), new JavaIsoVisitor<ExecutionContext>() {
            @Override
            public J.ClassDeclaration visitClassDeclaration(J.ClassDeclaration classDecl, ExecutionContext ctx) {
                J.ClassDeclaration cd = super.visitClassDeclaration(classDecl, ctx);

                return cd.withBody(cd.getBody().withStatements(ListUtils.map(cd.getBody().getStatements(), stmt -> {
                    if (stmt instanceof J.MethodDeclaration) {
                        if (FINALIZER.matches((J.MethodDeclaration) stmt, classDecl) &&
                            !calls.isCalled(classDecl.getType())) {
                            return null;
                        }
                    }
                    return stmt;
                })));
            }
        });
    }
}
