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
import org.openrewrite.TreeVisitor;
import org.openrewrite.java.JavaParser;
import org.openrewrite.java.tree.J;
import org.openrewrite.staticanalysis.table.SourceFilesFormatted;
import org.openrewrite.tree.ParseError;

class JavaFormatSourceVisitor extends FormatSourceVisitor {

    JavaFormatSourceVisitor(SourceFormatter formatter, SourceFilesFormatted table) {
        super(formatter, table);
    }

    @Override
    boolean accepts(SourceFile sourceFile) {
        // Only Java: Kotlin, Groovy and the other languages built on J have compilation units of their own
        return sourceFile instanceof J.CompilationUnit ||
               sourceFile instanceof ParseError && sourceFile.getSourcePath().toString().endsWith(".java");
    }

    /**
     * The formatter's output is parsed only for its shape, so without type attribution, which besides being
     * slower can run for many minutes on a deeply nested expression when the parser has no class path.
     */
    private static final ParserPool PARSERS = new ParserPool(() -> JavaParser.fromJavaVersion().typeAttribution(false).build());

    @Override
    @Nullable SourceFile parse(SourceFile original, Parser.Input input, ExecutionContext ctx) {
        return PARSERS.parse(input, ctx);
    }

    @Override
    TreeVisitor<?, Integer> formattingVisitor(WhitespaceReconciler.Listener listener) {
        return new WhitespaceReconciler.JavaFormatting(listener);
    }
}
