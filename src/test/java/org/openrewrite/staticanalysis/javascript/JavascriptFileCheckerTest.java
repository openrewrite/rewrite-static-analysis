/*
 * Copyright 2024 the original author or authors.
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
package org.openrewrite.staticanalysis.javascript;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.openrewrite.Tree;
import org.openrewrite.javascript.tree.JS;
import org.openrewrite.java.tree.Space;
import org.openrewrite.marker.Markers;

import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.Paths;

import static java.util.Collections.emptyList;
import static org.assertj.core.api.Assertions.assertThat;
import static org.openrewrite.Tree.randomId;

class JavascriptFileCheckerTest {
    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void detectFileIndependentlyOfContextLoader(boolean maskLanguage) throws Exception {
        String checkerName = "org.openrewrite.staticanalysis.javascript.JavascriptFileChecker";
        ClassLoader parent = getClass().getClassLoader();
        URL source = Class.forName(checkerName, false, parent).getProtectionDomain().getCodeSource().getLocation();
        JS.CompilationUnit file = new JS.CompilationUnit(randomId(), Space.EMPTY, Markers.EMPTY,
          Paths.get("example.ts"), null, "UTF-8", false, null, emptyList(), emptyList(), Space.EMPTY);
        ClassLoader previous = Thread.currentThread().getContextClassLoader();
        try (URLClassLoader isolated = new URLClassLoader(new URL[]{source}, parent) {
            @Override
            protected Class<?> loadClass(String name, boolean resolve) throws ClassNotFoundException {
                if (!checkerName.equals(name)) {
                    return super.loadClass(name, resolve);
                }
                Class<?> loaded = findLoadedClass(name);
                if (loaded == null) {
                    loaded = findClass(name);
                }
                if (resolve) {
                    resolveClass(loaded);
                }
                return loaded;
            }
        }) {
            Thread.currentThread().setContextClassLoader(new ClassLoader(parent) {
                @Override
                protected Class<?> loadClass(String name, boolean resolve) throws ClassNotFoundException {
                    if (maskLanguage && name.equals("org.openrewrite.javascript.tree.JS")) {
                        throw new ClassNotFoundException(name);
                    }
                    return super.loadClass(name, resolve);
                }
            });
            Class<?> checker = Class.forName(checkerName, true, isolated);
            assertThat(checker.getMethod("isJavaScript", Tree.class).invoke(null, file)).isEqualTo(true);
        } finally {
            Thread.currentThread().setContextClassLoader(previous);
        }
    }
}
