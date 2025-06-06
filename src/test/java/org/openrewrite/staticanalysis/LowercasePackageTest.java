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
import org.openrewrite.DocumentExample;
import org.openrewrite.PathUtils;
import org.openrewrite.test.RecipeSpec;
import org.openrewrite.test.RewriteTest;

import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;
import static org.openrewrite.java.Assertions.java;

class LowercasePackageTest implements RewriteTest {

    @Override
    public void defaults(RecipeSpec spec) {
        spec.recipe(new LowercasePackage());
    }

    @DocumentExample
    @Test
    void lowerCasePackage() {
        rewriteRun(
          java(
            //language=java
            """
              package com.UPPERCASE.CamelCase;
              class A {}
              """,
            """
              package com.uppercase.camelcase;
              class A {}
              """,
            spec -> spec.afterRecipe(cu ->
              assertThat(PathUtils.equalIgnoringSeparators(cu.getSourcePath(), Path.of("com/uppercase/camelcase/A.java"))).isTrue())
          )
        );
    }

    @Test
    void noChange() {
        rewriteRun(
          java(
            //language=java
            """
              package com.lowercase;
              class A {}
              """,
            spec -> spec.afterRecipe(cu ->
              assertThat(PathUtils.equalIgnoringSeparators(cu.getSourcePath(), Path.of("com/lowercase/A.java"))).isTrue())
          )
        );
    }
}
