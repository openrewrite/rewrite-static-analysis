/*
 * Copyright 2024 the original author or authors.
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

import org.jspecify.annotations.Nullable;
import org.openrewrite.*;
import org.openrewrite.java.*;
import org.openrewrite.java.service.AnnotationService;
import org.openrewrite.java.tree.Expression;
import org.openrewrite.java.tree.J;
import org.openrewrite.java.tree.JavaType;

import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

public class AnnotateNullableMethods extends Recipe {

    private static final String NULLABLE_ANN_CLASS = "org.jspecify.annotations.Nullable";
    private static final AnnotationMatcher NULLABLE_ANNOTATION_MATCHER = new AnnotationMatcher("@" + NULLABLE_ANN_CLASS);

    @Override
    public String getDisplayName() {
        return "Annotate methods which may return `null` with `@Nullable`";
    }

    @Override
    public String getDescription() {
        return "Automatically adds the `@org.jspecify.annotation.Nullable` to non-private methods " +
               "that may return `null`. This recipe scans for methods that do not already have a `@Nullable` " +
               "annotation and checks their return statements for potential null values. It also " +
               "identifies known methods from standard libraries that may return null, such as methods " +
               "from `Map`, `Queue`, `Deque`, `NavigableSet`, and `Spliterator`. The return of streams, or lambdas " +
               "are not taken into account.";
    }

    @Override
    public TreeVisitor<?, ExecutionContext> getVisitor() {
        return new JavaIsoVisitor<ExecutionContext>() {
            @Override
            public J.MethodDeclaration visitMethodDeclaration(J.MethodDeclaration methodDeclaration, ExecutionContext ctx) {
                if (!methodDeclaration.hasModifier(J.Modifier.Type.Public) ||
                    methodDeclaration.getMethodType() == null ||
                    methodDeclaration.getMethodType().getReturnType() instanceof JavaType.Primitive ||
                    service(AnnotationService.class).matches(getCursor(), NULLABLE_ANNOTATION_MATCHER)) {
                    return methodDeclaration;
                }

                J.MethodDeclaration md = super.visitMethodDeclaration(methodDeclaration, ctx);
                if (FindNullableReturnStatements.find(md)) {
                    maybeAddImport(NULLABLE_ANN_CLASS);
                    J.MethodDeclaration annotatedMethod = JavaTemplate.builder("@Nullable")
                            .imports(NULLABLE_ANN_CLASS)
                            .javaParser(JavaParser.fromJavaVersion().dependsOn(
                                    "package org.jspecify.annotations;public @interface Nullable {}"))
                            .build()
                            .apply(getCursor(), md.getCoordinates().addAnnotation(Comparator.comparing(J.Annotation::getSimpleName)));
                    return (J.MethodDeclaration) new NullableOnMethodReturnType().getVisitor().visitNonNull(annotatedMethod, ctx, getCursor().getParentTreeCursor());
                }
                return md;
            }
        };
    }

    private static class FindNullableReturnStatements extends JavaIsoVisitor<AtomicBoolean> {

        private static final List<MethodMatcher> KNOWN_NULLABLE_METHODS = Arrays.asList(
                new MethodMatcher("java.util.Map computeIfAbsent(..)"),
                new MethodMatcher("java.util.Map computeIfPresent(..)"),
                new MethodMatcher("java.util.Map get(..)"),
                new MethodMatcher("java.util.Map merge(..)"),
                new MethodMatcher("java.util.Map put(..)"),
                new MethodMatcher("java.util.Map putIfAbsent(..)"),

                new MethodMatcher("java.util.Queue poll(..)"),
                new MethodMatcher("java.util.Queue peek(..)"),

                new MethodMatcher("java.util.Deque peekFirst(..)"),
                new MethodMatcher("java.util.Deque pollFirst(..)"),
                new MethodMatcher("java.util.Deque peekLast(..)"),

                new MethodMatcher("java.util.NavigableSet lower(..)"),
                new MethodMatcher("java.util.NavigableSet floor(..)"),
                new MethodMatcher("java.util.NavigableSet ceiling(..)"),
                new MethodMatcher("java.util.NavigableSet higher(..)"),
                new MethodMatcher("java.util.NavigableSet pollFirst(..)"),
                new MethodMatcher("java.util.NavigableSet pollLast(..)"),

                new MethodMatcher("java.util.NavigableMap lowerEntry(..)"),
                new MethodMatcher("java.util.NavigableMap floorEntry(..)"),
                new MethodMatcher("java.util.NavigableMap ceilingEntry(..)"),
                new MethodMatcher("java.util.NavigableMap higherEntry(..)"),
                new MethodMatcher("java.util.NavigableMap lowerKey(..)"),
                new MethodMatcher("java.util.NavigableMap floorKey(..)"),
                new MethodMatcher("java.util.NavigableMap ceilingKey(..)"),
                new MethodMatcher("java.util.NavigableMap higherKey(..)"),
                new MethodMatcher("java.util.NavigableMap firstEntry(..)"),
                new MethodMatcher("java.util.NavigableMap lastEntry(..)"),
                new MethodMatcher("java.util.NavigableMap pollFirstEntry(..)"),
                new MethodMatcher("java.util.NavigableMap pollLastEntry(..)"),

                new MethodMatcher("java.util.Spliterator trySplit(..)")
        );

        static boolean find(J subtree) {
            return new FindNullableReturnStatements().reduce(subtree, new AtomicBoolean()).get();
        }

        @Override
        public J.Lambda visitLambda(J.Lambda lambda, AtomicBoolean atomicBoolean) {
            // Do not evaluate return statements in lambdas
            return lambda;
        }

        @Override
        public J.Return visitReturn(J.Return retrn, AtomicBoolean found) {
            if (found.get()) {
                return retrn;
            }
            J.Return r = super.visitReturn(retrn, found);
            found.set(maybeIsNull(r.getExpression()));
            return r;
        }

        private boolean maybeIsNull(@Nullable Expression returnExpression) {
            if (returnExpression instanceof J.Literal) {
                return ((J.Literal) returnExpression).getValue() == null;
            }
            if (returnExpression instanceof J.MethodInvocation) {
                return isKnowNullableMethod((J.MethodInvocation) returnExpression);
            }
            return false;
        }

        private boolean isKnowNullableMethod(J.MethodInvocation methodInvocation) {
            for (MethodMatcher m : KNOWN_NULLABLE_METHODS) {
                if (m.matches(methodInvocation)) {
                    return true;
                }
            }
            return false;
        }
    }
}
