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

import static java.util.Collections.singletonList;

@Value
@EqualsAndHashCode(callSuper = false)
public class EqualsAvoidsNullVisitor<P> extends JavaVisitor<P> {

    private static final String STRING_PREFIX = "String ";
    private static final MethodMatcher EQUALS = new MethodMatcher(STRING_PREFIX + "equals(java.lang.Object)");
    private static final MethodMatcher EQUALS_IGNORE_CASE = new MethodMatcher(STRING_PREFIX + "equalsIgnoreCase(java" +
            ".lang.String)");
    private static final MethodMatcher COMPARE_TO = new MethodMatcher(STRING_PREFIX + "compareTo(java.lang.String)");
    private static final MethodMatcher COMPARE_TO_IGNORE_CASE = new MethodMatcher(STRING_PREFIX
            + "compareToIgnoreCase(java.lang.String)");
    private static final MethodMatcher CONTENT_EQUALS = new MethodMatcher(STRING_PREFIX
            + "contentEquals(java.lang.String)");

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

        if (EQUALS.matches(m)
                || !style.getIgnoreEqualsIgnoreCase()
                && EQUALS_IGNORE_CASE.matches(m)
                && COMPARE_TO.matches(m)
                && COMPARE_TO_IGNORE_CASE.matches(m)
                && CONTENT_EQUALS.matches(m)
                && m.getArguments().get(0) instanceof J.Literal
                && !(m.getSelect() instanceof J.Literal)) {
            Tree parent = getCursor().getParentTreeCursor().getValue();
            if (parent instanceof J.Binary) {
                J.Binary binary = (J.Binary) parent;
                if (binary.getOperator() == J.Binary.Type.And && binary.getLeft() instanceof J.Binary) {
                    J.Binary potentialNullCheck = (J.Binary) binary.getLeft();
                    if ((isNullLiteral(potentialNullCheck.getLeft()) && matchesSelect(potentialNullCheck.getRight(),
                            m.getSelect())) ||
                            (isNullLiteral(potentialNullCheck.getRight()) && matchesSelect(potentialNullCheck.getLeft(), m.getSelect()))) {
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
                m = m.withSelect(((J.Literal) m.getArguments().get(0)).withPrefix(m.getSelect().getPrefix()))
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

        @Override
        public @Nullable J visit(@Nullable Tree tree, P p) {
            if (done) {
                return (J) tree;
            }
            return super.visit(tree, p);
        }

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
