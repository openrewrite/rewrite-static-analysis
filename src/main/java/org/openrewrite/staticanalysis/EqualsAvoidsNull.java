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
import static org.openrewrite.java.search.SemanticallyEqual.areEqual;
import static org.openrewrite.java.tree.J.Literal.isLiteralValue;

@EqualsAndHashCode(callSuper = false)
@Value
public class EqualsAvoidsNull extends Recipe {

    private static final String JAVA_LANG_STRING = "java.lang.String";
    private static final String JAVA_LANG_OBJECT = "java.lang.Object";

    private static final MethodMatcher EQUALS_STRING = new MethodMatcher(JAVA_LANG_STRING + " equals(" + JAVA_LANG_OBJECT + ")");
    private static final MethodMatcher EQUALS_OBJECT = new MethodMatcher(JAVA_LANG_OBJECT + " equals(" + JAVA_LANG_OBJECT + ")");
    private static final MethodMatcher EQUALS_IGNORE_CASE = new MethodMatcher(JAVA_LANG_STRING + " equalsIgnoreCase(" + JAVA_LANG_STRING + ")");
    private static final MethodMatcher CONTENT_EQUALS = new MethodMatcher(JAVA_LANG_STRING + " contentEquals(java.lang.CharSequence)");

    String displayName = "Equals avoids null";

    String description = "Checks that any combination of String literals is on the left side of an `equals()` comparison. " +
                "Also checks for String literals assigned to some field (such as `someString.equals(anotherString = \"text\"))`. " +
                "And removes redundant null checks in conjunction with equals comparisons.";

    Set<String> tags = singleton("RSPEC-S1132");

    @Override
    public Duration getEstimatedEffortPerOccurrence() {
        return ofMinutes(10);
    }

    @Override
    public TreeVisitor<?, ExecutionContext> getVisitor() {
        return Preconditions.check(
                Preconditions.or(
                        new UsesMethod<>(EQUALS_STRING),
                        new UsesMethod<>(EQUALS_OBJECT),
                        new UsesMethod<>(EQUALS_IGNORE_CASE),
                        new UsesMethod<>(CONTENT_EQUALS)),
                new JavaVisitor<ExecutionContext>() {
                    @Override
                    public J visitMethodInvocation(J.MethodInvocation method, ExecutionContext ctx) {
                        J.MethodInvocation mi = (J.MethodInvocation) super.visitMethodInvocation(method, ctx);

                        if (mi.getSelect() instanceof J.Literal || !isStringComparisonMethod(mi) || !hasCompatibleArgument(mi)) {
                            return mi;
                        }

                        Expression firstArgument = mi.getArguments().get(0);
                        return firstArgument.getType() == JavaType.Primitive.Null ?
                                literalsFirstInComparisonsNull(mi, firstArgument) :
                                literalsFirstInComparisons(mi, firstArgument);
                    }

                    @Override
                    public J visitBinary(J.Binary binary, ExecutionContext ctx) {
                        // First swap order of method invocation select and argument
                        J.Binary b = (J.Binary) super.visitBinary(binary, ctx);

                        // Independent of changes above, clear out unnecessary null comparisons
                        if (b.getLeft() instanceof J.Binary &&
                                b.getOperator() == J.Binary.Type.And &&
                                isStringComparisonMethod(b.getRight())) {
                            Expression nullCheckedLeft = nullCheckedArgument((J.Binary) b.getLeft());
                            if (nullCheckedLeft != null && areEqual(nullCheckedLeft, ((J.MethodInvocation) b.getRight()).getArguments().get(0))) {
                                return b.getRight().withPrefix(b.getPrefix());
                            }
                        }
                        return b;
                    }

                    private @Nullable Expression nullCheckedArgument(J.Binary binary) {
                        if (binary.getOperator() == J.Binary.Type.NotEqual) {
                            if (isLiteralValue(binary.getLeft(), null)) {
                                return binary.getRight();
                            }
                            if (isLiteralValue(binary.getRight(), null)) {
                                return binary.getLeft();
                            }
                        }
                        return null;
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

                    private boolean isStringComparisonMethod(J j) {
                        if (j instanceof J.MethodInvocation) {
                            J.MethodInvocation mi = (J.MethodInvocation) j;
                            return EQUALS_STRING.matches(mi) ||
                                    (EQUALS_OBJECT.matches(mi) && TypeUtils.isString(mi.getArguments().get(0).getType())) ||
                                    EQUALS_IGNORE_CASE.matches(mi) ||
                                    CONTENT_EQUALS.matches(mi);
                        }
                        return false;
                    }

                    private J.Binary literalsFirstInComparisonsNull(J.MethodInvocation m, Expression firstArgument) {
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
                        if (!(firstArgument instanceof J.Literal) && !(m.getSelect() instanceof J.Literal)) {
                            if (firstArgument.toString().compareTo(m.getSelect().toString()) > 0) {
                                // Don't swap the order to avoid thrashing.
                                // toString() is a somewhat arbitrary criterion, but at least it's deterministic.
                                return m;
                            }
                        }

                        return m.withSelect(firstArgument.withPrefix(requireNonNull(m.getSelect()).getPrefix()))
                                .withArguments(singletonList(m.getSelect().withPrefix(Space.EMPTY)));
                    }
                });
    }
}
