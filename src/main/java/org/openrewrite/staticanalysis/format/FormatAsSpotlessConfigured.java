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
import org.openrewrite.staticanalysis.table.SourceFilesFormatted;

import java.nio.file.Path;
import java.util.*;

public class FormatAsSpotlessConfigured extends ScanningRecipe<FormatAsSpotlessConfigured.Accumulator> {

    transient SourceFilesFormatted sourceFilesFormatted = new SourceFilesFormatted(this);

    @Override
    public String getDisplayName() {
        return "Format as the build's Spotless configuration does";
    }

    @Override
    public String getDescription() {
        return "Formats each Java and Kotlin source file with the formatter, and the options, that the Spotless " +
               "configuration of its project declares: `palantirJavaFormat` or `googleJavaFormat` in a `java` block, " +
               "`ktfmt` in a `kotlin` block. A Maven project's configuration includes what it inherits from parent " +
               "POMs. A Gradle project's is the one in its own build script, or the nearest one above it, and a script " +
               "under `buildSrc`, `build-logic` or `gradle` is taken to configure the whole repository. Files that no " +
               "such configuration covers are left as they are. The formatters run at the versions these recipes " +
               "bundle; `FindSpotlessFormatters` lists the builds that pin another.";
    }

    public static class Accumulator {
        /**
         * The steps of each directory a configuration covers, by language.
         */
        final Map<Path, Map<String, SpotlessStep>> steps = new HashMap<>();
    }

    @Override
    public Accumulator getInitialValue(ExecutionContext ctx) {
        return new Accumulator();
    }

    @Override
    public TreeVisitor<?, ExecutionContext> getScanner(Accumulator acc) {
        return new TreeVisitor<Tree, ExecutionContext>() {
            @Override
            public @Nullable Tree visit(@Nullable Tree tree, ExecutionContext ctx) {
                if (tree instanceof SourceFile) {
                    for (SpotlessStep step : FindSpotlessFormatters.steps((SourceFile) tree)) {
                        if (step.toSourceFormatter() != null) {
                            synchronized (acc) {
                                acc.steps.computeIfAbsent(step.scope(), k -> new HashMap<>())
                                        .putIfAbsent(step.getLanguage(), step);
                            }
                        }
                    }
                }
                return tree;
            }
        };
    }

    @Override
    public TreeVisitor<?, ExecutionContext> getVisitor(Accumulator acc) {
        return new TreeVisitor<Tree, ExecutionContext>() {
            private final Map<SourceFormatter, FormatSourceVisitor> visitors = new HashMap<>();

            @Override
            public @Nullable Tree visit(@Nullable Tree tree, ExecutionContext ctx) {
                if (!(tree instanceof SourceFile) || acc.steps.isEmpty()) {
                    return tree;
                }
                SourceFile sourceFile = (SourceFile) tree;
                String path = sourceFile.getSourcePath().toString();
                String language = path.endsWith(".java") ? "java" :
                        path.endsWith(".kt") ? "kotlin" :
                                path.endsWith(".kts") ? "kotlinGradle" : null;
                if (language == null) {
                    return tree;
                }
                SpotlessStep step = nearest(acc, sourceFile.getSourcePath(), language);
                if (step == null) {
                    return tree;
                }
                SourceFormatter formatter = step.toSourceFormatter();
                if (formatter == null) {
                    return tree;
                }
                FormatSourceVisitor visitor = visitors.computeIfAbsent(formatter, f -> "java".equals(language) ?
                        new JavaFormatSourceVisitor(f, sourceFilesFormatted) :
                        new KotlinFormatSourceVisitor(f, sourceFilesFormatted, true));
                return visitor.isAcceptable(sourceFile, ctx) ? visitor.visit(tree, ctx) : tree;
            }
        };
    }

    private static @Nullable SpotlessStep nearest(Accumulator acc, Path sourcePath, String language) {
        for (Path dir = sourcePath.getParent(); ; dir = dir.getParent()) {
            Path key = dir == null ? sourcePath.getFileSystem().getPath("") : dir;
            Map<String, SpotlessStep> steps = acc.steps.get(key);
            if (steps != null && steps.containsKey(language)) {
                return steps.get(language);
            }
            if (dir == null) {
                return null;
            }
        }
    }
}
