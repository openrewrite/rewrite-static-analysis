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
import org.openrewrite.java.tree.JavaSourceFile;
import org.openrewrite.marker.SearchResult;
import org.openrewrite.maven.tree.MavenResolutionResult;
import org.openrewrite.staticanalysis.table.SpotlessFormatters;

import java.util.List;
import java.util.Map;
import java.util.Optional;

public class FindSpotlessFormatters extends Recipe {

    transient SpotlessFormatters spotlessFormatters = new SpotlessFormatters(this);

    @Override
    public String getDisplayName() {
        return "Find Spotless formatter configuration";
    }

    @Override
    public String getDescription() {
        return "Lists each formatter step that a Spotless configuration declares, in a Maven POM, including what it " +
               "inherits from parent POMs, or in a Gradle build script, with the formatter's version and options. " +
               "The `SpotlessFormatters` data table says whether the formatter recipes run the same version, which " +
               "is what makes their output the output the build's check accepts.";
    }

    @Override
    public TreeVisitor<?, ExecutionContext> getVisitor() {
        return new TreeVisitor<Tree, ExecutionContext>() {
            @Override
            public @Nullable Tree visit(@Nullable Tree tree, ExecutionContext ctx) {
                if (!(tree instanceof SourceFile)) {
                    return tree;
                }
                List<SpotlessStep> steps = steps((SourceFile) tree);
                for (SpotlessStep step : steps) {
                    String bundled = step.bundledVersion();
                    spotlessFormatters.insertRow(ctx, new SpotlessFormatters.Row(
                            step.getBuildFile().toString(),
                            step.getBuildTool(),
                            step.getLanguage(),
                            step.getFormatter(),
                            step.getVersion(),
                            options(step),
                            bundled,
                            bundled != null && bundled.equals(step.getVersion()),
                            step.unsupportedReason()));
                }
                return steps.isEmpty() ? tree : SearchResult.found(tree, describe(steps));
            }
        };
    }

    /**
     * The options as {@code name=value} pairs separated by semicolons, which, unlike commas, leave the data
     * table's CSV without quoted fields.
     */
    private static String options(SpotlessStep step) {
        StringBuilder s = new StringBuilder();
        for (Map.Entry<String, String> option : step.getOptions().entrySet()) {
            if (s.length() > 0) {
                s.append("; ");
            }
            s.append(option.getKey()).append('=').append(option.getValue());
        }
        return s.toString();
    }

    private static String describe(List<SpotlessStep> steps) {
        StringBuilder s = new StringBuilder();
        for (SpotlessStep step : steps) {
            if (s.length() > 0) {
                s.append(", ");
            }
            s.append(step.getLanguage()).append(": ").append(step.getFormatter());
            if (step.getVersion() != null) {
                s.append(' ').append(step.getVersion());
            }
        }
        return s.toString();
    }

    /**
     * The Spotless formatter steps a build file declares.
     */
    static List<SpotlessStep> steps(SourceFile sourceFile) {
        String path = sourceFile.getSourcePath().toString();
        if (path.endsWith(".gradle") || path.endsWith(".gradle.kts")) {
            if (sourceFile instanceof JavaSourceFile) {
                return SpotlessStep.fromGradle((JavaSourceFile) sourceFile);
            }
        } else if (path.endsWith("pom.xml") || path.endsWith(".pom")) {
            Optional<MavenResolutionResult> resolution = sourceFile.getMarkers().findFirst(MavenResolutionResult.class);
            if (resolution.isPresent()) {
                return SpotlessStep.fromMaven(sourceFile, resolution.get());
            }
        }
        return java.util.Collections.emptyList();
    }
}
