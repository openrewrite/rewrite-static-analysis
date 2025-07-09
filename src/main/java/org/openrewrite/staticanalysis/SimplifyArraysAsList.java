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

import org.openrewrite.ExecutionContext;
import org.openrewrite.Preconditions;
import org.openrewrite.Recipe;
import org.openrewrite.TreeVisitor;
import org.openrewrite.java.JavaVisitor;
import org.openrewrite.java.MethodMatcher;
import org.openrewrite.java.search.UsesMethod;
import org.openrewrite.java.tree.J;

import java.time.Duration;
import java.util.Collections;
import java.util.Set;

public class SimplifyArraysAsList extends Recipe {
    private static final MethodMatcher ARRAYS_AS_LIST = new MethodMatcher("java.util.Arrays asList(..)");

    @Override
    public String getDisplayName() {
        return "Simplify `Arrays.asList(..)` with varargs";
    }

    @Override
    public String getDescription() {
        return "Simplifies `Arrays.asList()` method calls that use explicit array creation to use varargs instead. " +
                "For example, `Arrays.asList(new String[]{\"a\", \"b\", \"c\"})` becomes `Arrays.asList(\"a\", \"b\", \"c\")`.";
    }

    @Override
    public Set<String> getTags() {
        return Collections.singleton("RSPEC-S3878");
    }

    @Override
    public Duration getEstimatedEffortPerOccurrence() {
        return Duration.ofMinutes(2);
    }

    @Override
    public TreeVisitor<?, ExecutionContext> getVisitor() {
        return Preconditions.check(new UsesMethod<>(ARRAYS_AS_LIST), new JavaVisitor<ExecutionContext>() {
            @Override
            public J visitMethodInvocation(J.MethodInvocation method, ExecutionContext ctx) {
                J.MethodInvocation mi = (J.MethodInvocation) super.visitMethodInvocation(method, ctx);
                if (ARRAYS_AS_LIST.matches(mi) && mi.getArguments().size() == 1 &&
                        mi.getArguments().get(0) instanceof J.NewArray) {
                    J.NewArray newArray = (J.NewArray) mi.getArguments().get(0);
                    if (newArray.getDimensions().size() == 1 && newArray.getInitializer() != null) {
                        return mi.withArguments(newArray.getInitializer());
                    }
                }
                return mi;
            }
        });
    }
}
