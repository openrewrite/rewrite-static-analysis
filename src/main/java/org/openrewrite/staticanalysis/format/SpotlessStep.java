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

import com.fasterxml.jackson.databind.JsonNode;
import lombok.Value;
import org.jspecify.annotations.Nullable;
import org.openrewrite.Cursor;
import org.openrewrite.SourceFile;
import org.openrewrite.Tree;
import org.openrewrite.java.JavaIsoVisitor;
import org.openrewrite.java.tree.*;
import org.openrewrite.maven.tree.MavenResolutionResult;
import org.openrewrite.maven.tree.Plugin;
import org.openrewrite.maven.tree.ResolvedPom;

import java.nio.file.Path;
import java.util.*;

/**
 * A formatter step of a Spotless configuration, such as {@code palantirJavaFormat} in a {@code java} block, as a
 * Maven POM or a Gradle build script declares it.
 */
@Value
class SpotlessStep {

    /**
     * The steps that reformat a whole file, which are the ones to report. Spotless has many others, such as
     * {@code licenseHeader} or {@code importOrder}, which these recipes neither report nor run.
     */
    static final Set<String> FORMATTERS = new HashSet<>(Arrays.asList(
            "palantirJavaFormat", "googleJavaFormat", "eclipse", "prettier", "cleanthat",
            "ktfmt", "ktlint", "diktat"));

    private static final Set<String> LANGUAGES = new HashSet<>(Arrays.asList("java", "kotlin", "kotlinGradle"));

    Path buildFile;
    String buildTool;
    String language;
    String formatter;

    /**
     * As configured, or {@code null} when the configuration leaves it to the Spotless plugin's default.
     */
    @Nullable
    String version;

    /**
     * The step's options under the names the recipes' options have, such as {@code style}.
     */
    Map<String, String> options;

    /**
     * @return the version of the formatter that the recipes bundle, or {@code null} when they don't run this one
     */
    @Nullable
    String bundledVersion() {
        BundledFormatter bundled = bundled();
        return bundled == null ? null : bundled.getVersion();
    }

    @Nullable
    BundledFormatter bundled() {
        switch (formatter) {
            case "palantirJavaFormat":
                return BundledFormatter.PALANTIR_JAVA_FORMAT;
            case "googleJavaFormat":
                return BundledFormatter.GOOGLE_JAVA_FORMAT;
            case "ktfmt":
                return BundledFormatter.KTFMT;
            default:
                return null;
        }
    }

    /**
     * The formatter, configured as this step configures it, or {@code null} when the recipes do not run it or do
     * not support one of its options.
     */
    @Nullable
    SourceFormatter toSourceFormatter() {
        try {
            switch (formatter) {
                case "palantirJavaFormat":
                    return SourceFormatter.palantirJavaFormat(
                            option("style", "PALANTIR", "PALANTIR", "AOSP", "GOOGLE"),
                            Boolean.parseBoolean(option("formatJavadoc", "false")));
                case "googleJavaFormat":
                    return SourceFormatter.googleJavaFormat(
                            option("style", "GOOGLE", "GOOGLE", "AOSP"),
                            Boolean.parseBoolean(option("reflowLongStrings", "false")),
                            Boolean.parseBoolean(option("reorderImports", "false")),
                            Boolean.parseBoolean(option("formatJavadoc", "true")),
                            Boolean.parseBoolean(option("cleanup", "true")),
                            Integer.parseInt(option("maxLineLength", "0")));
                case "ktfmt":
                    String trailingCommas = options.get("trailingCommaManagementStrategy");
                    return SourceFormatter.ktfmt(
                            option("style", "META", "META", "GOOGLE", "KOTLINLANG"),
                            integer("maxWidth"), integer("blockIndent"), integer("continuationIndent"),
                            options.containsKey("removeUnusedImports") ? Boolean.parseBoolean(options.get("removeUnusedImports")) : null,
                            trailingCommas == null ? null : option("trailingCommaManagementStrategy", null, "NONE", "ONLY_ADD", "COMPLETE"));
                default:
                    return null;
            }
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    /**
     * Why {@link #toSourceFormatter()} returns {@code null}, for the data table.
     */
    @Nullable
    String unsupportedReason() {
        if (bundled() == null) {
            return "The recipes do not run " + formatter + ".";
        }
        return toSourceFormatter() == null ? "An option has a value the recipes do not support: " + options + "." : null;
    }

    private String option(String name, @Nullable String defaultValue, String... allowed) {
        String value = options.get(name);
        if (value == null) {
            if (defaultValue == null) {
                throw new IllegalArgumentException(name);
            }
            return defaultValue;
        }
        if (allowed.length == 0) {
            return value;
        }
        String upper = value.toUpperCase(Locale.ROOT);
        for (String a : allowed) {
            if (a.equals(upper)) {
                return a;
            }
        }
        throw new IllegalArgumentException(name + "=" + value);
    }

    private @Nullable Integer integer(String name) {
        String value = options.get(name);
        return value == null ? null : Integer.valueOf(value);
    }

    /**
     * The directory whose source files this step formats. A step in a script under {@code buildSrc}, {@code build-logic}
     * or {@code gradle}, which is a convention plugin or a script that projects apply, is taken to cover the whole
     * repository.
     */
    Path scope() {
        Path dir = buildFile.getParent();
        if (dir == null) {
            return buildFile.getFileSystem().getPath("");
        }
        String first = buildFile.getName(0).toString();
        if ("buildSrc".equals(first) || "build-logic".equals(first) || "gradle".equals(first)) {
            return buildFile.getFileSystem().getPath("");
        }
        return dir;
    }

    /**
     * The steps of the Spotless Maven plugin in a POM's effective build, which includes what it inherits from its
     * parents, as {@link MavenResolutionResult} resolved them.
     */
    static List<SpotlessStep> fromMaven(SourceFile pom, MavenResolutionResult resolution) {
        ResolvedPom resolved = resolution.getPom();
        JsonNode configuration = null;
        for (Plugin plugin : resolved.getPlugins()) {
            if (isSpotless(plugin)) {
                configuration = plugin.getConfiguration();
                break;
            }
        }
        if (configuration == null || configuration.isEmpty()) {
            for (Plugin plugin : resolved.getPluginManagement()) {
                if (isSpotless(plugin)) {
                    configuration = plugin.getConfiguration();
                    break;
                }
            }
        }
        List<SpotlessStep> steps = new ArrayList<>();
        if (configuration == null) {
            return steps;
        }
        for (Iterator<Map.Entry<String, JsonNode>> languages = configuration.fields(); languages.hasNext(); ) {
            Map.Entry<String, JsonNode> language = languages.next();
            if (!LANGUAGES.contains(language.getKey()) || !language.getValue().isObject()) {
                continue;
            }
            for (Iterator<Map.Entry<String, JsonNode>> it = language.getValue().fields(); it.hasNext(); ) {
                Map.Entry<String, JsonNode> step = it.next();
                if (!FORMATTERS.contains(step.getKey())) {
                    continue;
                }
                String version = null;
                Map<String, String> options = new TreeMap<>();
                if (step.getValue().isObject()) {
                    for (Iterator<Map.Entry<String, JsonNode>> o = step.getValue().fields(); o.hasNext(); ) {
                        Map.Entry<String, JsonNode> option = o.next();
                        if (!option.getValue().isValueNode()) {
                            continue;
                        }
                        String value = resolved.getValue(option.getValue().asText());
                        if (value == null) {
                            continue;
                        }
                        if ("version".equals(option.getKey())) {
                            version = value;
                        } else if (!"groupArtifact".equals(option.getKey())) {
                            options.put(option.getKey(), value);
                        }
                    }
                }
                steps.add(new SpotlessStep(pom.getSourcePath(), "Maven", language.getKey(), step.getKey(), version, options));
            }
        }
        return steps;
    }

    private static boolean isSpotless(Plugin plugin) {
        return "spotless-maven-plugin".equals(plugin.getArtifactId()) &&
               (plugin.getGroupId() == null || "com.diffplug.spotless".equals(plugin.getGroupId()));
    }

    /**
     * The steps a Gradle build script, Groovy or Kotlin, declares in the {@code java}, {@code kotlin} or
     * {@code kotlinGradle} block of a Spotless configuration, such as
     * {@code spotless { java { palantirJavaFormat('2.50.0').style('AOSP') } }}.
     */
    static List<SpotlessStep> fromGradle(JavaSourceFile script) {
        List<SpotlessStep> steps = new ArrayList<>();
        new JavaIsoVisitor<List<SpotlessStep>>() {
            @Override
            public J.MethodInvocation visitMethodInvocation(J.MethodInvocation method, List<SpotlessStep> found) {
                J.MethodInvocation m = super.visitMethodInvocation(method, found);
                if (!FORMATTERS.contains(m.getSimpleName()) || m.getSelect() != null) {
                    return m;
                }
                String language = enclosingLanguage(getCursor());
                if (language == null) {
                    return m;
                }
                String version = null;
                List<Expression> args = m.getArguments();
                if (!args.isEmpty() && !(args.get(0) instanceof J.Empty)) {
                    version = literal(args.get(0));
                    if (version == null) {
                        version = args.get(0).printTrimmed(getCursor());
                    }
                }
                Map<String, String> options = new TreeMap<>();
                // The chained calls that configure the step, such as .style("AOSP") or .googleStyle()
                Cursor c = getCursor().getParentTreeCursor();
                Tree inner = m;
                while (c.getValue() instanceof J.MethodInvocation && ((J.MethodInvocation) c.getValue()).getSelect() == inner) {
                    J.MethodInvocation chained = c.getValue();
                    option(m.getSimpleName(), chained, options);
                    inner = chained;
                    c = c.getParentTreeCursor();
                }
                found.add(new SpotlessStep(script.getSourcePath(), "Gradle", language, m.getSimpleName(), version, options));
                return m;
            }
        }.visit(script, steps);
        return steps;
    }

    private static @Nullable String enclosingLanguage(Cursor cursor) {
        for (Cursor c = cursor.getParent(); c != null; c = c.getParent()) {
            Object value = c.getValue();
            if (value instanceof J.MethodInvocation) {
                J.MethodInvocation m = (J.MethodInvocation) value;
                if (LANGUAGES.contains(m.getSimpleName()) && m.getSelect() == null &&
                    !m.getArguments().isEmpty() && m.getArguments().get(m.getArguments().size() - 1) instanceof J.Lambda) {
                    return m.getSimpleName();
                }
            }
        }
        return null;
    }

    private static void option(String formatter, J.MethodInvocation chained, Map<String, String> options) {
        String name = chained.getSimpleName();
        List<Expression> args = chained.getArguments();
        String arg = args.isEmpty() || args.get(0) instanceof J.Empty ? null : literal(args.get(0));
        switch (name) {
            case "aosp":
                options.put("style", "AOSP");
                return;
            case "googleStyle":
                options.put("style", "GOOGLE");
                return;
            case "kotlinlangStyle":
                options.put("style", "KOTLINLANG");
                return;
            case "metaStyle":
                options.put("style", "META");
                return;
            case "dropboxStyle":
                options.put("style", "DROPBOX");
                return;
            case "skipJavadocFormatting":
                options.put("formatJavadoc", "false");
                return;
            case "reflowLongStrings":
            case "reorderImports":
            case "formatJavadoc":
            case "cleanup":
                options.put(name, arg == null ? "true" : arg);
                return;
            case "style":
            case "maxLineLength":
                if (arg != null) {
                    options.put(name, arg);
                }
                return;
            case "configure":
                // ktfmt('0.51').configure { it.setMaxWidth(120) }, or it.maxWidth = 120 in Groovy
                if (!args.isEmpty() && args.get(args.size() - 1) instanceof J.Lambda) {
                    new JavaIsoVisitor<Map<String, String>>() {
                        @Override
                        public J.MethodInvocation visitMethodInvocation(J.MethodInvocation setter, Map<String, String> o) {
                            String n = setter.getSimpleName();
                            if (n.startsWith("set") && n.length() > 3 && setter.getArguments().size() == 1) {
                                String v = literal(setter.getArguments().get(0));
                                if (v != null) {
                                    o.put(Character.toLowerCase(n.charAt(3)) + n.substring(4), v);
                                }
                            }
                            return super.visitMethodInvocation(setter, o);
                        }

                        @Override
                        public J.Assignment visitAssignment(J.Assignment assignment, Map<String, String> o) {
                            if (assignment.getVariable() instanceof J.FieldAccess) {
                                String v = literal(assignment.getAssignment());
                                if (v != null) {
                                    o.put(((J.FieldAccess) assignment.getVariable()).getSimpleName(), v);
                                }
                            }
                            return super.visitAssignment(assignment, o);
                        }
                    }.visit(args.get(args.size() - 1), options);
                }
                return;
            default:
                options.put(name, arg == null ? "" : arg);
        }
    }

    /**
     * The value of a literal, or of the last part of a name such as {@code TrailingCommaManagementStrategy.ONLY_ADD}.
     */
    private static @Nullable String literal(Expression e) {
        if (e instanceof J.Literal) {
            Object value = ((J.Literal) e).getValue();
            return value == null ? null : value.toString();
        }
        if (e instanceof J.FieldAccess) {
            return ((J.FieldAccess) e).getSimpleName();
        }
        if (e instanceof J.Identifier) {
            String name = ((J.Identifier) e).getSimpleName();
            return "true".equals(name) || "false".equals(name) ? name : null;
        }
        return null;
    }
}
