/*
 * Copyright 2020 the original author or authors.
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
import org.openrewrite.*;
import org.openrewrite.java.JavaIsoVisitor;
import org.openrewrite.java.style.Checkstyle;
import org.openrewrite.java.style.HideUtilityClassConstructorStyle;
import org.openrewrite.java.tree.J;
import org.openrewrite.java.tree.JavaSourceFile;

import java.time.Duration;
import java.util.Collections;
import java.util.Set;

import static java.util.Objects.requireNonNull;

@Incubating(since = "7.0.0")
public class HideUtilityClassConstructor extends Recipe {

    @Override
    public String getDisplayName() {
        return "Hide utility class constructor";
    }

    @Override
    public String getDescription() {
        return "Ensures utility classes (classes containing only static methods or fields in their API) do not have a public constructor.";
    }

    @Override
    public Set<String> getTags() {
        return Collections.singleton("RSPEC-S1118");
    }

    @Override
    public Duration getEstimatedEffortPerOccurrence() {
        return Duration.ofMinutes(5);
    }

    @Override
    public TreeVisitor<?, ExecutionContext> getVisitor() {
        return new HideUtilityClassConstructorFromCompilationUnitStyle();
    }

    private static class HideUtilityClassConstructorFromCompilationUnitStyle extends JavaIsoVisitor<ExecutionContext> {
        @Override
        public J visit(@Nullable Tree tree, ExecutionContext ctx) {
            if (tree instanceof JavaSourceFile) {
                JavaSourceFile cu = (JavaSourceFile) requireNonNull(tree);
                HideUtilityClassConstructorStyle style = cu.getStyle(HideUtilityClassConstructorStyle.class);
                if (style == null) {
                    style = Checkstyle.hideUtilityClassConstructorStyle();
                }
                return new HideUtilityClassConstructorVisitor<>(style).visit(cu, ctx);
            }
            return (J) tree;
        }
    }
}
