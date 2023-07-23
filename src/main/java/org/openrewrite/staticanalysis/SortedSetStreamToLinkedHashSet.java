/*
 * Copyright 2023 the original author or authors.
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

import lombok.EqualsAndHashCode;
import lombok.Value;
import org.openrewrite.ExecutionContext;
import org.openrewrite.Recipe;
import org.openrewrite.TreeVisitor;
import org.openrewrite.java.JavaIsoVisitor;
import org.openrewrite.java.JavaTemplate;
import org.openrewrite.java.MethodMatcher;
import org.openrewrite.java.tree.J;

@Value
@EqualsAndHashCode(callSuper = false)
public class SortedSetStreamToLinkedHashSet extends Recipe {

    @Override
    public String getDisplayName() {
        return "Sorted set stream should be collected to LinkedHashSet";
    }

    @Override
    public String getDescription() {
        return "Correct 'set.stream().sorted().collect(Collectors.toSet())' to 'set.stream().sorted().collect(LinkedHashSet::new)'.";
    }

    private static final MethodMatcher STREAM_COLLECT_METHOD_MATCHER = new MethodMatcher("java.util.stream.Stream collect(java.util.stream.Collector)");
    private static final MethodMatcher STREAM_SORTED_METHOD_MATCHER = new MethodMatcher("java.util.stream.Stream sorted()");
    private static final MethodMatcher COLLECTORS_TO_SET_METHOD_MATCHER = new MethodMatcher("java.util.stream.Collectors toSet()");

    @Override
    public TreeVisitor<?, ExecutionContext> getVisitor() {
        return new JavaIsoVisitor<ExecutionContext>() {
            private JavaTemplate template = JavaTemplate.builder("Collectors.toCollection(LinkedHashSet::new)")
                    .imports("java.util.stream.Collectors", "java.util.LinkedHashSet")
                    .build();

            @Override
            public J.MethodInvocation visitMethodInvocation(J.MethodInvocation method, ExecutionContext executionContext) {
                J.MethodInvocation mi = super.visitMethodInvocation(method, executionContext);
                if (STREAM_COLLECT_METHOD_MATCHER.matches(mi)
                    && STREAM_SORTED_METHOD_MATCHER.matches(mi.getSelect())
                    && COLLECTORS_TO_SET_METHOD_MATCHER.matches(mi.getArguments().get(0))) {
                    maybeRemoveImport("java.util.stream.Collectors.toSet");
                    maybeAddImport("java.util.LinkedHashSet");
                    maybeAddImport("java.util.stream.Collectors");
                    return template.apply(updateCursor(mi), mi.getCoordinates().replaceArguments());
                }
                return mi;
            }
        };
    }
}
