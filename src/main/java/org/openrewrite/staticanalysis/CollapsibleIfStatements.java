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
import org.openrewrite.ExecutionContext;
import org.openrewrite.Recipe;
import org.openrewrite.Tree;
import org.openrewrite.TreeVisitor;
import org.openrewrite.java.JavaVisitor;
import org.openrewrite.java.tree.*;
import org.openrewrite.marker.Markers;

import java.time.Duration;
import java.util.Set;

import static java.util.Collections.singleton;

@Getter
public class CollapsibleIfStatements extends Recipe {

    final String displayName = "Mergeable \"if\" statements should be combined";

    final String description = "When an `if` statement body contains only another `if` with no `else`, " +
            "the two conditions can be combined with `&&`. " +
            "Merging the conditions reduces nesting and makes the code easier to read.";

    final Set<String> tags = singleton("RSPEC-S1066");

    final Duration estimatedEffortPerOccurrence = Duration.ofMinutes(5);

    @Override
    public TreeVisitor<?, ExecutionContext> getVisitor() {
        return new JavaVisitor<ExecutionContext>() {

            @Override
            public J visitIf(J.If if_, ExecutionContext ctx) {
                J.If outerIf = (J.If) super.visitIf(if_, ctx);

                if (outerIf.getElsePart() != null) {
                    return outerIf;
                }

                if (!(outerIf.getThenPart() instanceof J.Block)) {
                    return outerIf;
                }
                J.Block block = (J.Block) outerIf.getThenPart();
                if (block.getStatements().size() != 1) {
                    return outerIf;
                }

                Statement onlyStatement = block.getStatements().get(0);
                if (!(onlyStatement instanceof J.If)) {
                    return outerIf;
                }
                J.If innerIf = (J.If) onlyStatement;
                if (innerIf.getElsePart() != null) {
                    return outerIf;
                }

                Expression outerCond = outerIf.getIfCondition().getTree();
                Expression innerCond = innerIf.getIfCondition().getTree();

                if (outerCond instanceof J.Binary && ((J.Binary) outerCond).getOperator() == J.Binary.Type.Or) {
                    outerCond = wrapInParens(outerCond);
                }
                if (innerCond instanceof J.Binary && ((J.Binary) innerCond).getOperator() == J.Binary.Type.Or) {
                    innerCond = wrapInParens(innerCond);
                }

                Expression combined = JavaElementFactory.newLogicalExpression(
                        J.Binary.Type.And,
                        outerCond,
                        innerCond.withPrefix(Space.SINGLE_SPACE)
                );

                J.If merged = outerIf
                        .withIfCondition(outerIf.getIfCondition().withTree(combined))
                        .withThenPart(innerIf.getThenPart().withPrefix(outerIf.getThenPart().getPrefix()));

                return autoFormat(merged, ctx);
            }

            private Expression wrapInParens(Expression expr) {
                return new J.Parentheses<>(
                        Tree.randomId(),
                        expr.getPrefix(),
                        Markers.EMPTY,
                        JRightPadded.build(expr.withPrefix(Space.EMPTY))
                );
            }
        };
    }
}
