/*
 * Copyright 2022 the original author or authors.
 * <p>
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * <p>
 * https://www.apache.org/licenses/LICENSE-2.0
 * <p>
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.openrewrite.staticanalysis;

import java.time.Duration;
import java.util.Arrays;
import java.util.Collections;
import java.util.Set;

import org.openrewrite.ExecutionContext;
import org.openrewrite.Recipe;
import org.openrewrite.Tree;
import org.openrewrite.TreeVisitor;
import org.openrewrite.internal.lang.Nullable;
import org.openrewrite.java.JavaVisitor;
import org.openrewrite.java.tree.Expression;
import org.openrewrite.java.tree.J;
import org.openrewrite.java.tree.JRightPadded;
import org.openrewrite.java.tree.JavaSourceFile;
import org.openrewrite.java.tree.Space;
import org.openrewrite.java.tree.Statement;
import org.openrewrite.marker.Markers;


public class TernaryOperatorsShouldNotBeNested extends Recipe {
    @Override
    public String getDisplayName() {
        return "Ternary operators should not be nested";
    }

    @Override
    public String getDescription() {
        return "Nested ternary operators can be hard to read quickly. Prefer simpler constructs for improved readability.";
    }

    @Override
    public Duration getEstimatedEffortPerOccurrence() {
        return Duration.ofMinutes(5);
    }

    @Override
    public Set<String> getTags() {
        return Collections.singleton("RSPEC-3358");
    }

    @Override
    public TreeVisitor<?, ExecutionContext> getVisitor() {
        return new TernaryOperatorsShouldNotBeNestedVisitor();
    }

    private static class TernaryOperatorsShouldNotBeNestedVisitor extends JavaVisitor<ExecutionContext> {

        @Override
        public @Nullable J visit(@Nullable final Tree tree, final ExecutionContext executionContext) {
            J result = super.visit(tree, executionContext);
            if (tree instanceof JavaSourceFile) {
                result = (J) new RemoveUnneededBlock().getVisitor().visit(result, executionContext);
            }
            return result;
        }

        @Override
        public J visitLambda(final J.Lambda lambda, final ExecutionContext executionContext) {
            J result = rewriteNestedTernary(lambda);
            if (result == lambda) {
                return super.visitLambda(lambda, executionContext);
            }
            return autoFormat(lambda.withBody(result.withPrefix(lambda.getBody().getPrefix())), executionContext);
        }

        @Override
        public J visitReturn(final J.Return retrn, final ExecutionContext executionContext) {
            J result = rewriteNestedTernary(retrn);
            if (result == retrn) {
                return super.visitReturn(retrn, executionContext);
            }
            return autoFormat(result, executionContext);
        }

        private Statement rewriteNestedTernary(final Statement parent) {
            J possibleTernary;
            if (parent instanceof J.Return) {
                possibleTernary = ((J.Return) parent).getExpression();
            } else if (parent instanceof J.Lambda) {
                possibleTernary = ((J.Lambda) parent).getBody();
            } else {
                return parent;
            }
            if (possibleTernary instanceof J.Ternary) {
                J.Ternary ternary = (J.Ternary) possibleTernary;
                if (!isNestedTernary(ternary)) {
                    return parent;
                }
                J.If iff = ifOf(ternary);
                J.Return otherwise = returnOf(ternary.getFalsePart());
                return blockOf(iff, rewriteNestedTernary(otherwise)).withPrefix(parent.getPrefix());
            }
            return parent;
        }


        private J.If ifOf(final J.Ternary ternary) {
            return new J.If(
                    Tree.randomId(),
                    Space.EMPTY,
                    Markers.EMPTY,
                    new J.ControlParentheses<>(Tree.randomId(), Space.EMPTY, Markers.EMPTY,
                            JRightPadded.build(ternary.getCondition())
                    ).withComments(ternary.getCondition().getComments()),
                    JRightPadded.build(blockOf(rewriteNestedTernary(returnOf(ternary.getTruePart())))),
                    null
            );
        }

        private J.Return returnOf(Expression expression) {
            return new J.Return(Tree.randomId(), Space.EMPTY, Markers.EMPTY, expression.withPrefix(Space.EMPTY))
                    .withComments(expression.getComments());
        }

        private static J.Block blockOf(Statement... statements) {
            return J.Block.createEmptyBlock().withStatements(Arrays.asList(statements));
        }

        private static boolean isNestedTernary(final J possibleTernary) {
            int result = determineNestingLevels(possibleTernary, 0);
            return result > 1;
        }

        private static int determineNestingLevels(final J possibleTernary, final int level) {
            if (!(possibleTernary instanceof J.Ternary)) {
                return level;
            }
            J.Ternary ternary = (J.Ternary) possibleTernary;
            int truePath = determineNestingLevels(ternary.getTruePart(), level + 1);
            int falsePath = determineNestingLevels(ternary.getFalsePart(), level + 1);
            return Math.max(falsePath, truePath);
        }
    }
}
