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

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.openrewrite.java.JavaParser;
import org.openrewrite.test.RecipeSpec;
import org.openrewrite.test.RewriteTest;

import static org.openrewrite.java.Assertions.java;

class SimplifyBooleanExpressionRefasterTest implements RewriteTest {
    @Override
    public void defaults(RecipeSpec spec) {
        spec.recipe(new SimplifyBooleanExpression());
    }

    @ParameterizedTest
    @ValueSource(strings = {"@BeforeTemplate", "public @BeforeTemplate",
      "public @com.google.errorprone.refaster.annotation.BeforeTemplate",
      "@AfterTemplate", "public @AfterTemplate",
      "public @com.google.errorprone.refaster.annotation.AfterTemplate"})
    void preserveTemplateBody(String annotation) {
        rewriteRun(
          spec -> spec.parser(JavaParser.fromJavaVersion().dependsOn(
            """
              package com.google.errorprone.refaster.annotation;
              @java.lang.annotation.Target(java.lang.annotation.ElementType.METHOD)
              public @interface BeforeTemplate {}
              """,
            """
              package com.google.errorprone.refaster.annotation;
              @java.lang.annotation.Target(java.lang.annotation.ElementType.METHOD)
              public @interface AfterTemplate {}
              """
          )),
          java(
            """
              import com.google.errorprone.refaster.annotation.BeforeTemplate;
              import com.google.errorprone.refaster.annotation.AfterTemplate;

              class Test {
                  %s
                  boolean template(String string) {
                      return string.equals(string);
                  }

                  boolean helper(boolean flag) {
                      return flag == true;
                  }
              }
              """.formatted(annotation),
            """
              import com.google.errorprone.refaster.annotation.BeforeTemplate;
              import com.google.errorprone.refaster.annotation.AfterTemplate;

              class Test {
                  %s
                  boolean template(String string) {
                      return string.equals(string);
                  }

                  boolean helper(boolean flag) {
                      return flag;
                  }
              }
              """.formatted(annotation)
          )
        );
    }
}
