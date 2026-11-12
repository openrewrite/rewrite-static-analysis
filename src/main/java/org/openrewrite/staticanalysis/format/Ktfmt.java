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
public class Ktfmt extends Recipe {

    @Option(displayName = "Style",
            description = "The style to format in: `META`, the default, `GOOGLE` or `KOTLINLANG`. " +
                          "This is the `style` of Spotless' `ktfmt` step.",
            valid = {"META", "GOOGLE", "KOTLINLANG"},
            required = false,
            example = "GOOGLE")
    @Nullable
    String style;

    @Option(displayName = "Maximum width",
            description = "The column limit, when it is to differ from the style's.",
            required = false,
            example = "120")
    @Nullable
    Integer maxWidth;

    @Option(displayName = "Block indent",
            description = "The indent of a block, when it is to differ from the style's.",
            required = false,
            example = "4")
    @Nullable
    Integer blockIndent;

    @Option(displayName = "Continuation indent",
            description = "The indent of a continued line, when it is to differ from the style's.",
            required = false,
            example = "4")
    @Nullable
    Integer continuationIndent;

    @Option(displayName = "Remove unused imports",
            description = "Whether to remove unused imports, when it is to differ from the style's, which does.",
            required = false)
    @Nullable
    Boolean removeUnusedImports;

    @Option(displayName = "Trailing comma management strategy",
            description = "How to manage trailing commas, when it is to differ from the style's: `NONE`, " +
                          "`ONLY_ADD` or `COMPLETE`.",
            valid = {"NONE", "ONLY_ADD", "COMPLETE"},
            required = false,
            example = "ONLY_ADD")
    @Nullable
    String trailingCommaManagementStrategy;

    @Option(displayName = "Format scripts",
            description = "Whether to format Kotlin scripts, such as `build.gradle.kts`, as well. Spotless formats " +
                          "those in a `kotlinGradle` block rather than its `kotlin` block. Defaults to `false`.",
            required = false)
    @Nullable
    Boolean formatScripts;

    transient SourceFilesFormatted sourceFilesFormatted = new SourceFilesFormatted(this);

    @Override
    public String getDisplayName() {
        return "Format Kotlin code with ktfmt";
    }

    @Override
    public String getInstanceNameSuffix() {
        return style == null ? "" : "in the " + style + " style";
    }

    @Override
    public String getDescription() {
        return "Formats Kotlin source files with [ktfmt](https://github.com/facebook/ktfmt) 0.65, as Spotless' " +
               "`ktfmt` step does. A build that runs `spotless:check` or `spotlessCheck` with `ktfmt` at the same " +
               "version and options accepts the result. The formatter needs Java 17 or later to run. Files the " +
               "formatter cannot parse are left as they are and listed in the `SourceFilesFormatted` data table.";
    }

    @Override
    public TreeVisitor<?, ExecutionContext> getVisitor() {
        return new KotlinFormatSourceVisitor(
                SourceFormatter.ktfmt(style == null ? "META" : style, maxWidth, blockIndent, continuationIndent,
                        removeUnusedImports, trailingCommaManagementStrategy),
                sourceFilesFormatted,
                Boolean.TRUE.equals(formatScripts));
    }
}
