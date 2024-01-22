/*
 * Copyright 2022 the original author or authors.
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

import org.openrewrite.*;
import org.openrewrite.java.JavaTemplate;
import org.openrewrite.java.JavaVisitor;
import org.openrewrite.java.MethodMatcher;
import org.openrewrite.java.search.UsesMethod;
import org.openrewrite.java.tree.J;

import java.time.Duration;
import java.util.Collections;
import java.util.Set;

public class CompareEnumsWithEqualityOperator extends Recipe {

    @Override
    public String getDisplayName() {
        return "Enum values should be compared with \"==\"";
    }

    @Override
    public String getDescription() {
        return "Replaces `Enum equals(java.lang.Object)` with `Enum == java.lang.Object`. An `!Enum equals(java.lang.Object)` will change to `!=`.";
    }

    @Override
    public Set<String> getTags() {
        return Collections.singleton("RSPEC-4551");
    }

    @Override
    public Duration getEstimatedEffortPerOccurrence() {
        return Duration.ofMinutes(5);
    }

    @Override
    public TreeVisitor<?, ExecutionContext> getVisitor() {
        MethodMatcher enumEquals = new MethodMatcher("java.lang.Enum equals(java.lang.Object)");
        return Preconditions.check(new UsesMethod<>(enumEquals), new JavaVisitor<ExecutionContext>() {
            @Override
            public J visitMethodInvocation(J.MethodInvocation method, ExecutionContext ctx) {
                J.MethodInvocation m = (J.MethodInvocation) super.visitMethodInvocation(method, ctx);
                if (enumEquals.matches(m) && m.getSelect() != null) {
                    Cursor parent = getCursor().dropParentUntil(is -> is instanceof J.Unary || is instanceof J.Block || is instanceof J.Binary);
                    boolean isNot = parent.getValue() instanceof J.Unary && ((J.Unary) parent.getValue()).getOperator() == J.Unary.Type.Not;
                    if (isNot) {
                        parent.putMessage("REMOVE_UNARY_NOT", parent.getValue());
                    }
                    String code = "#{any()} " + (isNot ? "!=" : "==") + " #{any()}";
                    return autoFormat(JavaTemplate
                            .builder(code)
                            .build()
                            .apply(updateCursor(m), m.getCoordinates().replace(), m.getSelect(), m.getArguments().get(0)), ctx);
                }
                return m;
            }

            @Override
            public J visitUnary(J.Unary unary, ExecutionContext ctx) {
                J j = super.visitUnary(unary, ctx);
                J.Unary asUnary = (J.Unary) j;
                if (asUnary.equals(getCursor().pollMessage("REMOVE_UNARY_NOT"))) {
                    return asUnary.getExpression().unwrap().withPrefix(asUnary.getPrefix());
                }
                return j;
            }
        });
    }
}
