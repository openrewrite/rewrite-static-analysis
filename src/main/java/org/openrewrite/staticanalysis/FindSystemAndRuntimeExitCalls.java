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
import org.openrewrite.java.tree.J;
import org.openrewrite.marker.SearchResult;

import java.time.Duration;
import java.util.Set;

import static java.util.Collections.singleton;

@Value
@EqualsAndHashCode(callSuper = false)
public class FindSystemAndRuntimeExitCalls extends Recipe {

    private static final MethodMatcher SYSTEM_EXIT = new MethodMatcher("java.lang.System exit(int)");
    private static final MethodMatcher RUNTIME_EXIT = new MethodMatcher("java.lang.Runtime exit(int)");
    private static final MethodMatcher RUNTIME_HALT = new MethodMatcher("java.lang.Runtime halt(int)");

    String displayName = "Find JVM exit calls";

    String description = "Marks calls to `System.exit(int)`, `Runtime.exit(int)`, and " +
            "`Runtime.halt(int)`. Terminating the JVM from library or application code is rarely " +
            "correct: it bypasses the normal shutdown flow, prevents `finally` blocks from running " +
            "in other threads, and can leave file, socket, and database resources in an inconsistent " +
            "state. `Runtime.halt` is particularly dangerous because it also skips shutdown hooks.";

    Set<String> tags = singleton("RSPEC-S1147");

    Duration estimatedEffortPerOccurrence = Duration.ofMinutes(5);

    @Override
    public TreeVisitor<?, ExecutionContext> getVisitor() {
        return Preconditions.check(
                Preconditions.or(
                        new UsesMethod<>(SYSTEM_EXIT),
                        new UsesMethod<>(RUNTIME_EXIT),
                        new UsesMethod<>(RUNTIME_HALT)),
                new JavaIsoVisitor<ExecutionContext>() {
                    @Override
                    public J.MethodInvocation visitMethodInvocation(J.MethodInvocation method, ExecutionContext ctx) {
                        J.MethodInvocation m = super.visitMethodInvocation(method, ctx);
                        if (SYSTEM_EXIT.matches(m) || RUNTIME_EXIT.matches(m) || RUNTIME_HALT.matches(m)) {
                            return SearchResult.found(m,
                                    "JVM exit call; terminating from application code bypasses normal shutdown.");
                        }
                        return m;
                    }
                });
    }
}
