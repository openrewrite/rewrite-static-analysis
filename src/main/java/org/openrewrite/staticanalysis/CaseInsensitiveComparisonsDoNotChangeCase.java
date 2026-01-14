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
import org.openrewrite.internal.ListUtils;
import org.openrewrite.java.JavaIsoVisitor;
import org.openrewrite.java.MethodMatcher;
import org.openrewrite.java.search.UsesMethod;
import org.openrewrite.java.tree.J;

import java.util.Set;

import static java.util.Collections.singleton;

public class CaseInsensitiveComparisonsDoNotChangeCase extends Recipe {

    private static final MethodMatcher COMPARE_IGNORE_CASE_METHOD_MATCHER = new MethodMatcher("java.lang.String equalsIgnoreCase(java.lang.String)");
    private static final MethodMatcher TO_LOWER_CASE_METHOD_MATCHER = new MethodMatcher("java.lang.String toLowerCase()");
    private static final MethodMatcher TO_UPPER_CASE_METHOD_MATCHER = new MethodMatcher("java.lang.String toUpperCase()");

    @Getter
    final String displayName = "CaseInsensitive comparisons do not alter case";

    @Getter
    final String description = "Remove `String#toLowerCase()` or `String#toUpperCase()` from `String#equalsIgnoreCase(..)` comparisons.";

    @Override
    public Set<String> getTags() {
        return singleton("RSPEC-S1157");
    }

    @Override
    public TreeVisitor<?, ExecutionContext> getVisitor() {
        return Preconditions.check(new UsesMethod<>(COMPARE_IGNORE_CASE_METHOD_MATCHER), new JavaIsoVisitor<ExecutionContext>() {
            @Override
            public J.MethodInvocation visitMethodInvocation(J.MethodInvocation method, ExecutionContext ctx) {
                J.MethodInvocation mi = super.visitMethodInvocation(method, ctx);
                if (COMPARE_IGNORE_CASE_METHOD_MATCHER.matches(mi)) {
                    mi = mi.withArguments(ListUtils.map(mi.getArguments(), arg -> {
                        if (arg instanceof J.MethodInvocation && isChangeCaseMethod(arg)) {
                            return ((J.MethodInvocation) arg).getSelect();
                        }
                        return arg;
                    }));
                    if (isChangeCaseMethod(mi.getSelect())) {
                        J.MethodInvocation mChangeCase = (J.MethodInvocation) mi.getSelect();
                        mi = mi.withSelect(mChangeCase.getSelect());
                    }
                }
                return mi;
            }

            private boolean isChangeCaseMethod(@Nullable J j) {
                if (j instanceof J.MethodInvocation) {
                    J.MethodInvocation mi = (J.MethodInvocation) j;
                    return TO_LOWER_CASE_METHOD_MATCHER.matches(mi) || TO_UPPER_CASE_METHOD_MATCHER.matches(mi);
                }
                return false;
            }
        });
    }
}
