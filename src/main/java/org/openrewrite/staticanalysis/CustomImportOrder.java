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

import org.jspecify.annotations.Nullable;
import org.openrewrite.ExecutionContext;
import org.openrewrite.Recipe;
import org.openrewrite.Tree;
import org.openrewrite.TreeVisitor;
import org.openrewrite.java.JavaIsoVisitor;
import org.openrewrite.java.style.Checkstyle;
import org.openrewrite.java.style.CustomImportOrderStyle;
import org.openrewrite.java.tree.J;
import org.openrewrite.java.tree.JavaSourceFile;
import org.openrewrite.style.Style;

import static java.util.Objects.requireNonNull;

public class CustomImportOrder extends Recipe {

    @Override
    public String getDisplayName() {
        return "Custom import order";
    }

    @Override
    public String getDescription() {
        return "Updates and reorders Java import declarations according to group and order settings compatible with the Checkstyle 'CustomImportOrder' check.";
    }

    @Override
    public TreeVisitor<?, ExecutionContext> getVisitor() {
        return new CustomImportOrderCompilationUnitStyle();
    }

    private static class CustomImportOrderCompilationUnitStyle extends JavaIsoVisitor<ExecutionContext> {
        @Override
        public @Nullable J visit(@Nullable Tree tree, ExecutionContext ctx) {
            if (tree instanceof JavaSourceFile) {
                JavaSourceFile cu = (JavaSourceFile) requireNonNull(tree);
                CustomImportOrderStyle style = Style.from(CustomImportOrderStyle.class, cu);
                return new CustomImportOrderVisitor<>(style).visitNonNull(cu, ctx);
            }
            return (J) tree;
        }
    }
}
