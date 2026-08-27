/*
 * Copyright 2026 the original author or authors.
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
package org.openrewrite.staticanalysis.go;

import org.jspecify.annotations.Nullable;
import org.openrewrite.Tree;
import org.openrewrite.TreeVisitor;
import org.openrewrite.golang.tree.Go;
import org.openrewrite.internal.ReflectionUtils;
import org.openrewrite.marker.SearchResult;

/**
 * Add a search marker if visiting a Go file
 */
public class GoFileChecker<P> extends TreeVisitor<Tree, P> {
    private static final boolean IS_AVAILABLE = ReflectionUtils.isClassAvailable("org.openrewrite.golang.tree.Go");

    @Override
    public @Nullable Tree visit(@Nullable Tree tree, P p) {
        if (IS_AVAILABLE && tree instanceof Go.CompilationUnit) {
            return SearchResult.found(tree);
        }
        return tree;
    }
}
