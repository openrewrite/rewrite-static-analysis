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

import static org.openrewrite.Tree.randomId;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.openrewrite.ExecutionContext;
import org.openrewrite.Recipe;
import org.openrewrite.Tree;
import org.openrewrite.TreeVisitor;
import org.openrewrite.java.JavaIsoVisitor;
import org.openrewrite.java.JavaVisitor;
import org.openrewrite.java.format.AutoFormat;
import org.openrewrite.java.marker.JavaVersion;
import org.openrewrite.java.tree.Expression;
import org.openrewrite.java.tree.J;
import org.openrewrite.java.tree.JContainer;
import org.openrewrite.java.tree.JRightPadded;
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
        return "Nested ternary operators can be hard to read quickly. Prefer simpler constructs for improved readability. " +
                "If supported, this recipe will try to replace nested ternaries with switch expressions.";
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
        return new JavaIsoVisitor<ExecutionContext>() {
            @Override
            public J.CompilationUnit visitCompilationUnit(
                    final J.CompilationUnit cu,
                    final ExecutionContext executionContext
            ) {
                if (cu.getMarkers()
                        .findFirst(JavaVersion.class)
                        .filter(javaVersion -> javaVersion.getMajorVersion() >= 14)
                        .isPresent()) {
                    doAfterVisit(new NestedTernaryToSwitchExpressionVisitor());
                }
                doAfterVisit(new NestedTernaryToIfVisitor());
                return super.visitCompilationUnit(cu, executionContext);
            }
        };
    }

    private static class NestedTernaryToIfVisitor extends JavaVisitor<ExecutionContext> {

        @Override
        public J visitLambda(final J.Lambda lambda, final ExecutionContext executionContext) {
            J result = rewriteNestedTernary(lambda);
            if (result == lambda) {
                return super.visitLambda(lambda, executionContext);
            }
            doAfterVisit(new RemoveUnneededBlock());
            return autoFormat(lambda.withBody(result.withPrefix(lambda.getBody().getPrefix())), executionContext);
        }

        @Override
        public J visitReturn(final J.Return retrn, final ExecutionContext executionContext) {
            J result = rewriteNestedTernary(retrn);
            if (result == retrn) {
                return super.visitReturn(retrn, executionContext);
            }
            doAfterVisit(new RemoveUnneededBlock());
            return autoFormat(result, executionContext);
        }

        private Statement rewriteNestedTernary(final Statement parent) {
            return findTernary(parent).map(ternary -> {
                if (!isNestedTernary(ternary)) {
                    return parent;
                }
                J.If iff = ifOf(ternary);
                J.Return otherwise = returnOf(ternary.getFalsePart());
                return blockOf(iff, rewriteNestedTernary(otherwise)).withPrefix(parent.getPrefix());
            }).orElse(parent);
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

        private static Optional<J.Ternary> findTernary(Statement parent) {
            J possibleTernary = parent;
            if (parent instanceof J.Return) {
                possibleTernary = ((J.Return) parent).getExpression();
            } else if (parent instanceof J.Lambda) {
                possibleTernary = ((J.Lambda) parent).getBody();
            }
            if (possibleTernary instanceof J.Ternary) {
                return Optional.of(possibleTernary).map(J.Ternary.class::cast);
            }
            return Optional.empty();
        }

    }


    static class NestedTernaryToSwitchExpressionVisitor extends JavaVisitor<ExecutionContext> {

        @Override
        public J visitTernary(final J.Ternary ternary, final ExecutionContext executionContext) {
            return findConditionIdentifier(ternary).map(switchVar -> {
                        List<J.Ternary> nestList = new ArrayList<>();
                        J.Ternary next = ternary;
                        while (next.getFalsePart() instanceof J.Ternary) {
                            if (next.getTruePart() instanceof J.Ternary) {
                                return null;
                            }
                            if (!findConditionIdentifier(next).filter(found -> found.equals(switchVar)).isPresent()) {
                                return null;
                            }
                            J.Ternary nested = (J.Ternary) next.getFalsePart();
                            nestList.add(next);
                            next = nested;
                        }
                        nestList.add(next);
                        if (nestList.size() < 2) {
                            return null;
                        }
                        return autoFormat(toSwitch(switchVar, nestList), executionContext);
                    }).map(J.class::cast)
                    .orElseGet(() -> super.visitTernary(ternary, executionContext));
        }

        private J.SwitchExpression toSwitch(final J.Identifier switchVar, final List<J.Ternary> nestList) {
            J first = nestList.get(0);
            J.Ternary last = nestList.get(nestList.size() - 1);
            return new J.SwitchExpression(
                    Tree.randomId(),
                    first.getPrefix(),
                    first.getMarkers(),
                    new J.ControlParentheses<>(
                            Tree.randomId(),
                            switchVar.getPrefix().withWhitespace(" "),
                            switchVar.getMarkers(),
                            JRightPadded.build(switchVar)
                    ),
                    blockOf(Stream.concat(
                            nestList.stream().map(ternary -> toCase(switchVar, ternary)),
                            Stream.of(toDefault(last))
                    ).collect(Collectors.toList()))
                            .withPrefix(Space.SINGLE_SPACE)
            );
        }

        private J.Case toCase(final J.Identifier switchVar, final J.Ternary ternary) {
            //todo could be something else
            J.MethodInvocation inv = ((J.MethodInvocation) ternary.getCondition());
            Expression compare = inv.getSelect() == switchVar ? inv.getArguments().get(0) : inv.getSelect();
            return new J.Case(
                    Tree.randomId(),
                    ternary.getPrefix().withWhitespace(" "),
                    ternary.getMarkers(),
                    J.Case.Type.Rule,
                    JContainer.build(
                            Collections.singletonList(JRightPadded.<Expression>build(compare.withPrefix(Space.SINGLE_SPACE))
                                    .withAfter(Space.SINGLE_SPACE))
                    ),
                    JContainer.build(Collections.emptyList()),
                    JRightPadded.build(ternary.getTruePart())
            );
        }

        private J.Case toDefault(final J.Ternary ternary) {
            return new J.Case(
                    Tree.randomId(),
                    Space.EMPTY,
                    ternary.getMarkers(),
                    J.Case.Type.Rule,
                    JContainer.build(Collections.singletonList(JRightPadded.<Expression>build(new J.Identifier(
                            randomId(),
                            Space.EMPTY,
                            Markers.EMPTY,
                            "default",
                            null,
                            null
                    )).withAfter(Space.SINGLE_SPACE))),
                    JContainer.build(Collections.emptyList()),
                    JRightPadded.build(ternary.getFalsePart())
            );
        }

        private Optional<J.Identifier> findConditionIdentifier(final J.Ternary ternary) {
            if (!(ternary.getCondition() instanceof J.MethodInvocation)) {
                return Optional.empty();
            }
            J.MethodInvocation inv = (J.MethodInvocation) ternary.getCondition();
            //todo get a if inv is ~like~ "a".equals(a) or a.equals("a") or Object.equals(a,"a") or Object.equals("a",a)
            if (!inv.getSimpleName().equals("equals")) {
                return Optional.empty();
            }
            J.Identifier result = null;
            if (inv.getSelect() instanceof J.Identifier) {
                result = (J.Identifier) inv.getSelect();
            }
            if (inv.getArguments().size() == 1 && inv.getArguments().get(0) instanceof J.Identifier) {
                result = (J.Identifier) inv.getArguments().get(0);
            }
            return Optional.ofNullable(result);
        }
    }

    private static J.Return returnOf(Expression expression) {
        return new J.Return(Tree.randomId(), Space.EMPTY, Markers.EMPTY, expression.withPrefix(Space.EMPTY))
                .withComments(expression.getComments());
    }

    private static J.Block blockOf(Statement... statements) {
        return blockOf(Arrays.asList(statements));
    }

    private static J.Block blockOf(List<Statement> statements) {
        return J.Block.createEmptyBlock().withStatements(statements);
    }

}
