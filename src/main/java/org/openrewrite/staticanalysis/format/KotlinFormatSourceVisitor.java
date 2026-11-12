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
package org.openrewrite.staticanalysis.format;

import org.jspecify.annotations.Nullable;
import org.openrewrite.ExecutionContext;
import org.openrewrite.Parser;
import org.openrewrite.SourceFile;
import org.openrewrite.Tree;
import org.openrewrite.TreeVisitor;
import org.openrewrite.internal.ListUtils;
import org.openrewrite.java.tree.J;
import org.openrewrite.marker.Markers;
import org.openrewrite.java.tree.Space;
import org.openrewrite.kotlin.KotlinIsoVisitor;
import org.openrewrite.kotlin.KotlinParser;
import org.openrewrite.kotlin.tree.K;
import org.openrewrite.staticanalysis.table.SourceFilesFormatted;
import org.openrewrite.tree.ParseError;

import java.util.Collections;

class KotlinFormatSourceVisitor extends FormatSourceVisitor {

    private final boolean formatScripts;

    KotlinFormatSourceVisitor(SourceFormatter formatter, SourceFilesFormatted table, boolean formatScripts) {
        super(formatter, table);
        this.formatScripts = formatScripts;
    }

    @Override
    boolean accepts(SourceFile sourceFile) {
        String path = sourceFile.getSourcePath().toString();
        return (sourceFile instanceof K.CompilationUnit || sourceFile instanceof ParseError && (path.endsWith(".kt") || path.endsWith(".kts"))) &&
               (formatScripts || !path.endsWith(".kts"));
    }

    @Override
    @Nullable SourceFile parse(SourceFile original, Parser.Input input, ExecutionContext ctx) {
        return KotlinParser.builder().build()
                .parseInputs(Collections.singletonList(input), null, ctx)
                .findFirst()
                .orElse(null);
    }

    @Override
    TreeVisitor<?, Integer> formattingVisitor(WhitespaceReconciler.Listener listener) {
        return new KotlinFormatting(listener);
    }

    /**
     * Visits every tree, {@link Space} and set of {@link Markers} of a Kotlin source file. Kotlin's own
     * locations of a {@link Space} reach this as {@link Space.Location#LANGUAGE_EXTENSION}.
     */
    private static final class KotlinFormatting extends KotlinIsoVisitor<Integer> {
        private final WhitespaceReconciler.Listener listener;

        KotlinFormatting(WhitespaceReconciler.Listener listener) {
            this.listener = listener;
        }

        @Override
        public @Nullable J visit(@Nullable Tree tree, Integer p) {
            return super.visit(tree == null ? null : listener.onTree(tree), p);
        }

        @Override
        public K.CompilationUnit visitCompilationUnit(K.CompilationUnit cu, Integer p) {
            // KotlinVisitor does not visit the markers of the padding of top-level statements, where a
            // semicolon that ends one is kept
            K.CompilationUnit c = super.visitCompilationUnit(cu, p);
            return c.getPadding().withStatements(ListUtils.map(c.getPadding().getStatements(),
                    rp -> rp.withMarkers(visitMarkers(rp.getMarkers(), p))));
        }

        @Override
        @SuppressWarnings("DataFlowIssue")
        public Space visitSpace(@Nullable Space space, Space.Location loc, Integer p) {
            return listener.onSpace(space, loc);
        }

        @Override
        public Markers visitMarkers(@Nullable Markers markers, Integer p) {
            return markers == null ? Markers.EMPTY : listener.onMarkers(markers);
        }
    }
}
