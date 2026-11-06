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
import org.openrewrite.java.tree.J;
import org.openrewrite.java.tree.JLeftPadded;
import org.openrewrite.java.tree.JRightPadded;
import org.openrewrite.java.tree.JavaType;
import org.openrewrite.java.tree.Space;
import org.openrewrite.marker.Markers;
import org.openrewrite.python.tree.Py;

import java.nio.file.Paths;

import static java.util.Collections.emptyList;
import static java.util.Collections.singletonList;
import static org.assertj.core.api.Assertions.assertThat;
import static org.openrewrite.Tree.randomId;

class SimplifyBooleanExpressionPythonTest {
    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    void preservePythonStandaloneNegation(boolean value) {
        // Python may use J.Unary directly in a statement list because it implements Statement.
        // Replacing it with J.Literal makes that list impossible to deserialize as statements.
        J.Literal literal = new J.Literal(randomId(), Space.SINGLE_SPACE, Markers.EMPTY,
          value, value ? "True" : "False", null, JavaType.Primitive.Boolean);
        J.Unary unary = new J.Unary(randomId(), Space.EMPTY, Markers.EMPTY,
          JLeftPadded.build(J.Unary.Type.Not), literal, JavaType.Primitive.Boolean);
        Py.CompilationUnit source = new Py.CompilationUnit(randomId(), Space.EMPTY, Markers.EMPTY,
          Paths.get("example.py"), null, "UTF-8", false, null, emptyList(),
          singletonList(JRightPadded.build(unary)), Space.format("\n"));

        assertThat(new SimplifyBooleanExpression().getVisitor().visit(source, new InMemoryExecutionContext()))
          .isSameAs(source);
    }
}
