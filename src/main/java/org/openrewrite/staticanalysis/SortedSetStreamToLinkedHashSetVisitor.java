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
import org.openrewrite.internal.ListUtils;
import org.openrewrite.java.JavaIsoVisitor;
import org.openrewrite.java.JavaTemplate;
import org.openrewrite.java.MethodMatcher;
import org.openrewrite.java.tree.J;
import org.openrewrite.java.tree.JavaCoordinates;

import javax.annotation.Nullable;
import java.util.HashSet;
import java.util.stream.Collectors;

@Value
@EqualsAndHashCode(callSuper = false)
public class SortedSetStreamToLinkedHashSetVisitor<P> extends JavaIsoVisitor<P> {

    private static final MethodMatcher STREAM_COLLECT_METHOD_MATCHER = new MethodMatcher("java.util.stream.Stream.collect(java.util.stream.Collector)");
    private static final MethodMatcher STREAM_SORTED_METHOD_MATCHER = new MethodMatcher("java.util.stream.Stream.sorted()");
    private static final MethodMatcher COLLECTORS_TO_SET_METHOD_MATCHER = new MethodMatcher("java.util.stream.Collectors.toSet()");
    JavaTemplate template = JavaTemplate.builder("LinkedHashSet::new").imports("java.util.LinkedHashSet").build();

    @Override
    public J.MethodInvocation visitMethodInvocation(J.MethodInvocation method, P p) {
        J.MethodInvocation mi = super.visitMethodInvocation(method, p);
        if (STREAM_COLLECT_METHOD_MATCHER.matches(mi) && isSortedStream(mi.getSelect())) {
            JavaCoordinates replace = mi.getCoordinates().replace();
            mi = mi.withArguments(ListUtils.map(mi.getArguments(), arg -> {
                if (isCollectToUnorderedSet(arg)) {
                    return template.apply(getCursor(), replace);
                }
                return arg;
            }));
        }
        return mi;
    }

    private boolean isSortedStream(@Nullable J j) {
        if (j instanceof J.MethodInvocation) {
            J.MethodInvocation mi = (J.MethodInvocation) j;
            return STREAM_COLLECT_METHOD_MATCHER.matches(mi);
        }
        return false;
    }

    private boolean isCollectToUnorderedSet(@Nullable J j) {
        if (j instanceof J.MethodInvocation) {
            J.MethodInvocation mi = (J.MethodInvocation) j;
            return COLLECTORS_TO_SET_METHOD_MATCHER.matches(mi);
        }
        return false;
    }
}
