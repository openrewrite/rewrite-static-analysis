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
import org.openrewrite.internal.ListUtils;
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
    final String description = "Change C-Style array declarations `int i[];` to " +
            "`int[] i;`. Keeping the brackets with the type groups all type " +
            "information in one place, so readers do not have to inspect both the " +
            "type and the variable name to determine whether something is an array.";

    @Getter
    final Set<String> tags = singleton("RSPEC-S1197");

    @Override
    public TreeVisitor<?, ExecutionContext> getVisitor() {
        return Preconditions.check(Preconditions.not(new CSharpFileChecker<>()), new JavaIsoVisitor<ExecutionContext>() {
            @Override
            public J.VariableDeclarations visitVariableDeclarations(J.VariableDeclarations multiVariable, ExecutionContext ctx) {
                J.VariableDeclarations varDecls = super.visitVariableDeclarations(multiVariable, ctx);
                if (varDecls.getTypeExpression() == null || varDecls.getVariables().isEmpty()) {
                    return varDecls;
                }
                List<JLeftPadded<Space>> dimensions = varDecls.getVariables().get(0).getDimensionsAfterName();
                if (dimensions.isEmpty() ||
                    varDecls.getVariables().stream().anyMatch(nv -> nv.getDimensionsAfterName().size() != dimensions.size())) {
                    return varDecls;
                }
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
                return varDecls
                        .withTypeExpression(typeExpression)
                        .withVariables(ListUtils.map(varDecls.getVariables(), nv -> nv.withDimensionsAfterName(emptyList())));
            }
        });
    }
}
