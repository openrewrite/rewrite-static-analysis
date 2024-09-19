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

import lombok.AllArgsConstructor;
import org.jspecify.annotations.Nullable;
import org.openrewrite.java.JavaIsoVisitor;
import org.openrewrite.java.MethodMatcher;
import org.openrewrite.java.tree.Expression;
import org.openrewrite.java.tree.J;
import org.openrewrite.java.tree.JavaType;
import org.openrewrite.java.tree.MethodCall;

import java.util.function.BiPredicate;
import java.util.function.Supplier;

/**
 * Removes all {@link MethodCall} matching both the
 * {@link RemoveMethodCallVisitor#methodMatcher}
 * and the
 * {@link RemoveMethodCallVisitor#argumentPredicate} for all arguments.
 * <p>
 * Only removes {@link MethodCall} where the call's return value is unused.
 */
@AllArgsConstructor
public class RemoveMethodCallVisitor<P> extends JavaIsoVisitor<P> {
    /**
     * The {@link MethodCall} to match to be removed.
     */
    private final MethodMatcher methodMatcher;
    /**
     * All arguments must match the predicate for the {@link MethodCall} to be removed.
     */
    private final BiPredicate<Integer, Expression> argumentPredicate;

    @SuppressWarnings("NullableProblems")
    @Override
    public J.@Nullable NewClass visitNewClass(J.NewClass newClass, P p) {
        Supplier<J.NewClass> visitSuper = () -> super.visitNewClass(newClass, p);
        if (!methodMatcher.matches(newClass)) {
            return visitSuper.get();
        }
        J.Block parentBlock = getCursor().firstEnclosing(J.Block.class);
        //noinspection SuspiciousMethodCalls
        if (parentBlock != null && !parentBlock.getStatements().contains(newClass)) {
            return visitSuper.get();
        }
        // Remove the method invocation when the argumentMatcherPredicate is true for all arguments
        for (int i = 0; i < newClass.getArguments().size(); i++) {
            if (!argumentPredicate.test(i, newClass.getArguments().get(i))) {
                return visitSuper.get();
            }
        }
        if (newClass.getMethodType() != null) {
            maybeRemoveImport(newClass.getMethodType().getDeclaringType());
        }
        return null;
    }

    @SuppressWarnings("NullableProblems")
    @Override
    public J.@Nullable MethodInvocation visitMethodInvocation(J.MethodInvocation method, P p) {
        // Find method invocations that match the specified method and arguments
        if (matchesMethod(method) && predicateMatchesAllArguments(method)) {
            // If the method invocation is a standalone statement, remove it altogether
            if (isStatementInParentBlock(method)) {
                if (method.getMethodType() != null) {
                    maybeRemoveImport(method.getMethodType().getDeclaringType());
                }
                return null;
            }

            // If the method invocation is in a fluent chain, remove just the current invocation
            if (isInFluentChain(method)) {
                //noinspection ConstantConditions
                return super.visitMethodInvocation((J.MethodInvocation) method.getSelect(), p);
            }
        }
        return super.visitMethodInvocation(method, p);
    }

    private boolean matchesMethod(J.MethodInvocation method) {
        return methodMatcher.matches(method);
    }

    private boolean predicateMatchesAllArguments(J.MethodInvocation method) {
        for (int i = 0; i < method.getArguments().size(); i++) {
            if (!argumentPredicate.test(i, method.getArguments().get(i))) {
                return false;
            }
        }
        return true;
    }

    private boolean isStatementInParentBlock(J.MethodInvocation method) {
        J.Block parentBlock = getCursor().firstEnclosing(J.Block.class);
        return parentBlock == null || parentBlock.getStatements().contains(method);
    }

    private boolean isInFluentChain(J.MethodInvocation method) {
        Expression select = method.getSelect();
        if (select instanceof J.MethodInvocation) {
            return sameReturnType(method, (J.MethodInvocation) select);
        }
        return false;
    }

    private boolean sameReturnType(J.MethodInvocation m1, J.MethodInvocation m2) {
        if (m1.getMethodType() == null || m2.getMethodType() == null) {
            return false;
        }
        return m1.getMethodType().getReturnType() == m2.getMethodType().getReturnType();
    }
}
