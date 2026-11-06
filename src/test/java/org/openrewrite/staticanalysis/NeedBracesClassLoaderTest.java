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
package org.openrewrite.staticanalysis;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.openrewrite.InMemoryExecutionContext;
import org.openrewrite.Recipe;
import org.openrewrite.TreeVisitor;
import org.openrewrite.java.JavaParser;
import org.openrewrite.java.JavaVisitor;
import org.openrewrite.java.format.AutoFormatVisitor;

import javax.tools.ToolProvider;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

class NeedBracesClassLoaderTest {
    @Test
    void usesRecipeJavaFormatterWithSharedServices(@TempDir Path directory) throws Exception {
        String property = getClass().getName() + ".formatted";
        String formatterName = AutoFormatVisitor.class.getName();
        Path source = directory.resolve("AutoFormatVisitor.java");
        Files.writeString(source, """
          package org.openrewrite.java.format;
          public class AutoFormatVisitor extends org.openrewrite.java.JavaIsoVisitor<Object> {
              public AutoFormatVisitor(org.openrewrite.Tree stopAfter, org.openrewrite.style.NamedStyles... styles) {
                  System.setProperty("%s", "true");
              }
              public org.openrewrite.java.tree.J visit(org.openrewrite.Tree tree, Object p, org.openrewrite.Cursor cursor) {
                  return (org.openrewrite.java.tree.J) super.visit(tree, p, cursor);
              }
          }
          """.formatted(property));
        String classpath = Path.of(JavaVisitor.class.getProtectionDomain().getCodeSource().getLocation().toURI()) +
          File.pathSeparator + Path.of(TreeVisitor.class.getProtectionDomain().getCodeSource().getLocation().toURI());
        ByteArrayOutputStream errors = new ByteArrayOutputStream();
        int compiled = ToolProvider.getSystemJavaCompiler().run(null, null, errors,
          "-proc:none", "-classpath", classpath, "-d", directory.toString(), source.toString());
        assertThat(compiled).withFailMessage(errors.toString()).isZero();

        ClassLoader isolated = new ClassLoader(NeedBraces.class.getClassLoader()) {
            @Override
            protected Class<?> loadClass(String name, boolean resolve) throws ClassNotFoundException {
                synchronized (getClassLoadingLock(name)) {
                    if (!name.equals(formatterName) && !name.equals(NeedBraces.class.getName()) &&
                        !name.startsWith(NeedBraces.class.getName() + "$")) {
                        return super.loadClass(name, resolve);
                    }
                    Class<?> loaded = findLoadedClass(name);
                    if (loaded == null) {
                        try {
                            byte[] bytes;
                            if (name.equals(formatterName)) {
                                bytes = Files.readAllBytes(directory.resolve(name.replace('.', '/') + ".class"));
                            } else {
                                try (InputStream in = getParent().getResourceAsStream(name.replace('.', '/') + ".class")) {
                                    bytes = in.readAllBytes();
                                }
                            }
                            loaded = defineClass(name, bytes, 0, bytes.length);
                        } catch (Exception e) {
                            throw new ClassNotFoundException(name, e);
                        }
                    }
                    if (resolve) {
                        resolveClass(loaded);
                    }
                    return loaded;
                }
            }
        };
        System.clearProperty(property);
        try {
            Recipe recipe = (Recipe) isolated.loadClass(NeedBraces.class.getName()).getConstructor().newInstance();
            InMemoryExecutionContext ctx = new InMemoryExecutionContext();
            var cu = JavaParser.fromJavaVersion().build()
              .parse(ctx, "class A { void f(boolean flag) { if (flag) System.out.println(flag); } }")
              .findFirst().orElseThrow();
            assertThat(recipe.getVisitor().visit(cu, ctx)).isNotSameAs(cu);
            assertThat(System.getProperty(property)).isEqualTo("true");
        } finally {
            System.clearProperty(property);
        }
    }
}
