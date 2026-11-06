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
import org.junit.jupiter.params.provider.MethodSource;
import org.openrewrite.InMemoryExecutionContext;
import org.openrewrite.Recipe;
import org.openrewrite.java.JavaParser;
import org.openrewrite.java.tree.J;
import org.openrewrite.java.tree.JRightPadded;
import org.openrewrite.java.tree.Space;
import org.openrewrite.marker.Markers;
import org.openrewrite.marker.SearchResult;
import org.openrewrite.javascript.tree.JS;

import java.nio.file.Paths;
import java.util.stream.Stream;

import static java.util.Collections.emptyList;
import static java.util.Collections.singletonList;
import static org.assertj.core.api.Assertions.assertThat;
import static org.openrewrite.Tree.randomId;

class JavaScriptControlFlowTest {
    static Stream<Object[]> cases() {
        return Stream.of(
          new Object[]{new SimplifyBooleanReturn(), "boolean f(boolean a) { if (a) { return true; } else { return false; } }"},
          new Object[]{new SimplifyBooleanExpression(), "boolean f(boolean a) { return a || false; }"},
          new Object[]{new InlineVariable(), "int f() { int backend = 1; return backend; }"},
          new Object[]{new BooleanChecksNotInverted(), "boolean f(boolean a) { return !(!a); }"},
          new Object[]{new MinimumSwitchCases(), "void f(String route) { switch (route) { case \"/settings/account\": System.out.println(route); break; } }"}
        );
    }

    @ParameterizedTest
    @MethodSource("cases")
    void preserveJavaScriptSemantics(Recipe recipe, String methodText) {
        InMemoryExecutionContext ctx = new InMemoryExecutionContext();
        J.CompilationUnit javaSource = (J.CompilationUnit) JavaParser.fromJavaVersion().build()
          .parse(ctx, "class Example { " + methodText + " }").findFirst().orElseThrow();
        J.MethodDeclaration method = (J.MethodDeclaration) javaSource.getClasses().get(0).getBody().getStatements().get(0);
        JS.CompilationUnit source = new JS.CompilationUnit(randomId(), Space.EMPTY, Markers.EMPTY,
          Paths.get("example.ts"), null, "UTF-8", false, null, emptyList(),
          singletonList(JRightPadded.build(method)), Space.format("\n"));
        assertThat(recipe.getVisitor().visit(source, ctx)).isSameAs(source);
        JS.CompilationUnit marked = SearchResult.found(source);
        assertThat(recipe.getVisitor().visit(marked, ctx)).isSameAs(marked);
    }

    @ParameterizedTest
    @MethodSource("cases")
    void stillCleansMarkedJavaFiles(Recipe recipe, String methodText) {
        InMemoryExecutionContext ctx = new InMemoryExecutionContext();
        J.CompilationUnit source = (J.CompilationUnit) JavaParser.fromJavaVersion().build()
          .parse(ctx, "class Example { " + methodText + " }").findFirst().orElseThrow();
        // MinimumSwitchCases already uses a separate C# precondition.
        if (!(recipe instanceof MinimumSwitchCases)) {
            source = SearchResult.found(source);
        }
        assertThat(recipe.getVisitor().visit(source, ctx)).isNotSameAs(source);
    }
}
