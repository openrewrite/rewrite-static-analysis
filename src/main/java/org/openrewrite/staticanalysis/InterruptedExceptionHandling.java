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
import org.openrewrite.Recipe;
import org.openrewrite.TreeVisitor;
import org.openrewrite.java.JavaIsoVisitor;
import org.openrewrite.java.JavaTemplate;
import org.openrewrite.java.MethodMatcher;
import org.openrewrite.java.tree.J;
import org.openrewrite.java.tree.JavaType;
import org.openrewrite.java.tree.TypeUtils;

import java.time.Duration;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;

import static java.util.Collections.singleton;

@EqualsAndHashCode(callSuper = false)
@Value
public class InterruptedExceptionHandling extends Recipe {

    private static final MethodMatcher THREAD_INTERRUPT = new MethodMatcher("java.lang.Thread interrupt()", true);
    private static final MethodMatcher CURRENT_THREAD = new MethodMatcher("java.lang.Thread currentThread()", true);

    String displayName = "Restore interrupted state in catch blocks";
    String description = "When `InterruptedException` is caught, `Thread.currentThread().interrupt()` should be called " +
            "to restore the thread's interrupted state. Failing to do so can suppress the interruption signal and prevent " +
            "proper thread cancellation.";
    Set<String> tags = singleton("RSPEC-S2142");
    Duration estimatedEffortPerOccurrence = Duration.ofMinutes(5);

    @Override
    public TreeVisitor<?, ExecutionContext> getVisitor() {
        return new JavaIsoVisitor<ExecutionContext>() {
            @Override
            public J.Try.Catch visitCatch(J.Try.Catch aCatch, ExecutionContext ctx) {
                J.Try.Catch c = super.visitCatch(aCatch, ctx);
                if (catchesInterruptedException(c) && !hasThreadInterruptCall(c)) {
                    boolean isMultiCatch = c.getParameter().getType() instanceof JavaType.MultiCatch;
                    if (isMultiCatch) {
                        J.Identifier varId = c.getParameter().getTree().getVariables().get(0).getName();
                        return JavaTemplate.builder("if (#{any()} instanceof InterruptedException) { Thread.currentThread().interrupt(); }")
                                .contextSensitive()
                                .build()
                                .apply(updateCursor(c), c.getBody().getCoordinates().firstStatement(), varId);
                    }
                    return JavaTemplate.builder("Thread.currentThread().interrupt();")
                            .contextSensitive()
                            .build()
                            .apply(updateCursor(c), c.getBody().getCoordinates().firstStatement());
                }
                return c;
            }

            private boolean catchesInterruptedException(J.Try.Catch aCatch) {
                JavaType type = aCatch.getParameter().getType();
                if (type instanceof JavaType.MultiCatch) {
                    for (JavaType throwableType : ((JavaType.MultiCatch) type).getThrowableTypes()) {
                        if (TypeUtils.isOfClassType(throwableType, "java.lang.InterruptedException")) {
                            return true;
                        }
                    }
                    return false;
                }
                return TypeUtils.isOfClassType(type, "java.lang.InterruptedException");
            }

            private boolean hasThreadInterruptCall(J.Try.Catch aCatch) {
                return new JavaIsoVisitor<AtomicBoolean>() {
                    @Override
                    public J.MethodInvocation visitMethodInvocation(J.MethodInvocation method, AtomicBoolean found) {
                        if (found.get()) {
                            return method;
                        }
                        J.MethodInvocation mi = super.visitMethodInvocation(method, found);
                        if (THREAD_INTERRUPT.matches(mi) &&
                            mi.getSelect() instanceof J.MethodInvocation &&
                            CURRENT_THREAD.matches((J.MethodInvocation) mi.getSelect())) {
                            found.set(true);
                        }
                        return mi;
                    }
                }.reduce(aCatch, new AtomicBoolean()).get();
            }
        };
    }
}
