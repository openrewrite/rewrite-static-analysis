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
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.openrewrite.InMemoryExecutionContext;
import org.openrewrite.groovy.GroovyIsoVisitor;
import org.openrewrite.groovy.GroovyParser;
import org.openrewrite.groovy.tree.G;
import org.openrewrite.java.tree.J;
import org.openrewrite.test.RewriteTest;

import static org.assertj.core.api.Assertions.assertThat;
import static org.openrewrite.groovy.Assertions.groovy;

class NeedBracesGroovyTest implements RewriteTest {
    @ParameterizedTest
    @ValueSource(strings = {"setup:", "when:", "expect:", "cleanup:", "custom:", "legacy-token"})
    void preserveLegacyNonWhitespaceIfPrefix(String label) {
        var ctx = new InMemoryExecutionContext();
        var parsed = (G.CompilationUnit) GroovyParser.builder().build()
          .parse(ctx, "class Spec {\n    def feature() {\n        if (true) assert false\n    }\n}").findFirst().orElseThrow();
        // Serialized models made by the old parser store the label as whitespace, not J.Label.
        // Mutate after parsing: RewriteTest correctly rejects non-whitespace prefixes up front.
        var legacy = (G.CompilationUnit) new GroovyIsoVisitor<InMemoryExecutionContext>() {
            @Override
            public J.If visitIf(J.If iff, InMemoryExecutionContext p) {
                return iff.withPrefix(iff.getPrefix().withWhitespace("\n        " + label + " "));
            }
        }.visitNonNull(parsed, ctx);
        String before = legacy.printAll();
        assertThat(before).isEqualTo("class Spec {\n    def feature() {\n        " + label + " if (true) assert false\n    }\n}");
        var after = (G.CompilationUnit) new NeedBraces().getVisitor().visitNonNull(legacy, ctx);
        assertThat(after.printAll()).isEqualTo(before);
        assertThat(after).isSameAs(legacy);
    }

    @Test
    void freshLabelsAndOrdinaryIfsGetBraces() {
        rewriteRun(spec -> spec.recipe(new NeedBraces()),
          groovy(
            """
              setup: if (true) assert false
              when: if (true) assert false
              if (true) assert false
              """,
            """
              setup: if (true) {
                  assert false
              }
              when: if (true) {
                  assert false
              }
              if (true) {
                  assert false
              }
              """,
            spec -> spec.beforeRecipe(cu -> {
                assertThat(cu.getStatements()).hasSize(3);
                assertThat(cu.getStatements().subList(0, 2)).allMatch(J.Label.class::isInstance);
                assertThat(cu.getStatements().get(2)).isInstanceOf(J.If.class);
            })
          ));
    }
}
