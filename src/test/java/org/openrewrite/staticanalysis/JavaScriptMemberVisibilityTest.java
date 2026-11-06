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
import org.openrewrite.marker.Markers;

import java.nio.file.Paths;

import static java.util.Collections.emptyList;
import static java.util.Collections.singletonList;
import static org.assertj.core.api.Assertions.assertThat;
import static org.openrewrite.Tree.randomId;

class JavaScriptMemberVisibilityTest {
    @Test
    void preservePublicMethodWithInferredReturnType() {
        InMemoryExecutionContext ctx = new InMemoryExecutionContext();
        J.CompilationUnit javaSource = (J.CompilationUnit) JavaParser.fromJavaVersion().build()
          .parse(ctx, "abstract class Example { public boolean isSupported() { return true; } }")
          .findFirst().orElseThrow();
        J.ClassDeclaration clazz = javaSource.getClasses().get(0);
        J.MethodDeclaration method = (J.MethodDeclaration) clazz.getBody().getStatements().get(0);
        // JS methods with inferred return types have no explicit return-type expression.
        method = method.withReturnTypeExpression(null);
        clazz = clazz.withBody(clazz.getBody().withStatements(singletonList(method)));
        JS.CompilationUnit source = new JS.CompilationUnit(randomId(), Space.EMPTY, Markers.EMPTY,
          Paths.get("example.ts"), null, "UTF-8", false, null, emptyList(),
          singletonList(JRightPadded.build(clazz)), Space.format("\n"));
        assertThat(new AbstractClassPublicConstructor().getVisitor().visit(source, ctx)).isSameAs(source);
    }
}
