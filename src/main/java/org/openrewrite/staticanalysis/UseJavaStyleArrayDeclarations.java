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

import org.openrewrite.ExecutionContext;
import org.openrewrite.Preconditions;
import org.openrewrite.Recipe;
import org.openrewrite.TreeVisitor;
import org.openrewrite.internal.ListUtils;
import org.openrewrite.java.JavaIsoVisitor;
import org.openrewrite.java.tree.J.VariableDeclarations;
import org.openrewrite.java.tree.JLeftPadded;
import org.openrewrite.java.tree.Space;
import org.openrewrite.staticanalysis.csharp.CSharpFileChecker;

import java.util.Collections;
import java.util.List;
import java.util.Set;

public class UseJavaStyleArrayDeclarations extends Recipe {

    @Override
    public String getDisplayName() {
        return "No C-style array declarations";
    }

    @Override
    public String getDescription() {
        return "Change C-Style array declarations `int i[];` to `int[] i;`.";
    }

    @Override
    public Set<String> getTags() {
        return Collections.singleton("RSPEC-S1197");
    }

    @Override
    public TreeVisitor<?, ExecutionContext> getVisitor() {
        return Preconditions.check(Preconditions.not(new CSharpFileChecker<>()), new JavaIsoVisitor<ExecutionContext>() {
            @Override
            public VariableDeclarations visitVariableDeclarations(VariableDeclarations multiVariable, ExecutionContext ctx) {
                VariableDeclarations varDecls = super.visitVariableDeclarations(multiVariable, ctx);
                List<JLeftPadded<Space>> dimensions = getCursor().pollMessage("VAR_DIMENSIONS");
                if (dimensions != null) {
                    varDecls = varDecls.withDimensionsBeforeName(dimensions);
                }
                return varDecls;
            }

            @Override
            public VariableDeclarations.NamedVariable visitVariable(VariableDeclarations.NamedVariable variable, ExecutionContext ctx) {
                VariableDeclarations.NamedVariable nv = super.visitVariable(variable, ctx);
                if (!nv.getDimensionsAfterName().isEmpty()) {
                    getCursor().dropParentUntil(VariableDeclarations.class::isInstance).putMessage("VAR_DIMENSIONS", nv.getDimensionsAfterName());
                    nv = nv.withDimensionsAfterName(ListUtils.map(nv.getDimensionsAfterName(), dim -> null));
                }
                return nv;
            }
        });
    }
}
