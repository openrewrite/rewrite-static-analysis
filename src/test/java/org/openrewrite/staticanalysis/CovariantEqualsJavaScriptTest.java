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
import org.junit.jupiter.params.provider.CsvSource;
import org.openrewrite.InMemoryExecutionContext;
import org.openrewrite.java.JavaParser;
import org.openrewrite.java.tree.J;
import org.openrewrite.java.tree.JRightPadded;
import org.openrewrite.java.tree.JavaType;
import org.openrewrite.java.tree.Space;
import org.openrewrite.javascript.tree.JS;
import org.openrewrite.marker.Markers;
import org.openrewrite.marker.SearchResult;
import org.openrewrite.test.RecipeSpec;
import org.openrewrite.test.RewriteTest;

import java.nio.file.Paths;
import java.util.Arrays;

import static java.util.Collections.emptyList;
import static java.util.Collections.singletonList;
import static org.assertj.core.api.Assertions.assertThat;
import static org.openrewrite.Tree.randomId;
import static org.openrewrite.javascript.Assertions.javascript;
import static org.openrewrite.javascript.Assertions.typescript;

class CovariantEqualsJavaScriptTest implements RewriteTest {
    @Override
    public void defaults(RecipeSpec spec) {
        spec.recipe(new CovariantEquals());
    }

    @Test
    void parsedAnonymousJavaScriptClass() {
        rewriteRun(javascript(
          """
            const Example = class {
                equals(other) { return this === other; }
                hashCode() { return 0; }
            };
            """
        ));
    }

    @Test
    void parsedAnonymousTypeScriptClass() {
        rewriteRun(typescript(
          """
            const Example = class {
                equals(other: unknown): boolean { return this === other; }
                hashCode(): number { return 0; }
            };
            """
        ));
    }

    @ParameterizedTest
    @CsvSource({
      "example.js, (Anonymous class)",
      "example.ts, (Anonymous class)",
      "example.java, (Anonymous class)",
      "example.js, Example",
      "example.ts, Example"
    })
    void retainNativeEquals(String path, String typeName) {
        // Exercise real JS visitor dispatch without depending on an external JavaScript RPC install.
        InMemoryExecutionContext ctx = new InMemoryExecutionContext();
        J.CompilationUnit javaSource = (J.CompilationUnit) JavaParser.fromJavaVersion().build()
          .parse(ctx, "class Example { public boolean equals(Example other) { return this == other; } " +
                      "public int hashCode() { return 0; } }")
          .findFirst().orElseThrow();
        J.ClassDeclaration clazz = javaSource.getClasses().get(0);
        JavaType.FullyQualified type = JavaType.ShallowClass.build(typeName);
        J.MethodDeclaration equals = (J.MethodDeclaration) clazz.getBody().getStatements().get(0);
        equals = equals.withMethodType(equals.getMethodType().withDeclaringType(type)
          .withParameterTypes(singletonList(type)));
        clazz = clazz.withType(type).withBody(clazz.getBody().withStatements(
          Arrays.asList(equals, clazz.getBody().getStatements().get(1))));
        JS.CompilationUnit source = new JS.CompilationUnit(randomId(), Space.EMPTY, Markers.EMPTY,
          Paths.get(path), null, "UTF-8", false, null, emptyList(),
          singletonList(JRightPadded.build(clazz)), Space.EMPTY);
        assertThat(new org.openrewrite.java.MethodMatcher("* equals(..)").matches(equals.getMethodType())).isTrue();
        assertThat(new CovariantEquals().getVisitor().visit(source, ctx)).isSameAs(source);
        JS.CompilationUnit marked = SearchResult.found(source);
        assertThat(new CovariantEquals().getVisitor().visit(marked, ctx)).isSameAs(marked);
    }
}
