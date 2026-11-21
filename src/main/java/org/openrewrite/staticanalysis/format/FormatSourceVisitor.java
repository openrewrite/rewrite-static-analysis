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
import org.openrewrite.*;
import org.openrewrite.internal.ExceptionUtils;
import org.openrewrite.marker.Markers;
import org.openrewrite.staticanalysis.table.SourceFilesFormatted;
import org.openrewrite.text.PlainTextParser;
import org.openrewrite.tree.ParseError;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.Optional;

/**
 * Formats each source file it accepts with a {@link SourceFormatter}, and makes the source file print as the
 * formatter's output, keeping as much of the original tree as it can:
 * <ol>
 *     <li>the formatter's whitespace put onto the original tree, which keeps its types, when the two parse to the
 *     same tree but for whitespace and the order of imports;</li>
 *     <li>otherwise the formatter's output parsed again, without types, under the original's id and markers;</li>
 *     <li>otherwise the formatter's output as plain text.</li>
 * </ol>
 * A file the formatter rejects is left as it was. Each file that is changed or rejected gets a row in
 * {@link SourceFilesFormatted}.
 */
abstract class FormatSourceVisitor extends TreeVisitor<Tree, ExecutionContext> {

    private final SourceFormatter formatter;
    private final SourceFilesFormatted table;

    FormatSourceVisitor(SourceFormatter formatter, SourceFilesFormatted table) {
        this.formatter = formatter;
        this.table = table;
    }

    /**
     * Whether this formatter formats this source file.
     */
    abstract boolean accepts(SourceFile sourceFile);

    /**
     * Parse the formatter's output, with no class path.
     */
    abstract @Nullable SourceFile parse(SourceFile original, Parser.Input input, ExecutionContext ctx);

    /**
     * A visitor of the source file's language that calls the listener with what it visits.
     */
    abstract TreeVisitor<?, Integer> formattingVisitor(WhitespaceReconciler.Listener listener);

    @Override
    public boolean isAcceptable(SourceFile sourceFile, ExecutionContext ctx) {
        return accepts(sourceFile);
    }

    @Override
    public @Nullable Tree visit(@Nullable Tree tree, ExecutionContext ctx) {
        if (!(tree instanceof SourceFile)) {
            return tree;
        }
        SourceFile original = (SourceFile) tree;
        // A file the parser could not read keeps its text, which the formatter's own parser may well read
        String source = original instanceof ParseError ? ((ParseError) original).getText() :
                original.printAll(new PrintOutputCapture<>(0, PrintOutputCapture.MarkerPrinter.SANITIZED));
        String formatterName = formatter.formatter.getName();
        String formatted;
        try {
            formatted = formatter.format(source, original.getSourcePath().toString().endsWith(".kts"));
            formatterName += " " + formatter.formatter.getVersion();
        } catch (SourceFormatter.FormatterFailure | IllegalStateException e) {
            table.insertRow(ctx, new SourceFilesFormatted.Row(original.getSourcePath().toString(), formatterName,
                    "Not formatted", e.getMessage()));
            return original;
        }
        if (formatted.equals(source)) {
            return original;
        }

        Parser.Input input = new Parser.Input(original.getSourcePath(), null,
                () -> new ByteArrayInputStream(formatted.getBytes(StandardCharsets.UTF_8)), true);
        SourceFile reparsed = null;
        String parseFailure = null;
        try {
            reparsed = parse(original, input, new InMemoryExecutionContext());
        } catch (Throwable t) {
            parseFailure = ExceptionUtils.sanitizeStackTrace(t, FormatSourceVisitor.class);
        }
        if (reparsed instanceof ParseError) {
            parseFailure = reparsed.getMarkers().findFirst(ParseExceptionResult.class)
                    .map(e -> "The formatter's output did not parse: " + e.getMessage())
                    .orElse(null);
        }
        if (reparsed != null && !(reparsed instanceof ParseError) &&
            formatted.equals(reparsed.printAll(new PrintOutputCapture<>(0, PrintOutputCapture.MarkerPrinter.SANITIZED)))) {
            if (original instanceof ParseError) {
                table.insertRow(ctx, new SourceFilesFormatted.Row(original.getSourcePath().toString(), formatterName,
                        "Formatted, without types", "The file had not parsed to an LST, and parsed once formatted."));
                return carryOver(original, reparsed);
            }
            SourceFile reconciled;
            try {
                reconciled = WhitespaceReconciler.reconcile(original, reparsed, formatted, this::formattingVisitor);
            } catch (RuntimeException e) {
                // Then the formatter's output takes the file's place, which prints the same
                reconciled = null;
            }
            if (reconciled != null) {
                table.insertRow(ctx, new SourceFilesFormatted.Row(original.getSourcePath().toString(), formatterName,
                        "Formatted", null));
                return reconciled;
            }
            table.insertRow(ctx, new SourceFilesFormatted.Row(original.getSourcePath().toString(), formatterName,
                    "Formatted, without types",
                    "The formatter's output parses to a tree of another shape than the file's, so it took the file's place."));
            return carryOver(original, reparsed);
        }

        if (original instanceof ParseError) {
            table.insertRow(ctx, new SourceFilesFormatted.Row(original.getSourcePath().toString(), formatterName,
                    "Formatted, as plain text", "The file did not parse to an LST, so its text was formatted."));
            return ((ParseError) original).withText(formatted).withErroneous(null);
        }
        Optional<SourceFile> text = PlainTextParser.builder().build()
                .parseInputs(Collections.singletonList(input), null, new InMemoryExecutionContext())
                .findFirst();
        if (!text.isPresent()) {
            table.insertRow(ctx, new SourceFilesFormatted.Row(original.getSourcePath().toString(), formatterName,
                    "Not formatted", "The formatter's output could not be stored."));
            return original;
        }
        table.insertRow(ctx, new SourceFilesFormatted.Row(original.getSourcePath().toString(), formatterName,
                "Formatted, as plain text",
                parseFailure != null ? parseFailure : "The formatter's output did not parse back to the same text."));
        return carryOver(original, text.get());
    }

    private static SourceFile carryOver(SourceFile original, SourceFile replacement) {
        Markers markers = original.getMarkers();
        return replacement
                .<SourceFile>withId(original.getId())
                .<SourceFile>withMarkers(markers)
                .<SourceFile>withSourcePath(original.getSourcePath())
                .<SourceFile>withCharsetBomMarked(original.isCharsetBomMarked())
                .<SourceFile>withCharset(original.getCharset() == null ? StandardCharsets.UTF_8 : original.getCharset())
                .<SourceFile>withFileAttributes(original.getFileAttributes())
                .withChecksum(original.getChecksum());
    }

}
