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
public class PalantirJavaFormat extends Recipe {

    @Option(displayName = "Style",
            description = "The style to format in: `PALANTIR`, the default, `AOSP` or `GOOGLE`. " +
                          "This is the `style` of Spotless' `palantirJavaFormat` step.",
            valid = {"PALANTIR", "AOSP", "GOOGLE"},
            required = false,
            example = "PALANTIR")
    @Nullable
    String style;

    @Option(displayName = "Format Javadoc",
            description = "Whether to format Javadoc as well. Defaults to `false`. " +
                          "This is the `formatJavadoc` of Spotless' `palantirJavaFormat` step.",
            required = false)
    @Nullable
    Boolean formatJavadoc;

    transient SourceFilesFormatted sourceFilesFormatted = new SourceFilesFormatted(this);

    @Override
    public String getDisplayName() {
        return "Format Java code with palantir-java-format";
    }

    @Override
    public String getInstanceNameSuffix() {
        return style == null ? "" : "in the " + style + " style";
    }

    @Override
    public String getDescription() {
        return "Formats Java source files with [palantir-java-format](https://github.com/palantir/palantir-java-format) " +
               "2.103.0, as Spotless' `palantirJavaFormat` step does: imports are ordered, unused imports removed, " +
               "then the file is formatted. A build that runs `spotless:check` or `spotlessCheck` with " +
               "`palantirJavaFormat` at the same version and options accepts the result. The formatter needs Java 11 " +
               "or later to run. Files the formatter cannot parse are left as they are and listed in the " +
               "`SourceFilesFormatted` data table.";
    }

    @Override
    public TreeVisitor<?, ExecutionContext> getVisitor() {
        return new JavaFormatSourceVisitor(
                SourceFormatter.palantirJavaFormat(style == null ? "PALANTIR" : style, Boolean.TRUE.equals(formatJavadoc)),
                sourceFilesFormatted);
    }
}
