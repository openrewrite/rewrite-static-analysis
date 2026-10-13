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
import org.jspecify.annotations.Nullable;
import org.openrewrite.Cursor;
import org.openrewrite.ExecutionContext;
import org.openrewrite.Recipe;
import org.openrewrite.Tree;
import org.openrewrite.TreeVisitor;
import org.openrewrite.internal.ListUtils;
import org.openrewrite.java.JavaIsoVisitor;
import org.openrewrite.java.JavaVisitor;
import org.openrewrite.java.marker.JavaVersion;
import org.openrewrite.java.tree.*;
import org.openrewrite.marker.Markers;

import java.util.*;
import java.util.function.Function;
import java.util.stream.Stream;

import static java.util.Collections.*;
import static java.util.stream.Collectors.toList;
import static org.openrewrite.Tree.randomId;
import static org.openrewrite.java.tree.J.Binary.Type.Equal;


public class TernaryOperatorsShouldNotBeNested extends Recipe {

    @Getter
    final String displayName = "Ternary operators should not be nested";

    @Getter
    final String description = "Nested ternary operators can be hard to read quickly. " +
            "Prefer simpler constructs for improved readability. If supported, this " +
            "recipe will try to replace nested ternaries with switch expressions. " +
            "Deeply nested conditional expressions obscure the branching logic and " +
            "make it easy to misread which value corresponds to which condition.";

    @Getter
    final Set<String> tags = singleton("RSPEC-S3358");

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
            if (!isNestedTernary(lambda.getBody()) || !keepsBranchValues((J.Ternary) lambda.getBody(), null)) {
                return super.visitLambda(lambda, ctx);
            }
            doAfterVisit(new RemoveUnneededBlock().getVisitor());
            Statement body = branchOf((J.Ternary) lambda.getBody(), TernaryOperatorsShouldNotBeNested::returnOf, true);
            return autoFormat(lambda.withBody(body.withPrefix(Space.SINGLE_SPACE)), ctx);
        }

        @Override
        public J visitReturn(final J.Return retrn, final ExecutionContext ctx) {
            Object scope = getCursor().dropParentUntil(p -> p instanceof J.MethodDeclaration || p instanceof J.Lambda || p == Cursor.ROOT_VALUE).getValue();
            JavaType.Method method = scope instanceof J.MethodDeclaration ? ((J.MethodDeclaration) scope).getMethodType() : null;
            if (!isNestedTernary(retrn.getExpression()) ||
                    !keepsBranchValues((J.Ternary) retrn.getExpression(), method == null ? null : method.getReturnType())) {
                return super.visitReturn(retrn, ctx);
            }
            doAfterVisit(new RemoveUnneededBlock().getVisitor());
            Statement result = branchOf((J.Ternary) retrn.getExpression(), TernaryOperatorsShouldNotBeNested::returnOf, true);
            return autoFormat(result.withPrefix(retrn.getPrefix()), ctx);
        }

        @Override
        public J visitBlock(final J.Block block, final ExecutionContext ctx) {
            J.Block b = (J.Block) super.visitBlock(block, ctx);
            Object parent = getCursor().getParentTreeCursor().getValue();
            if (parent instanceof J.ClassDeclaration || parent instanceof J.NewClass) {
                return b;
            }
            return b.withStatements(ListUtils.flatMap(b.getStatements(), statement -> {
                Statement s = statement;
                J.VariableDeclarations declaration = null;
                if (statement instanceof J.VariableDeclarations) {
                    J.VariableDeclarations declarations = (J.VariableDeclarations) statement;
                    if (declarations.getVariables().size() != 1 ||
                            declarations.getTypeExpression() == null ||
                            declarations.getTypeExpression() instanceof J.Identifier &&
                                    "var".equals(((J.Identifier) declarations.getTypeExpression()).getSimpleName()) ||
                            !isNestedTernary(declarations.getVariables().get(0).getInitializer())) {
                        return statement;
                    }
                    J.VariableDeclarations.NamedVariable variable = declarations.getVariables().get(0);
                    declaration = declarations.withVariables(singletonList(variable.withInitializer(null)));
                    s = new J.Assignment(
                            randomId(),
                            Space.format("\n"),
                            Markers.EMPTY,
                            variable.getName().withId(randomId()).withPrefix(Space.EMPTY),
                            JLeftPadded.<Expression>build(variable.getInitializer()).withBefore(Space.SINGLE_SPACE),
                            variable.getType()
                    );
                }
                Expression target;
                Expression value;
                JavaType targetType = null;
                Function<Expression, Statement> sink;
                if (s instanceof J.Assignment) {
                    J.Assignment assignment = ((J.Assignment) s).withPrefix(Space.EMPTY);
                    target = assignment.getVariable();
                    targetType = target.getType();
                    value = assignment.getAssignment();
                    sink = e -> assignment.withId(randomId()).withAssignment(e.withPrefix(Space.SINGLE_SPACE));
                } else if (s instanceof J.AssignmentOperation) {
                    J.AssignmentOperation assignment = ((J.AssignmentOperation) s).withPrefix(Space.EMPTY);
                    target = assignment.getVariable();
                    value = assignment.getAssignment();
                    sink = e -> assignment.withId(randomId()).withAssignment(e.withPrefix(Space.SINGLE_SPACE));
                } else {
                    return statement;
                }
                if (!isSimpleVariable(target) || !isNestedTernary(value) || !keepsBranchValues((J.Ternary) value, targetType)) {
                    return statement;
                }
                Statement iff = autoFormat(branchOf((J.Ternary) value, sink, false).withPrefix(s.getPrefix()), ctx, getCursor());
                return declaration == null ? iff : Arrays.asList(declaration, iff);
            }));
        }

        private static boolean isSimpleVariable(final Expression variable) {
            return variable instanceof J.Identifier ||
                    variable instanceof J.FieldAccess && ((J.FieldAccess) variable).getTarget() instanceof J.Identifier;
        }

        private static Statement branchOf(final Expression part, final Function<Expression, Statement> sink, final boolean sinkReturns) {
            if (!isNestedTernary(part)) {
                return sink.apply(part);
            }
            J.Ternary ternary = (J.Ternary) part;
            J.If iff = new J.If(
                    randomId(),
                    ternary.getPrefix(),
                    Markers.EMPTY,
                    new J.ControlParentheses<>(randomId(), Space.EMPTY, Markers.EMPTY,
                            JRightPadded.build(ternary.getCondition())
                    ).withComments(ternary.getCondition().getComments()),
                    JRightPadded.build(blockOf(branchOf(ternary.getTruePart(), sink, sinkReturns))),
                    null
            );
            Statement otherwise = branchOf(ternary.getFalsePart(), sink, sinkReturns);
            if (sinkReturns) {
                return blockOf(iff, otherwise);
            }
            return iff.withElsePart(new J.If.Else(randomId(), Space.EMPTY, Markers.EMPTY,
                    JRightPadded.build(otherwise instanceof J.If ? otherwise.withPrefix(Space.SINGLE_SPACE) : blockOf(otherwise))));
        }

        private static boolean isNestedTernary(final @Nullable J possibleTernary) {
            int result = determineNestingLevels(possibleTernary, 0);
            return result > 1;
        }

        private static int determineNestingLevels(final @Nullable J possibleTernary, final int level) {
            if (!(possibleTernary instanceof J.Ternary)) {
                return level;
            }
            J.Ternary ternary = (J.Ternary) possibleTernary;
            int truePath = determineNestingLevels(ternary.getTruePart(), level + 1);
            int falsePath = determineNestingLevels(ternary.getFalsePart(), level + 1);
            return Math.max(falsePath, truePath);
        }

    }


    static class UseSwitchExpressionVisitor extends JavaVisitor<ExecutionContext> {

        @Override
        public J visitTernary(final J.Ternary ternary, final ExecutionContext ctx) {
            return findConditionIdentifier(ternary).map(switchVar -> {
                        List<J.Ternary> nestList = findNestedTernaries(ternary, switchVar);
                        if (nestList.size() < 2 || !keepsBranchValues(ternary, null)) {
                            return null;
                        }
                        return autoFormat(toSwitch(switchVar, nestList, ternary.getType()), ctx);
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
                    return emptyList();
                }
                J.Ternary nested = (J.Ternary) next.getFalsePart();
                if (!findConditionIdentifier(nested)
                        .filter(found -> isEqualVariable(switchVar, found))
                        .isPresent()) {
                    return emptyList();
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

        private J.SwitchExpression toSwitch(final J.Identifier switchVar, final List<J.Ternary> nestList, @Nullable JavaType type) {
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
                    ).collect(toList()))
                            .withPrefix(Space.SINGLE_SPACE),
                    type
            );
        }

        private J.Case toCase(final J.Identifier switchVar, final J.Ternary ternary) {
            Expression compare;
            if (ternary.getCondition() instanceof J.MethodInvocation) {
                J.MethodInvocation inv = (J.MethodInvocation) ternary.getCondition();
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
                J.Binary bin = (J.Binary) ternary.getCondition();
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
                            singletonList(JRightPadded.<J>build(compare.withPrefix(Space.SINGLE_SPACE))
                                    .withAfter(Space.SINGLE_SPACE))
                    ),
                    JContainer.build(emptyList()),
                    JRightPadded.build(ternary.getTruePart()),
                    null
            );
        }

        private J.Case toDefault(final J.Ternary ternary) {
            return new J.Case(
                    Tree.randomId(),
                    Space.EMPTY,
                    ternary.getMarkers(),
                    J.Case.Type.Rule,
                    JContainer.build(singletonList(JRightPadded.<J>build(new J.Identifier(
                            randomId(),
                            Space.EMPTY,
                            Markers.EMPTY,
                            emptyList(),
                            "default",
                            null,
                            null
                    )).withAfter(Space.SINGLE_SPACE))),
                    JContainer.build(emptyList()),
                    JRightPadded.build(ternary.getFalsePart()),
                    null
            );
        }

        private Optional<J.Identifier> findConditionIdentifier(final J.Ternary ternary) {
            J.Identifier result = null;
            if (ternary.getCondition() instanceof J.MethodInvocation) {
                J.MethodInvocation inv = (J.MethodInvocation) ternary.getCondition();
                if (!"equals".equals(inv.getSimpleName())) {
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

    // Numeric and boolean conditionals convert every branch value to the ternary's type (JLS 15.25), splitting them loses that
    private static boolean keepsBranchValues(J.Ternary ternary, @Nullable JavaType target) {
        JavaType type = ternary.getType();
        if (type == null || type instanceof JavaType.Unknown) {
            return false;
        }
        boolean converts = type instanceof JavaType.Primitive && type != JavaType.Primitive.String && type != JavaType.Primitive.Null;
        return (!converts || TypeUtils.isOfType(type, target) ||
                TypeUtils.isOfType(ternary.getTruePart().getType(), type) && TypeUtils.isOfType(ternary.getFalsePart().getType(), type)) &&
                Stream.of(ternary.getTruePart(), ternary.getFalsePart())
                        .allMatch(part -> !(part instanceof J.Ternary) || keepsBranchValues((J.Ternary) part, target));
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
