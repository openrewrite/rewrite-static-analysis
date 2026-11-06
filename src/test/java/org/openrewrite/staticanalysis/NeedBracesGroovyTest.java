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
import org.openrewrite.java.tree.JRightPadded;
import org.openrewrite.java.tree.Space;
import org.openrewrite.marker.Markers;
import org.openrewrite.test.RewriteTest;

import java.util.List;

import static java.util.Collections.emptyList;
import static org.openrewrite.Tree.randomId;

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

    @ParameterizedTest
    @ValueSource(strings = {"setup", "when", "expect", "cleanup", "custom"})
    void freshLabelNodesGetBraces(String label) {
        var ctx = new InMemoryExecutionContext();
        var parsed = (G.CompilationUnit) GroovyParser.builder().build()
          .parse(ctx, "if (true) assert false").findFirst().orElseThrow();
        // Construct the fixed parser's shape from an ordinary parsed if. The standalone recipe
        // dependency can predate the parser fix; this control must test the visitor independently.
        var iff = (J.If) parsed.getStatements().get(0);
        var labeled = new J.Label(randomId(), iff.getPrefix(), Markers.EMPTY,
          JRightPadded.build(new J.Identifier(randomId(), Space.EMPTY, Markers.EMPTY,
            emptyList(), label, null, null)), iff.withPrefix(Space.SINGLE_SPACE));
        var fresh = parsed.withStatements(List.of(labeled));
        String before = label + ": if (true) assert false";
        assertThat(fresh.printAll()).isEqualTo(before);
        assertThat(labeled.getStatement().getPrefix().getWhitespace()).isEqualTo(" ");
        var after = (G.CompilationUnit) new NeedBraces().getVisitor().visitNonNull(fresh, ctx);
        assertThat(after.getStatements().get(0)).isInstanceOf(J.Label.class);
        var afterLabel = (J.Label) after.getStatements().get(0);
        assertThat(afterLabel.getLabel().getSimpleName()).isEqualTo(label);
        assertThat(((J.If) afterLabel.getStatement()).getThenPart()).isInstanceOf(J.Block.class);
        assertThat(after.printAll()).isEqualTo(label + ": if (true) {\n    assert false\n}");
        var secondPass = (G.CompilationUnit) new NeedBraces().getVisitor().visitNonNull(after, ctx);
        assertThat(secondPass.printAll()).isEqualTo(after.printAll());
    }

    @Test
    void ordinaryGroovyIfGetsBraces() {
        rewriteRun(spec -> spec.recipe(new NeedBraces()),
          groovy(
            "if (true) assert false",
            """
              if (true) {
                  assert false
              }
              """
          ));
    }
}
