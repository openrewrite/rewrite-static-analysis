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
import org.openrewrite.java.MethodMatcher;
import org.openrewrite.java.search.UsesType;
import org.openrewrite.java.tree.Expression;
import org.openrewrite.java.tree.J;
import org.openrewrite.java.tree.TypeUtils;

import static java.util.Collections.singletonList;

public class EqualsToContentEquals extends Recipe {
    private static final TreeVisitor<?, ExecutionContext> PRECONDITION = Preconditions.or(
            new UsesType<>("java.lang.CharSequence", false),
            new UsesType<>("java.lang.StringBuffer", false),
            new UsesType<>("java.lang.StringBuilder", false)
    );

    @Getter
    final String displayName = "Use `String.contentEquals(CharSequence)` instead of `String.equals(CharSequence.toString())`";

    @Getter
    final String description = "Use `String.contentEquals(CharSequence)` instead of `String.equals(CharSequence.toString())`.";

    @Override
    public TreeVisitor<?, ExecutionContext> getVisitor() {
        return Preconditions.check(PRECONDITION, new EqualsToContentEqualsVisitor());
    }

    private static class EqualsToContentEqualsVisitor extends JavaIsoVisitor<ExecutionContext> {
        private static final MethodMatcher EQUALS_MATCHER = new MethodMatcher("String equals(Object)");
        private static final MethodMatcher TOSTRING_MATCHER = new MethodMatcher("java.lang.* toString()");

        @Override
        public J.MethodInvocation visitMethodInvocation(J.MethodInvocation mi, ExecutionContext ctx) {
            J.MethodInvocation m = super.visitMethodInvocation(mi, ctx);
            if (!EQUALS_MATCHER.matches(m)) {
                return m;
            }
            Expression equalsArgument = m.getArguments().get(0);
            if (!TOSTRING_MATCHER.matches(equalsArgument)) {
                return m;
            }
            J.MethodInvocation inv = (J.MethodInvocation) equalsArgument;
            Expression toStringSelect = inv.getSelect();
            if (toStringSelect == null || !TypeUtils.isAssignableTo("java.lang.CharSequence", toStringSelect.getType())) {
                return m;
            }
            // Strip out the toString() on the argument and replace with contentEquals
            return m.withArguments(singletonList(toStringSelect))
                    .withName(m.getName().withSimpleName("contentEquals"));
        }
    }
}
