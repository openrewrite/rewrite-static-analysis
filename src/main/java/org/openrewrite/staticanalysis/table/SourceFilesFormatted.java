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
package org.openrewrite.staticanalysis.table;

import com.fasterxml.jackson.annotation.JsonIgnoreType;
import lombok.Value;
import org.jspecify.annotations.Nullable;
import org.openrewrite.Column;
import org.openrewrite.DataTable;
import org.openrewrite.Recipe;

@JsonIgnoreType
public class SourceFilesFormatted extends DataTable<SourceFilesFormatted.Row> {

    public SourceFilesFormatted(Recipe recipe) {
        super(recipe,
                "Source files formatted",
                "Each source file a formatter changed or could not format, with how the change was made. " +
                "Every file it changed prints as the formatter wrote it, whichever the result.");
    }

    @Value
    public static class Row {
        @Column(displayName = "Source path",
                description = "The path to the source file.")
        String sourcePath;

        @Column(displayName = "Formatter",
                description = "The formatter, such as `palantir-java-format`, and the version of it that ran.")
        String formatter;

        @Column(displayName = "Result",
                description = "`Formatted` when the formatter's formatting was put onto the file's LST, which keeps its types. " +
                              "`Formatted, without types` when the formatter's output was parsed again in its place, without " +
                              "the types of the project's dependencies: this is when the two parse to trees of different " +
                              "shapes, as Kotlin's parser builds a call of a constructor it cannot resolve as a method call, " +
                              "or when the file had not parsed before. " +
                              "`Formatted, as plain text` when the formatter's output does not parse. " +
                              "`Not formatted` when the formatter rejected the file, which is then left as it was.")
        String result;

        @Column(displayName = "Message",
                description = "Why the file was not formatted, or why its types were dropped.")
        @Nullable
        String message;
    }
}
