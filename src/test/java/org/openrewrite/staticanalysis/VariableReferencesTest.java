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

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class VariableReferencesTest {

    @Test
    void compoundAssignmentAsLambdaBodyIsRead() {
        // given
        J.Lambda lambda = parseLambda(
          """
            import java.util.function.BinaryOperator;
            class A {
                BinaryOperator<Integer> f = (x, v) -> x += v;
            }
            """
        );
        J.Identifier x = ((J.VariableDeclarations) lambda.getParameters().getParameters().get(0)).getVariables().get(0).getName();

        // when
        List<J> references = VariableReferences.findRhsReferences(lambda.getBody(), x);

        // then
        assertThat(references).singleElement().satisfies(ref -> assertThat(((J.Identifier) ref).getSimpleName()).isEqualTo("x"));
    }

    @Test
    void compoundAssignmentAsStatementIsNotRead() {
        // given
        J.Lambda lambda = parseLambda(
          """
            import java.util.function.BiConsumer;
            class A {
                BiConsumer<Integer, Integer> f = (x, v) -> { x += v; };
            }
            """
        );
        J.Identifier x = ((J.VariableDeclarations) lambda.getParameters().getParameters().get(0)).getVariables().get(0).getName();

        // when
        List<J> references = VariableReferences.findRhsReferences(lambda.getBody(), x);

        // then
        assertThat(references).isEmpty();
    }

    private static J.Lambda parseLambda(String source) {
        J.CompilationUnit cu = (J.CompilationUnit) JavaParser.fromJavaVersion().build()
          .parse(new InMemoryExecutionContext(t -> {
              throw new AssertionError(t);
          }), source)
          .findFirst()
          .orElseThrow();
        J.VariableDeclarations field = (J.VariableDeclarations) cu.getClasses().get(0).getBody().getStatements().get(0);
        return (J.Lambda) field.getVariables().get(0).getInitializer();
    }
}
