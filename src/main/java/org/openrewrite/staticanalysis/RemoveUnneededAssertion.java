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

import org.jspecify.annotations.Nullable;
import org.openrewrite.ExecutionContext;
import org.openrewrite.Preconditions;
import org.openrewrite.Recipe;
import org.openrewrite.TreeVisitor;
import org.openrewrite.java.JavaIsoVisitor;
import org.openrewrite.java.MethodMatcher;
import org.openrewrite.java.RemoveMethodInvocationsVisitor;
import org.openrewrite.java.search.UsesMethod;
import org.openrewrite.java.tree.Expression;
import org.openrewrite.java.tree.J;
import org.openrewrite.marker.SearchResult;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Predicate;

public class RemoveUnneededAssertion extends Recipe {
    // Junit Jupiter
    private static final MethodMatcher JUNIT_JUPITER_ASSERT_TRUE_MATCHER = new MethodMatcher("org.junit.jupiter.api.Assertions assertTrue(..)");
    private static final MethodMatcher JUNIT_JUPITER_ASSERT_FALSE_MATCHER = new MethodMatcher("org.junit.jupiter.api.Assertions assertFalse(..)");

    // Junit 4
    private static final MethodMatcher JUNIT_ASSERT_TRUE_MATCHER = new MethodMatcher("org.junit.Assert assertTrue(boolean)");
    private static final MethodMatcher JUNIT_ASSERT_FALSE_MATCHER = new MethodMatcher("org.junit.Assert assertFalse(boolean)");
    private static final MethodMatcher JUNIT_ASSERT_MESSAGE_TRUE_MATCHER = new MethodMatcher("org.junit.Assert assertTrue(String, boolean)");
    private static final MethodMatcher JUNIT_ASSERT_MESSAGE_FALSE_MATCHER = new MethodMatcher("org.junit.Assert assertFalse(String, boolean)");

    @Override
    public String getDisplayName() {
        return "Remove unneeded assertions";
    }

    @Override
    public String getDescription() {
        return "Remove unneeded assertions like `assert true`, `assertTrue(true)`, or `assertFalse(false)`.";
    }

    @Override
    public TreeVisitor<?, ExecutionContext> getVisitor() {
        TreeVisitor<?, ExecutionContext> constraints = Preconditions.or(
                new UsesMethod<>(JUNIT_JUPITER_ASSERT_TRUE_MATCHER),
                new UsesMethod<>(JUNIT_JUPITER_ASSERT_FALSE_MATCHER),
                new UsesMethod<>(JUNIT_ASSERT_TRUE_MATCHER),
                new UsesMethod<>(JUNIT_ASSERT_FALSE_MATCHER),
                new UsesMethod<>(JUNIT_ASSERT_MESSAGE_TRUE_MATCHER),
                new UsesMethod<>(JUNIT_ASSERT_MESSAGE_FALSE_MATCHER),
                new JavaIsoVisitor<ExecutionContext>() {
                    @Override
                    public J.Assert visitAssert(J.Assert _assert, ExecutionContext ctx) {
                        if (J.Literal.isLiteralValue(_assert.getCondition(), true)) {
                            return SearchResult.found(_assert);
                        }
                        return _assert;
                    }
                }
        );

        Predicate<List<Expression>> isTrue = args -> J.Literal.isLiteralValue(args.get(0), true);
        Predicate<List<Expression>> isFalse = args -> J.Literal.isLiteralValue(args.get(0), false);

        Map<MethodMatcher, Predicate<List<Expression>>> matchers = new HashMap<>();
        matchers.put(JUNIT_JUPITER_ASSERT_TRUE_MATCHER, isTrue);
        matchers.put(JUNIT_JUPITER_ASSERT_FALSE_MATCHER, isFalse);
        matchers.put(JUNIT_ASSERT_TRUE_MATCHER, isTrue);
        matchers.put(JUNIT_ASSERT_FALSE_MATCHER, isFalse);
        matchers.put(JUNIT_ASSERT_MESSAGE_TRUE_MATCHER, args -> J.Literal.isLiteralValue(args.get(1), true));
        matchers.put(JUNIT_ASSERT_MESSAGE_FALSE_MATCHER, args -> J.Literal.isLiteralValue(args.get(1), false));

        return Preconditions.check(constraints, new JavaIsoVisitor<ExecutionContext>() {
            @Override
            public J.CompilationUnit visitCompilationUnit(J.CompilationUnit compilationUnit, ExecutionContext ctx) {
                J.CompilationUnit cu = super.visitCompilationUnit(compilationUnit, ctx);
                return (J.CompilationUnit) new RemoveMethodInvocationsVisitor(matchers)
                        .visitNonNull(cu, ctx, getCursor().getParentOrThrow());
            }

            @Override
            @SuppressWarnings("NullableProblems")
            public J.@Nullable Assert visitAssert(J.Assert _assert, ExecutionContext ctx) {
                if (J.Literal.isLiteralValue(_assert.getCondition(), true)) {
                    return null;
                }
                return super.visitAssert(_assert, ctx);
            }
        });
    }
}
