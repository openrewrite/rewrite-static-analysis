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
import org.openrewrite.java.JavaVisitor;
import org.openrewrite.java.MethodMatcher;
import org.openrewrite.java.search.UsesMethod;
import org.openrewrite.java.tree.J;
import org.openrewrite.java.tree.TypeUtils;

import java.time.Duration;
import java.util.Set;

import static java.util.Collections.singleton;

public class RemoveSystemOutPrintln extends Recipe {
    private static final MethodMatcher SYSTEM_OUT_PRINTLN = new MethodMatcher("java.io.PrintStream println(..)");

    @Getter
    final String displayName = "Remove `System.out#println` and `System.err#println` statements";

    @Getter
    final String description = "Print statements are often left accidentally after debugging an issue. " +
            "This recipe removes all `System.out#println` and `System.err#println` statements " +
            "from the code. Production code should use a proper logging framework which provides " +
            "consistent formatting, configurable log levels, and centralized output control.";

    @Getter
    final Set<String> tags = singleton("RSPEC-S106");

    @Getter
    final Duration estimatedEffortPerOccurrence = Duration.ofMinutes(10);

    @Override
    public TreeVisitor<?, ExecutionContext> getVisitor() {
        return Preconditions.check(new UsesMethod<>(SYSTEM_OUT_PRINTLN), new JavaVisitor<ExecutionContext>() {

            @Override
            public @Nullable J visitMethodInvocation(J.MethodInvocation method, ExecutionContext ctx) {
                if (SYSTEM_OUT_PRINTLN.matches(method) &&
                        method.getSelect() instanceof J.FieldAccess &&
                        TypeUtils.isAssignableTo("java.lang.System", ((J.FieldAccess) method.getSelect()).getTarget().getType())) {
                    return StatementRemoval.remove(getCursor(), method);
                }
                return super.visitMethodInvocation(method, ctx);
            }
        });
    }
}
