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
import org.openrewrite.ExecutionContext;
import org.openrewrite.Preconditions;
import org.openrewrite.Recipe;
import org.openrewrite.TreeVisitor;
import org.openrewrite.java.JavaIsoVisitor;
import org.openrewrite.java.JavaTemplate;
import org.openrewrite.java.MethodMatcher;
import org.openrewrite.java.search.UsesMethod;
import org.openrewrite.java.tree.Expression;
import org.openrewrite.java.tree.J;
import org.openrewrite.java.tree.JavaType;

import java.util.Set;

import static java.util.Collections.singleton;

public class RemoveHashCodeCallsFromArrayInstances extends Recipe {
    private static final MethodMatcher HASHCODE_MATCHER = new MethodMatcher("java.lang.Object hashCode()");

    @Getter
    final String displayName = "`hashCode()` should not be called on array instances";

    @Getter
    final String description = "Replace `hashCode()` calls on arrays with `Arrays.hashCode()` because the results from `hashCode()` " +
            "are not helpful.";

    @Override
    public Set<String> getTags() {
        return singleton("RSPEC-S2116");
    }

    @Override
    public TreeVisitor<?, ExecutionContext> getVisitor() {
        return Preconditions.check(new UsesMethod<>(HASHCODE_MATCHER), new RemoveHashCodeCallsFromArrayInstancesVisitor());
    }

    private static class RemoveHashCodeCallsFromArrayInstancesVisitor extends JavaIsoVisitor<ExecutionContext> {
        @Override
        public J.MethodInvocation visitMethodInvocation(J.MethodInvocation methodInvocation, ExecutionContext ctx) {
            J.MethodInvocation mi = super.visitMethodInvocation(methodInvocation, ctx);

            if (HASHCODE_MATCHER.matches(mi)) {
                Expression select = mi.getSelect();
                if (select != null && select.getType() instanceof JavaType.Array) {
                    maybeAddImport("java.util.Arrays");
                    return JavaTemplate.builder("Arrays.hashCode(#{anyArray(java.lang.Object)})")
                            .imports("java.util.Arrays")
                            .build()
                            .apply(getCursor(), mi.getCoordinates().replace(), select);
                }
            }

            return mi;
        }
    }
}
