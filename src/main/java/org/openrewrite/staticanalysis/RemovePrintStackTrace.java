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
import org.openrewrite.ExecutionContext;
import org.openrewrite.Preconditions;
import org.openrewrite.Recipe;
import org.openrewrite.TreeVisitor;
import org.openrewrite.java.JavaIsoVisitor;
import org.openrewrite.java.MethodMatcher;
import org.openrewrite.java.search.UsesMethod;
import org.openrewrite.java.tree.J;

public class RemovePrintStackTrace extends Recipe {
    private static final MethodMatcher PRINT_STACK_TRACE = new MethodMatcher("java.lang.Throwable printStackTrace(..)");

    @Getter
    final String displayName = "Remove `Throwable#printStackTrace()` statements";

    @Getter
    final String description = "Calling `Throwable#printStackTrace()` prints to standard error, " +
            "which can inadvertently expose sensitive information and is not suitable for production logging. " +
            "This recipe removes `printStackTrace` statements, which should be replaced with proper logging.";

    @Override
    public TreeVisitor<?, ExecutionContext> getVisitor() {
        return Preconditions.check(new UsesMethod<>(PRINT_STACK_TRACE), new JavaIsoVisitor<ExecutionContext>() {

            @Override
            @SuppressWarnings("NullableProblems")
            public J.@Nullable MethodInvocation visitMethodInvocation(J.MethodInvocation method, ExecutionContext ctx) {
                if (PRINT_STACK_TRACE.matches(method)) {
                    return null;
                }
                return super.visitMethodInvocation(method, ctx);
            }
        });
    }
}
