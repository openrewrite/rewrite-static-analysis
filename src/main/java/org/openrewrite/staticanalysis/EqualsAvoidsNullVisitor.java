package org.openrewrite.staticanalysis;

import lombok.EqualsAndHashCode;
import lombok.Value;
import lombok.val;
import org.jetbrains.annotations.NotNull;
import org.openrewrite.Tree;
import org.openrewrite.java.JavaVisitor;
import org.openrewrite.java.MethodMatcher;
import org.openrewrite.java.style.EqualsAvoidsNullStyle;
import org.openrewrite.java.tree.*;
import org.openrewrite.marker.Markers;

import static java.util.Collections.singletonList;
import static java.util.Objects.requireNonNull;

@Value
@EqualsAndHashCode(callSuper = false)
public class EqualsAvoidsNullVisitor<P> extends JavaVisitor<P> {

    private static final MethodMatcher EQUALS = new MethodMatcher("java.lang.String equals(java.lang.Object)");
    private static final MethodMatcher EQUALS_IGNORE_CASE = new MethodMatcher("java.lang.String equalsIgnoreCase(java" +
            ".lang.String)");
    private static final MethodMatcher COMPARE_TO = new MethodMatcher("java.lang.String compareTo(java.lang.String)");
    private static final MethodMatcher COMPARE_TO_IGNORE_CASE = new MethodMatcher("java.lang.String " +
            "compareToIgnoreCase(java.lang.String)");
    private static final MethodMatcher CONTENT_EQUALS = new MethodMatcher("java.lang.String contentEquals(java.lang" +
            ".CharSequence)");

    EqualsAvoidsNullStyle style;

    @Override
    public J visitMethodInvocation(J.MethodInvocation method, P p) {
        val j = super.visitMethodInvocation(method, p);
        if (!(j instanceof J.MethodInvocation)) {
            return j;
        }
        val m = (J.MethodInvocation) j;
        if (m.getSelect() == null) {
            return m;
        } else if (!(m.getSelect() instanceof J.Literal)
                && m.getArguments().get(0) instanceof J.Literal
                && EQUALS.matches(m)
                || !style.getIgnoreEqualsIgnoreCase()
                && EQUALS_IGNORE_CASE.matches(m)
                || COMPARE_TO.matches(m)
                || COMPARE_TO_IGNORE_CASE.matches(m)
                || CONTENT_EQUALS.matches(m)) {
            return visitMethodInvocation(m);
        }
        return m;
    }

    private @NotNull Expression visitMethodInvocation(final J.MethodInvocation m) {
        val parent = getCursor().getParentTreeCursor().getValue();
        if (parent instanceof J.Binary) {
            val binary = (J.Binary) parent;
            if (binary.getOperator() == J.Binary.Type.And && binary.getLeft() instanceof J.Binary) {
                val left = (J.Binary) binary.getLeft();
                if ((isNullLiteral(left.getLeft()) && matchesSelect(left.getRight(), requireNonNull(m.getSelect())))
                        || (isNullLiteral(left.getRight()) && matchesSelect(left.getLeft(),
                        requireNonNull(m.getSelect())))) {
                    doAfterVisit(new RemoveUnnecessaryNullCheck<>(binary));
                }
            }
        } else if (m.getArguments().get(0).getType() == JavaType.Primitive.Null) {
            return new J.Binary(Tree.randomId(), m.getPrefix(), Markers.EMPTY,
                    requireNonNull(m.getSelect()),
                    JLeftPadded.build(J.Binary.Type.Equal).withBefore(Space.SINGLE_SPACE),
                    m.getArguments().get(0).withPrefix(Space.SINGLE_SPACE),
                    JavaType.Primitive.Boolean);
        }
        return m.withSelect(m.getArguments().get(0).withPrefix(requireNonNull(m.getSelect()).getPrefix()))
                .withArguments(singletonList(m.getSelect().withPrefix(Space.EMPTY)));
    }

    private boolean isNullLiteral(Expression expression) {
        return expression instanceof J.Literal && ((J.Literal) expression).getType() == JavaType.Primitive.Null;
    }

    private boolean matchesSelect(Expression expression, Expression select) {
        return expression.printTrimmed(getCursor()).replaceAll("\\s", "").equals(select.printTrimmed(getCursor()).replaceAll("\\s", ""));
    }

    private static class RemoveUnnecessaryNullCheck<P> extends JavaVisitor<P> {
        private final J.Binary scope;
        boolean done;

        public RemoveUnnecessaryNullCheck(J.Binary scope) {
            this.scope = scope;
        }

        @Override
        public J visitBinary(J.Binary binary, P p) {
            if (scope.isScope(binary)) {
                done = true;
                return binary.getRight().withPrefix(Space.EMPTY);
            }
            return super.visitBinary(binary, p);
        }
    }
}
