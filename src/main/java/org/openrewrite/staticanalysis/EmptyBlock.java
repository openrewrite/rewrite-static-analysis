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
import org.openrewrite.Recipe;
import org.openrewrite.Tree;
import org.openrewrite.TreeVisitor;
import org.openrewrite.java.JavaIsoVisitor;
import org.openrewrite.java.style.Checkstyle;
import org.openrewrite.java.style.EmptyBlockStyle;
import org.openrewrite.java.tree.J;
import org.openrewrite.java.tree.JavaSourceFile;

import java.util.Set;

import static java.util.Collections.singleton;
import static java.util.Objects.requireNonNull;

public class EmptyBlock extends Recipe {

    @Override
    public String getDisplayName() {
        return "Remove empty blocks";
    }

    @Override
    public String getDescription() {
        return "Remove empty blocks that effectively do nothing.";
    }

    @Override
    public Set<String> getTags() {
        return singleton("RSPEC-S108");
    }

    @Override
    public TreeVisitor<?, ExecutionContext> getVisitor() {
        return new EmptyBlockFromCompilationUnitStyle();
    }

    private static class EmptyBlockFromCompilationUnitStyle extends JavaIsoVisitor<ExecutionContext> {
        @Override
        public J visit(@Nullable Tree tree, ExecutionContext ctx) {
            if (tree instanceof JavaSourceFile) {
                JavaSourceFile cu = (JavaSourceFile) requireNonNull(tree);
                EmptyBlockStyle style = cu.getStyle(EmptyBlockStyle.class);
                if (style == null) {
                    style = Checkstyle.emptyBlock();
                }
                return new EmptyBlockVisitor<>(style).visit(cu, ctx);
            }
            return (J) tree;
        }
    }
}
