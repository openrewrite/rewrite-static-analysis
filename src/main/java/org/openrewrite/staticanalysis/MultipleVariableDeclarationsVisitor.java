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
import org.openrewrite.internal.ListUtils;
import org.openrewrite.java.JavaIsoVisitor;
import org.openrewrite.java.tree.J;
import org.openrewrite.java.tree.JLeftPadded;
import org.openrewrite.java.tree.JRightPadded;
import org.openrewrite.java.tree.Space;
import org.openrewrite.marker.Markers;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import static java.util.Collections.emptyList;
import static org.openrewrite.Tree.randomId;

public class MultipleVariableDeclarationsVisitor extends JavaIsoVisitor<ExecutionContext> {
    @Override
    public J.Block visitBlock(J.Block block, ExecutionContext ctx) {
        J.Block b = super.visitBlock(block, ctx);

        return b.withStatements(ListUtils.flatMap(b.getStatements(), statement -> {
            if (!(statement instanceof J.VariableDeclarations)) {
                return statement;
            }
            J.VariableDeclarations mv = (J.VariableDeclarations) statement;
            if (mv.getVariables().size() <= 1) {
                return mv;
            }

            List<J.VariableDeclarations> newDecls = new ArrayList<>(mv.getVariables().size());
            for (int i = 0; i < mv.getVariables().size(); i++) {
                J.VariableDeclarations.NamedVariable nv = mv.getVariables().get(i);
                List<JLeftPadded<Space>> dimensions = ListUtils.concatAll(mv.getDimensionsBeforeName(), nv.getDimensionsAfterName());
                nv = nv.withDimensionsAfterName(emptyList()).withPrefix(Space.EMPTY);
                J.VariableDeclarations vd = new J.VariableDeclarations(
                        randomId(),
                        Space.EMPTY,
                        Markers.EMPTY,
                        mv.getLeadingAnnotations(),
                        mv.getModifiers(),
                        mv.getTypeExpression(),
                        mv.getVarargs(),
                        dimensions,
                        Collections.singletonList(JRightPadded.build(nv))
                );
                if (i == 0) {
                    vd = vd.withComments(mv.getComments()).withPrefix(mv.getPrefix());
                }
                vd = autoFormat(vd, ctx, getCursor());
                newDecls.add(vd);
            }
            return newDecls;
        }));
    }
}
