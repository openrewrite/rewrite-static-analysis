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
import org.openrewrite.java.JavaVisitor;
import org.openrewrite.java.tree.J;
import org.openrewrite.java.tree.JLeftPadded;
import org.openrewrite.java.tree.JavaType;
import org.openrewrite.java.tree.Space;
import org.openrewrite.marker.Markers;

import java.time.Duration;
import java.util.Collections;
import java.util.Set;

public class SimplifyBooleanExpressionWithDeMorgan extends Recipe {

    @Override
    public String getDisplayName() {
        return "Simplify boolean expressions using De Morgan's laws";
    }

    @Override
    public String getDescription() {
        return "Applies De Morgan's laws to simplify boolean expressions with negation. " +
                "Transforms `!(a && b)` to `!a || !b` and `!(a || b)` to `!a && !b`.";
    }

    @Override
    public Set<String> getTags() {
        return Collections.singleton("RSPEC-1125");
    }

    @Override
    public Duration getEstimatedEffortPerOccurrence() {
        return Duration.ofMinutes(2);
    }

    @Override
    public TreeVisitor<?, ExecutionContext> getVisitor() {
        return new JavaVisitor<ExecutionContext>() {

            @Override
            public J visitUnary(J.Unary unary, ExecutionContext ctx) {
                if (unary.getOperator() == J.Unary.Type.Not && unary.getExpression() instanceof J.Parentheses) {
                    J.Parentheses<?> parentheses = (J.Parentheses<?>) unary.getExpression();
                    if (parentheses.getTree() instanceof J.Binary) {
                        J.Binary binary = (J.Binary) parentheses.getTree();
                        
                        // Apply De Morgan's laws
                        if (binary.getOperator() == J.Binary.Type.And) {
                            // !(a && b) -> !a || !b
                            J.Unary leftNegated = new J.Unary(
                                    Tree.randomId(),
                                    binary.getLeft().getPrefix(),
                                    Markers.EMPTY,
                                    new JLeftPadded<>(Space.EMPTY, J.Unary.Type.Not, Markers.EMPTY),
                                    binary.getLeft().withPrefix(Space.EMPTY),
                                    JavaType.Primitive.Boolean
                            );
                            J.Unary rightNegated = new J.Unary(
                                    Tree.randomId(),
                                    Space.EMPTY,
                                    Markers.EMPTY,
                                    new JLeftPadded<>(Space.EMPTY, J.Unary.Type.Not, Markers.EMPTY),
                                    binary.getRight().withPrefix(Space.EMPTY),
                                    JavaType.Primitive.Boolean
                            );
                            
                            return new J.Binary(
                                    binary.getId(),
                                    unary.getPrefix(),
                                    unary.getMarkers(),
                                    leftNegated,
                                    JLeftPadded.build(J.Binary.Type.Or).withBefore(Space.SINGLE_SPACE),
                                    rightNegated.withPrefix(Space.SINGLE_SPACE),
                                    JavaType.Primitive.Boolean
                            );
                        } else if (binary.getOperator() == J.Binary.Type.Or) {
                            // !(a || b) -> !a && !b
                            J.Unary leftNegated = new J.Unary(
                                    Tree.randomId(),
                                    binary.getLeft().getPrefix(),
                                    Markers.EMPTY,
                                    new JLeftPadded<>(Space.EMPTY, J.Unary.Type.Not, Markers.EMPTY),
                                    binary.getLeft().withPrefix(Space.EMPTY),
                                    JavaType.Primitive.Boolean
                            );
                            J.Unary rightNegated = new J.Unary(
                                    Tree.randomId(),
                                    Space.EMPTY,
                                    Markers.EMPTY,
                                    new JLeftPadded<>(Space.EMPTY, J.Unary.Type.Not, Markers.EMPTY),
                                    binary.getRight().withPrefix(Space.EMPTY),
                                    JavaType.Primitive.Boolean
                            );
                            
                            return new J.Binary(
                                    binary.getId(),
                                    unary.getPrefix(),
                                    unary.getMarkers(),
                                    leftNegated,
                                    JLeftPadded.build(J.Binary.Type.And).withBefore(Space.SINGLE_SPACE),
                                    rightNegated.withPrefix(Space.SINGLE_SPACE),
                                    JavaType.Primitive.Boolean
                            );
                        }
                    }
                }
                return super.visitUnary(unary, ctx);
            }
        };
    }
}