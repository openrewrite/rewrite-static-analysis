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

import lombok.Getter;
import org.openrewrite.ExecutionContext;
import org.openrewrite.Recipe;
import org.openrewrite.Tree;
import org.openrewrite.TreeVisitor;
import org.openrewrite.internal.ListUtils;
import org.openrewrite.java.JavaIsoVisitor;
import org.openrewrite.java.search.SemanticallyEqual;
import org.openrewrite.java.tree.Expression;
import org.openrewrite.java.tree.J;
import org.openrewrite.java.tree.JLeftPadded;
import org.openrewrite.java.tree.Space;
import org.openrewrite.marker.Markers;

import java.time.Duration;

public class PreferIncrementOperator extends Recipe {

    @Getter
    final String displayName = "Prefer increment/decrement and compound assignment operators";

    @Getter
    final String description = "Prefer the use of increment and decrement operators (`++`, `--`, `+=`, `-=`) over their more verbose equivalents.";

    @Getter
    final Duration estimatedEffortPerOccurrence = Duration.ofMinutes(1);

    @Override
    public TreeVisitor<?, ExecutionContext> getVisitor() {
        return new JavaIsoVisitor<ExecutionContext>() {
            @Override
            public J.Block visitBlock(J.Block block, ExecutionContext ctx) {
                J.Block b = super.visitBlock(block, ctx);

                return b.withStatements(ListUtils.map(b.getStatements(), statement -> {
                    if (statement instanceof J.Assignment) {
                        J.Assignment assignment = (J.Assignment) statement;

                        if (assignment.getAssignment() instanceof J.Binary) {
                            Expression variable = assignment.getVariable();
                            J.Binary binary = (J.Binary) assignment.getAssignment();

                            if (binary.getOperator() == J.Binary.Type.Addition || binary.getOperator() == J.Binary.Type.Subtraction) {
                                Expression binaryLeft = binary.getLeft();

                                if (SemanticallyEqual.areEqual(variable, binaryLeft)) {
                                    Expression right = binary.getRight();

                                    if (right instanceof J.Literal) {
                                        J.Literal literal = (J.Literal) right;
                                        if (literal.getValue() instanceof Integer && (Integer) literal.getValue() == 1) {
                                            J.Unary.Type unaryType = binary.getOperator() == J.Binary.Type.Addition ?
                                                    J.Unary.Type.PostIncrement : J.Unary.Type.PostDecrement;

                                            return new J.Unary(
                                                    Tree.randomId(),
                                                    assignment.getPrefix(),
                                                    assignment.getMarkers(),
                                                    new JLeftPadded<>(Space.EMPTY, unaryType, Markers.EMPTY),
                                                    variable.withPrefix(Space.EMPTY),
                                                    assignment.getType()
                                            );
                                        }
                                    }

                                    J.AssignmentOperation.Type opType = binary.getOperator() == J.Binary.Type.Addition ?
                                            J.AssignmentOperation.Type.Addition : J.AssignmentOperation.Type.Subtraction;

                                    return new J.AssignmentOperation(
                                            Tree.randomId(),
                                            assignment.getPrefix(),
                                            assignment.getMarkers(),
                                            variable.withPrefix(Space.EMPTY),
                                            new JLeftPadded<>(Space.SINGLE_SPACE, opType, Markers.EMPTY),
                                            right.withPrefix(Space.SINGLE_SPACE),
                                            assignment.getType()
                                    );
                                }
                            }
                        }
                    }

                    return statement;
                }));
            }
        };
    }
}
