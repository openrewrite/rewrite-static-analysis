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
import org.openrewrite.InMemoryExecutionContext;
import org.openrewrite.java.JavaParser;
import org.openrewrite.java.tree.J;
import org.openrewrite.java.tree.JRightPadded;
import org.openrewrite.java.tree.Space;
import org.openrewrite.javascript.tree.JS;
import org.openrewrite.java.marker.Semicolon;
import org.openrewrite.marker.Markers;

import java.nio.file.Paths;

import static java.util.Collections.emptyList;
import static java.util.Collections.singletonList;
import static org.assertj.core.api.Assertions.assertThat;
import static org.openrewrite.Tree.randomId;

class JavaScriptBracesTest {
    @Test
    void preserveSemicolonPlacementAroundElse() {
        InMemoryExecutionContext ctx = new InMemoryExecutionContext();
        J.CompilationUnit javaSource = (J.CompilationUnit) JavaParser.fromJavaVersion().build()
          .parse(ctx, "class Example { void f(boolean b) { if (b) System.out.println(1); else System.out.println(2); } }")
          .findFirst().orElseThrow();
        J.MethodDeclaration method = (J.MethodDeclaration) javaSource.getClasses().get(0).getBody().getStatements().get(0);
        J.If iff = (J.If) method.getBody().getStatements().get(0);
        // JS stores the semicolon on the padded statement. Java-only block wrapping leaves it outside the new block.
        iff = iff.getPadding().withThenPart(iff.getPadding().getThenPart()
          .withMarkers(Markers.EMPTY.add(new Semicolon(randomId()))));
        method = method.withBody(method.getBody().withStatements(singletonList(iff)));
        JS.CompilationUnit source = new JS.CompilationUnit(randomId(), Space.EMPTY, Markers.EMPTY,
          Paths.get("example.ts"), null, "UTF-8", false, null, emptyList(),
          singletonList(JRightPadded.build(method)), Space.format("\n"));
        assertThat(new NeedBraces().getVisitor().visit(source, ctx)).isSameAs(source);
    }
}
