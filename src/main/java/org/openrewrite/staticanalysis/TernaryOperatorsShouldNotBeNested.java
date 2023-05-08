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
import org.openrewrite.java.JavaTemplate;
import org.openrewrite.java.JavaVisitor;
import org.openrewrite.java.tree.Expression;
import org.openrewrite.java.tree.J;
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

        final JavaTemplate iffTemplate = JavaTemplate.builder(this::getCursor, "if(#{any(boolean)}) {}").build();

        @Override
        public @Nullable J visit(@Nullable final Tree tree, final ExecutionContext executionContext) {
            J result = super.visit(tree, executionContext);
            if (tree instanceof JavaSourceFile) {
                result = (J) new RemoveUnneededBlock().getVisitor().visit(result, executionContext);
            }
            return result;
        }

        @Override
        public J visitReturn(final J.Return retrn, final ExecutionContext executionContext) {
            J possiblyTernary = retrn.getExpression();
            if (possiblyTernary instanceof J.Ternary) {
                J.Ternary ternary = (J.Ternary) possiblyTernary;
                if (ternary.getFalsePart() instanceof J.Ternary || ternary.getTruePart() instanceof J.Ternary) {
                    J.If iff = retrn.withTemplate(
                            iffTemplate,
                            retrn.getCoordinates().replace(),
                            ternary.getCondition()
                    );
                    iff = iff.withThenPart(blockOf(returnOf(ternary.getTruePart())));
                    J result = blockOf(iff, returnOf(ternary.getFalsePart()));
                    return autoFormat(result, executionContext);
                }
            }
            return super.visitReturn(retrn, executionContext);
        }
    }

    private static J.Return returnOf(Expression expression) {
        return new J.Return(Tree.randomId(), Space.EMPTY, Markers.EMPTY, expression);
    }

    private static J.Block blockOf(Statement... statements) {
        return J.Block.createEmptyBlock().withStatements(Arrays.asList(statements));
    }
}
