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

import org.jspecify.annotations.Nullable;
import org.openrewrite.ExecutionContext;
import org.openrewrite.Preconditions;
import org.openrewrite.Recipe;
import org.openrewrite.TreeVisitor;
import org.openrewrite.java.JavaIsoVisitor;
import org.openrewrite.java.MethodMatcher;
import org.openrewrite.java.search.UsesMethod;
import org.openrewrite.java.tree.J;
import org.openrewrite.java.tree.JRightPadded;
import org.openrewrite.java.tree.Space;
import org.openrewrite.marker.Markers;

import static java.util.Collections.emptyList;
import static org.openrewrite.Tree.randomId;

public class RemoveSystemOutPrintln extends Recipe {
    private static final MethodMatcher SYSTEM_OUT_PRINTLN = new MethodMatcher("java.io.PrintStream println(..)");

    @Override
    public String getDisplayName() {
        return "Remove `System.out#println` statements";
    }

    @Override
    public String getDescription() {
        return "Print statements are often left accidentally after debugging an issue.";
    }

    @Override
    public TreeVisitor<?, ExecutionContext> getVisitor() {
        return Preconditions.check(new UsesMethod<>(SYSTEM_OUT_PRINTLN), new JavaIsoVisitor<ExecutionContext>() {

            @Override
            public J.Lambda visitLambda(J.Lambda lambda, ExecutionContext ctx) {
                J.Lambda l = super.visitLambda(lambda, ctx);
                //noinspection ConstantValue
                if (l.getBody() == null) {
                    l = l.withBody(new J.Block(randomId(), lambda.getPrefix(), Markers.EMPTY, JRightPadded.build(false), emptyList(), Space.EMPTY));
                }
                return l;
            }

            @Override
            @SuppressWarnings("NullableProblems")
            public  J.@Nullable MethodInvocation visitMethodInvocation(J.MethodInvocation method, ExecutionContext ctx) {
                if (SYSTEM_OUT_PRINTLN.matches(method)) {
                    return null;
                }
                return super.visitMethodInvocation(method, ctx);
            }
        });
    }
}
