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

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.List;

/**
 * A formatter that this module carries as the jars it runs from, under {@code META-INF/rewrite/formatters/<name>/},
 * and loads into a class loader of its own the first time a recipe asks for it.
 */
enum BundledFormatter {
    GOOGLE_JAVA_FORMAT("google-java-format", 21, true),
    PALANTIR_JAVA_FORMAT("palantir-java-format", 11, true);

    private final String name;
    private final int minimumJavaVersion;
    private final boolean usesJavac;

    private volatile @Nullable String version;
    private volatile @Nullable ClassLoader classLoader;

    BundledFormatter(String name, int minimumJavaVersion, boolean usesJavac) {
        this.name = name;
        this.minimumJavaVersion = minimumJavaVersion;
        this.usesJavac = usesJavac;
    }

    String getName() {
        return name;
    }

    String getVersion() {
        load();
        //noinspection DataFlowIssue
        return version;
    }

    ClassLoader getClassLoader() {
        load();
        //noinspection DataFlowIssue
        return classLoader;
    }

    private void load() {
        if (classLoader != null) {
            return;
        }
        synchronized (this) {
            if (classLoader != null) {
                return;
            }
            int javaVersion = javaVersion();
            if (javaVersion < minimumJavaVersion) {
                throw new IllegalStateException(name + " needs Java " + minimumJavaVersion +
                                                " or later to run, and this recipe runs on Java " + javaVersion + ".");
            }
            String directory = "META-INF/rewrite/formatters/" + name + "/";
            ClassLoader resources = BundledFormatter.class.getClassLoader();
            List<String> jars = new ArrayList<>();
            String bundledVersion = null;
            try (InputStream index = resources.getResourceAsStream(directory + "index.txt")) {
                if (index == null) {
                    throw new IllegalStateException("This build of the recipe does not bundle " + name + ".");
                }
                BufferedReader reader = new BufferedReader(new InputStreamReader(index, StandardCharsets.UTF_8));
                for (String line; (line = reader.readLine()) != null; ) {
                    if (line.startsWith("version=")) {
                        bundledVersion = line.substring("version=".length());
                    } else if (line.startsWith("jar=")) {
                        jars.add(line.substring("jar=".length()));
                    }
                }
                Path extracted = Files.createTempDirectory("rewrite-" + name + "-");
                extracted.toFile().deleteOnExit();
                List<URL> urls = new ArrayList<>(jars.size());
                for (String jar : jars) {
                    Path target = extracted.resolve(jar);
                    try (InputStream in = resources.getResourceAsStream(directory + jar)) {
                        if (in == null) {
                            throw new IllegalStateException("The bundled " + name + " is missing " + jar + ".");
                        }
                        Files.copy(in, target, StandardCopyOption.REPLACE_EXISTING);
                    }
                    target.toFile().deleteOnExit();
                    urls.add(target.toUri().toURL());
                }
                version = bundledVersion;
                classLoader = new FormatterClassLoader(urls.toArray(new URL[0]), resources, usesJavac);
            } catch (IOException e) {
                throw new IllegalStateException("Unable to load the bundled " + name + ".", e);
            }
        }
    }

    private static int javaVersion() {
        String specification = System.getProperty("java.specification.version", "1.8");
        if (specification.startsWith("1.")) {
            specification = specification.substring(2);
        }
        try {
            return Integer.parseInt(specification);
        } catch (NumberFormatException e) {
            return 8;
        }
    }
}
