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
import org.openrewrite.InMemoryExecutionContext;
import org.openrewrite.csharp.tree.Cs;
import org.openrewrite.java.JavaParser;
import org.openrewrite.java.tree.J;
import org.openrewrite.java.tree.JRightPadded;
import org.openrewrite.java.tree.Space;
import org.openrewrite.java.tree.Statement;
import org.openrewrite.marker.Markers;
import org.openrewrite.marker.SearchResult;

import static java.util.Collections.emptyList;
import static java.util.Collections.singletonList;
import static org.assertj.core.api.Assertions.assertThat;
import static org.openrewrite.Tree.randomId;

class CSharpExplicitInitializationTest {
    @ParameterizedTest
    @ValueSource(strings = {"int value = 0;", "boolean value = false;", "String value = null;"})
    void preserveRequiredConstantInitializer(String field) {
        InMemoryExecutionContext ctx = new InMemoryExecutionContext();
        Cs.CompilationUnit source = source(field, true, ctx);
        assertThat(new ExplicitInitialization().getVisitor().visit(source, ctx)).isSameAs(source);
        Cs.CompilationUnit marked = SearchResult.found(source);
        assertThat(new ExplicitInitialization().getVisitor().visit(marked, ctx)).isSameAs(marked);
    }

    @ParameterizedTest
    @ValueSource(strings = {"int value = 0;", "boolean value = false;", "String value = null;"})
    void stillRemovesOrdinaryCSharpFieldDefaults(String field) {
        InMemoryExecutionContext ctx = new InMemoryExecutionContext();
        Cs.CompilationUnit source = source(field, false, ctx);
        assertThat(new ExplicitInitialization().getVisitor().visit(source, ctx)).isNotSameAs(source);
    }

    private static Cs.CompilationUnit source(String field, boolean constant, InMemoryExecutionContext ctx) {
        J.CompilationUnit cu = (J.CompilationUnit) JavaParser.fromJavaVersion().build()
          .parse(ctx, "class Example { " + field + " }").findFirst().orElseThrow();
        J.ClassDeclaration clazz = cu.getClasses().get(0);
        if (constant) {
            J.VariableDeclarations declaration = (J.VariableDeclarations) clazz.getBody().getStatements().get(0);
            declaration = declaration.withModifiers(singletonList(new J.Modifier(randomId(), Space.EMPTY,
              Markers.EMPTY, "const", J.Modifier.Type.LanguageExtension, emptyList())));
            clazz = clazz.withBody(clazz.getBody().withStatements(singletonList(declaration)));
        }
        return new Cs.CompilationUnit(cu.getId(), cu.getPrefix(), cu.getMarkers(), cu.getSourcePath(),
          cu.getFileAttributes(), cu.getCharset().name(), cu.isCharsetBomMarked(), cu.getChecksum(),
          emptyList(), emptyList(), emptyList(), singletonList(JRightPadded.build((Statement) clazz)), cu.getEof());
    }
}
