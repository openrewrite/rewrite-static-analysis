/*
 * Copyright 2024 the original author or authors.
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
import org.openrewrite.Recipe;
import org.openrewrite.Repeat;
import org.openrewrite.TreeVisitor;
import org.openrewrite.java.JavaVisitor;
import org.openrewrite.java.tree.Expression;
import org.openrewrite.java.tree.J;

import java.time.Duration;

public class SimplifyConstantTernaryExecution extends Recipe {
    @Override
    public String getDisplayName() {
        return "Simplify constant ternary branch execution";
    }

    @Override
    public String getDescription() {
        return "Checks for ternary expressions that are always `true` or `false` and simplifies them.";
    }

    @Override
    public @Nullable Duration getEstimatedEffortPerOccurrence() {
        return Duration.ofSeconds(15);
    }

    @Override
    public TreeVisitor<?, ExecutionContext> getVisitor() {
        JavaVisitor<ExecutionContext> v = new JavaVisitor<ExecutionContext>() {
            @Override
            public J visitTernary(J.Ternary ternary, ExecutionContext ctx) {
                J.Ternary t = (J.Ternary) super.visitTernary(ternary, ctx);
                Expression condition = SimplifyConstantIfBranchExecution.cleanupBooleanExpression(t.getCondition(), getCursor(), ctx);
                if (J.Literal.isLiteralValue(condition, true)) {
                    getCursor().getParentTreeCursor().putMessage("AUTO_FORMAT", true);
                    return t.getTruePart();
                } else if (J.Literal.isLiteralValue(condition, false)) {
                    getCursor().getParentTreeCursor().putMessage("AUTO_FORMAT", true);
                    return t.getFalsePart();
                }
                return t;
            }

            @Override
            public @Nullable J postVisit(J tree, ExecutionContext ctx) {
                if (getCursor().pollMessage("AUTO_FORMAT") != null) {
                    return autoFormat(tree, ctx);
                }
                return super.postVisit(tree, ctx);
            }
        };
        return Repeat.repeatUntilStable(v);
    }
}
