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
        return Preconditions.check(constraints, new JavaIsoVisitor<ExecutionContext>() {
            @Override
            public J.CompilationUnit visitCompilationUnit(J.CompilationUnit compilationUnit, ExecutionContext ctx) {
                J.CompilationUnit cu = super.visitCompilationUnit(compilationUnit, ctx);

                cu = maybeRemoveAssert(JUNIT_JUPITER_ASSERT_TRUE_MATCHER, args -> !args.isEmpty() && J.Literal.isLiteralValue(args.get(0), true), cu, ctx);
                cu = maybeRemoveAssert(JUNIT_JUPITER_ASSERT_FALSE_MATCHER, args -> !args.isEmpty() && J.Literal.isLiteralValue(args.get(0), false), cu, ctx);

                cu = maybeRemoveAssert(JUNIT_ASSERT_TRUE_MATCHER, args -> J.Literal.isLiteralValue(args.get(0), true), cu, ctx);
                cu = maybeRemoveAssert(JUNIT_ASSERT_FALSE_MATCHER, args -> J.Literal.isLiteralValue(args.get(0), false), cu, ctx);
                cu = maybeRemoveAssert(JUNIT_ASSERT_MESSAGE_TRUE_MATCHER, args -> J.Literal.isLiteralValue(args.get(1), true), cu, ctx);
                cu = maybeRemoveAssert(JUNIT_ASSERT_MESSAGE_FALSE_MATCHER, args -> J.Literal.isLiteralValue(args.get(1), false), cu, ctx);

                return cu;
            }

            @Override
            @SuppressWarnings("NullableProblems")
            public J.@Nullable Assert visitAssert(J.Assert anAssert, ExecutionContext ctx) {
                if (anAssert.getCondition() instanceof J.Literal) {
                    if (J.Literal.isLiteralValue(anAssert.getCondition(), true)) {
                        return null;
                    }
                }
                return super.visitAssert(anAssert, ctx);
            }

            private J.CompilationUnit maybeRemoveAssert(MethodMatcher methodMatcher, Predicate<List<Expression>> predicate, J.CompilationUnit cu, ExecutionContext ctx) {
                return (J.CompilationUnit) new RemoveMethodInvocationsVisitor(new HashMap<MethodMatcher, Predicate<List<Expression>>>() {{
                    put(methodMatcher, predicate);
                }}).visitNonNull(cu, ctx, getCursor().getParentOrThrow());
            }
        });
    }
}
