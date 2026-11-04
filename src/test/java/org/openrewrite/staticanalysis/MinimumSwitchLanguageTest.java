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
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.openrewrite.InMemoryExecutionContext;
import org.openrewrite.golang.tree.Go;
import org.openrewrite.java.JavaParser;
import org.openrewrite.java.tree.J;
import org.openrewrite.java.tree.JContainer;
import org.openrewrite.java.tree.JRightPadded;
import org.openrewrite.java.tree.Space;
import org.openrewrite.java.tree.Statement;
import org.openrewrite.marker.Markers;
import org.openrewrite.marker.SearchResult;
import org.openrewrite.test.RewriteTest;

import java.nio.file.Paths;

import static java.util.Collections.singletonList;
import static org.assertj.core.api.Assertions.assertThat;
import static org.openrewrite.Tree.randomId;
import static org.openrewrite.scala.Assertions.scala;

class MinimumSwitchLanguageTest implements RewriteTest {
    @ParameterizedTest
    @ValueSource(strings = {"example.go", "example.java"})
    void retainGoSourceWithAnyPath(String path) {
        InMemoryExecutionContext ctx = new InMemoryExecutionContext();
        J.CompilationUnit javaSource = (J.CompilationUnit) JavaParser.fromJavaVersion().build()
          .parse(ctx, "class Example { void f() { switch (0) { default: effect(); } } void effect() {} }")
          .findFirst().orElseThrow();
        J.MethodDeclaration method = (J.MethodDeclaration) javaSource.getClasses().get(0).getBody().getStatements().get(0);
        J.Identifier packageName = javaSource.getClasses().get(0).getName().withId(randomId())
          .withSimpleName("example").withType(null);
        Go.CompilationUnit source = new Go.CompilationUnit(randomId(), Space.EMPTY, Markers.EMPTY,
          Paths.get(path), null, "UTF-8", false, null, JRightPadded.build(packageName), JContainer.empty(),
          singletonList(JRightPadded.build((Statement) method)), Space.format("\n"));
        assertThat(new MinimumSwitchCases().getVisitor().visit(source, ctx)).isSameAs(source);
        Go.CompilationUnit marked = SearchResult.found(source);
        assertThat(new MinimumSwitchCases().getVisitor().visit(marked, ctx)).isSameAs(marked);
    }

    @Test
    void rewritesMarkedJavaSource() {
        InMemoryExecutionContext ctx = new InMemoryExecutionContext();
        J.CompilationUnit source = (J.CompilationUnit) JavaParser.fromJavaVersion().build()
          .parse(ctx, "class Example { void f() { switch (0) { default: effect(); } } void effect() {} }")
          .findFirst().orElseThrow();
        J.CompilationUnit marked = SearchResult.found(source);
        assertThat(new MinimumSwitchCases().getVisitor().visit(marked, ctx)).isNotSameAs(marked);
    }

    @Test
    void retainScalaMatch() {
        rewriteRun(
          spec -> spec.recipe(new MinimumSwitchCases()),
          scala(
            """
              class Example {
                def f(n: Int): Unit = {
                  n match {
                    case 1 => println(n)
                    case _ => println(0)
                  }
                }
              }
              """
          )
        );
    }
}
