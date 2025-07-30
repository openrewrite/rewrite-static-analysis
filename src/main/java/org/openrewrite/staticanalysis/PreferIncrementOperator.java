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
import org.openrewrite.Tree;
import org.openrewrite.TreeVisitor;
import org.openrewrite.internal.ListUtils;
import org.openrewrite.java.JavaIsoVisitor;
import org.openrewrite.java.tree.*;
import org.openrewrite.marker.Markers;

import java.time.Duration;
import java.util.Set;

import static java.util.Collections.emptySet;

public class PreferIncrementOperator extends Recipe {

    @Override
    public String getDisplayName() {
        return "Prefer increment and decrement operators";
    }

    @Override
    public String getDescription() {
        return "Prefer the use of increment and decrement operators (`++` and `--`) over their more verbose equivalents.";
    }

    @Override
    public Set<String> getTags() {
        return emptySet();
    }

    @Override
    public Duration getEstimatedEffortPerOccurrence() {
        return Duration.ofMinutes(1);
    }

    @Override
    public TreeVisitor<?, ExecutionContext> getVisitor() {
        return new JavaIsoVisitor<ExecutionContext>() {
            @Override
            public J.Block visitBlock(J.Block block, ExecutionContext ctx) {
                J.Block b = super.visitBlock(block, ctx);

                return b.withStatements(ListUtils.map(b.getStatements(), statement -> {
                    if (statement instanceof J.Assignment) {
                        J.Assignment assignment = (J.Assignment) statement;

                        if (assignment.getVariable() instanceof J.Identifier && assignment.getAssignment() instanceof J.Binary) {
                            J.Identifier variable = (J.Identifier) assignment.getVariable();
                            J.Binary binary = (J.Binary) assignment.getAssignment();

                            if (binary.getOperator() == J.Binary.Type.Addition || binary.getOperator() == J.Binary.Type.Subtraction) {
                                if (binary.getLeft() instanceof J.Identifier) {
                                    J.Identifier binaryLeft = (J.Identifier) binary.getLeft();
                                    if (variable.getSimpleName().equals(binaryLeft.getSimpleName()) &&
                                        TypeUtils.isOfType(variable.getType(), binaryLeft.getType())) {

                                        if (binary.getRight() instanceof J.Literal) {
                                            J.Literal literal = (J.Literal) binary.getRight();
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
                                    }
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
