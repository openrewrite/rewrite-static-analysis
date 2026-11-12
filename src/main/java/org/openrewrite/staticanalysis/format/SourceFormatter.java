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

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.*;
import java.util.function.Function;
import java.util.concurrent.ConcurrentHashMap;

/**
 * One configuration of a {@link BundledFormatter}, called through reflection since the formatter's classes are
 * only visible to its own class loader. Each method runs the same steps, in the same order, as the Spotless step
 * for that formatter, so that a file this formats is one that {@code spotless:check} or {@code spotlessCheck}
 * accepts when the build pins the same formatter version and options.
 */
abstract class SourceFormatter {

    private static final Map<String, SourceFormatter> CACHE = new ConcurrentHashMap<>();

    final BundledFormatter formatter;

    private SourceFormatter(BundledFormatter formatter) {
        this.formatter = formatter;
    }

    /**
     * @return the source as the formatter writes it
     * @throws FormatterFailure when the formatter rejects the source, typically because it does not parse it
     */
    final String format(String source, boolean script) {
        Thread thread = Thread.currentThread();
        ClassLoader contextClassLoader = thread.getContextClassLoader();
        thread.setContextClassLoader(formatter.getClassLoader());
        try {
            boolean crlf = source.contains("\r\n");
            String unix = crlf ? source.replace("\r\n", "\n") : source;
            String canonical = canonical(unix, script);
            return crlf ? canonical.replace("\n", "\r\n") : canonical;
        } catch (InvocationTargetException e) {
            Throwable cause = e.getCause() == null ? e : e.getCause();
            throw new FormatterFailure(formatter.getName() + " " + formatter.getVersion() + " did not format the file: " +
                                       (cause.getMessage() == null ? cause.toString() : cause.getMessage()), cause);
        } catch (ReflectiveOperationException | LinkageError e) {
            throw new FormatterFailure("Unable to run " + formatter.getName() + ": " + e, e);
        } finally {
            thread.setContextClassLoader(contextClassLoader);
        }
    }

    abstract String doFormat(String source, boolean script) throws ReflectiveOperationException;

    private static final int MAX_CYCLE = 10;

    /**
     * What Spotless writes, and so what its check accepts. Formatters are not always idempotent: google-java-format
     * can move a trailing comment on one pass and again on the next. Spotless' {@code PaddedCell} formats again until
     * the result stops changing, and takes the last result, or, when the results repeat in a cycle, the shortest of
     * them. Like Spotless, this gives up after ten passes.
     */
    private String canonical(String source, boolean script) throws ReflectiveOperationException {
        String once = doFormat(source, script);
        if (once.equals(source)) {
            return source;
        }
        List<String> applied = new ArrayList<>();
        applied.add(once);
        String input = once;
        while (applied.size() < MAX_CYCLE) {
            String output = doFormat(input, script);
            if (output.equals(input)) {
                return output;
            }
            int cycleStart = applied.indexOf(output);
            if (cycleStart >= 0) {
                List<String> cycle = applied.subList(cycleStart, applied.size());
                return Collections.min(cycle, Comparator.comparingInt(String::length).thenComparing(Function.identity()));
            }
            applied.add(output);
            input = output;
        }
        throw new FormatterFailure(formatter.getName() + " " + formatter.getVersion() +
                                   " did not settle on a format for the file after " + MAX_CYCLE + " passes.", null);
    }

    static final class FormatterFailure extends RuntimeException {
        FormatterFailure(String message, @Nullable Throwable cause) {
            super(message, cause);
        }
    }

    /**
     * The steps of Spotless' {@code GoogleJavaFormatFormatterFunc}: format, remove unused imports, order the imports
     * (in the style's order when {@code reorderImports} is set, and Google's otherwise), then optionally reflow
     * long strings.
     */
    static SourceFormatter googleJavaFormat(String style, boolean reflowLongStrings, boolean reorderImports,
                                            boolean formatJavadoc, boolean cleanup, int maxLineLength) {
        String key = "gjf:" + style + ':' + reflowLongStrings + ':' + reorderImports + ':' + formatJavadoc + ':' + cleanup + ':' + maxLineLength;
        return CACHE.computeIfAbsent(key, k -> new SourceFormatter(BundledFormatter.GOOGLE_JAVA_FORMAT) {
            private volatile Object formatterInstance;
            private Method formatSource;
            private Method removeUnusedImports;
            private Method reorderImportsMethod;
            private Method wrap;
            private Object importStyle;

            private synchronized void init() throws ReflectiveOperationException {
                if (formatterInstance != null) {
                    return;
                }
                ClassLoader cl = formatter.getClassLoader();
                String pkg = "com.google.googlejavaformat.java.";
                Class<?> styleClass = cl.loadClass(pkg + "JavaFormatterOptions$Style");
                Object baseStyle = styleClass.getMethod("valueOf", String.class).invoke(null, style);
                Object effectiveStyle = baseStyle;
                if (maxLineLength > 0) {
                    Method createBuilder = styleClass.getMethod("builder");
                    Class<?> builderType = createBuilder.getReturnType();
                    Object builder = createBuilder.invoke(null);
                    builder = builderType.getMethod("AOSP".equals(style) ? "aosp" : "google").invoke(builder);
                    builder = builderType.getMethod("maxLineLength", int.class).invoke(builder, maxLineLength);
                    effectiveStyle = builderType.getMethod("build").invoke(builder);
                }
                Class<?> optionsClass = cl.loadClass(pkg + "JavaFormatterOptions");
                Class<?> optionsBuilderClass = cl.loadClass(pkg + "JavaFormatterOptions$Builder");
                Object options = optionsClass.getMethod("builder").invoke(null);
                options = optionsBuilderClass.getMethod("style", styleClass).invoke(options, effectiveStyle);
                options = optionsBuilderClass.getMethod("formatJavadoc", boolean.class).invoke(options, formatJavadoc);
                options = optionsBuilderClass.getMethod("cleanup", boolean.class).invoke(options, cleanup);
                options = optionsBuilderClass.getMethod("build").invoke(options);
                Class<?> formatterClass = cl.loadClass(pkg + "Formatter");
                Object instance = formatterClass.getConstructor(optionsClass).newInstance(options);
                formatSource = formatterClass.getMethod("formatSource", String.class);
                removeUnusedImports = cl.loadClass(pkg + "RemoveUnusedImports").getMethod("removeUnusedImports", String.class);
                reorderImportsMethod = cl.loadClass(pkg + "ImportOrderer").getMethod("reorderImports", String.class, styleClass);
                wrap = cl.loadClass(pkg + "StringWrapper").getMethod("wrap", String.class, formatterClass);
                importStyle = reorderImports ? baseStyle : styleClass.getField("GOOGLE").get(null);
                formatterInstance = instance;
            }

            @Override
            String doFormat(String source, boolean script) throws ReflectiveOperationException {
                init();
                String formatted = (String) formatSource.invoke(formatterInstance, source);
                formatted = (String) removeUnusedImports.invoke(null, formatted);
                formatted = (String) reorderImportsMethod.invoke(null, formatted, importStyle);
                if (reflowLongStrings) {
                    formatted = (String) wrap.invoke(null, formatted, formatterInstance);
                }
                return formatted;
            }
        });
    }

    /**
     * The steps of Spotless' {@code PalantirJavaFormatFormatterFunc}: order the imports, remove unused imports, then
     * format.
     */
    static SourceFormatter palantirJavaFormat(String style, boolean formatJavadoc) {
        String key = "pjf:" + style + ':' + formatJavadoc;
        return CACHE.computeIfAbsent(key, k -> new SourceFormatter(BundledFormatter.PALANTIR_JAVA_FORMAT) {
            private volatile Object formatterInstance;
            private Method formatSource;
            private Method removeUnusedImports;
            private Method reorderImports;
            private Object styleValue;

            private synchronized void init() throws ReflectiveOperationException {
                if (formatterInstance != null) {
                    return;
                }
                ClassLoader cl = formatter.getClassLoader();
                String pkg = "com.palantir.javaformat.java.";
                Class<?> styleClass = cl.loadClass(pkg + "JavaFormatterOptions$Style");
                styleValue = styleClass.getMethod("valueOf", String.class).invoke(null, style);
                Class<?> optionsClass = cl.loadClass(pkg + "JavaFormatterOptions");
                Class<?> optionsBuilderClass = cl.loadClass(pkg + "JavaFormatterOptions$Builder");
                Object options = optionsClass.getMethod("builder").invoke(null);
                options = optionsBuilderClass.getMethod("style", styleClass).invoke(options, styleValue);
                options = optionsBuilderClass.getMethod("formatJavadoc", boolean.class).invoke(options, formatJavadoc);
                options = optionsBuilderClass.getMethod("build").invoke(options);
                Class<?> formatterClass = cl.loadClass(pkg + "Formatter");
                Object instance = formatterClass.getMethod("createFormatter", optionsClass).invoke(null, options);
                formatSource = formatterClass.getMethod("formatSource", String.class);
                removeUnusedImports = cl.loadClass(pkg + "RemoveUnusedImports").getMethod("removeUnusedImports", String.class);
                reorderImports = cl.loadClass(pkg + "ImportOrderer").getMethod("reorderImports", String.class, styleClass);
                formatterInstance = instance;
            }

            @Override
            String doFormat(String source, boolean script) throws ReflectiveOperationException {
                init();
                String formatted = (String) reorderImports.invoke(null, source, styleValue);
                formatted = (String) removeUnusedImports.invoke(null, formatted);
                return (String) formatSource.invoke(formatterInstance, formatted);
            }
        });
    }

    /**
     * What Spotless' {@code KtfmtFormatterFunc} does: start from the style's options, apply any that are set, and
     * format a {@code .kts} file as a script.
     */
    static SourceFormatter ktfmt(String style, Integer maxWidth, Integer blockIndent, Integer continuationIndent,
                                 Boolean removeUnusedImports, String trailingCommaManagementStrategy) {
        String key = "ktfmt:" + style + ':' + maxWidth + ':' + blockIndent + ':' + continuationIndent + ':' +
                     removeUnusedImports + ':' + trailingCommaManagementStrategy;
        return CACHE.computeIfAbsent(key, k -> new SourceFormatter(BundledFormatter.KTFMT) {
            private volatile Object options;
            private Method format;
            private Method from;
            private Object kotlinCodeCompanion;
            private Object regular;
            private Object scriptType;

            private synchronized void init() throws ReflectiveOperationException {
                if (options != null) {
                    return;
                }
                ClassLoader cl = formatter.getClassLoader();
                String pkg = "org.jetbrains.kotlinx.ktfmt.format.";
                Class<?> formatterClass = cl.loadClass(pkg + "Formatter");
                Class<?> optionsClass = cl.loadClass(pkg + "FormattingOptions");
                Object base = formatterClass.getField(style + "_FORMAT").get(null);
                Object builder = optionsClass.getMethod("toBuilder").invoke(base);
                Class<?> builderClass = builder.getClass();
                if (maxWidth != null) {
                    builderClass.getMethod("maxWidth", int.class).invoke(builder, maxWidth);
                }
                if (blockIndent != null) {
                    builderClass.getMethod("blockIndent", int.class).invoke(builder, blockIndent);
                }
                if (continuationIndent != null) {
                    builderClass.getMethod("continuationIndent", int.class).invoke(builder, continuationIndent);
                }
                if (removeUnusedImports != null) {
                    builderClass.getMethod("removeUnusedImports", boolean.class).invoke(builder, removeUnusedImports);
                }
                if (trailingCommaManagementStrategy != null) {
                    Class<?> strategyClass = cl.loadClass(pkg + "TrailingCommaManagementStrategy");
                    Object strategy = strategyClass.getMethod("valueOf", String.class).invoke(null, trailingCommaManagementStrategy);
                    builderClass.getMethod("trailingCommaManagementStrategy", strategyClass).invoke(builder, strategy);
                }
                Object built = builderClass.getMethod("build").invoke(builder);
                Class<?> kotlinCode = cl.loadClass(pkg + "KotlinCode");
                Class<?> fileType = cl.loadClass(pkg + "FileType");
                kotlinCodeCompanion = kotlinCode.getField("Companion").get(null);
                from = kotlinCodeCompanion.getClass().getMethod("from", String.class, fileType);
                regular = fileType.getField("REGULAR").get(null);
                scriptType = fileType.getField("SCRIPT").get(null);
                format = formatterClass.getMethod("format", optionsClass, kotlinCode);
                options = built;
            }

            @Override
            String doFormat(String source, boolean script) throws ReflectiveOperationException {
                init();
                Object code = from.invoke(kotlinCodeCompanion, source, script ? scriptType : regular);
                return (String) format.invoke(null, options, code);
            }
        });
    }
}
