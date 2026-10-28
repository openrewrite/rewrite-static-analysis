/*
 * Copyright 2026 the original author or authors.
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
import org.openrewrite.Cursor;
import org.openrewrite.ExecutionContext;
import org.openrewrite.Preconditions;
import org.openrewrite.Recipe;
import org.openrewrite.TreeVisitor;
import org.openrewrite.java.JavaIsoVisitor;
import org.openrewrite.java.search.UsesType;
import org.openrewrite.java.tree.Expression;
import org.openrewrite.java.tree.J;
import org.openrewrite.java.tree.TypeUtils;
import org.openrewrite.marker.SearchResult;

import java.time.Duration;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Set;

import static java.util.Arrays.asList;
import static java.util.Collections.singleton;

@Value
@EqualsAndHashCode(callSuper = false)
public class FindNullReturnedFromBooleanMethod extends Recipe {

    private static final String BOOLEAN = "java.lang.Boolean";

    /** Nullability annotations come from many libraries, so a nullable return is recognized by simple name. */
    private static final Set<String> NULLABLE_NAMES = new HashSet<>(asList("Nullable", "CheckForNull"));

    private static final String MESSAGE = "A `Boolean` returning `null` forces every caller to handle three " +
            "outcomes, and unboxing the result in a condition throws a `NullPointerException`. Return `false`, or " +
            "declare the method as returning `boolean`.";

    String displayName = "Find `null` returned from a `Boolean` method";

    String description = "Marks `return null` in a method declared to return `java.lang.Boolean`. Callers reasonably " +
            "treat a `Boolean` as two-valued, and using the result in an `if` or a `&&` unboxes it, so a `null` " +
            "answer throws a `NullPointerException` at a place far from the method that produced it. A `return null` " +
            "inside a lambda or a class body nested in the method is attributed to that body instead. A method " +
            "annotated `@Nullable` has opted into three-valued logic and is not reported.";

    Set<String> tags = singleton("RSPEC-S2447");

    Duration estimatedEffortPerOccurrence = Duration.ofMinutes(20);

    @Override
    public TreeVisitor<?, ExecutionContext> getVisitor() {
        return Preconditions.check(new UsesType<>(BOOLEAN, false), new JavaIsoVisitor<ExecutionContext>() {
            @Override
            public J.Return visitReturn(J.Return return_, ExecutionContext ctx) {
                J.Return r = super.visitReturn(return_, ctx);
                if (!isNullLiteral(r.getExpression())) {
                    return r;
                }
                J.MethodDeclaration method = enclosingMethod(getCursor());
                if (method == null || method.getReturnTypeExpression() == null ||
                        !TypeUtils.isOfClassType(method.getReturnTypeExpression().getType(), BOOLEAN) ||
                        isNullable(method)) {
                    return r;
                }
                return SearchResult.found(r, MESSAGE);
            }
        });
    }

    private static boolean isNullLiteral(@Nullable Expression expression) {
        if (expression instanceof J.TypeCast) {
            return isNullLiteral(((J.TypeCast) expression).getExpression());
        }
        // `unwrap` also sees through nested parentheses, which a single-level check would miss
        return expression != null && J.Literal.isLiteralValue(expression.unwrap(), null);
    }

    /**
     * A method that declares its result nullable has opted into three-valued logic, and its callers are told to
     * expect `null`, so the rule does not report it.
     */
    private static boolean isNullable(J.MethodDeclaration method) {
        for (J.Annotation annotation : method.getAllAnnotations()) {
            if (NULLABLE_NAMES.contains(annotation.getSimpleName())) {
                return true;
            }
        }
        return false;
    }

    /** The method this {@code return} leaves, which is not the enclosing one when a lambda or class body intervenes. */
    private static J.@Nullable MethodDeclaration enclosingMethod(Cursor cursor) {
        for (Iterator<Cursor> path = cursor.getPathAsCursors(); path.hasNext(); ) {
            Object value = path.next().getValue();
            if (value instanceof J.Lambda) {
                return null;
            }
            if (value instanceof J.MethodDeclaration) {
                return (J.MethodDeclaration) value;
            }
        }
        return null;
    }
}
