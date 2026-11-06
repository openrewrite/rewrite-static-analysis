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
package org.openrewrite.staticanalysis;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.openrewrite.test.RecipeSpec;
import org.openrewrite.test.RewriteTest;

import javax.tools.ToolProvider;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;
import static org.openrewrite.java.Assertions.java;
import static org.openrewrite.java.Assertions.srcMainJava;

class CommonStaticAnalysisMethodNamesTest implements RewriteTest {
    @TempDir
    Path classes;

    @Override
    public void defaults(RecipeSpec spec) {
        spec.recipeFromResources("org.openrewrite.staticanalysis.CommonStaticAnalysis");
    }

    // Reactive Streams TCK rule 2.3 identifies the initiating call by its stack-frame name.
    private static final String STACK_CONTRACT = """
      public class StackContract {
          public boolean detectsForbiddenCall() {
              return new Object() {
                  boolean required_spec203_call() {
                      for (StackTraceElement frame : Thread.currentThread().getStackTrace()) {
                          if ("required_spec203_call".equals(frame.getMethodName())) {
                              return true;
                          }
                      }
                      return false;
                  }
                  boolean run() {
                      return required_spec203_call();
                  }
              }.run();
          }
      }
      """;

    @Test
    void preserveStackTraceMethodNameContract() throws Exception {
        assertThat(evaluate(STACK_CONTRACT)).isTrue();
        rewriteRun(
          srcMainJava(java(STACK_CONTRACT,
            spec -> spec.afterRecipe(cu -> assertThat(evaluate(cu.printAll())).isTrue())))
        );
    }

    @Test
    void explicitMethodNameRefactoringRemainsAvailable() {
        rewriteRun(
          spec -> spec.recipe(new MethodNameCasing(false, false)),
          srcMainJava(java(
            STACK_CONTRACT,
            STACK_CONTRACT.replace("boolean required_spec203_call()", "boolean requiredSpec203Call()")
              .replace("return required_spec203_call();", "return requiredSpec203Call();"),
            spec -> spec.afterRecipe(cu -> assertThat(evaluate(cu.printAll())).isFalse())
          ))
        );
    }

    private boolean evaluate(String source) throws Exception {
        Path file = classes.resolve("StackContract.java");
        Files.write(file, source.getBytes(StandardCharsets.UTF_8));
        assertThat(ToolProvider.getSystemJavaCompiler().run(null, null, null,
          "-d", classes.toString(), file.toString())).isZero();
        try (URLClassLoader loader = new URLClassLoader(new URL[]{classes.toUri().toURL()}, null)) {
            Class<?> type = loader.loadClass("StackContract");
            return (boolean) type.getMethod("detectsForbiddenCall").invoke(type.getConstructor().newInstance());
        }
    }
}
