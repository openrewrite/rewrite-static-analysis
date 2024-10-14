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
import lombok.val;
import org.openrewrite.Tree;
import org.openrewrite.java.JavaVisitor;
import org.openrewrite.java.MethodMatcher;
import org.openrewrite.java.style.EqualsAvoidsNullStyle;
import org.openrewrite.java.tree.*;
import org.openrewrite.marker.Markers;

import static java.util.Collections.singletonList;

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

    MethodMatcher EQUALS = new MethodMatcher("java.lang.String equals(java.lang.Object)");
    MethodMatcher EQUALS_IGNORE_CASE = new MethodMatcher("java.lang.String equalsIgnoreCase(java.lang.String)");
    MethodMatcher COMPARE_TO = new MethodMatcher("java.lang.String compareTo(java.lang.String)");
    MethodMatcher COMPARE_TO_IGNORE_CASE = new MethodMatcher("java.lang.String compareToIgnoreCase(java.lang.String)");
    MethodMatcher CONTENT_EQUALS = new MethodMatcher("java.lang.String contentEquals(java.lang.CharSequence)");

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
        if (!(m.getSelect() instanceof J.Literal)
                && m.getArguments().get(0) instanceof J.Literal
                && (EQUALS.matches(m)
                || !style.getIgnoreEqualsIgnoreCase()
                && EQUALS_IGNORE_CASE.matches(m)
                || COMPARE_TO.matches(m)
                || COMPARE_TO_IGNORE_CASE.matches(m)
                || CONTENT_EQUALS.matches(m))) {
            val parent = getCursor().getParentTreeCursor().getValue();
            if (parent instanceof J.Binary) {
                val binary = (J.Binary) parent;
                if (binary.getLeft() instanceof J.Binary
                        && binary.getOperator() == J.Binary.Type.And) {
                    val potentialNullCheck = (J.Binary) binary.getLeft();
                    if (isNullLiteral(potentialNullCheck.getLeft())
                            && matchesSelect(potentialNullCheck.getRight(), m.getSelect())
                            || isNullLiteral(potentialNullCheck.getRight())
                            && matchesSelect(potentialNullCheck.getLeft(), m.getSelect())) {
                        doAfterVisit(new RemoveUnnecessaryNullCheck<>(binary));
                    }
                }
            }
            if (m.getArguments().get(0).getType() == JavaType.Primitive.Null) {
                return new J.Binary(Tree.randomId(), m.getPrefix(), Markers.EMPTY,
                        m.getSelect(),
                        JLeftPadded.build(J.Binary.Type.Equal).withBefore(Space.SINGLE_SPACE),
                        m.getArguments().get(0).withPrefix(Space.SINGLE_SPACE),
                        JavaType.Primitive.Boolean);
            } else {
                return m.withSelect(m.getArguments().get(0).withPrefix(m.getSelect().getPrefix()))
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
