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

import org.jspecify.annotations.Nullable;
import org.openrewrite.ExecutionContext;
import org.openrewrite.Recipe;
import org.openrewrite.Tree;
import org.openrewrite.TreeVisitor;
import org.openrewrite.java.JavaIsoVisitor;
import org.openrewrite.java.JavaVisitor;
import org.openrewrite.java.marker.JavaVersion;
import org.openrewrite.java.tree.*;
import org.openrewrite.marker.Markers;

import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static org.openrewrite.Tree.randomId;
import static org.openrewrite.java.tree.J.Binary.Type.Equal;


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
    public Set<String> getTags() {
        return Collections.singleton("RSPEC-S3358");
    }

    @Override
    public TreeVisitor<?, ExecutionContext> getVisitor() {
        return new JavaIsoVisitor<ExecutionContext>() {
            @Override
            public J.CompilationUnit visitCompilationUnit(
                    final J.CompilationUnit cu,
                    final ExecutionContext ctx
            ) {
                if (cu.getMarkers()
                        .findFirst(JavaVersion.class)
                        .filter(javaVersion -> javaVersion.getMajorVersion() >= 14)
                        .isPresent()) {
                    doAfterVisit(new UseSwitchExpressionVisitor());
                }
                doAfterVisit(new UseIfVisitor());
                return cu;
            }
        };
    }

    private static class UseIfVisitor extends JavaVisitor<ExecutionContext> {

        @Override
        public J visitLambda(final J.Lambda lambda, final ExecutionContext ctx) {
            J result = rewriteNestedTernary(lambda);
            if (result == lambda) {
                return super.visitLambda(lambda, ctx);
            }
            doAfterVisit(new RemoveUnneededBlock().getVisitor());
            return autoFormat(lambda.withBody(result.withPrefix(Space.SINGLE_SPACE)), ctx);
        }

        @Override
        public J visitReturn(final J.Return retrn, final ExecutionContext ctx) {
            J result = rewriteNestedTernary(retrn);
            if (result == retrn) {
                return super.visitReturn(retrn, ctx);
            }
            doAfterVisit(new RemoveUnneededBlock().getVisitor());
            return autoFormat(result, ctx);
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
                    ternary.getPrefix(),
                    Markers.EMPTY,
                    new J.ControlParentheses<>(Tree.randomId(), Space.EMPTY, Markers.EMPTY,
                            JRightPadded.build(ternary.getCondition())
                    ).withComments(ternary.getCondition().getComments()),
                    JRightPadded.build(blockOf(rewriteNestedTernary(returnOf(ternary.getTruePart()
                            .withComments(ternary.getTruePart().getComments()))))),
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


    static class UseSwitchExpressionVisitor extends JavaVisitor<ExecutionContext> {

        @Override
        public J visitTernary(final J.Ternary ternary, final ExecutionContext ctx) {
            return findConditionIdentifier(ternary).map(switchVar -> {
                        List<J.Ternary> nestList = findNestedTernaries(ternary, switchVar);
                        if (nestList.size() < 2) {
                            return null;
                        }
                        return autoFormat(toSwitch(switchVar, nestList), ctx);
                    }).map(J.class::cast)
                    .orElseGet(() -> super.visitTernary(ternary, ctx));
        }

        private List<J.Ternary> findNestedTernaries(final J.Ternary ternary, final J.Identifier switchVar) {
            List<J.Ternary> nestList = new ArrayList<>();
            J.Ternary next = ternary;
            while (next.getFalsePart() instanceof J.Ternary) {
                if (next.getTruePart() instanceof J.Ternary) {
                    //as long as we do not use pattern matching, an "and" nested ternary will never work for a switch:
                    // Example: a equals a and a equals b will never be true
                    return Collections.emptyList();
                }
                J.Ternary nested = (J.Ternary) next.getFalsePart();
                if (!findConditionIdentifier(nested)
                        .filter(found -> isEqualVariable(switchVar, found))
                        .isPresent()) {
                    return Collections.emptyList();
                }
                nestList.add(next);
                next = nested;
            }
            nestList.add(next);
            return nestList;
        }

        private static boolean isEqualVariable(final J.Identifier switchVar, @Nullable final J found) {
            if (!(found instanceof J.Identifier)) {
                return false;
            }
            J.Identifier foundVar = (J.Identifier) found;
            return Objects.equals(foundVar.getFieldType(), switchVar.getFieldType());
        }

        private J.SwitchExpression toSwitch(final J.Identifier switchVar, final List<J.Ternary> nestList) {
            J.Ternary last = nestList.get(nestList.size() - 1);
            return new J.SwitchExpression(
                    Tree.randomId(),
                    Space.SINGLE_SPACE,
                    Markers.EMPTY,
                    new J.ControlParentheses<>(
                            Tree.randomId(),
                            switchVar.getPrefix().withWhitespace(" "),
                            switchVar.getMarkers(),
                            JRightPadded.build(switchVar.withPrefix(Space.EMPTY))
                    ),
                    blockOf(Stream.concat(
                            nestList.stream().map(ternary -> toCase(switchVar, ternary)),
                            Stream.of(toDefault(last))
                    ).collect(Collectors.toList()))
                            .withPrefix(Space.SINGLE_SPACE)
            );
        }

        private J.Case toCase(final J.Identifier switchVar, final J.Ternary ternary) {
            Expression compare;
            if (ternary.getCondition() instanceof J.MethodInvocation) {
                J.MethodInvocation inv = ((J.MethodInvocation) ternary.getCondition());
                if (isObjectsEquals(inv)) {
                    maybeRemoveImport("java.util.Objects");
                    compare = isVariable(inv.getArguments().get(0)) ?
                            inv.getArguments().get(1) :
                            inv.getArguments().get(0);
                } else {
                    compare = isEqualVariable(switchVar, inv.getSelect()) ?
                            inv.getArguments().get(0) :
                            inv.getSelect();
                }
            } else if (isEqualsBinary(ternary.getCondition())) {
                J.Binary bin = ((J.Binary) ternary.getCondition());
                compare = isEqualVariable(switchVar, bin.getLeft()) ?
                        bin.getRight() :
                        bin.getLeft();
            } else {
                throw new IllegalArgumentException(
                        "Only J.Binary or J.MethodInvocation are expected as ternary conditions when creating a switch case");
            }
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
            J.Identifier result = null;
            if (ternary.getCondition() instanceof J.MethodInvocation) {
                J.MethodInvocation inv = (J.MethodInvocation) ternary.getCondition();
                if (!inv.getSimpleName().equals("equals")) {
                    return Optional.empty();
                }
                if (inv.getArguments().size() == 1) {
                    J other = null;
                    if (isVariable(inv.getSelect())) {
                        result = (J.Identifier) inv.getSelect();
                        other = inv.getArguments().get(0);
                    }
                    if (inv.getArguments().get(0) instanceof J.Identifier) {
                        result = (J.Identifier) inv.getArguments().get(0);
                        other = inv.getSelect();
                    }
                    if (!isConstant(other)) {
                        return Optional.empty();
                    }
                }
            } else if (isEqualsBinary(ternary.getCondition())) {
                J.Binary bin = (J.Binary) ternary.getCondition();
                result = xorVariable(bin.getLeft(), bin.getRight());
            }
            return Optional.ofNullable(result);

        }

        private static J.@Nullable Identifier xorVariable(J first, J second) {
            J.Identifier result = null;
            if (isVariable(first) && isVariable(second)) {
                return null;
            }
            if (isVariable(first)) {
                result = (J.Identifier) first;
            }
            if (isVariable(second)) {
                result = (J.Identifier) second;
            }
            return result;
        }

        private static boolean isVariable(@Nullable J maybeVariable) {
            if (maybeVariable == null) {
                return false;
            }
            if (!(maybeVariable instanceof J.Identifier)) {
                return false;
            }
            J.Identifier identifier = (J.Identifier) maybeVariable;
            if (identifier.getFieldType() == null) {
                return false;
            }
            return !identifier.getFieldType().hasFlags(Flag.Final) || !identifier.getFieldType().hasFlags(Flag.Static);
        }

        private static boolean isConstant(@Nullable J maybeConstant) {
            if (maybeConstant == null) {
                return false;
            }
            if (maybeConstant instanceof J.Literal) {
                return true;
            }
            if (!(maybeConstant instanceof J.Identifier)) {
                return false;
            }
            J.Identifier identifier = (J.Identifier) maybeConstant;
            if (identifier.getFieldType() == null) {
                return false;
            }
            return !identifier.getFieldType().hasFlags(Flag.Final) || !identifier.getFieldType().hasFlags(Flag.Static);
        }

        private static boolean isObjectsEquals(J.MethodInvocation inv) {
            if (inv.getSelect() instanceof J.Identifier) {
                J.Identifier maybeObjects = (J.Identifier) inv.getSelect();
                boolean isObjects = TypeUtils.isOfClassType(maybeObjects.getType(), "java.util.Objects");
                return isObjects && "equals".equals(inv.getSimpleName());
            }
            return false;
        }

        private static boolean isEqualsBinary(J maybeEqualsBinary) {
            return maybeEqualsBinary instanceof J.Binary && ((J.Binary) maybeEqualsBinary).getOperator() == Equal;
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
