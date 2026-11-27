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
import org.junit.jupiter.api.Test;
import org.openrewrite.DocumentExample;
import org.openrewrite.ExecutionContext;
import org.openrewrite.Parser;
import org.openrewrite.SourceFile;
import org.openrewrite.java.JavaParser;
import org.openrewrite.staticanalysis.table.SourceFilesFormatted;
import org.openrewrite.test.RecipeSpec;
import org.openrewrite.test.RewriteTest;
import org.openrewrite.test.SourceSpec;
import org.openrewrite.tree.ParseError;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

import static java.util.Collections.singletonList;
import static org.assertj.core.api.Assertions.assertThat;
import static org.openrewrite.java.Assertions.java;

class GoogleJavaFormatTest implements RewriteTest {

    @Override
    public void defaults(RecipeSpec spec) {
        spec.recipe(new GoogleJavaFormat(null, null, null, null, null, null));
    }

    @DocumentExample
    @Test
    void formatsAndKeepsTypes() {
        rewriteRun(
          //language=java
          java(
            """
              import java.util.Map;
              import java.util.List;
              import java.util.ArrayList;
              class A {
                  List<String> names( Map<String,Integer> counts ) {
                      List<String> result = new ArrayList<>(  );
                      for(String name : counts.keySet()) { if (counts.get(name) > 1) result.add(name); }
                      return result;
                  }
              }
              """,
            """
              import java.util.ArrayList;
              import java.util.List;
              import java.util.Map;

              class A {
                List<String> names(Map<String, Integer> counts) {
                  List<String> result = new ArrayList<>();
                  for (String name : counts.keySet()) {
                    if (counts.get(name) > 1) result.add(name);
                  }
                  return result;
                }
              }
              """,
            spec -> spec.noTrim().afterRecipe(cu -> assertThat(cu.getClasses().getFirst().getType()).isNotNull())
          )
        );
    }

    @Test
    void aospStyle() {
        rewriteRun(
          spec -> spec.recipe(new GoogleJavaFormat("AOSP", null, null, null, null, null)),
          //language=java
          java(
            """
              class A {
                void m() { int x = 1; }
              }
              """,
            """
              class A {
                  void m() {
                      int x = 1;
                  }
              }
              """,
            SourceSpec::noTrim
          )
        );
    }

    @Test
    void ordersModifiers() {
        rewriteRun(
          spec -> spec.dataTable(SourceFilesFormatted.Row.class, rows ->
            assertThat(rows).singleElement().extracting(SourceFilesFormatted.Row::getResult).isEqualTo("Formatted")),
          //language=java
          java(
            """
              class A {
                final static int X = 1;
              }
              """,
            """
              class A {
                static final int X = 1;
              }
              """,
            SourceSpec::noTrim
          )
        );
    }

    @Test
    void notFormattedWhenTheFormatterCannotParse() {
        rewriteRun(
          spec -> spec.dataTable(SourceFilesFormatted.Row.class, rows ->
            assertThat(rows).singleElement().satisfies(row -> assertThat(row.getResult()).isEqualTo("Not formatted"))),
          //language=java
          java(
            """
              class A {
                void m() { int  x = 1; }
              }
              """,
            spec -> spec.noTrim().mapBeforeRecipe(cu -> cu.withEof(cu.getEof().withWhitespace("\\n}\\n")))
          )
        );
    }

    @Test
    void formatsAFileThatDidNotParse() {
        rewriteRun(
          spec -> spec.dataTable(SourceFilesFormatted.Row.class, rows -> assertThat(rows).singleElement()
            .extracting(SourceFilesFormatted.Row::getResult).isEqualTo("Formatted, without types")),
          new SourceSpec<>(ParseError.class, null, new FailingJavaParser.Builder(),
            """
              class A {
                void m() { int  x = 1; }
              }
              """,
            after -> """
              class A {
                void m() {
                  int x = 1;
                }
              }
              """
          ).noTrim()
        );
    }

    /**
     * Reads Java as a {@link ParseError}, as the Java parser leaves a file it fails on.
     */
    private static class FailingJavaParser implements Parser {
        private final JavaParser parser = JavaParser.fromJavaVersion().build();

        @Override
        public Stream<SourceFile> parseInputs(Iterable<Input> sources, @Nullable Path relativeTo, ExecutionContext ctx) {
            List<SourceFile> parseErrors = new ArrayList<>();
            for (Input input : sources) {
                SourceFile parsed = parser.parseInputs(singletonList(input), relativeTo, ctx).findFirst().orElseThrow();
                parseErrors.add(ParseError.build(this, input, relativeTo, ctx, new IllegalStateException("did not parse"))
                  .withErroneous(parsed));
            }
            return parseErrors.stream();
        }

        @Override
        public boolean accept(Path path) {
            return path.toString().endsWith(".java");
        }

        @Override
        public Path sourcePathFromSourceText(Path prefix, String sourceCode) {
            return prefix.resolve("A.java");
        }

        static class Builder extends Parser.Builder {
            Builder() {
                super(ParseError.class);
            }

            @Override
            public Parser build() {
                return new FailingJavaParser();
            }

            @Override
            public String getDslName() {
                return "java";
            }
        }
    }
}
