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

import org.openrewrite.ExecutionContext;
import org.openrewrite.Recipe;
import org.openrewrite.TreeVisitor;
import org.openrewrite.java.JavaTemplate;
import org.openrewrite.java.JavaVisitor;
import org.openrewrite.java.MethodMatcher;
import org.openrewrite.java.tree.Expression;
import org.openrewrite.java.tree.J;
import org.openrewrite.java.tree.JavaType;
import org.openrewrite.java.tree.TypeUtils;

import java.time.Duration;
import java.util.Collections;
import java.util.Set;

import static java.util.Objects.requireNonNull;

public class CollectionToArrayShouldHaveProperType extends Recipe {
    private static final MethodMatcher TO_ARRAY = new MethodMatcher("java.util.Collection toArray()", true);

    @Override
    public String getDisplayName() {
        return "'Collection.toArray()' should be passed an array of the proper type";
    }

    @Override
    public String getDescription() {
        return "Using `Collection.toArray()` without parameters returns an `Object[]`, which requires casting. " +
                "It is more efficient and clearer to use `Collection.toArray(new T[0])` instead.";
    }

    @Override
    public Duration getEstimatedEffortPerOccurrence() {
        return Duration.ofMinutes(2);
    }

    @Override
    public Set<String> getTags() {
        return Collections.singleton("RSPEC-S3020");
    }

    @Override
    public TreeVisitor<?, ExecutionContext> getVisitor() {
        return new JavaVisitor<ExecutionContext>() {
            @Override
            public <T extends J> J visitParentheses(J.Parentheses<T> parentheses, ExecutionContext ctx) {
                J visited = super.visitParentheses(parentheses, ctx);
                if (visited instanceof J.Parentheses) {
                    @SuppressWarnings("unchecked") J.Parentheses<T> visitedParentheses = (J.Parentheses<T>) visited;
                    if (visitedParentheses.getTree() instanceof J.TypeCast) {
                        J.TypeCast typeCast = (J.TypeCast) visitedParentheses.getTree();
                        Expression expression = typeCast.getExpression();

                        // Check if the expression is a method invocation of toArray() with no arguments
                        if (expression instanceof J.MethodInvocation) {
                            J.MethodInvocation methodInvocation = (J.MethodInvocation) expression;
                            if (TO_ARRAY.matches(methodInvocation)) {
                                // Get the target type from the cast
                                JavaType targetType = typeCast.getType();
                                JavaType.Array arrayType = TypeUtils.asArray(targetType);
                                if (arrayType != null) {
                                    JavaType componentType = arrayType.getElemType();

                                    // Use TypeUtils for safer type checking and casting
                                    JavaType.FullyQualified fqType = TypeUtils.asFullyQualified(componentType);
                                    if (fqType != null) {
                                        String className = fqType.getClassName();
                                        String fqn = fqType.getFullyQualifiedName();
                                        // Build the toArray call with the proper array argument
                                        JavaTemplate template = JavaTemplate.builder("#{any()}.toArray(new #{}[0])")
                                                .imports(fqn)
                                                .build();

                                        // Apply the template, replacing the entire parentheses expression
                                        Expression result = template.apply(getCursor(), visitedParentheses.getCoordinates().replace(),
                                                requireNonNull(methodInvocation.getSelect()),
                                                className);

                                        // Add the import if needed
                                        maybeAddImport(fqn);

                                        // Return the result wrapped in parentheses to preserve the original structure
                                        //noinspection unchecked
                                        return visitedParentheses.withTree((T) result);
                                    }
                                }
                            }
                        }
                    }
                }
                return visited;
            }

            @Override
            public J visitTypeCast(J.TypeCast typeCast, ExecutionContext ctx) {
                J visited = super.visitTypeCast(typeCast, ctx);
                if (!(visited instanceof J.TypeCast)) {
                    return visited;
                }

                J.TypeCast visitedTypeCast = (J.TypeCast) visited;
                Expression expression = visitedTypeCast.getExpression();

                // Check if the expression is a method invocation of toArray() with no arguments
                if (expression instanceof J.MethodInvocation) {
                    J.MethodInvocation methodInvocation = (J.MethodInvocation) expression;
                    if (TO_ARRAY.matches(methodInvocation)) {

                        // Get the target type from the cast
                        JavaType targetType = visitedTypeCast.getType();
                        JavaType.Array arrayType = TypeUtils.asArray(targetType);
                        if (arrayType != null) {
                            JavaType componentType = arrayType.getElemType();

                            // Use TypeUtils for safer type checking and casting
                            JavaType.FullyQualified fqType = TypeUtils.asFullyQualified(componentType);
                            if (fqType != null) {
                                String className = fqType.getClassName();
                                String fqn = fqType.getFullyQualifiedName();
                                // Build the toArray call with the proper array argument
                                JavaTemplate template = JavaTemplate.builder("#{any()}.toArray(new #{}[0])")
                                        .imports(fqn)
                                        .build();

                                // Apply the template, replacing the cast expression
                                Expression result = template.apply(getCursor(), visitedTypeCast.getCoordinates().replace(),
                                        requireNonNull(methodInvocation.getSelect()),
                                        className);

                                // Add the import if needed
                                maybeAddImport(fqn);

                                return result;
                            }
                        }
                    }
                }

                return visitedTypeCast;
            }
        };
    }
}
