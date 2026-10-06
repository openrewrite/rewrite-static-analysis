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
import org.openrewrite.java.JavaVisitor;
import org.openrewrite.java.tree.Expression;
import org.openrewrite.java.tree.J;
import org.openrewrite.java.tree.JavaType;
import org.openrewrite.java.tree.Space;
import org.openrewrite.staticanalysis.java.JavaFileChecker;

import java.time.Duration;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;

import static java.util.Collections.singleton;

@Getter
public class RemoveUnnecessaryBitwiseOperation extends Recipe {

    final String displayName = "Unnecessary bit operations should not be performed";

    final String description = "Remove bitwise operations that always yield their other operand: " +
            "`x & -1`, `x | 0` and `x ^ 0` are all just `x`.";

    final Set<String> tags = singleton("RSPEC-S2437");

    final Duration estimatedEffortPerOccurrence = Duration.ofMinutes(5);

    @Override
    public TreeVisitor<?, ExecutionContext> getVisitor() {
        return Preconditions.check(new JavaFileChecker<>(), new JavaVisitor<ExecutionContext>() {
            @Override
            public J visitBinary(J.Binary binary, ExecutionContext ctx) {
                J.Binary b = (J.Binary) super.visitBinary(binary, ctx);

                Long identity;
                switch (b.getOperator()) {
                    case BitAnd:
                        identity = -1L;
                        break;
                    case BitOr:
                    case BitXor:
                        identity = 0L;
                        break;
                    default:
                        return b;
                }

                Expression kept;
                Expression dropped;
                if (identity.equals(literalValue(b.getRight()))) {
                    kept = b.getLeft();
                    dropped = b.getRight();
                } else if (identity.equals(literalValue(b.getLeft()))) {
                    kept = b.getRight();
                    dropped = b.getLeft();
                } else {
                    return b;
                }
                // A byte, short, char or boxed operand is promoted, so dropping the operation would change the type
                JavaType type = b.getType();
                if ((type == JavaType.Primitive.Int || type == JavaType.Primitive.Long) && kept.getType() == type &&
                        b.getPadding().getOperator().getBefore().getComments().isEmpty() &&
                        kept.getPrefix().getComments().isEmpty() &&
                        !hasComments(dropped)) {
                    return kept.withPrefix(separated(b.getPrefix()));
                }
                return b;
            }

            @Override
            public <T extends J> J visitParentheses(J.Parentheses<T> parens, ExecutionContext ctx) {
                J visited = super.visitParentheses(parens, ctx);
                if (!(parens.getTree() instanceof J.Binary) || !(visited instanceof J.Parentheses)) {
                    return visited;
                }
                // Only unwrap when the binary inside was reduced to a primary expression. Literals are excluded
                // because javac folds `-1` into a single literal, and `-(-1)` must not become `--1`.
                J.Parentheses<?> p = (J.Parentheses<?>) visited;
                J tree = p.getTree();
                if ((tree instanceof J.Identifier || tree instanceof J.FieldAccess ||
                        tree instanceof J.MethodInvocation || tree instanceof J.ArrayAccess) &&
                        tree.getPrefix().getComments().isEmpty() &&
                        p.getPadding().getTree().getAfter().getComments().isEmpty()) {
                    return tree.withPrefix(separated(p.getPrefix()));
                }
                return p;
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

    private static boolean hasComments(J tree) {
        AtomicBoolean found = new AtomicBoolean();
        new JavaVisitor<AtomicBoolean>() {
            @Override
            public Space visitSpace(@Nullable Space space, Space.Location loc, AtomicBoolean f) {
                if (space != null && !space.getComments().isEmpty()) {
                    f.set(true);
                }
                return super.visitSpace(space, loc, f);
            }
        }.visit(tree, found);
        return found.get();
    }
}
