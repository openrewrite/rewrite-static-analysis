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
import org.openrewrite.java.JavaVisitor;
import org.openrewrite.java.MethodMatcher;
import org.openrewrite.java.search.UsesMethod;
import org.openrewrite.java.tree.*;
import org.openrewrite.marker.Markers;

import java.time.Duration;
import java.util.Set;

import static java.time.Duration.ofMinutes;
import static java.util.Collections.singleton;
import static java.util.Collections.singletonList;
import static java.util.Objects.requireNonNull;

@Value
@EqualsAndHashCode(callSuper = false)
public class EqualsAvoidsNull extends Recipe {

    private static final String JAVA_LANG_STRING = "java.lang.String";

    private static final MethodMatcher EQUALS = new MethodMatcher(JAVA_LANG_STRING + " equals(java.lang.Object)");
    private static final MethodMatcher EQUALS_IGNORE_CASE = new MethodMatcher(JAVA_LANG_STRING + " equalsIgnoreCase(" + JAVA_LANG_STRING + ")");
    private static final MethodMatcher CONTENT_EQUALS = new MethodMatcher(JAVA_LANG_STRING + " contentEquals(java.lang.CharSequence)");

    @Override
    public String getDisplayName() {
        return "Equals avoids null";
    }

    @Override
    public String getDescription() {
        return "Checks that any combination of String literals is on the left side of an `equals()` comparison. Also checks for String literals assigned to some field (such as `someString.equals(anotherString = \"text\"))`.";
    }

    @Override
    public Set<String> getTags() {
        return singleton("RSPEC-S1132");
    }

    @Override
    public Duration getEstimatedEffortPerOccurrence() {
        return ofMinutes(10);
    }

    @Override
    public TreeVisitor<?, ExecutionContext> getVisitor() {
        return Preconditions.check(
                Preconditions.or(new UsesMethod<>(EQUALS), new UsesMethod<>(EQUALS_IGNORE_CASE), new UsesMethod<>(CONTENT_EQUALS)),
                new JavaVisitor<ExecutionContext>() {
                    @Override
                    public J visitMethodInvocation(J.MethodInvocation method, ExecutionContext ctx) {
                        J.MethodInvocation m = (J.MethodInvocation) super.visitMethodInvocation(method, ctx);

                        if (!isStringComparisonMethod(m) || !hasCompatibleArgument(m)) {
                            return m;
                        }

                        maybeHandleParentBinary(m, getCursor().getParentTreeCursor().getValue());
                        Expression firstArgument = m.getArguments().get(0);

                        return firstArgument.getType() == JavaType.Primitive.Null ?
                                literalsFirstInComparisonsNull(m, firstArgument) :
                                literalsFirstInComparisons(m, firstArgument);

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
                                EQUALS_IGNORE_CASE.matches(methodInvocation) ||
                                CONTENT_EQUALS.matches(methodInvocation);
                    }

                    private void maybeHandleParentBinary(J.MethodInvocation m, final Tree parent) {
                        if (parent instanceof J.Binary) {
                            if (((J.Binary) parent).getOperator() == J.Binary.Type.And &&
                                    ((J.Binary) parent).getLeft() instanceof J.Binary) {
                                if (isNullLiteral(potentialNullCheck.getLeft()) &&
                                        matchesSelect(potentialNullCheck.getRight(),
                                        requireNonNull(m.getSelect())) ||
                                        isNullLiteral(potentialNullCheck.getRight()) &&
                                        matchesSelect(potentialNullCheck.getLeft(),
                                        && matchesSelect(potentialNullCheck.getLeft(),
                                        requireNonNull(m.getSelect()))) {
                                    doAfterVisit(new JavaVisitor<ExecutionContext>() {

                                        private final J.Binary scope = (J.Binary) parent;
                                        private boolean done;

                                        @Override
                                        public @Nullable J visit(@Nullable Tree tree, ExecutionContext ctx) {
                                            return done ? (J) tree : super.visit(tree, ctx);
                                        }

                                        @Override
                                        public J visitBinary(J.Binary binary, ExecutionContext ctx) {
                                            if (scope.isScope(binary)) {
                                                done = true;
                                                return binary.getRight().withPrefix(binary.getPrefix());
                                            }
                                            return super.visitBinary(binary, ctx);
                                        }
                                    });
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

                    private J.Binary literalsFirstInComparisonsNull(J.MethodInvocation m,
                                                                    Expression firstArgument) {
                        return new J.Binary(Tree.randomId(),
                                m.getPrefix(),
                                Markers.EMPTY,
                                requireNonNull(m.getSelect()),
                                JLeftPadded.build(J.Binary.Type.Equal).withBefore(Space.SINGLE_SPACE),
                                firstArgument.withPrefix(Space.SINGLE_SPACE),
                                JavaType.Primitive.Boolean);
                    }

                    private J.MethodInvocation literalsFirstInComparisons(J.MethodInvocation m,
                                                                          Expression firstArgument) {
                        return m.withSelect(firstArgument.withPrefix(requireNonNull(m.getSelect()).getPrefix()))
                                .withArguments(singletonList(m.getSelect().withPrefix(Space.EMPTY)));
                    }
                });
    }
}
