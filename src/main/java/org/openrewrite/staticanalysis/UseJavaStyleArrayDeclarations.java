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

import lombok.Getter;
import org.openrewrite.*;
import org.openrewrite.java.JavaIsoVisitor;
import org.openrewrite.java.tree.*;
import org.openrewrite.marker.Markers;
import org.openrewrite.staticanalysis.csharp.CSharpFileChecker;

import java.util.List;
import java.util.Set;

import static java.util.Collections.emptyList;
import static java.util.Collections.singleton;

public class UseJavaStyleArrayDeclarations extends Recipe {

    @Getter
    final String displayName = "No C-style array declarations";

    @Getter
    final String description = "Change C-Style array declarations `int i[];` to `int[] i;`.";

    @Override
    public Set<String> getTags() {
        return singleton("RSPEC-S1197");
    }

    @Override
    public TreeVisitor<?, ExecutionContext> getVisitor() {
        return Preconditions.check(Preconditions.not(new CSharpFileChecker<>()), new JavaIsoVisitor<ExecutionContext>() {
            @Override
            public J.VariableDeclarations visitVariableDeclarations(J.VariableDeclarations multiVariable, ExecutionContext ctx) {
                J.VariableDeclarations varDecls = super.visitVariableDeclarations(multiVariable, ctx);
                List<JLeftPadded<Space>> dimensions = getCursor().pollMessage("VAR_DIMENSIONS");
                if (dimensions != null && varDecls.getTypeExpression() != null) {
                    // Build array type by wrapping the type expression
                    TypeTree typeExpression = varDecls.getTypeExpression();
                    JavaType type = varDecls.getType();
                    for (JLeftPadded<Space> dim : dimensions) {
                        type = new JavaType.Array(null, type, null);
                        typeExpression = new J.ArrayType(
                                Tree.randomId(),
                                Space.EMPTY,
                                Markers.EMPTY,
                                typeExpression,
                                null,
                                dim,
                                type
                        );
                    }
                    varDecls = varDecls.withTypeExpression(typeExpression);
                }
                return varDecls;
            }

            @Override
            public J.VariableDeclarations.NamedVariable visitVariable(J.VariableDeclarations.NamedVariable variable, ExecutionContext ctx) {
                J.VariableDeclarations.NamedVariable nv = super.visitVariable(variable, ctx);
                if (!nv.getDimensionsAfterName().isEmpty()) {
                    getCursor().dropParentUntil(J.VariableDeclarations.class::isInstance).putMessage("VAR_DIMENSIONS", nv.getDimensionsAfterName());
                    nv = nv.withDimensionsAfterName(emptyList());
                }
                return nv;
            }
        });
    }
}
