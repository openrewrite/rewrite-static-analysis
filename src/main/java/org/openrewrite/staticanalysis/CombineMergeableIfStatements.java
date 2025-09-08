/*
 * Copyright 2025 the original author or authors.
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
import org.openrewrite.Recipe;
import org.openrewrite.TreeVisitor;
import org.openrewrite.internal.ListUtils;
import org.openrewrite.java.JavaIsoVisitor;
import org.openrewrite.java.JavaTemplate;
import org.openrewrite.java.tree.Expression;
import org.openrewrite.java.tree.J;
import org.openrewrite.java.tree.Statement;

import java.util.List;
import java.util.Set;

import static java.util.Collections.singleton;
import static org.openrewrite.java.format.ShiftFormat.indent;

public class CombineMergeableIfStatements extends Recipe {
    @Override
    public String getDisplayName() {
        // language=markdown
        return "Mergeable `if` statements should be combined";
    }

    @Override
    public String getDescription() {
        // language=markdown
        return "Mergeable `if` statements should be combined.";
    }

    @Override
    public Set<String> getTags() {
        return singleton("RSPEC-S1066");
    }

    @Override
    public TreeVisitor<?, ExecutionContext> getVisitor() {
        return new JavaIsoVisitor<ExecutionContext>() {
            @Override
            public J.If visitIf(J.If iff, ExecutionContext ctx) {
                J.If outerIf = super.visitIf(iff, ctx);

                if (outerIf.getElsePart() == null) {
                    // thenPart is either a single if or a block with a single if
                    J.Block outerBlock = null;
                    J.If innerIf = null;
                    if (outerIf.getThenPart() instanceof J.If) {
                        innerIf = (J.If) outerIf.getThenPart();
                    } else if (outerIf.getThenPart() instanceof J.Block) {
                        outerBlock = (J.Block) outerIf.getThenPart();
                        List<Statement> statements = outerBlock.getStatements();
                        if (statements.size() == 1 && statements.get(0) instanceof J.If) {
                            innerIf = (J.If) statements.get(0);
                        }
                    }

                    if (innerIf != null && innerIf.getElsePart() == null) {
                        // thenPart of outer if is replaced with thenPart of innerIf
                        // combine conditions with logical AND : correct parenthesizing is handled by JavaTemplate
                        Expression outerCondition = outerIf.getIfCondition().getTree();
                        Expression innerCondition = innerIf.getIfCondition().getTree();

                        innerIf = indent(innerIf, getCursor(), -1);
                        outerIf = outerIf.withThenPart(innerIf.getThenPart());
                        outerIf = JavaTemplate.apply(
                                "#{any()} && #{any()}",
                                updateCursor(outerIf),
                                outerCondition.getCoordinates().replace(),
                                outerCondition,
                                innerCondition);
                        outerIf = outerIf.withComments(outerBlock != null ?
                                ListUtils.concatAll(outerBlock.getComments(), innerIf.getComments()) :
                                innerIf.getComments());
                    }
                }

                return outerIf;
            }
        };
    }
}
