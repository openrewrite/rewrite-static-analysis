/*
 * Copyright 2025 the original author or authors.
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
import org.openrewrite.*;
import org.openrewrite.java.JavaTemplate;
import org.openrewrite.java.JavaVisitor;
import org.openrewrite.java.MethodMatcher;
import org.openrewrite.java.search.UsesMethod;
import org.openrewrite.java.tree.Expression;
import org.openrewrite.java.tree.J;
import org.openrewrite.java.tree.JavaType;
import org.openrewrite.java.tree.TypeUtils;
import org.openrewrite.marker.SearchResult;

import java.time.Duration;
import java.util.Set;

import static java.util.Collections.singleton;

@Value
@EqualsAndHashCode(callSuper = false)
public class SillyEqualsCheck extends Recipe {
    private static final MethodMatcher EQUALS_MATCHER = new MethodMatcher("java.lang.Object equals(java.lang.Object)", true);

    final String displayName = "Silly equality checks should not be made";

    final String description = "Detects `.equals()` calls that compare incompatible types and will always return `false`. " +
            "Replaces `.equals(null)` with `== null` and array `.equals()` with `Arrays.equals()`. " +
            "Flags comparisons between unrelated types or between arrays and non-arrays.";

    final Set<String> tags = singleton("RSPEC-S2159");

    final Duration estimatedEffortPerOccurrence = Duration.ofMinutes(5);

    @Override
    public TreeVisitor<?, ExecutionContext> getVisitor() {
        return Preconditions.check(new UsesMethod<>(EQUALS_MATCHER), new JavaVisitor<ExecutionContext>() {
            @Override
            public J visitMethodInvocation(J.MethodInvocation method, ExecutionContext ctx) {
                J.MethodInvocation mi = (J.MethodInvocation) super.visitMethodInvocation(method, ctx);
                if (!EQUALS_MATCHER.matches(mi) || mi.getSelect() == null) {
                    return mi;
                }

                Expression arg = mi.getArguments().get(0);

                // Case: x.equals(null) -> x == null
                if (arg instanceof J.Literal && ((J.Literal) arg).getValue() == null) {
                    return replaceWithEqualityCheck(mi);
                }

                JavaType selectType = mi.getSelect().getType();
                JavaType argType = arg.getType();
                if (selectType == null || argType == null) {
                    return mi;
                }

                JavaType.Array selectArray = TypeUtils.asArray(selectType);
                JavaType.Array argArray = TypeUtils.asArray(argType);

                // Case: both arrays -> Arrays.equals() or Arrays.deepEquals() for multidimensional
                if (selectArray != null && argArray != null) {
                    maybeAddImport("java.util.Arrays");
                    boolean multidimensional = selectArray.getElemType() instanceof JavaType.Array;
                    String arrayMethod = multidimensional ? "deepEquals" : "equals";
                    return JavaTemplate.builder("Arrays." + arrayMethod + "(#{any()}, #{any()})")
                            .imports("java.util.Arrays")
                            .build()
                            .apply(getCursor(), mi.getCoordinates().replace(), mi.getSelect(), arg);
                }

                // Case: array vs non-array -> always false
                if (selectArray != null || argArray != null) {
                    return SearchResult.found(mi, "Comparing array with non-array always returns false");
                }

                // Case: unrelated types -> always false
                JavaType.FullyQualified selectFq = TypeUtils.asFullyQualified(selectType);
                JavaType.FullyQualified argFq = TypeUtils.asFullyQualified(argType);
                if (selectFq == null || argFq == null) {
                    return mi;
                }
                if (TypeUtils.isOfClassType(selectType, "java.lang.Object") ||
                    TypeUtils.isOfClassType(argType, "java.lang.Object")) {
                    return mi;
                }
                if (!TypeUtils.isAssignableTo(selectFq, argFq) &&
                    !TypeUtils.isAssignableTo(argFq, selectFq)) {
                    return SearchResult.found(mi, "Comparing unrelated types " +
                            selectFq.getFullyQualifiedName() + " and " +
                            argFq.getFullyQualifiedName() + " always returns false");
                }

                return mi;
            }

            private J replaceWithEqualityCheck(J.MethodInvocation mi) {
                Cursor parent = getCursor().getParentTreeCursor();
                while (parent.getValue() instanceof J.Parentheses) {
                    parent = parent.getParentTreeCursor();
                }
                boolean isNot = parent.getValue() instanceof J.Unary &&
                        ((J.Unary) parent.getValue()).getOperator() == J.Unary.Type.Not;
                if (isNot) {
                    parent.putMessage("REMOVE_UNARY_NOT", parent.getValue());
                }
                String operator = isNot ? "!=" : "==";
                return JavaTemplate.apply("#{any()} " + operator + " null",
                        updateCursor(mi),
                        mi.getCoordinates().replace(),
                        mi.getSelect());
            }

            @Override
            public J visitUnary(J.Unary unary, ExecutionContext ctx) {
                J j = super.visitUnary(unary, ctx);
                J.Unary asUnary = (J.Unary) j;
                if (asUnary.equals(getCursor().pollMessage("REMOVE_UNARY_NOT"))) {
                    return asUnary.getExpression().unwrap().withPrefix(asUnary.getPrefix());
                }
                return j;
            }
        });
    }
}
