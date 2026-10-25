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
import org.openrewrite.internal.ListUtils;
import org.openrewrite.java.JavaIsoVisitor;
import org.openrewrite.java.MethodMatcher;
import org.openrewrite.java.search.UsesMethod;
import org.openrewrite.java.tree.J;
import org.openrewrite.java.tree.Statement;

import java.time.Duration;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

import static java.util.Collections.singleton;
import static org.openrewrite.staticanalysis.SideEffects.mayHaveSideEffects;

public class RemoveCallsToSystemGc extends Recipe {

    private static final MethodMatcher SYSTEM_GC = new MethodMatcher("java.lang.System gc()");
    private static final MethodMatcher RUNTIME_GC = new MethodMatcher("java.lang.Runtime gc()");

    @Getter
    final String displayName = "Remove garbage collection invocations";

    @Getter
    final String description = "Removes calls to `System.gc()` and `Runtime.gc()`. When to " +
            "invoke garbage collection is best left to the JVM. Manual GC calls " +
            "produce unpredictable results across different JVM implementations " +
            "and can cause unnecessary application pauses.";

    @Getter
    final Set<String> tags = singleton("RSPEC-S1215");

    @Getter
    final Duration estimatedEffortPerOccurrence = Duration.ofMinutes(1);

    @Override
    public TreeVisitor<?, ExecutionContext> getVisitor() {
        return Preconditions.check(Preconditions.or(new UsesMethod<>(SYSTEM_GC), new UsesMethod<>(RUNTIME_GC)), new JavaIsoVisitor<ExecutionContext>() {
            private final Set<UUID> emptiedBlocks = new HashSet<>();

            @Override
            public J.Block visitBlock(J.Block block, ExecutionContext ctx) {
                J.Block b = super.visitBlock(block, ctx);
                b = b.withStatements(ListUtils.map(b.getStatements(), s -> isEmptied(s) ||
                        s instanceof J.If && ((J.If) s).getElsePart() == null && isEmptied(((J.If) s).getThenPart()) &&
                        !mayHaveSideEffects(((J.If) s).getIfCondition()) ? null : s));
                if (b.getStatements().isEmpty() && !block.getStatements().isEmpty() && b.getEnd().getComments().isEmpty()) {
                    emptiedBlocks.add(b.getId());
                }
                return b;
            }

            private boolean isEmptied(Statement statement) {
                return statement instanceof J.Block && emptiedBlocks.contains(statement.getId());
            }

            @Override
            public  J.@Nullable MethodInvocation visitMethodInvocation(J.MethodInvocation method, ExecutionContext ctx) {
                J.MethodInvocation invocation = super.visitMethodInvocation(method, ctx);
                if (SYSTEM_GC.matches(invocation) || RUNTIME_GC.matches(invocation)) {
                    return null;
                }
                return invocation;
            }
        });
    }
}
