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
package org.openrewrite.staticanalysis.csharp;

import org.jspecify.annotations.Nullable;
import org.openrewrite.Tree;
import org.openrewrite.TreeVisitor;
import org.openrewrite.csharp.tree.Cs;
import org.openrewrite.marker.SearchResult;
import org.openrewrite.staticanalysis.internal.ClassPathUtils;

/**
 * Add a search marker if vising a CSharp file
 */
public class CSharpFileChecker<P> extends TreeVisitor<Tree, P> {
    private static final boolean IS_CSHARP_AVAILABLE = ClassPathUtils.isAvailable("org.openrewrite.csharp.tree.Cs");

    @Override
    public @Nullable Tree visit(@Nullable Tree tree, P p) {
        if (IS_CSHARP_AVAILABLE && tree instanceof Cs.CompilationUnit) {
            return SearchResult.found(tree);
        }
        return tree;
    }
}
