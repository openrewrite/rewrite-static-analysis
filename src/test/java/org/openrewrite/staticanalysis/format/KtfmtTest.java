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

import org.junit.jupiter.api.Test;
import org.openrewrite.DocumentExample;
import org.openrewrite.test.RecipeSpec;
import org.openrewrite.test.RewriteTest;
import org.openrewrite.test.SourceSpec;

import org.openrewrite.staticanalysis.table.SourceFilesFormatted;

import static org.assertj.core.api.Assertions.assertThat;
import static org.openrewrite.kotlin.Assertions.kotlin;

class KtfmtTest implements RewriteTest {

    @Override
    public void defaults(RecipeSpec spec) {
        spec.recipe(new Ktfmt(null, null, null, null, null, null, null));
    }

    @DocumentExample
    @Test
    void formats() {
        rewriteRun(
          spec -> spec.dataTable(SourceFilesFormatted.Row.class, rows ->
            assertThat(rows).singleElement().extracting(SourceFilesFormatted.Row::getResult).isEqualTo("Formatted")),
          kotlin(
            """
              fun  main( ) { val x = listOf(1,2,3) ; println(x) }
              """,
            """
              fun main() {
                val x = listOf(1, 2, 3)
                println(x)
              }
              """,
            SourceSpec::noTrim
          )
        );
    }

    @Test
    void googleStyle() {
        rewriteRun(
          spec -> spec.recipe(new Ktfmt("GOOGLE", null, null, null, null, null, null)),
          kotlin(
            """
              class A {
                  fun m(): Int { return 1 }
              }
              """,
            """
              class A {
                fun m(): Int {
                  return 1
                }
              }
              """,
            SourceSpec::noTrim
          )
        );
    }

    @Test
    void kotlinlangStyle() {
        rewriteRun(
          spec -> spec.recipe(new Ktfmt("KOTLINLANG", null, null, null, null, null, null)),
          kotlin(
            """
              class A {
                fun m(): Int { return 1 }
              }
              """,
            """
              class A {
                  fun m(): Int {
                      return 1
                  }
              }
              """,
            SourceSpec::noTrim
          )
        );
    }

    @Test
    void addsTrailingCommas() {
        rewriteRun(
          spec -> spec.dataTable(SourceFilesFormatted.Row.class, rows ->
            assertThat(rows).singleElement().extracting(SourceFilesFormatted.Row::getResult).isEqualTo("Formatted")),
          kotlin(
            """
              fun f(first: String, second: String, third: String, fourth: String, fifth: String, sixth: String, seventh: String) = first
              """,
            """
              fun f(
                  first: String,
                  second: String,
                  third: String,
                  fourth: String,
                  fifth: String,
                  sixth: String,
                  seventh: String,
              ) = first
              """,
            SourceSpec::noTrim
          )
        );
    }

    @Test
    void removesUnusedImportsAndRedundantSemicolons() {
        rewriteRun(
          spec -> spec.dataTable(SourceFilesFormatted.Row.class, rows ->
            assertThat(rows).singleElement().extracting(SourceFilesFormatted.Row::getResult).isEqualTo("Formatted")),
          kotlin(
            """
              import java.util.UUID
              import java.io.File;
              fun f() = File("a");
              """,
            """
              import java.io.File

              fun f() = File("a")
              """,
            SourceSpec::noTrim
          )
        );
    }
}
