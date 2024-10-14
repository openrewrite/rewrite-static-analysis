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
import org.openrewrite.Tree;
import org.openrewrite.java.JavaVisitor;
import org.openrewrite.java.MethodMatcher;
import org.openrewrite.java.style.EqualsAvoidsNullStyle;
import org.openrewrite.java.tree.Expression;
import org.openrewrite.java.tree.J;
import org.openrewrite.java.tree.JavaType;
import org.openrewrite.java.tree.Space;
import org.openrewrite.marker.Markers;

import static java.util.Collections.singletonList;
import static java.util.Objects.nonNull;
import static java.util.Objects.requireNonNull;
import static org.openrewrite.java.tree.JLeftPadded.build;
import static org.openrewrite.java.tree.Space.SINGLE_SPACE;

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

    MethodMatcher EQUALS = getMethodMatcher("equals(java.lang.Object)");
    MethodMatcher EQUALS_IGNORE_CASE = getMethodMatcher("equalsIgnoreCase(java.lang.String)");
    MethodMatcher COMPARE_TO = getMethodMatcher("compareTo(java.lang.String)");
    MethodMatcher COMPARE_TO_IGNORE_CASE = getMethodMatcher("compareToIgnoreCase(java.lang.String)");
    MethodMatcher CONTENT_EQUALS = getMethodMatcher("contentEquals(java.lang.CharSequence)");

    EqualsAvoidsNullStyle style;

    @Override
    public J visitMethodInvocation(J.MethodInvocation method, P p) {
        return getSuperIfSelectNull((J.MethodInvocation) super.visitMethodInvocation(method, p));
    }

    private J getSuperIfSelectNull(J.MethodInvocation methodInvocation) {
        return nonNull(methodInvocation.getSelect()) ?
                !(methodInvocation.getSelect() instanceof J.Literal)
                        && methodInvocation.getArguments().get(0) instanceof J.Literal
                        && isStringComparisonMethod(methodInvocation)
                        ? literalsFirstInComparisons(methodInvocation, getCursor().getParentTreeCursor().getValue())
                        : methodInvocation :
                methodInvocation;
    }

    private boolean isStringComparisonMethod(J.MethodInvocation methodInvocation) {
        return EQUALS.matches(methodInvocation)
                || !style.getIgnoreEqualsIgnoreCase()
                && EQUALS_IGNORE_CASE.matches(methodInvocation)
                || COMPARE_TO.matches(methodInvocation)
                || COMPARE_TO_IGNORE_CASE.matches(methodInvocation)
                || CONTENT_EQUALS.matches(methodInvocation);
    }

    private Expression literalsFirstInComparisons(J.MethodInvocation m, P parent) {
        if (parent instanceof J.Binary) {
            handleBinaryExpression(m, (J.Binary) parent);
        }
        return m.getArguments().get(0).getType() == JavaType.Primitive.Null ?
                literalsFirstInComparisonsNull(m) :
                literalsFirstInComparisons(m);
    }

    private static J.Binary literalsFirstInComparisonsNull(J.MethodInvocation m) {
        return new J.Binary(Tree.randomId(),
                m.getPrefix(),
                Markers.EMPTY,
                requireNonNull(m.getSelect()),
                build(J.Binary.Type.Equal).withBefore(SINGLE_SPACE),
                m.getArguments().get(0).withPrefix(SINGLE_SPACE),
                JavaType.Primitive.Boolean);
    }

    private static J.MethodInvocation literalsFirstInComparisons(J.MethodInvocation m) {
        return m.withSelect(m.getArguments().get(0).withPrefix(requireNonNull(m.getSelect()).getPrefix()))
                .withArguments(singletonList(m.getSelect().withPrefix(Space.EMPTY)));
    }

    private void handleBinaryExpression(J.MethodInvocation m, J.Binary binary) {
        if (binary.getOperator() == J.Binary.Type.And
                && binary.getLeft() instanceof J.Binary) {
            J.Binary left = (J.Binary) binary.getLeft();
            if (isNullLiteral(left.getLeft())
                    && matchesSelect(left.getRight(), requireNonNull(m.getSelect()))
                    || isNullLiteral(left.getRight())
                    && matchesSelect(left.getLeft(), requireNonNull(m.getSelect()))) {
                doAfterVisit(new RemoveUnnecessaryNullCheck<>(binary));
            }
        }
    }

    private boolean isNullLiteral(Expression expression) {
        return expression instanceof J.Literal && ((J.Literal) expression).getType() == JavaType.Primitive.Null;
    }

    private boolean matchesSelect(Expression expression, Expression select) {
        return expression.printTrimmed(getCursor()).replaceAll("\\s", "").equals(select.printTrimmed(getCursor()).replaceAll("\\s", ""));
    }

    private static MethodMatcher getMethodMatcher(String method) {
        return new MethodMatcher("java.lang.String " + method);
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
