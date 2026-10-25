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

import java.util.HashSet;
import java.util.Iterator;
import java.util.Set;

import static java.util.Arrays.asList;

@Value
@EqualsAndHashCode(callSuper = false)
public class FindWaitWithMultipleLocksHeld extends Recipe {

    String displayName = "Find `Object.wait()` calls made while holding multiple monitors";

    String description = "Finds zero-argument `Object.wait()` invocations whose enclosing method holds " +
            "two or more monitors — either through nested `synchronized (...)` blocks, or a " +
            "`synchronized` method combined with a nested `synchronized` block. `wait()` releases " +
            "only the monitor of its receiver, so other held monitors continue to block their " +
            "waiters and can deadlock. Timed waits (`wait(long)`, `wait(long, int)`) are " +
            "intentionally excluded — sonar-java's S3046 does the same, since timed waits are " +
            "self-releasing and less likely to cause the failure mode.";

    Set<String> tags = new HashSet<>(asList("RSPEC-S3046"));

    private static final MethodMatcher OBJECT_WAIT = new MethodMatcher("java.lang.Object wait()");

    @Override
    public TreeVisitor<?, ExecutionContext> getVisitor() {
        return Preconditions.check(
                new UsesMethod<>(OBJECT_WAIT),
                new JavaIsoVisitor<ExecutionContext>() {
                    @Override
                    public J.MethodInvocation visitMethodInvocation(J.MethodInvocation method, ExecutionContext ctx) {
                        J.MethodInvocation mi = super.visitMethodInvocation(method, ctx);
                        if (!OBJECT_WAIT.matches(mi)) {
                            return mi;
                        }
                        if (heldMonitorCount() >= 2) {
                            return SearchResult.found(mi,
                                    "`wait()` called while holding multiple monitors. Only the receiver's " +
                                            "monitor is released; other held monitors continue to block their " +
                                            "waiters and can deadlock.");
                        }
                        return mi;
                    }

                    /**
                     * Count monitors held by the enclosing method at this call site. Walks up
                     * `getCursor().getPath()`, counting each `J.Synchronized` and adding one more
                     * if the enclosing method is declared `synchronized`. A `J.Lambda` cuts the
                     * count off (its body executes in a deferred context that doesn't inherit the
                     * outer lock frames); `J.MethodDeclaration` is the walk boundary (inner-class
                     * or anonymous-class methods have their own lock context).
                     */
                    private int heldMonitorCount() {
                        int count = 0;
                        for (Iterator<Object> it = getCursor().getPath(); it.hasNext(); ) {
                            Object p = it.next();
                            if (p instanceof J.Synchronized) {
                                count++;
                            } else if (p instanceof J.Lambda) {
                                return 0;
                            } else if (p instanceof J.MethodDeclaration) {
                                if (((J.MethodDeclaration) p).hasModifier(J.Modifier.Type.Synchronized)) {
                                    count++;
                                }
                                return count;
                            }
                        }
                        return count;
                    }
                }
        );
    }
}
