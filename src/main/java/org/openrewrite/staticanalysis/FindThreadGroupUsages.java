/*
 * Copyright 2026 the original author or authors.
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

import lombok.EqualsAndHashCode;
import lombok.Value;
import org.openrewrite.ExecutionContext;
import org.openrewrite.Preconditions;
import org.openrewrite.Recipe;
import org.openrewrite.TreeVisitor;
import org.openrewrite.java.JavaIsoVisitor;
import org.openrewrite.java.MethodMatcher;
import org.openrewrite.java.search.UsesMethod;
import org.openrewrite.java.search.UsesType;
import org.openrewrite.java.tree.J;
import org.openrewrite.java.tree.TypeUtils;
import org.openrewrite.marker.SearchResult;

import java.time.Duration;
import java.util.Set;

import static java.util.Collections.singleton;

@Value
@EqualsAndHashCode(callSuper = false)
public class FindThreadGroupUsages extends Recipe {

    private static final String THREAD_GROUP = "java.lang.ThreadGroup";
    private static final MethodMatcher THREAD_GET_THREAD_GROUP =
            new MethodMatcher("java.lang.Thread getThreadGroup()");

    String displayName = "Find `ThreadGroup` usages";

    String description = "Marks uses of `java.lang.ThreadGroup`. `ThreadGroup` was originally " +
            "intended to help with thread management but its API has serious design flaws " +
            "(most methods are either deprecated or unsafe) and it has been superseded by " +
            "`java.util.concurrent.ExecutorService`. Sites flagged include `new ThreadGroup(...)` " +
            "constructor calls, calls to `Thread.getThreadGroup()`, and method invocations on " +
            "`ThreadGroup` receivers.";

    Set<String> tags = singleton("RSPEC-S3014");

    Duration estimatedEffortPerOccurrence = Duration.ofMinutes(30);

    @Override
    public TreeVisitor<?, ExecutionContext> getVisitor() {
        return Preconditions.check(
                Preconditions.or(
                        new UsesType<>(THREAD_GROUP, false),
                        new UsesMethod<>(THREAD_GET_THREAD_GROUP)),
                new JavaIsoVisitor<ExecutionContext>() {
                    @Override
                    public J.NewClass visitNewClass(J.NewClass newClass, ExecutionContext ctx) {
                        J.NewClass n = super.visitNewClass(newClass, ctx);
                        if (TypeUtils.isOfClassType(n.getType(), THREAD_GROUP)) {
                            return SearchResult.found(n,
                                    "`ThreadGroup` is superseded by `java.util.concurrent.ExecutorService`.");
                        }
                        return n;
                    }

                    @Override
                    public J.MethodInvocation visitMethodInvocation(J.MethodInvocation method, ExecutionContext ctx) {
                        J.MethodInvocation m = super.visitMethodInvocation(method, ctx);
                        if (THREAD_GET_THREAD_GROUP.matches(m)) {
                            return SearchResult.found(m,
                                    "`Thread.getThreadGroup()` exposes the discouraged `ThreadGroup` API.");
                        }
                        if (m.getSelect() != null &&
                                TypeUtils.isOfClassType(m.getSelect().getType(), THREAD_GROUP)) {
                            return SearchResult.found(m,
                                    "Method call on a `ThreadGroup` receiver; use an `ExecutorService` instead.");
                        }
                        return m;
                    }
                });
    }
}
