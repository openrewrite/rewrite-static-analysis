/*
 * Copyright 2021 the original author or authors.
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
import org.openrewrite.Cursor;
import org.openrewrite.internal.ListUtils;
import org.openrewrite.java.JavaIsoVisitor;
import org.openrewrite.java.JavaTemplate;
import org.openrewrite.java.MethodMatcher;
import org.openrewrite.java.tree.Expression;
import org.openrewrite.java.tree.J;
import org.openrewrite.java.tree.JavaCoordinates;

import javax.annotation.Nullable;

@Value
@EqualsAndHashCode(callSuper = false)
public class SortedSetStreamToLinkedHashSetVisitor<ExecutionContext> extends JavaIsoVisitor<ExecutionContext> {

    private static final MethodMatcher STREAM_COLLECT_METHOD_MATCHER = new MethodMatcher("java.util.stream.Stream " +
            "collect(java.util.stream.Collector)");
    private static final MethodMatcher STREAM_SORTED_METHOD_MATCHER = new MethodMatcher("java.util.stream.Stream sorted()");
    private static final MethodMatcher COLLECTORS_TO_SET_METHOD_MATCHER = new MethodMatcher("java.util.stream.Collectors toSet()");
    JavaTemplate template = JavaTemplate.builder("Collectors.toCollection(LinkedHashSet::new)")
            .imports("java.util.LinkedHashSet", "java.util.stream.Collectors").build();

    @Override
    public J.MethodInvocation visitMethodInvocation(J.MethodInvocation method, ExecutionContext executionContext) {
        J.MethodInvocation mi = super.visitMethodInvocation(method, executionContext);
        if (STREAM_COLLECT_METHOD_MATCHER.matches(mi) && isSortedStreamToUnOrderedSet(mi)) {
            mi = template.apply(updateCursor(mi), mi.getCoordinates().replaceArguments());
            maybeAddImport("java.util.LinkedHashSet");
            maybeAddImport("java.util.stream.Collectors");
        }
        return mi;
    }

    private boolean isSortedStreamToUnOrderedSet(J.MethodInvocation mi) {
        Expression selectExp = mi.getSelect();
        Expression argExp = mi.getArguments().get(0);
        if (selectExp instanceof J.MethodInvocation && argExp instanceof J.MethodInvocation) {
            J.MethodInvocation selectMI = (J.MethodInvocation) selectExp;
            J.MethodInvocation argMI = (J.MethodInvocation) argExp;
            return STREAM_SORTED_METHOD_MATCHER.matches(selectMI) && COLLECTORS_TO_SET_METHOD_MATCHER.matches(argMI);
        }
        return false;
    }
}
