/*
 * Copyright 2025 the original author or authors.
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
import org.openrewrite.java.JavaTemplate;
import org.openrewrite.java.JavaVisitor;
import org.openrewrite.java.MethodMatcher;
import org.openrewrite.java.search.UsesJavaVersion;
import org.openrewrite.java.search.UsesMethod;
import org.openrewrite.java.tree.J;

import java.time.Duration;

/**
 * Replaces Stream.collect(Collectors.toList()) with Stream.toList()
 * <p>
 * <a href="https://github.com/apache/maven/pull/2322">[POC] rewrite-maven-plugin: Introduce OpenRewrite by Moderne</a>
 * <a href="https://github.com/apache/maven/pull/2277">Modernize codebase with Java improvements</a>
 * <a href="https://github.com/apache/maven/pull/2277#pullrequestreview-2814419390">suppression request</a>
 *
 */
public class ReplaceCollectToListWithToList extends Recipe {

    private static final MethodMatcher COLLECT_TO_LIST = new MethodMatcher("java.util.stream.Stream collect(java.util.stream.Collector)");

    @Override
    public String getDisplayName() {
        return "Replace `Stream.collect(collectors.toList())` with `Stream.toList()`";
    }

    @Override
    public String getDescription() {
        return "Replace Java 11 `Stream.collect(Collectors.toList())` with Java 16+ `Stream.toList()` for more concise syntax and an unmodifiable return value.";
    }

    @Override
    public Duration getEstimatedEffortPerOccurrence() {
        return Duration.ofMinutes(1);
    }

    @Override
    public TreeVisitor<?, ExecutionContext> getVisitor() {
        return Preconditions.check(Preconditions.and(
                        new UsesJavaVersion<>(16),
                        new UsesMethod<>(COLLECT_TO_LIST)),
                new JavaVisitor<ExecutionContext>() {

                    private final JavaTemplate template = JavaTemplate
                            .builder("#{any(java.util.stream.Stream)}.toList()")
                            .build();

                    @Override
                    public J visitMethodInvocation(J.MethodInvocation method, ExecutionContext ctx) {
                        J.MethodInvocation m = (J.MethodInvocation) super.visitMethodInvocation(method, ctx);

                        if (COLLECT_TO_LIST.matches(m) && isCollectorsToList(m)) {
                            maybeRemoveImport("java.util.stream.Collectors");
                            return template.apply(
                                    updateCursor(m),
                                    m.getCoordinates().replace(),
                                    m.getSelect()
                            );
                        }
                        return m;
                    }

                    private boolean isCollectorsToList(J.MethodInvocation method) {
                        return method.getArguments().get(0) instanceof J.MethodInvocation &&
                                ((J.MethodInvocation) method.getArguments().get(0)).getSimpleName().equals("toList") &&
                                ((J.MethodInvocation) method.getArguments().get(0)).getSelect() != null &&
                                ((J.MethodInvocation) method.getArguments().get(0)).getSelect().toString().equals("Collectors");
                    }
                });
    }
}
