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

import lombok.EqualsAndHashCode;
import lombok.Value;
import org.jspecify.annotations.Nullable;
import org.openrewrite.*;
import org.openrewrite.internal.ListUtils;
import org.openrewrite.java.JavaIsoVisitor;
import org.openrewrite.java.style.Checkstyle;
import org.openrewrite.java.style.OperatorWrapStyle;
import org.openrewrite.java.tree.*;
import org.openrewrite.style.Style;

import java.util.List;

@EqualsAndHashCode(callSuper = false)
@Value
public class OperatorWrap extends Recipe {

    @Option(displayName = "Operator wrapping style",
            description = "The operator wrapping style to enforce, which may differ from the configured or detected style.",
            valid = {"EOL", "NL"},
            example = "NL",
            required = false)
    OperatorWrapStyle.@Nullable WrapOption wrapOption;

    String displayName = "Operator wrapping";

    String description = "Fixes line wrapping policies on operators.";

    @Override
    public TreeVisitor<?, ExecutionContext> getVisitor() {
        return new JavaIsoVisitor<ExecutionContext>() {
            OperatorWrapStyle operatorWrapStyle;

            @Override
            public J visit(@Nullable Tree tree, ExecutionContext ctx) {
                if (tree instanceof JavaSourceFile) {
                    // Kotlin treats a newline as an expression terminator, so a leading binary operator
                    // becomes a unary operator on a new statement. Only reformat Java compilation units.
                    if (!(tree instanceof J.CompilationUnit)) {
                        return (J) tree;
                    }
                    SourceFile cu = (SourceFile) tree;
                    operatorWrapStyle = Style.from(OperatorWrapStyle.class, cu, Checkstyle::operatorWrapStyle);

                    if (wrapOption != null) {
                        // Convenience override, to bypass having to configure a style once to change detected style
                        operatorWrapStyle = operatorWrapStyle.withWrapOption(wrapOption);
                    }
                }
                return super.visit(tree, ctx);
            }

            @Override
            public J.Binary visitBinary(J.Binary binary, ExecutionContext ctx) {
                J.Binary b = super.visitBinary(binary, ctx);
                J.Binary.Type op = b.getOperator();
                if ((Boolean.TRUE.equals(operatorWrapStyle.getDiv()) && op == J.Binary.Type.Division) ||
                    (Boolean.TRUE.equals(operatorWrapStyle.getStar()) && op == J.Binary.Type.Multiplication) ||
                    (Boolean.TRUE.equals(operatorWrapStyle.getPlus()) && op == J.Binary.Type.Addition) ||
                    (Boolean.TRUE.equals(operatorWrapStyle.getMinus()) && op == J.Binary.Type.Subtraction) ||
                    (Boolean.TRUE.equals(operatorWrapStyle.getMod()) && op == J.Binary.Type.Modulo) ||
                    (Boolean.TRUE.equals(operatorWrapStyle.getSr()) && op == J.Binary.Type.RightShift) ||
                    (Boolean.TRUE.equals(operatorWrapStyle.getSl()) && op == J.Binary.Type.LeftShift) ||
                    (Boolean.TRUE.equals(operatorWrapStyle.getBsr()) && op == J.Binary.Type.UnsignedRightShift) ||
                    (Boolean.TRUE.equals(operatorWrapStyle.getEqual()) && op == J.Binary.Type.Equal) ||
                    (Boolean.TRUE.equals(operatorWrapStyle.getNotEqual()) && op == J.Binary.Type.NotEqual) ||
                    (Boolean.TRUE.equals(operatorWrapStyle.getGt()) && op == J.Binary.Type.GreaterThan) ||
                    (Boolean.TRUE.equals(operatorWrapStyle.getGe()) && op == J.Binary.Type.GreaterThanOrEqual) ||
                    (Boolean.TRUE.equals(operatorWrapStyle.getLt()) && op == J.Binary.Type.LessThan) ||
                    (Boolean.TRUE.equals(operatorWrapStyle.getLe()) && op == J.Binary.Type.LessThanOrEqual) ||
                    (Boolean.TRUE.equals(operatorWrapStyle.getBand()) && op == J.Binary.Type.BitAnd) ||
                    (Boolean.TRUE.equals(operatorWrapStyle.getBxor()) && op == J.Binary.Type.BitXor) ||
                    (Boolean.TRUE.equals(operatorWrapStyle.getBor()) && op == J.Binary.Type.BitOr) ||
                    (Boolean.TRUE.equals(operatorWrapStyle.getLand()) && op == J.Binary.Type.And) ||
                    (Boolean.TRUE.equals(operatorWrapStyle.getLor()) && op == J.Binary.Type.Or)) {
                    Space[] wrapped = wrap(b.getPadding().getOperator().getBefore(), b.getRight().getPrefix(), Space.SINGLE_SPACE);
                    if (wrapped != null) {
                        b = b.getPadding().withOperator(b.getPadding().getOperator().withBefore(wrapped[0]))
                                .withRight(b.getRight().withPrefix(wrapped[1]));
                    }
                }
                return b;
            }

            @Override
            public J.TypeParameter visitTypeParameter(J.TypeParameter typeParam, ExecutionContext ctx) {
                J.TypeParameter tp = super.visitTypeParameter(typeParam, ctx);
                JContainer<TypeTree> bounds = tp.getPadding().getBounds();
                if (Boolean.TRUE.equals(operatorWrapStyle.getTypeExtensionAnd()) && bounds != null) {
                    List<JRightPadded<TypeTree>> elements = bounds.getPadding().getElements();
                    tp = tp.getPadding().withBounds(bounds.getPadding().withElements(ListUtils.map(elements, (index, bound) -> {
                        if (index > 0) {
                            Space[] wrapped = wrap(elements.get(index - 1).getAfter(), bound.getElement().getPrefix(), Space.SINGLE_SPACE);
                            if (wrapped != null) {
                                bound = bound.withElement(bound.getElement().withPrefix(wrapped[1]));
                            }
                        }
                        if (index < elements.size() - 1) {
                            Space[] wrapped = wrap(bound.getAfter(), elements.get(index + 1).getElement().getPrefix(), Space.SINGLE_SPACE);
                            if (wrapped != null) {
                                bound = bound.withAfter(wrapped[0]);
                            }
                        }
                        return bound;
                    })));
                }
                return tp;
            }

            @Override
            public J.InstanceOf visitInstanceOf(J.InstanceOf instanceOf, ExecutionContext ctx) {
                J.InstanceOf i = super.visitInstanceOf(instanceOf, ctx);
                if (Boolean.TRUE.equals(operatorWrapStyle.getLiteralInstanceof()) && i.getClazz() != null) {
                    Space[] wrapped = wrap(i.getPadding().getExpression().getAfter(), i.getClazz().getPrefix(), Space.SINGLE_SPACE);
                    if (wrapped != null) {
                        i = i.getPadding().withExpression(i.getPadding().getExpression().withAfter(wrapped[0]))
                                .withClazz(i.getClazz().withPrefix(wrapped[1]));
                    }
                }
                return i;
            }

            @Override
            public J.Ternary visitTernary(J.Ternary ternary, ExecutionContext ctx) {
                J.Ternary t = super.visitTernary(ternary, ctx);
                if (Boolean.TRUE.equals(operatorWrapStyle.getQuestion())) {
                    JLeftPadded<Expression> truePart = t.getPadding().getTruePart();
                    Space[] wrapped = wrap(truePart.getBefore(), truePart.getElement().getPrefix(), Space.SINGLE_SPACE);
                    if (wrapped != null) {
                        t = t.getPadding().withTruePart(truePart.withBefore(wrapped[0])
                                .withElement(truePart.getElement().withPrefix(wrapped[1])));
                    }
                }
                if (Boolean.TRUE.equals(operatorWrapStyle.getColon())) {
                    JLeftPadded<Expression> falsePart = t.getPadding().getFalsePart();
                    Space[] wrapped = wrap(falsePart.getBefore(), falsePart.getElement().getPrefix(), Space.SINGLE_SPACE);
                    if (wrapped != null) {
                        t = t.getPadding().withFalsePart(falsePart.withBefore(wrapped[0])
                                .withElement(falsePart.getElement().withPrefix(wrapped[1])));
                    }
                }
                return t;
            }

            @Override
            public J.MemberReference visitMemberReference(J.MemberReference memberRef, ExecutionContext ctx) {
                J.MemberReference m = super.visitMemberReference(memberRef, ctx);
                if (Boolean.TRUE.equals(operatorWrapStyle.getMethodRef())) {
                    Space[] wrapped = wrap(m.getPadding().getContaining().getAfter(), m.getPadding().getReference().getBefore(), Space.EMPTY);
                    if (wrapped != null) {
                        m = m.getPadding().withContaining(m.getPadding().getContaining().withAfter(wrapped[0]))
                                .getPadding().withReference(m.getPadding().getReference().withBefore(wrapped[1]));
                    }
                }
                return m;
            }

            @Override
            public J.Assignment visitAssignment(J.Assignment assignment, ExecutionContext ctx) {
                J.Assignment a = super.visitAssignment(assignment, ctx);
                if (Boolean.TRUE.equals(operatorWrapStyle.getAssign())) {
                    JLeftPadded<Expression> value = a.getPadding().getAssignment();
                    Space[] wrapped = wrap(value.getBefore(), value.getElement().getPrefix(), Space.SINGLE_SPACE);
                    if (wrapped != null) {
                        a = a.getPadding().withAssignment(value.withBefore(wrapped[0])
                                .withElement(value.getElement().withPrefix(wrapped[1])));
                    }
                }
                return a;
            }

            @Override
            public J.AssignmentOperation visitAssignmentOperation(J.AssignmentOperation assignOp, ExecutionContext ctx) {
                J.AssignmentOperation a = super.visitAssignmentOperation(assignOp, ctx);
                J.AssignmentOperation.Type op = a.getOperator();
                if ((Boolean.TRUE.equals(operatorWrapStyle.getPlusAssign()) && op == J.AssignmentOperation.Type.Addition) ||
                    (Boolean.TRUE.equals(operatorWrapStyle.getMinusAssign()) && op == J.AssignmentOperation.Type.Subtraction) ||
                    (Boolean.TRUE.equals(operatorWrapStyle.getStarAssign()) && op == J.AssignmentOperation.Type.Multiplication) ||
                    (Boolean.TRUE.equals(operatorWrapStyle.getDivAssign()) && op == J.AssignmentOperation.Type.Division) ||
                    (Boolean.TRUE.equals(operatorWrapStyle.getModAssign()) && op == J.AssignmentOperation.Type.Modulo) ||
                    (Boolean.TRUE.equals(operatorWrapStyle.getSrAssign()) && op == J.AssignmentOperation.Type.RightShift) ||
                    (Boolean.TRUE.equals(operatorWrapStyle.getSlAssign()) && op == J.AssignmentOperation.Type.LeftShift) ||
                    (Boolean.TRUE.equals(operatorWrapStyle.getBsrAssign()) && op == J.AssignmentOperation.Type.UnsignedRightShift) ||
                    (Boolean.TRUE.equals(operatorWrapStyle.getBandAssign()) && op == J.AssignmentOperation.Type.BitAnd) ||
                    (Boolean.TRUE.equals(operatorWrapStyle.getBxorAssign()) && op == J.AssignmentOperation.Type.BitXor) ||
                    (Boolean.TRUE.equals(operatorWrapStyle.getBorAssign()) && op == J.AssignmentOperation.Type.BitOr)) {
                    Space[] wrapped = wrap(a.getPadding().getOperator().getBefore(), a.getAssignment().getPrefix(), Space.SINGLE_SPACE);
                    if (wrapped != null) {
                        a = a.getPadding().withOperator(a.getPadding().getOperator().withBefore(wrapped[0]))
                                .withAssignment(a.getAssignment().withPrefix(wrapped[1]));
                    }
                }
                return a;
            }

            @Override
            public J.VariableDeclarations.NamedVariable visitVariable(J.VariableDeclarations.NamedVariable variable, ExecutionContext ctx) {
                J.VariableDeclarations.NamedVariable v = super.visitVariable(variable, ctx);
                JLeftPadded<Expression> initializer = v.getPadding().getInitializer();
                if (Boolean.TRUE.equals(operatorWrapStyle.getAssign()) && initializer != null) {
                    Space[] wrapped = wrap(initializer.getBefore(), initializer.getElement().getPrefix(), Space.SINGLE_SPACE);
                    if (wrapped != null) {
                        v = v.getPadding().withInitializer(initializer.withBefore(wrapped[0])
                                .withElement(initializer.getElement().withPrefix(wrapped[1])));
                    }
                }
                return v;
            }

            /// Moves the line break around an operator to the side the style asks for.
            ///
            /// Comments move along with the line break, so the side left on the operator's line must have none:
            /// a comment there stays next to the operator and the wrapping is left alone.
            ///
            /// @return the new space before and after the operator, or `null` to leave both unchanged
            private Space @Nullable [] wrap(Space beforeOperator, Space afterOperator, Space sameLine) {
                if (OperatorWrapStyle.WrapOption.NL == operatorWrapStyle.getWrapOption()) {
                    return afterOperator.getWhitespace().contains("\n") && beforeOperator.getComments().isEmpty() ?
                            new Space[]{afterOperator, sameLine} : null;
                }
                return beforeOperator.getWhitespace().contains("\n") && afterOperator.getComments().isEmpty() ?
                        new Space[]{sameLine, beforeOperator} : null;
            }
        };
    }
}
