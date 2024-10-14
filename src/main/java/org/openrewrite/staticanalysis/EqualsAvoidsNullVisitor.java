package org.openrewrite.staticanalysis;

import lombok.EqualsAndHashCode;
import lombok.Value;
import org.openrewrite.Tree;
import org.openrewrite.java.JavaVisitor;
import org.openrewrite.java.MethodMatcher;
import org.openrewrite.java.style.EqualsAvoidsNullStyle;
import org.openrewrite.java.tree.*;
import org.openrewrite.marker.Markers;

import static java.util.Collections.singletonList;

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
        J j = super.visitMethodInvocation(method, p);
        if (!(j instanceof J.MethodInvocation)) {
            return j;
        }
        J.MethodInvocation m = (J.MethodInvocation) j;
        if (m.getSelect() == null) {
            return m;
        }

        boolean isLiteralArgument = m.getArguments().get(0) instanceof J.Literal;
        boolean isNotLiteralSelect = !(m.getSelect() instanceof J.Literal);

        // Perform the swap for equals, equalsIgnoreCase, compareTo, compareToIgnoreCase
        if (isNotLiteralSelect && isLiteralArgument &&
                (EQUALS.matches(m)
                        || (!Boolean.TRUE.equals(style.getIgnoreEqualsIgnoreCase()) && EQUALS_IGNORE_CASE.matches(m))
                        || COMPARE_TO.matches(m)
                        || COMPARE_TO_IGNORE_CASE.matches(m)
                        // Exclude contentEquals() from swapping, as it's a safe call with a literal on the left.
                        || (CONTENT_EQUALS.matches(m) && isNotLiteralSelect))) {

            Tree parent = getCursor().getParentTreeCursor().getValue();
            // Check for null checks
            if (parent instanceof J.Binary) {
                J.Binary binary = (J.Binary) parent;
                if (binary.getOperator() == J.Binary.Type.And && binary.getLeft() instanceof J.Binary) {
                    J.Binary potentialNullCheck = (J.Binary) binary.getLeft();
                    if (isNullLiteral(potentialNullCheck.getLeft()) && matchesSelect(potentialNullCheck.getRight(),
                            m.getSelect())
                            || isNullLiteral(potentialNullCheck.getRight()) && matchesSelect(potentialNullCheck.getLeft(), m.getSelect())) {
                        doAfterVisit(new RemoveUnnecessaryNullCheck<>(binary));
                    }
                }
            }

            // If the argument is null, replace with a binary null check
            if (m.getArguments().get(0).getType() == JavaType.Primitive.Null) {
                return new J.Binary(Tree.randomId(), m.getPrefix(), Markers.EMPTY,
                        m.getSelect(),
                        JLeftPadded.build(J.Binary.Type.Equal).withBefore(Space.SINGLE_SPACE),
                        m.getArguments().get(0).withPrefix(Space.SINGLE_SPACE),
                        JavaType.Primitive.Boolean);
            } else {
                // Swap the select and argument, maintaining prefixes
                m = m.withSelect(m.getArguments().get(0).withPrefix(m.getSelect().getPrefix()))
                        .withArguments(singletonList(m.getSelect().withPrefix(Space.EMPTY)));
            }
        }

        return m;
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
