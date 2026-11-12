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
public class SpotlessFormatters extends DataTable<SpotlessFormatters.Row> {

    public SpotlessFormatters(Recipe recipe) {
        super(recipe,
                "Spotless formatters",
                "Each formatter step of a Spotless configuration, with its version and options, and whether the " +
                "formatter recipes run the same version.");
    }

    @Value
    public static class Row {
        @Column(displayName = "Build file",
                description = "The POM or Gradle build script that configures the step.")
        String buildFile;

        @Column(displayName = "Build tool",
                description = "`Maven` or `Gradle`.")
        String buildTool;

        @Column(displayName = "Language",
                description = "The Spotless block the step is in: `java`, `kotlin` or `kotlinGradle`.")
        String language;

        @Column(displayName = "Formatter",
                description = "The step, such as `palantirJavaFormat`, `googleJavaFormat` or `ktfmt`.")
        String formatter;

        @Column(displayName = "Version",
                description = "The formatter version the build pins, or empty when it takes the Spotless plugin's default.")
        @Nullable
        String version;

        @Column(displayName = "Options",
                description = "The step's options, such as `style=AOSP; reflowLongStrings=true`.")
        String options;

        @Column(displayName = "Bundled version",
                description = "The version of the formatter that the formatter recipes run, or empty when they do not run it.")
        @Nullable
        String bundledVersion;

        @Column(displayName = "Same version",
                description = "Whether the build pins the version the recipes run, so that what they write is what the " +
                              "build's check accepts.")
        boolean sameVersion;

        @Column(displayName = "Unsupported",
                description = "Why the recipes cannot format as this step does, or empty when they can.")
        @Nullable
        String unsupported;
    }
}
