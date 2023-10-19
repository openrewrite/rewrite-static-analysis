/*
 * Copyright 2020 the original author or authors.
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

import org.openrewrite.ExecutionContext;
import org.openrewrite.Recipe;
import org.openrewrite.Tree;
import org.openrewrite.TreeVisitor;
import org.openrewrite.internal.lang.Nullable;
import org.openrewrite.java.cleanup.SimplifyBooleanExpressionVisitor;
import org.openrewrite.java.tree.J;
import org.openrewrite.kotlin.marker.IsNullSafe;

import java.time.Duration;
import java.util.Collections;
import java.util.Set;

public class SimplifyBooleanExpression extends Recipe {

    @Override
    public String getDisplayName() {
        return "Simplify boolean expression";
    }

    @Override
    public String getDescription() {
        return "Checks for overly complicated boolean expressions, such as `if (b == true)`, `b || true`, `!false`, etc.";
    }

    @Override
    public Set<String> getTags() {
        return Collections.singleton("RSPEC-1125");
    }

    @Override
    public Duration getEstimatedEffortPerOccurrence() {
        return Duration.ofMinutes(5);
    }

    @Override
    public TreeVisitor<?, ExecutionContext> getVisitor() {
        return new SimplifyBooleanExpressionVisitor() {

            @Override
            public @Nullable J visit(@Nullable Tree tree, ExecutionContext ctx) {
                // NOTE: This method is required here for the `TreeVisitorAdapter` to work
                return super.visit(tree, ctx);
            }

            // Comparing Kotlin nullable type `?` with true/false can not be simplified,
            // e.g. `X?.fun() == true` is not equivalent to `X?.fun()`
            @Override
            protected boolean shouldSimplifyEqualsOn(@Nullable J j) {
                if (j == null) {
                    return true;
                }

                if (j instanceof J.MethodInvocation) {
                    J.MethodInvocation m = (J.MethodInvocation) j;
                    return !m.getMarkers().findFirst(IsNullSafe.class).isPresent();
                }

                return true;
            }
        };
    }

    @Override
    public boolean causesAnotherCycle() {
        return true;
    }
}
