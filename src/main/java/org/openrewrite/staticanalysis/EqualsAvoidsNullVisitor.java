/*
 * Copyright 2020 the original author or authors.
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

import lombok.EqualsAndHashCode;
import lombok.Value;
import org.jspecify.annotations.Nullable;
import org.openrewrite.Tree;
import org.openrewrite.java.JavaVisitor;
import org.openrewrite.java.MethodMatcher;
import org.openrewrite.java.style.EqualsAvoidsNullStyle;
import org.openrewrite.java.tree.*;
import org.openrewrite.marker.Markers;

import static java.lang.String.valueOf;
import static java.util.Collections.singletonList;
import static java.util.Objects.requireNonNull;

/**
 * A visitor that identifies and addresses potential issues related to
 * the use of {@code equals} methods in Java, particularly to avoid
 * null pointer exceptions when comparing strings.
 * <p>
 * This visitor looks for method invocations of {@code equals},
 * {@code equalsIgnoreCase}, {@code compareTo}, and {@code contentEquals},
 * and performs optimizations to ensure null checks are correctly applied.
 * <p>
 * For more details, refer to the PMD best practices:
 * <a href="https://pmd.github.io/pmd/pmd_rules_java_bestpractices.html#LiteralsFirstInComparisons">Literals First in Comparisons</a>
 *
 * @param <P> The type of the parent context used for visiting the AST.
 */
@Value
@EqualsAndHashCode(callSuper = false)
public class EqualsAvoidsNullVisitor<P> extends JavaVisitor<P> {

    private static final MethodMatcher EQUALS = new MethodMatcher("java.lang.String " + "equals(java.lang.Object)");
    private static final MethodMatcher EQUALS_IGNORE_CASE = new MethodMatcher("java.lang.String " + "equalsIgnoreCase" +
            "(java.lang.String)");
    private static final MethodMatcher COMPARE_TO = new MethodMatcher("java.lang.String " + "compareTo(java.lang" +
            ".String)");
    private static final MethodMatcher COMPARE_TO_IGNORE_CASE = new MethodMatcher("java.lang.String " +
            "compareToIgnoreCase(java.lang.String)");
    private static final MethodMatcher CONTENT_EQUALS = new MethodMatcher("java.lang.String " + "contentEquals(java" +
            ".lang.CharSequence)");

    EqualsAvoidsNullStyle style;

    @Override
    public J visitMethodInvocation(J.MethodInvocation method, P p) {
        return visitMethodInvocation((J.MethodInvocation) super.visitMethodInvocation(method, p));
    }

    private Expression visitMethodInvocation(final J.MethodInvocation m) {
        final boolean stringComparisonMethod = isStringComparisonMethod(m);
        final Expression expression = literalsFirstInComparisonsBinaryCheck(m,
                getCursor().getParentTreeCursor().getValue());
        return isStringExpression(requireNonNull(m.getSelect())) && stringComparisonMethod
                ? expression
                : m;
    }

    private static boolean isStringExpression(final Expression select) {
        return valueOf(String.class).contains(valueOf(select.getType()));
    }

    private boolean isStringComparisonMethod(J.MethodInvocation methodInvocation) {
        return EQUALS.matches(methodInvocation) ||
                !style.getIgnoreEqualsIgnoreCase() &&
                        EQUALS_IGNORE_CASE.matches(methodInvocation) ||
                COMPARE_TO.matches(methodInvocation) ||
                COMPARE_TO_IGNORE_CASE.matches(methodInvocation) ||
                CONTENT_EQUALS.matches(methodInvocation);
    }

    private Expression literalsFirstInComparisonsBinaryCheck(J.MethodInvocation m, P parent) {
        if (parent instanceof J.Binary) {
            handleBinaryExpression(m, (J.Binary) parent);
        }
        return getExpression(m, m.getArguments().get(0));
    }

    private static Expression getExpression(J.MethodInvocation m, Expression firstArgument) {
        return firstArgument.getType() == JavaType.Primitive.Null ?
                literalsFirstInComparisonsNull(m, firstArgument) :
                literalsFirstInComparisons(m, firstArgument);
    }

    private static J.Binary literalsFirstInComparisonsNull(J.MethodInvocation m, Expression firstArgument) {
        return new J.Binary(Tree.randomId(),
                m.getPrefix(),
                Markers.EMPTY,
                requireNonNull(m.getSelect()),
                JLeftPadded.build(J.Binary.Type.Equal).withBefore(Space.SINGLE_SPACE),
                firstArgument.withPrefix(Space.SINGLE_SPACE),
                JavaType.Primitive.Boolean);
    }

    private static J.MethodInvocation literalsFirstInComparisons(J.MethodInvocation m, Expression firstArgument) {
        return m.withSelect(firstArgument.withPrefix(requireNonNull(m.getSelect()).getPrefix()))
                .withArguments(singletonList(m.getSelect().withPrefix(Space.EMPTY)));
    }

    private void handleBinaryExpression(J.MethodInvocation m, J.Binary binary) {
        if (binary.getOperator() == J.Binary.Type.And && binary.getLeft() instanceof J.Binary) {
            J.Binary potentialNullCheck = (J.Binary) binary.getLeft();
            if (isNullLiteral(potentialNullCheck.getLeft()) && matchesSelect(potentialNullCheck.getRight(),
                    requireNonNull(m.getSelect())) ||
                    isNullLiteral(potentialNullCheck.getRight()) && matchesSelect(potentialNullCheck.getLeft(),
                            requireNonNull(m.getSelect()))) {
                doAfterVisit(new RemoveUnnecessaryNullCheck<>(binary));
            }
        }
    }

    private boolean isNullLiteral(Expression expression) {
        return expression instanceof J.Literal && ((J.Literal) expression).getType() == JavaType.Primitive.Null;
    }

    private boolean matchesSelect(Expression expression, Expression select) {
        return expression.printTrimmed(getCursor()).replaceAll("\\s", "")
                .equals(select.printTrimmed(getCursor()).replaceAll("\\s", ""));
    }

    private static class RemoveUnnecessaryNullCheck<P> extends JavaVisitor<P> {

        private final J.Binary scope;

        boolean done;

        public RemoveUnnecessaryNullCheck(J.Binary scope) {
            this.scope = scope;
        }

        @Override
        public @Nullable J visit(@Nullable Tree tree, P p) {
            if (done) {
                return (J) tree;
            }
            return super.visit(tree, p);
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
