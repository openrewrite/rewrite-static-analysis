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
import org.openrewrite.ExecutionContext;
import org.openrewrite.Preconditions;
import org.openrewrite.Recipe;
import org.openrewrite.TreeVisitor;
import org.openrewrite.java.JavaIsoVisitor;
import org.openrewrite.java.MethodMatcher;
import org.openrewrite.java.search.UsesMethod;
import org.openrewrite.java.tree.J;

import java.time.Duration;
import java.util.Set;

import static java.util.Collections.singleton;

public class RemoveCallsToObjectFinalize extends Recipe {

    private static final MethodMatcher OBJECT_FINALIZE = new MethodMatcher("java.lang.Object finalize()");

    @Override
    public String getDisplayName() {
        return "Remove `Object.finalize()` invocations";
    }

    @Override
    public String getDescription() {
        return "Remove calls to `Object.finalize()`. This method is called during garbage collection and calling it manually is misleading.";
    }

    @Override
    public Set<String> getTags() {
        return singleton("RSPEC-S1111");
    }

    @Override
    public Duration getEstimatedEffortPerOccurrence() {
        return Duration.ofMinutes(1);
    }

    @Override
    public TreeVisitor<?, ExecutionContext> getVisitor() {
        return Preconditions.check(new UsesMethod<>(OBJECT_FINALIZE), new JavaIsoVisitor<ExecutionContext>() {

            @Override
            public  J.@Nullable MethodInvocation visitMethodInvocation(J.MethodInvocation method, ExecutionContext ctx) {
                J.MethodInvocation invocation = super.visitMethodInvocation(method, ctx);

                if (invocation.getMethodType() != null && "finalize".equals(invocation.getMethodType().getName()) &&
                    (invocation.getMethodType().getDeclaringType().getSupertype() != null && Object.class.getName().equals(invocation.getMethodType().getDeclaringType().getSupertype().getFullyQualifiedName()))) {
                    //noinspection DataFlowIssue
                    return null;
                }
                return invocation;
            }
        });
    }
}
