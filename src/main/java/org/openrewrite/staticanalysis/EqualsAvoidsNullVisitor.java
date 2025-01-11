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
import org.openrewrite.Tree;
import org.openrewrite.java.JavaVisitor;
import org.openrewrite.java.MethodMatcher;
import org.openrewrite.java.style.EqualsAvoidsNullStyle;
import org.openrewrite.java.tree.*;
import org.openrewrite.marker.Markers;

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

    private static final String JAVA_LANG_STRING = "java.lang.String ";
    private static final MethodMatcher EQUALS = new MethodMatcher(JAVA_LANG_STRING + "equals(java.lang.Object)");
    private static final MethodMatcher EQUALS_IGNORE_CASE = new MethodMatcher(JAVA_LANG_STRING + "equalsIgnoreCase(java.lang.String)");
    private static final MethodMatcher COMPARE_TO = new MethodMatcher(JAVA_LANG_STRING + "compareTo(java.lang.String)");
    private static final MethodMatcher COMPARE_TO_IGNORE_CASE = new MethodMatcher(JAVA_LANG_STRING + "compareToIgnoreCase(java.lang.String)");
    private static final MethodMatcher CONTENT_EQUALS = new MethodMatcher(JAVA_LANG_STRING + "contentEquals(java.lang.CharSequence)");

    EqualsAvoidsNullStyle style;

    @Override
    public J visitMethodInvocation(J.MethodInvocation method, P p) {
        J.MethodInvocation m = (J.MethodInvocation) super.visitMethodInvocation(method, p);
        if (m.getSelect() != null && !(m.getSelect() instanceof J.Literal) &&
                isStringComparisonMethod(m) && hasCompatibleArgument(m)) {

            maybeHandleParentBinary(m);

            Expression firstArgument = m.getArguments().get(0);
            return firstArgument.getType() == JavaType.Primitive.Null ?
                    literalsFirstInComparisonsNull(m, firstArgument) :
                    literalsFirstInComparisons(m, firstArgument);
        }
        return m;
    }

    private boolean hasCompatibleArgument(J.MethodInvocation m) {
        if (m.getArguments().isEmpty()) {
            return false;
        }
        Expression firstArgument = m.getArguments().get(0);
        if (firstArgument instanceof J.Literal) {
            return true;
        }
        if (firstArgument instanceof J.FieldAccess) {
            firstArgument = ((J.FieldAccess) firstArgument).getName();
        }
        if (firstArgument instanceof J.Identifier) {
            JavaType.Variable fieldType = ((J.Identifier) firstArgument).getFieldType();
            return fieldType != null && fieldType.hasFlags(Flag.Static, Flag.Final);
        }
        return false;
    }

    private boolean isStringComparisonMethod(J.MethodInvocation methodInvocation) {
        return EQUALS.matches(methodInvocation) ||
                !style.getIgnoreEqualsIgnoreCase() &&
                        EQUALS_IGNORE_CASE.matches(methodInvocation) ||
                COMPARE_TO.matches(methodInvocation) ||
                COMPARE_TO_IGNORE_CASE.matches(methodInvocation) ||
                CONTENT_EQUALS.matches(methodInvocation);
    }

    private void maybeHandleParentBinary(J.MethodInvocation m) {
        P parent = getCursor().getParentTreeCursor().getValue();
        if (parent instanceof J.Binary) {
            if (((J.Binary) parent).getOperator() == J.Binary.Type.And && ((J.Binary) parent).getLeft() instanceof J.Binary) {
                J.Binary potentialNullCheck = (J.Binary) ((J.Binary) parent).getLeft();
                if (isNullLiteral(potentialNullCheck.getLeft()) && matchesSelect(potentialNullCheck.getRight(), requireNonNull(m.getSelect())) ||
                        isNullLiteral(potentialNullCheck.getRight()) && matchesSelect(potentialNullCheck.getLeft(), requireNonNull(m.getSelect()))) {
                    doAfterVisit(new RemoveUnnecessaryNullCheck<>((J.Binary) parent));
                }
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
