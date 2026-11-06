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

import lombok.Getter;
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
import java.util.Set;

import static java.util.Collections.singleton;
import static java.util.Objects.requireNonNull;

public class CollectionToArrayShouldHaveProperType extends Recipe {
    private static final MethodMatcher TO_ARRAY = new MethodMatcher("java.util.Collection toArray()", true);

    @Getter
    final String displayName = "`Collection.toArray()` should be passed an array of the proper type";

    @Getter
    final String description = "Using `Collection.toArray()` without parameters returns an " +
            "`Object[]`, which requires casting. It is more efficient and clearer to use " +
            "`Collection.toArray(new T[0])` instead. The parameterless form can cause a " +
            "`ClassCastException` at runtime when the returned `Object[]` is cast to a " +
            "more specific array type.";

    @Getter
    final Duration estimatedEffortPerOccurrence = Duration.ofMinutes(2);

    @Getter
    final Set<String> tags = singleton("RSPEC-S3020");

    @Override
    public TreeVisitor<?, ExecutionContext> getVisitor() {
        return new JavaVisitor<ExecutionContext>() {
            @Override
            public J visitTypeCast(J.TypeCast typeCast, ExecutionContext ctx) {
                J visited = super.visitTypeCast(typeCast, ctx);
                if (!(visited instanceof J.TypeCast)) {
                    return visited;
                }

                J.TypeCast visitedTypeCast = (J.TypeCast) visited;
                Expression expression = visitedTypeCast.getExpression();

                if (expression instanceof J.MethodInvocation) {
                    J.MethodInvocation methodInvocation = (J.MethodInvocation) expression;
                    if (TO_ARRAY.matches(methodInvocation)) {

                        JavaType targetType = visitedTypeCast.getType();
                        JavaType.Array arrayType = TypeUtils.asArray(targetType);
                        if (arrayType != null) {
                            JavaType componentType = arrayType.getElemType();

                            JavaType.FullyQualified fqType = TypeUtils.asFullyQualified(componentType);
                            if (fqType != null) {
                                String className = fqType.getClassName();
                                String fqn = fqType.getFullyQualifiedName();
                                JavaTemplate template = JavaTemplate.builder("#{any()}.toArray(new #{}[0])")
                                        .imports(fqn)
                                        .build();

                                Expression result = template.apply(getCursor(), visitedTypeCast.getCoordinates().replace(),
                                        requireNonNull(methodInvocation.getSelect()),
                                        className);

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
