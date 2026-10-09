/*
 * Copyright 2026 the original author or authors.
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
import org.openrewrite.Preconditions;
import org.openrewrite.Recipe;
import org.openrewrite.TreeVisitor;
import org.openrewrite.internal.ListUtils;
import org.openrewrite.java.JavaVisitor;
import org.openrewrite.java.tree.*;
import org.openrewrite.marker.Markers;
import org.openrewrite.staticanalysis.java.JavaFileChecker;

import java.time.Duration;
import java.util.*;

import static java.util.Collections.emptyList;
import static java.util.Collections.singleton;
import static java.util.Collections.singletonList;
import static java.util.stream.Collectors.toList;
import static org.openrewrite.Tree.randomId;

@Getter
public class RemoveUnnecessaryBitwiseOperation extends Recipe {

    final String displayName = "Unnecessary bit operations should not be performed";

    final String description = "Remove bitwise operations that always yield their other operand: " +
            "`x & -1`, `x | 0` and `x ^ 0` are all just `x`. Compound assignments such as `x |= 0` are " +
            "replaced by `x` where their value is used, and removed where they stand alone as a statement.";

    final Set<String> tags = singleton("RSPEC-S2437");

    final Duration estimatedEffortPerOccurrence = Duration.ofMinutes(5);

    @Override
    public TreeVisitor<?, ExecutionContext> getVisitor() {
        return Preconditions.check(new JavaFileChecker<>(), new JavaVisitor<ExecutionContext>() {
            // Comments that trailed a removed operand, keyed by the padding they now belong to
            final Map<Object, Space> trailing = new IdentityHashMap<>();

            @Override
            public <T> @Nullable JRightPadded<T> visitRightPadded(@Nullable JRightPadded<T> right, JRightPadded.Location loc, ExecutionContext ctx) {
                JRightPadded<T> r = super.visitRightPadded(right, loc, ctx);
                Space moved = trailing.remove(right);
                return r == null || moved == null ? r : r.withAfter(attach(moved, r.getAfter()));
            }

            @Override
            public <T> @Nullable JLeftPadded<T> visitLeftPadded(@Nullable JLeftPadded<T> left, JLeftPadded.Location loc, ExecutionContext ctx) {
                JLeftPadded<T> l = super.visitLeftPadded(left, loc, ctx);
                Space moved = trailing.remove(left);
                return l == null || moved == null ? l : l.withBefore(attach(moved, l.getBefore()));
            }

            @Override
            public J visitBinary(J.Binary binary, ExecutionContext ctx) {
                J.Binary b = (J.Binary) super.visitBinary(binary, ctx);
                Long identity = identity(b.getOperator().name());
                if (identity == null) {
                    return b;
                }

                boolean keepLeft;
                if (identity.equals(literalValue(b.getRight()))) {
                    keepLeft = true;
                } else if (identity.equals(literalValue(b.getLeft()))) {
                    keepLeft = false;
                } else {
                    return b;
                }
                // A byte, short, char or boxed operand is promoted, so dropping the operation would change the type
                JavaType type = b.getType();
                Expression kept = keepLeft ? b.getLeft() : b.getRight();
                if ((type != JavaType.Primitive.Int && type != JavaType.Primitive.Long) || kept.getType() != type) {
                    return b;
                }

                List<Space> after = new ArrayList<>();
                kept = unwrap(kept, after);
                List<Comment> before = new ArrayList<>(b.getPrefix().getComments());
                if (keepLeft) {
                    before.addAll(kept.getPrefix().getComments());
                    after.add(b.getPadding().getOperator().getBefore());
                    after.add(comments(b.getRight()));
                } else {
                    before.addAll(comments(b.getLeft()).getComments());
                    before.addAll(b.getPadding().getOperator().getBefore().getComments());
                    before.addAll(kept.getPrefix().getComments());
                }
                if (!relocate(join(after))) {
                    return b;
                }
                return kept.withPrefix(separated(prefix(b.getPrefix().getWhitespace(), before)));
            }

            @Override
            public J visitAssignmentOperation(J.AssignmentOperation assignOp, ExecutionContext ctx) {
                J.AssignmentOperation a = (J.AssignmentOperation) super.visitAssignmentOperation(assignOp, ctx);
                Boolean valueUsed = isNoOp(a) ? valueUsed(assignOp) : null;
                if (valueUsed == null) {
                    return a;
                }
                if (valueUsed) {
                    List<Comment> before = new ArrayList<>(a.getPrefix().getComments());
                    before.addAll(a.getVariable().getPrefix().getComments());
                    if (!relocate(join(Arrays.asList(a.getPadding().getOperator().getBefore(), comments(a.getAssignment()))))) {
                        return a;
                    }
                    return a.getVariable().withPrefix(separated(prefix(a.getPrefix().getWhitespace(), before)));
                }
                // The sole statement of a body that needs one, such as the then part of an if
                if (isPure(a.getVariable()) && comments(a).getComments().isEmpty()) {
                    return new J.Block(randomId(), a.getPrefix(), Markers.EMPTY, JRightPadded.build(false), emptyList(), Space.EMPTY);
                }
                return a;
            }

            // True if the value is used, false if it is the only statement of a body, otherwise null
            private @Nullable Boolean valueUsed(J.AssignmentOperation original) {
                Cursor parentCursor = getCursor().getParentTreeCursor();
                Object parent = parentCursor.getValue();
                if (parent instanceof J.Parentheses || parent instanceof J.ControlParentheses ||
                        parent instanceof J.Assignment || parent instanceof J.AssignmentOperation ||
                        parent instanceof J.VariableDeclarations.NamedVariable || parent instanceof J.MethodInvocation ||
                        parent instanceof J.NewClass || parent instanceof J.NewArray || parent instanceof J.ArrayDimension ||
                        parent instanceof J.Ternary || parent instanceof J.Return || parent instanceof J.Yield) {
                    return true;
                }
                if (parent instanceof J.If || parent instanceof J.If.Else || parent instanceof J.WhileLoop ||
                        parent instanceof J.DoWhileLoop || parent instanceof J.ForLoop || parent instanceof J.ForEachLoop ||
                        parent instanceof J.Label) {
                    return false;
                }
                if (parent instanceof J.Case && ((J.Case) parent).getBody() == original) {
                    return parentCursor.getParentTreeCursor().getParentTreeCursor().getValue() instanceof J.SwitchExpression;
                }
                JavaType.FullyQualified functionalInterface = parent instanceof J.Lambda ?
                        TypeUtils.asFullyQualified(((J.Lambda) parent).getType()) : null;
                if (functionalInterface == null) {
                    return null;
                }
                List<JavaType.Method> abstractMethods = functionalInterface.getMethods().stream()
                        .filter(m -> m.hasFlags(Flag.Abstract))
                        .collect(toList());
                return abstractMethods.size() == 1 ? abstractMethods.get(0).getReturnType() != JavaType.Primitive.Void : null;
            }

            @Override
            public J visitBlock(J.Block block, ExecutionContext ctx) {
                J.Block bl = (J.Block) super.visitBlock(block, ctx);
                List<JRightPadded<Statement>> statements = bl.getPadding().getStatements();
                return bl.getPadding().withStatements(ListUtils.map(statements, (i, s) -> isRemovable(s,
                        i + 1 < statements.size() ? statements.get(i + 1).getElement().getPrefix() : bl.getEnd()) ? null : s));
            }

            @Override
            public J visitCase(J.Case case_, ExecutionContext ctx) {
                J.Case c = (J.Case) super.visitCase(case_, ctx);
                JContainer<Statement> container = c.getPadding().getStatements();
                List<JRightPadded<Statement>> statements = container.getPadding().getElements();
                // A comment trailing the last statement lives in the next case
                return c.getPadding().withStatements(container.getPadding().withElements(ListUtils.map(statements, (i, s) ->
                        i + 1 < statements.size() && isRemovable(s, statements.get(i + 1).getElement().getPrefix()) ? null : s)));
            }

            @Override
            public J visitForControl(J.ForLoop.Control control, ExecutionContext ctx) {
                J.ForLoop.Control fc = (J.ForLoop.Control) super.visitForControl(control, ctx);
                List<JRightPadded<Statement>> update = ListUtils.map(fc.getPadding().getUpdate(), s -> isRemovable(s, Space.EMPTY) ? null : s);
                if (update == fc.getPadding().getUpdate()) {
                    return fc;
                }
                return fc.getPadding().withUpdate(update.isEmpty() ?
                        singletonList(JRightPadded.build(new J.Empty(randomId(), Space.EMPTY, Markers.EMPTY))) : update);
            }

            @Override
            public <T extends J> J visitParentheses(J.Parentheses<T> parens, ExecutionContext ctx) {
                J visited = super.visitParentheses(parens, ctx);
                if (!(parens.getTree() instanceof J.Binary || parens.getTree() instanceof J.AssignmentOperation) ||
                        !(visited instanceof J.Parentheses)) {
                    return visited;
                }
                List<Space> after = new ArrayList<>();
                Expression unwrapped = unwrap((J.Parentheses<?>) visited, after);
                if (unwrapped == visited || !relocate(join(after))) {
                    return visited;
                }
                return unwrapped.withPrefix(separated(unwrapped.getPrefix()));
            }

            @Override
            public J visitTypeCast(J.TypeCast typeCast, ExecutionContext ctx) {
                J.TypeCast t = (J.TypeCast) super.visitTypeCast(typeCast, ctx);
                // A parenthesized cast operand is parsed as control parentheses, which visitParentheses never sees
                if (!(typeCast.getExpression() instanceof J.ControlParentheses) || !(t.getExpression() instanceof J.ControlParentheses)) {
                    return t;
                }
                J original = ((J.ControlParentheses<?>) typeCast.getExpression()).getTree();
                J.ControlParentheses<?> cp = (J.ControlParentheses<?>) t.getExpression();
                if (!(original instanceof J.Binary || original instanceof J.AssignmentOperation || original instanceof J.Parentheses) ||
                        !isPrimary(cp.getTree()) || !relocate(cp.getPadding().getTree().getAfter())) {
                    return t;
                }
                List<Comment> before = new ArrayList<>(cp.getPrefix().getComments());
                before.addAll(cp.getTree().getPrefix().getComments());
                Space prefix = prefix(cp.getPrefix().getWhitespace(), before);
                return t.withExpression(((Expression) cp.getTree()).withPrefix(prefix.isEmpty() ? Space.SINGLE_SPACE : prefix));
            }

            // Moves comments that followed the current node to whatever is printed next, or returns false
            private boolean relocate(Space comments) {
                if (comments.getComments().isEmpty()) {
                    return true;
                }
                Cursor c = getCursor();
                for (Cursor parent = c.getParent(); parent != null; c = parent, parent = parent.getParent()) {
                    Object p = parent.getValue();
                    Object next = p instanceof JRightPadded ? p : null;
                    if (p instanceof J.Binary && ((J.Binary) p).getLeft() == c.getValue()) {
                        next = ((J.Binary) p).getPadding().getOperator();
                    } else if (p instanceof J.Ternary) {
                        J.Ternary.Padding ternary = ((J.Ternary) p).getPadding();
                        next = c.getValue() == ((J.Ternary) p).getCondition() ? ternary.getTruePart() :
                                c.getValue() == ternary.getTruePart() ? ternary.getFalsePart() : null;
                    } else if (p instanceof J.Assert && ((J.Assert) p).getCondition() == c.getValue()) {
                        next = ((J.Assert) p).getDetail();
                    }
                    if (next != null) {
                        trailing.merge(next, comments, (existing, added) -> join(Arrays.asList(existing, added)));
                        return true;
                    }
                    // Keep walking only while the node is the last thing its parent prints
                    if (!(p instanceof JLeftPadded || p instanceof J.Binary || p instanceof J.Ternary || p instanceof J.Assert ||
                            p instanceof J.Unary || p instanceof J.TypeCast || p instanceof J.Return || p instanceof J.Yield ||
                            p instanceof J.Lambda || p instanceof J.Assignment || p instanceof J.AssignmentOperation ||
                            p instanceof J.VariableDeclarations.NamedVariable ||
                            p instanceof J.MethodDeclaration && ((J.MethodDeclaration) p).getPadding().getDefaultValue() == c.getValue())) {
                        return false;
                    }
                }
                return false;
            }

            /**
             * `return(x | 0)` must become `return x`, not `returnx`. The keyword can also sit in front of an
             * enclosing expression the node starts, as in `return(x | 0) + 1`.
             */
            private Space separated(Space prefix) {
                if (!prefix.getWhitespace().isEmpty() || !prefix.getComments().isEmpty()) {
                    return prefix;
                }
                Cursor c = getCursor();
                Cursor parent = c.getParentTreeCursor();
                while (parent.getValue() instanceof J.Binary && ((J.Binary) parent.getValue()).getLeft() == c.getValue() ||
                        parent.getValue() instanceof J.Ternary && ((J.Ternary) parent.getValue()).getCondition() == c.getValue()) {
                    Space enclosing = ((J) parent.getValue()).getPrefix();
                    if (!enclosing.getWhitespace().isEmpty() || !enclosing.getComments().isEmpty()) {
                        return prefix;
                    }
                    c = parent;
                    parent = c.getParentTreeCursor();
                }
                Object keyword = parent.getValue();
                // An annotation element's default value is the only expression directly under a method declaration
                return keyword instanceof J.Return || keyword instanceof J.Yield || keyword instanceof J.Case ||
                        keyword instanceof J.Assert || keyword instanceof J.MethodDeclaration ? Space.SINGLE_SPACE : prefix;
            }
        });
    }

    private static @Nullable Long identity(String operator) {
        switch (operator) {
            case "BitAnd":
                return -1L;
            case "BitOr":
            case "BitXor":
                return 0L;
            default:
                return null;
        }
    }

    // Compound assignment narrows back, so `b |= 0` is a no-op even for a byte; volatile writes are kept
    private static boolean isNoOp(J.AssignmentOperation a) {
        Long identity = identity(a.getOperator().name());
        JavaType type = a.getVariable().getType();
        JavaType.Variable variable = a.getVariable() instanceof J.Identifier ? ((J.Identifier) a.getVariable()).getFieldType() :
                a.getVariable() instanceof J.FieldAccess ? ((J.FieldAccess) a.getVariable()).getName().getFieldType() : null;
        return identity != null && identity.equals(literalValue(a.getAssignment())) &&
                (type == JavaType.Primitive.Int || type == JavaType.Primitive.Long || type == JavaType.Primitive.Short ||
                        type == JavaType.Primitive.Byte || type == JavaType.Primitive.Char) &&
                (variable == null || !variable.hasFlags(Flag.Volatile));
    }

    // Targets that can neither throw nor have side effects, unlike `a[i++]`
    private static boolean isPure(Expression target) {
        if (target instanceof J.FieldAccess && ((J.FieldAccess) target).getTarget() instanceof J.Identifier) {
            J.Identifier owner = (J.Identifier) ((J.FieldAccess) target).getTarget();
            JavaType.Variable field = ((J.FieldAccess) target).getName().getFieldType();
            return "this".equals(owner.getSimpleName()) ||
                    owner.getFieldType() == null && field != null && field.hasFlags(Flag.Static);
        }
        return target instanceof J.Identifier;
    }

    // `next` is checked for a comment trailing the statement on the same line
    private static boolean isRemovable(JRightPadded<Statement> s, Space next) {
        if (!(s.getElement() instanceof J.AssignmentOperation)) {
            return false;
        }
        J.AssignmentOperation a = (J.AssignmentOperation) s.getElement();
        return isNoOp(a) && isPure(a.getVariable()) && comments(a).getComments().isEmpty() &&
                s.getAfter().getComments().isEmpty() &&
                (next.getComments().isEmpty() || next.getWhitespace().contains("\n"));
    }

    /**
     * Widened to long the way Java does, so the int literals `-1` and `0xFFFFFFFF` both sign-extend to all ones.
     */
    private static @Nullable Long literalValue(Expression expression) {
        Expression e = expression.unwrap();
        if (e instanceof J.Literal && ((J.Literal) e).getValue() instanceof Number) {
            return ((Number) ((J.Literal) e).getValue()).longValue();
        }
        if (e instanceof J.Unary) {
            Long operand = literalValue(((J.Unary) e).getExpression());
            if (operand != null) {
                switch (((J.Unary) e).getOperator()) {
                    case Negative:
                        return -operand;
                    case Complement:
                        return ~operand;
                    case Positive:
                        return operand;
                    default:
                        break;
                }
            }
        }
        return null;
    }

    // Not literals: javac folds `-1` into one, and `-(-1)` must not become `--1`
    private static boolean isPrimary(J tree) {
        return tree instanceof J.Identifier || tree instanceof J.FieldAccess ||
                tree instanceof J.MethodInvocation || tree instanceof J.ArrayAccess;
    }

    // Comments before a closing parenthesis are added to `after`
    private static Expression unwrap(Expression e, List<Space> after) {
        if (!(e instanceof J.Parentheses)) {
            return e;
        }
        J.Parentheses<?> p = (J.Parentheses<?>) e;
        J tree = p.getTree() instanceof Expression ? unwrap((Expression) p.getTree(), after) : p.getTree();
        if (!isPrimary(tree)) {
            return e;
        }
        List<Comment> before = new ArrayList<>(p.getPrefix().getComments());
        before.addAll(tree.getPrefix().getComments());
        after.add(p.getPadding().getTree().getAfter());
        return ((Expression) tree).withPrefix(prefix(p.getPrefix().getWhitespace(), before));
    }

    private static Space prefix(String whitespace, List<Comment> comments) {
        return Space.build(whitespace, ListUtils.mapLast(comments, c -> c.getSuffix().isEmpty() ? c.withSuffix(" ") : c));
    }

    private static Space join(List<Space> spaces) {
        Space joined = Space.EMPTY;
        for (Space s : spaces) {
            if (s.getComments().isEmpty()) {
                continue;
            }
            if (joined.getComments().isEmpty()) {
                joined = s;
            } else {
                List<Comment> comments = ListUtils.mapLast(joined.getComments(), c -> c.getSuffix().isEmpty() ? c.withSuffix(s.getWhitespace()) : c);
                joined = joined.withComments(ListUtils.concatAll(comments, s.getComments()));
            }
        }
        return joined;
    }

    // A line comment keeps its line break
    private static Space attach(Space moved, Space existing) {
        List<Comment> comments = ListUtils.mapLast(moved.getComments(), c -> c.isMultiline() ? c.withSuffix(existing.getWhitespace()) : c);
        return Space.build(moved.getWhitespace(), ListUtils.concatAll(comments, existing.getComments()));
    }

    private static Space comments(J tree) {
        List<Space> spaces = new ArrayList<>();
        new JavaVisitor<List<Space>>() {
            @Override
            public Space visitSpace(@Nullable Space space, Space.Location loc, List<Space> s) {
                if (space != null && !space.getComments().isEmpty()) {
                    s.add(space);
                }
                return super.visitSpace(space, loc, s);
            }
        }.visit(tree, spaces);
        return join(spaces);
    }
}
