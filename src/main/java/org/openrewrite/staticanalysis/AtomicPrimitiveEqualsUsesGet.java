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

import org.jspecify.annotations.Nullable;
import org.openrewrite.ExecutionContext;
import org.openrewrite.Preconditions;
import org.openrewrite.Recipe;
import org.openrewrite.TreeVisitor;
import org.openrewrite.java.JavaTemplate;
import org.openrewrite.java.JavaVisitor;
import org.openrewrite.java.MethodMatcher;
import org.openrewrite.java.search.UsesType;
import org.openrewrite.java.tree.J;
import org.openrewrite.java.tree.JavaType;
import org.openrewrite.java.tree.TypeUtils;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

public class AtomicPrimitiveEqualsUsesGet extends Recipe {

    private static final Set<String> ATOMIC_PRIMITIVE_TYPES = new HashSet<>(Arrays.asList(
            "java.util.concurrent.atomic.AtomicBoolean",
            "java.util.concurrent.atomic.AtomicInteger",
            "java.util.concurrent.atomic.AtomicLong"
    ));

    @Override
    public String getDisplayName() {
        return "Atomic Boolean, Integer, and Long equality checks compare their values";
    }

    @Override
    public String getDescription() {
        return "`AtomicBoolean#equals(Object)`, `AtomicInteger#equals(Object)` and `AtomicLong#equals(Object)` are only equal to their instance. This recipe converts `a.equals(b)` to `a.get() == b.get()`.";
    }

    @Override
    public Set<String> getTags() {
        return Collections.singleton("RSPEC-S2204");
    }

    @Override
    public TreeVisitor<?, ExecutionContext> getVisitor() {
        return Preconditions.check(Preconditions.or(
                new UsesType<>("java.util.concurrent.atomic.AtomicBoolean", false),
                new UsesType<>("java.util.concurrent.atomic.AtomicInteger", false),
                new UsesType<>("java.util.concurrent.atomic.AtomicLong", false)
        ), new JavaVisitor<ExecutionContext>() {
            private final MethodMatcher aiMethodMatcher = new MethodMatcher("java.lang.Object equals(java.lang.Object)");

            @Override
            public J visitMethodInvocation(J.MethodInvocation method, ExecutionContext ctx) {
                J.MethodInvocation mi = (J.MethodInvocation) super.visitMethodInvocation(method, ctx);
                if (mi.getSelect() != null && isAtomicEqualsType(mi.getSelect().getType()) && aiMethodMatcher.matches(mi) &&
                    TypeUtils.isOfType(mi.getSelect().getType(), mi.getArguments().get(0).getType())) {
                    JavaType.FullyQualified fqt = TypeUtils.asFullyQualified(mi.getSelect().getType());
                    if (fqt != null) {
                        String templateString = "#{any(" + fqt.getFullyQualifiedName() + ")}.get() == #{any(" + fqt.getFullyQualifiedName() + ")}.get()";
                        return JavaTemplate.builder(templateString)
                                .imports(fqt.getFullyQualifiedName())
                                .build()
                                .apply(updateCursor(mi), mi.getCoordinates().replace(), mi.getSelect(), mi.getArguments().get(0));
                    }
                }
                return mi;
            }

            private boolean isAtomicEqualsType(@Nullable JavaType type) {
                return type instanceof JavaType.FullyQualified &&
                       ATOMIC_PRIMITIVE_TYPES.contains(((JavaType.FullyQualified) type).getFullyQualifiedName());
            }
        });
    }
}
