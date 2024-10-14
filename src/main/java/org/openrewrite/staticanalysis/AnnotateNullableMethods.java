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

import lombok.AllArgsConstructor;
import org.openrewrite.Cursor;
import org.openrewrite.ExecutionContext;
import org.openrewrite.Recipe;
import org.openrewrite.TreeVisitor;
import org.openrewrite.java.*;
import org.openrewrite.java.service.AnnotationService;
import org.openrewrite.java.tree.Expression;
import org.openrewrite.java.tree.J;
import org.openrewrite.java.tree.JavaType;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

public class AnnotateNullableMethods extends Recipe {
    private static final String NULLABLE_ANN_PACKAGE = "org.jspecify.annotations";
    private static final String NULLABLE_ANN_CLASS = NULLABLE_ANN_PACKAGE + ".Nullable";
    private static final AnnotationMatcher NULLABLE_ANNOTATION_MATCHER =
            new AnnotationMatcher("@" + NULLABLE_ANN_CLASS);
    private static final String NULLABLE_ANN_ARTIFACT = "jspecify";

    @Override
    public String getDisplayName() {
        return "Annotate methods which may return null with @Nullable";
    }

    @Override
    public String getDescription() {
        return "Automatically adds the @org.jspecify.annotation.Nullable to non-private methods" +
                "that may return null. This recipe scans for methods that do not already have a @Nullable" +
                "annotation and checks their return statements for potential null values. It also" +
                "identifies known methods from standard libraries that may return null, such as methods" +
                "from Map, Queue, Deque, NavigableSet, and Spliterator. The return of streams, or lambdas" +
                " are not taken into account.";
    }

    @Override
    public TreeVisitor<?, ExecutionContext> getVisitor() {
        return new AnnotateNullableMethodsVisitor();
    }

    private static class AnnotateNullableMethodsVisitor extends JavaIsoVisitor<ExecutionContext> {
        @Override
        public J.MethodDeclaration visitMethodDeclaration(J.MethodDeclaration md, ExecutionContext ctx) {
            if (md.hasModifier(J.Modifier.Type.Private)) {
                return md;
            }

            if (md.getMethodType() != null && md.getMethodType().getReturnType() instanceof JavaType.Primitive) {
                return md;
            }

            if (service(AnnotationService.class).matches(getCursor(), NULLABLE_ANNOTATION_MATCHER)) {
                return md;
            }

            md = super.visitMethodDeclaration(md, ctx);
            updateCursor(md);

            if (FindNullableReturnStatements.find(md).get()) {
                maybeAddImport(NULLABLE_ANN_CLASS);
                return JavaTemplate.builder("@Nullable")
                        .imports(NULLABLE_ANN_CLASS)
                        .javaParser(JavaParser.fromJavaVersion().classpath(NULLABLE_ANN_ARTIFACT))
                        .build()
                        .apply(getCursor(), md.getCoordinates().addAnnotation(Comparator.comparing(J.Annotation::getSimpleName)));
            }
            return md;
        }
    }

    @AllArgsConstructor
    private static class FindNullableReturnStatements extends JavaIsoVisitor<AtomicBoolean> {
        private static final List<MethodMatcher> KNOWN_NULLABLE_METHODS = getMatchersKnownNullableMethods();

        static AtomicBoolean find(J subtree) {
            return new FindNullableReturnStatements().reduce(subtree, new AtomicBoolean());
        }

        private static List<MethodMatcher> getMatchersKnownNullableMethods() {
            List<MethodMatcher> matchers = new ArrayList<>();

            matchers.add(new MethodMatcher("java.util.Map compute(..)"));
            matchers.add(new MethodMatcher("java.util.Map computeIfAbsent(..)"));
            matchers.add(new MethodMatcher("java.util.Map computeIfPresent(..)"));
            matchers.add(new MethodMatcher("java.util.Map get(..)"));
            matchers.add(new MethodMatcher("java.util.Map merge(..)"));
            matchers.add(new MethodMatcher("java.util.Map put(..)"));
            matchers.add(new MethodMatcher("java.util.Map putIfAbsent(..)"));

            matchers.add(new MethodMatcher("java.util.Queue poll(..)"));
            matchers.add(new MethodMatcher("java.util.Queue peek(..)"));

            matchers.add(new MethodMatcher("java.util.Deque peekFirst(..)"));
            matchers.add(new MethodMatcher("java.util.Deque pollFirst(..)"));
            matchers.add(new MethodMatcher("java.util.Deque peekLast(..)"));

            matchers.add(new MethodMatcher("java.util.NavigableSet lower(..)"));
            matchers.add(new MethodMatcher("java.util.NavigableSet floor(..)"));
            matchers.add(new MethodMatcher("java.util.NavigableSet ceiling(..)"));
            matchers.add(new MethodMatcher("java.util.NavigableSet higher(..)"));
            matchers.add(new MethodMatcher("java.util.NavigableSet pollFirst(..)"));
            matchers.add(new MethodMatcher("java.util.NavigableSet pollLast(..)"));

            matchers.add(new MethodMatcher("java.util.NavigableMap lowerEntry(..)"));
            matchers.add(new MethodMatcher("java.util.NavigableMap floorEntry(..)"));
            matchers.add(new MethodMatcher("java.util.NavigableMap ceilingEntry(..)"));
            matchers.add(new MethodMatcher("java.util.NavigableMap higherEntry(..)"));
            matchers.add(new MethodMatcher("java.util.NavigableMap lowerKey(..)"));
            matchers.add(new MethodMatcher("java.util.NavigableMap floorKey(..)"));
            matchers.add(new MethodMatcher("java.util.NavigableMap ceilingKey(..)"));
            matchers.add(new MethodMatcher("java.util.NavigableMap higherKey(..)"));
            matchers.add(new MethodMatcher("java.util.NavigableMap firstEntry(..)"));
            matchers.add(new MethodMatcher("java.util.NavigableMap lastEntry(..)"));
            matchers.add(new MethodMatcher("java.util.NavigableMap pollFirstEntry(..)"));
            matchers.add(new MethodMatcher("java.util.NavigableMap pollLastEntry(..)"));

            matchers.add(new MethodMatcher("java.util.Spliterator trySplit(..)"));

            return Collections.unmodifiableList(matchers);
        }

        @Override
        public J.Return visitReturn(J.Return retrn, AtomicBoolean containsNullableReturn) {
            if (containsNullableReturn.get()) {
                return retrn;
            }

            J.Return r = super.visitReturn(retrn, containsNullableReturn);
            updateCursor(r);

            // If the returns is contained within a lambda statement, we don't consider it.
            Cursor ex = getCursor().dropParentUntil(e -> e instanceof J.MethodDeclaration || e instanceof J.Lambda);
            if (!(ex.getValue() instanceof J.MethodDeclaration)) {
                return r;
            }

            if (r.getExpression() != null && maybeIsNull(r.getExpression())) {
                containsNullableReturn.set(true);
            }

            return r;
        }

        private boolean maybeIsNull(Expression returnExpression) {
            if (returnExpression instanceof J.Literal && ((J.Literal) returnExpression).getValue() == null) {
                return true;
            } else if (returnExpression instanceof J.MethodInvocation) {
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
