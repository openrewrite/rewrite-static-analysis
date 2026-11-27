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

import lombok.EqualsAndHashCode;
import lombok.Value;
import org.jspecify.annotations.Nullable;
import org.openrewrite.ExecutionContext;
import org.openrewrite.Option;
import org.openrewrite.Recipe;
import org.openrewrite.TreeVisitor;
import org.openrewrite.staticanalysis.table.SourceFilesFormatted;

@Value
@EqualsAndHashCode(callSuper = false)
public class GoogleJavaFormat extends Recipe {

    @Option(displayName = "Style",
            description = "The style to format in: `GOOGLE`, the default, or `AOSP`. " +
                          "This is the `style` of Spotless' `googleJavaFormat` step.",
            valid = {"GOOGLE", "AOSP"},
            required = false,
            example = "GOOGLE")
    @Nullable
    String style;

    @Option(displayName = "Reflow long strings",
            description = "Whether to break string literals that run past the line length. Defaults to `false`.",
            required = false)
    @Nullable
    Boolean reflowLongStrings;

    @Option(displayName = "Reorder imports",
            description = "Whether to order imports as the style orders them, rather than as the `GOOGLE` style does. " +
                          "Imports are ordered either way, as Spotless orders them. Defaults to `false`.",
            required = false)
    @Nullable
    Boolean reorderImports;

    @Option(displayName = "Format Javadoc",
            description = "Whether to format Javadoc as well. Defaults to `true`.",
            required = false)
    @Nullable
    Boolean formatJavadoc;

    @Option(displayName = "Clean up",
            description = "Whether to apply google-java-format's cleanups beyond whitespace, such as ordering " +
                          "modifiers. Defaults to `true`.",
            required = false)
    @Nullable
    Boolean cleanup;

    @Option(displayName = "Maximum line length",
            description = "The column limit, when it is to differ from the style's 100.",
            required = false,
            example = "120")
    @Nullable
    Integer maxLineLength;

    transient SourceFilesFormatted sourceFilesFormatted = new SourceFilesFormatted(this);

    @Override
    public String getDisplayName() {
        return "Format Java code with google-java-format";
    }

    @Override
    public String getInstanceNameSuffix() {
        return style == null ? "" : "in the " + style + " style";
    }

    @Override
    public String getDescription() {
        return "Formats Java source files with [google-java-format](https://github.com/google/google-java-format) " +
               "2.0.0, as Spotless' `googleJavaFormat` step does: the file is formatted, unused imports removed and " +
               "imports ordered, then long strings optionally reflowed. A build that runs `spotless:check` or " +
               "`spotlessCheck` with `googleJavaFormat` at the same version and options accepts the result. The " +
               "formatter needs Java 21 or later to run. Files the formatter cannot parse are left as they are and " +
               "listed in the `SourceFilesFormatted` data table.";
    }

    @Override
    public TreeVisitor<?, ExecutionContext> getVisitor() {
        return new JavaFormatSourceVisitor(
                SourceFormatter.googleJavaFormat(
                        style == null ? "GOOGLE" : style,
                        Boolean.TRUE.equals(reflowLongStrings),
                        Boolean.TRUE.equals(reorderImports),
                        !Boolean.FALSE.equals(formatJavadoc),
                        !Boolean.FALSE.equals(cleanup),
                        maxLineLength == null ? 0 : maxLineLength),
                sourceFilesFormatted);
    }
}
