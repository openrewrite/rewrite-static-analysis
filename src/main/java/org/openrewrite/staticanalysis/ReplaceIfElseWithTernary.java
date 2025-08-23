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
import org.openrewrite.InMemoryExecutionContext;
import org.openrewrite.Recipe;
import org.openrewrite.TreeVisitor;
import org.openrewrite.java.JavaTemplate;
import org.openrewrite.java.JavaVisitor;
import org.openrewrite.java.tree.J;
import org.openrewrite.java.tree.Statement;

import java.util.Collections;
import java.util.Set;

public class ReplaceIfElseWithTernary extends Recipe {
    @Override
    public String getDisplayName() {
        return "Replace simple if-else statements with ternary operator";
    }

    @Override
    public String getDescription() {
        return "Replaces simple `if-else` statements with a ternary operator for improved readability. " +
               "Only applies to if-else blocks that contain a single assignment statement in each branch " +
               "where both branches assign to the same variable.";
    }

    @Override
    public Set<String> getTags() {
        return Collections.singleton("RSPEC-S3358");
    }

    @Override
    public TreeVisitor<?, ExecutionContext> getVisitor() {
        return new JavaVisitor<ExecutionContext>() {
            @Override
            public J visitIf(J.If if_, ExecutionContext ctx) {
                J.If i = (J.If) super.visitIf(if_, ctx);

                // Check if we have an else block
                if (i.getElsePart() == null || i.getElsePart().getBody() == null) {
                    return i;
                }

                // Check if both then and else parts are single statements or blocks with single statements
                Statement thenStatement = getSingleStatement(i.getThenPart());
                Statement elseStatement = getSingleStatement(i.getElsePart().getBody());

                if (thenStatement == null || elseStatement == null) {
                    return i;
                }

                // Don't convert if the then part contains another if statement (would create nested ternary)
                if (thenStatement instanceof J.If) {
                    return i;
                }

                // Check if both statements are assignments to the same variable
                if (!(thenStatement instanceof J.Assignment) || !(elseStatement instanceof J.Assignment)) {
                    return i;
                }

                J.Assignment thenAssign = (J.Assignment) thenStatement;
                J.Assignment elseAssign = (J.Assignment) elseStatement;

                // Check if they assign to the same variable
                if (!areAssignmentsToSameVariable(thenAssign, elseAssign)) {
                    return i;
                }

                // Don't convert if either assignment already contains a ternary operator
                if (containsTernary(thenAssign.getAssignment()) || containsTernary(elseAssign.getAssignment())) {
                    return i;
                }

                // Create ternary expression
                JavaTemplate template = JavaTemplate.builder("#{any()} = #{any()} ? #{any()} : #{any()}")
                        .build();

                J.Assignment result = template.apply(getCursor(), i.getCoordinates().replace(),
                        thenAssign.getVariable(),
                        i.getIfCondition().getTree(),
                        thenAssign.getAssignment(),
                        elseAssign.getAssignment());

                return result;
            }

            private Statement getSingleStatement(Statement statement) {
                if (statement instanceof J.Block) {
                    J.Block block = (J.Block) statement;
                    if (block.getStatements().size() == 1) {
                        return block.getStatements().get(0);
                    }
                } else {
                    return statement;
                }
                return null;
            }

            private boolean areAssignmentsToSameVariable(J.Assignment a1, J.Assignment a2) {
                // Check if both assignments target the same variable
                if (a1.getVariable() instanceof J.Identifier && a2.getVariable() instanceof J.Identifier) {
                    J.Identifier id1 = (J.Identifier) a1.getVariable();
                    J.Identifier id2 = (J.Identifier) a2.getVariable();
                    return id1.getSimpleName().equals(id2.getSimpleName());
                } else if (a1.getVariable() instanceof J.FieldAccess && a2.getVariable() instanceof J.FieldAccess) {
                    J.FieldAccess fa1 = (J.FieldAccess) a1.getVariable();
                    J.FieldAccess fa2 = (J.FieldAccess) a2.getVariable();
                    return fa1.toString().equals(fa2.toString());
                }
                return false;
            }

            private boolean containsTernary(J expr) {
                if (expr instanceof J.Ternary) {
                    return true;
                }

                // Check if expression contains a ternary operator
                boolean[] hasTernary = {false};
                new JavaVisitor<ExecutionContext>() {
                    @Override
                    public J visitTernary(J.Ternary ternary, ExecutionContext ctx) {
                        hasTernary[0] = true;
                        return ternary;
                    }
                }.visit(expr, new InMemoryExecutionContext());

                return hasTernary[0];
            }
        };
    }
}
