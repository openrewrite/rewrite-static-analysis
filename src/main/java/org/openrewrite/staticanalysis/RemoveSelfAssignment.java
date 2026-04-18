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
import org.openrewrite.Recipe;
import org.openrewrite.TreeVisitor;
import org.openrewrite.java.JavaVisitor;
import org.openrewrite.java.search.SemanticallyEqual;
import org.openrewrite.java.tree.J;

import java.time.Duration;
import java.util.Set;

import static java.util.Collections.singleton;

@Getter
public class RemoveSelfAssignment extends Recipe {

    final String displayName = "Variables should not be self-assigned";

    final String description = "Self-assignments such as `x = x` have no effect and indicate a copy-paste " +
            "error or typo where the left-hand or right-hand side should reference a different variable.";

    final Set<String> tags = singleton("RSPEC-S1656");

    final Duration estimatedEffortPerOccurrence = Duration.ofMinutes(3);

    @Override
    public TreeVisitor<?, ExecutionContext> getVisitor() {
        return new JavaVisitor<ExecutionContext>() {

            @Override
            public J.@Nullable Assignment visitAssignment(J.Assignment assignment, ExecutionContext ctx) {
                J.Assignment a = (J.Assignment) super.visitAssignment(assignment, ctx);
                if (SemanticallyEqual.areEqual(a.getVariable(), a.getAssignment())) {
                    //noinspection DataFlowIssue
                    return null;
                }
                return a;
            }
        };
    }
}
