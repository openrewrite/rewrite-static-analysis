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

import java.io.IOException;
import java.net.URI;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.*;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/**
 * The class loader a {@link BundledFormatter} runs in. It looks for every class in the formatter's own jars before
 * anywhere else, so that the formatter runs against the libraries it was released with, whatever the recipe's own
 * class path holds. What the jars don't have comes from the recipe's class loader.
 * <p>
 * google-java-format and palantir-java-format parse with javac's internal API, which the JDK keeps in packages that
 * {@code jdk.compiler} does not export, so they fail with an {@link IllegalAccessError} unless the JVM was started
 * with {@code --add-exports}. For those this class loader defines javac's classes itself, read from the JDK's
 * modules, so that they belong to its unnamed module along with the formatter. This is what rewrite-java's
 * {@code JavaUnrestrictedClassLoader} does for the Java parser.
 */
final class FormatterClassLoader extends URLClassLoader {

    static {
        ClassLoader.registerAsParallelCapable();
    }

    private final ClassLoader fallback;
    private final List<Path> jdkModules;

    FormatterClassLoader(URL[] jars, ClassLoader fallback, boolean definesJavac) {
        super(jars, platformClassLoader());
        this.fallback = fallback;
        if (definesJavac) {
            FileSystem jrt = FileSystems.getFileSystem(URI.create("jrt:/"));
            jdkModules = Arrays.asList(
                    jrt.getPath("modules", "jdk.compiler"),
                    jrt.getPath("modules", "java.compiler"),
                    jrt.getPath("modules", "java.base"));
        } else {
            jdkModules = Collections.emptyList();
        }
    }

    @Override
    protected Class<?> loadClass(String name, boolean resolve) throws ClassNotFoundException {
        synchronized (getClassLoadingLock(name)) {
            Class<?> c = findLoadedClass(name);
            if (c == null && !jdkModules.isEmpty() && (name.startsWith("com.sun.") || name.startsWith("sun."))) {
                c = defineFromJdk(name);
            }
            if (c == null && !name.startsWith("java.")) {
                try {
                    c = findClass(name);
                } catch (ClassNotFoundException ignored) {
                    // not in the formatter's own jars
                }
            }
            if (c == null) {
                try {
                    c = getParent().loadClass(name);
                } catch (ClassNotFoundException e) {
                    c = fallback.loadClass(name);
                }
            }
            if (resolve) {
                resolveClass(c);
            }
            return c;
        }
    }

    @Override
    public URL getResource(String name) {
        URL url = findResource(name);
        if (url == null) {
            url = getParent().getResource(name);
        }
        if (url == null) {
            url = fallback.getResource(name);
        }
        return url;
    }

    private Class<?> defineFromJdk(String name) {
        String classFile = name.replace('.', '/') + ".class";
        for (Path module : jdkModules) {
            Path path = module.resolve(classFile);
            if (Files.exists(path)) {
                try {
                    byte[] bytes = Files.readAllBytes(path);
                    return defineClass(name, bytes, 0, bytes.length);
                } catch (IOException e) {
                    throw new IllegalStateException("Unable to read " + path, e);
                } catch (LinkageError e) {
                    // The JDK's own loader already defined it, so use that one.
                    return null;
                }
            }
        }
        return null;
    }

    private static ClassLoader platformClassLoader() {
        try {
            return (ClassLoader) ClassLoader.class.getMethod("getPlatformClassLoader").invoke(null);
        } catch (ReflectiveOperationException e) {
            return ClassLoader.getSystemClassLoader().getParent();
        }
    }
}
