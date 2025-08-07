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
import org.openrewrite.java.JavaTemplate;
import org.openrewrite.java.JavaVisitor;
import org.openrewrite.java.cleanup.SimplifyBooleanExpressionVisitor;
import org.openrewrite.java.tree.J;
import org.openrewrite.java.tree.Statement;

import java.util.List;

public class MoveConditionsToWhile extends Recipe {

    @Override
    public String getDisplayName() {
        return "Convert `while (true)` with initial `if` break to loop condition";
    }

    @Override
    public String getDescription() {
        return "Simplifies `while (true)` loops where the first statement is an `if` statement that only contains a `break`. " +
                "The condition is inverted and moved to the loop condition for better readability.";
    }

    @Override
    public TreeVisitor<?, ExecutionContext> getVisitor() {
        return new JavaVisitor<ExecutionContext>() {
            @Override
            public J visitWhileLoop(J.WhileLoop whileLoop, ExecutionContext ctx) {
                J.WhileLoop wl = (J.WhileLoop) super.visitWhileLoop(whileLoop, ctx);

                if (!(wl.getCondition().getTree() instanceof J.Literal) ||
                        !J.Literal.isLiteralValue(wl.getCondition().getTree(), true) ||
                        !(wl.getBody() instanceof J.Block)) {
                    return wl;
                }

                List<Statement> statements = ((J.Block) wl.getBody()).getStatements();
                if (statements.isEmpty() || !(statements.get(0) instanceof J.If)) {
                    return wl;
                }

                J.If ifStatement = (J.If) statements.get(0);
                if (ifStatement.getElsePart() != null) {
                    // Actually in some cases it would be safe to proceed, but let's skip for now. Can be amended later.
                    return wl;
                }

                Statement thenBody = ifStatement.getThenPart();
                final J.Break breakStatement;
                if (thenBody instanceof J.Block) {
                    J.Block thenBlock = (J.Block) thenBody;
                    if (thenBlock.getStatements().size() != 1 || !(thenBlock.getStatements().get(0) instanceof J.Break)) {
                        return wl;
                    }
                    breakStatement = (J.Break) thenBlock.getStatements().get(0);
                } else if (thenBody instanceof J.Break) {
                    breakStatement = (J.Break) thenBody;
                } else {
                    return wl;
                }

                // Check that the break has no label
                if (breakStatement.getLabel() != null) {
                    return wl;
                }

                doAfterVisit(new SimplifyBooleanExpressionVisitor());
                return JavaTemplate.apply(
                        "while (!(#{any()})) #{}",
                        getCursor(),
                        wl.getCoordinates().replace(),
                        ifStatement.getIfCondition().getTree(),
                        ((J.Block) wl.getBody()).withStatements(statements.subList(1, statements.size())));
            }
        };
    }
}
