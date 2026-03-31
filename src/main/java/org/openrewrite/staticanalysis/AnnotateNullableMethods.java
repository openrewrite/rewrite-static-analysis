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
import org.openrewrite.java.*;
import org.openrewrite.java.tree.Expression;
import org.openrewrite.java.tree.J;
import org.openrewrite.java.tree.JavaType;
import org.openrewrite.java.tree.TypeTree;
import org.openrewrite.staticanalysis.java.MoveFieldAnnotationToType;

import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;

@EqualsAndHashCode(callSuper = false)
@Value
public class AnnotateNullableMethods extends Recipe {

    private static final String DEFAULT_NULLABLE_ANN_CLASS = "org.jspecify.annotations.Nullable";

    /**
     * Simple names of well-known nullable annotations. If a method already carries an annotation
     * (on the method itself or on its return type) whose simple name matches any of these,
     * we skip annotating it to avoid duplication.
     */
    private static final Set<String> NULLABLE_ANNOTATION_SIMPLE_NAMES = new HashSet<>(Arrays.asList(
            "Nullable",
            "CheckForNull"
    ));

    /**
     * FQNs of nullable annotations that are NOT meta-annotated with {@code @Target(TYPE_USE)}.
     * These annotations cannot be positioned before the inner type of a nested type or on array brackets,
     * and must remain as method-level annotations. All other nullable annotations (including custom ones)
     * are assumed to be TYPE_USE and will be moved to the return type position.
     */
    private static final Set<String> NON_TYPE_USE_FQN = new HashSet<>(Arrays.asList(
            "javax.annotation.CheckForNull"
    ));

    @Option(displayName = "`@Nullable` annotation class",
            description = "The fully qualified name of the @Nullable annotation to add. " +
                    "Both `@Target(TYPE_USE)` and declaration annotations (e.g. `javax.annotation.CheckForNull`) are supported. " +
                    "Defaults to `org.jspecify.annotations.Nullable`.",
            example = "org.jspecify.annotations.Nullable",
            required = false)
    @Nullable
    String nullableAnnotationClass;

    String displayName = "Annotate methods which may return `null` with `@Nullable`";

    String description = "Add `@Nullable` to non-private methods that may return `null`. " +
            "By default `org.jspecify.annotations.Nullable` is used, but through the `nullableAnnotationClass` option a custom annotation can be provided. " +
            "Both `@Target(TYPE_USE)` and declaration annotations (e.g. `javax.annotation.CheckForNull`) are supported. " +
            "Methods that already carry a known nullable annotation (matched by simple name) are skipped to avoid duplication. " +
            "This recipe scans for methods that do not already have a `@Nullable` annotation and checks their return " +
            "statements for potential null values. It also identifies known methods from standard libraries that may " +
            "return null, such as methods from `Map`, `Queue`, `Deque`, `NavigableSet`, and `Spliterator`. " +
            "The return of streams, or lambdas are not taken into account.";

    @Override
    public Validated<Object> validate() {
        return super.validate()
                .and(Validated.test("nullableAnnotationClass", "Property `nullableAnnotationClass` must be a fully qualified classname.", nullableAnnotationClass,
                        it -> it == null || it.contains(".")));
    }

    @Override
    public TreeVisitor<?, ExecutionContext> getVisitor() {
        String fullyQualifiedName = nullableAnnotationClass != null ? nullableAnnotationClass : DEFAULT_NULLABLE_ANN_CLASS;
        String fullyQualifiedPackage = fullyQualifiedName.substring(0, fullyQualifiedName.lastIndexOf('.'));
        String simpleName = fullyQualifiedName.substring(fullyQualifiedName.lastIndexOf('.') + 1);
        boolean isTypeUseAnnotation = !NON_TYPE_USE_FQN.contains(fullyQualifiedName);

        JavaIsoVisitor<ExecutionContext> javaIsoVisitor = new JavaIsoVisitor<ExecutionContext>() {
            @Override
            public J.MethodDeclaration visitMethodDeclaration(J.MethodDeclaration methodDeclaration, ExecutionContext ctx) {
                if (!methodDeclaration.hasModifier(J.Modifier.Type.Public) ||
                        methodDeclaration.getMethodType() == null ||
                        methodDeclaration.getMethodType().getReturnType() instanceof JavaType.Primitive ||
                        hasAnyNullableAnnotation(methodDeclaration)) {
                    return methodDeclaration;
                }

                J.MethodDeclaration md = super.visitMethodDeclaration(methodDeclaration, ctx);
                updateCursor(md);
                if (FindNullableReturnStatements.find(md.getBody(), getCursor().getParentTreeCursor(), nullableAnnotationClass)) {
                    J.MethodDeclaration annotatedMethod = JavaTemplate.builder("@" + fullyQualifiedName)
                            .javaParser(JavaParser.fromJavaVersion().dependsOn(
                                    String.format("package %s;public @interface %s {}", fullyQualifiedPackage, simpleName)))
                            .build()
                            .apply(getCursor(), md.getCoordinates().addAnnotation(Comparator.comparing(J.Annotation::getSimpleName)));
                    doAfterVisit(ShortenFullyQualifiedTypeReferences.modifyOnly(annotatedMethod));

                    // TYPE_USE annotations are moved to the return type position (e.g. public @Nullable String foo())
                    // and positioned before inner types of nested types (e.g. Outer.@Nullable Inner).
                    // Declaration-target annotations stay as method-level annotations (e.g. @CheckForNull \n public String foo()).
                    if (isTypeUseAnnotation) {
                        doAfterVisit(new MoveFieldAnnotationToType(fullyQualifiedName).getVisitor());
                        return (J.MethodDeclaration) new NullableOnMethodReturnType().getVisitor()
                                .visitNonNull(annotatedMethod, ctx, getCursor().getParentTreeCursor());
                    }
                    return annotatedMethod;
                }
                return md;
            }

            /**
             * Checks whether the method declaration already has any known nullable annotation,
             * either as a method-level annotation or anywhere on the return type.
             */
            private boolean hasAnyNullableAnnotation(J.MethodDeclaration methodDeclaration) {
                // Check method-level annotations
                for (J.Annotation annotation : methodDeclaration.getLeadingAnnotations()) {
                    if (NULLABLE_ANNOTATION_SIMPLE_NAMES.contains(annotation.getSimpleName())) {
                        return true;
                    }
                }
                // Scan the entire return type tree for any annotation with a known nullable simple name.
                // Uses a TreeVisitor to reliably traverse all AST node types regardless of structure
                // (J.AnnotatedType, J.FieldAccess with annotated names, J.ArrayType with bracket annotations, etc.)
                TypeTree returnType = methodDeclaration.getReturnTypeExpression();
                if (returnType != null) {
                    return new JavaIsoVisitor<AtomicBoolean>() {
                        @Override
                        public J.Annotation visitAnnotation(J.Annotation annotation, AtomicBoolean found) {
                            if (NULLABLE_ANNOTATION_SIMPLE_NAMES.contains(annotation.getSimpleName())) {
                                found.set(true);
                            }
                            return annotation;
                        }
                    }.reduce(returnType, new AtomicBoolean(false), getCursor()).get();
                }
                return false;
            }
        };
        return Repeat.repeatUntilStable(javaIsoVisitor, 5);
    }

    private static class FindNullableReturnStatements extends JavaIsoVisitor<AtomicBoolean> {

        private static final List<MethodMatcher> KNOWN_NULLABLE_METHODS = Arrays.asList(
                // These mostly return a nullable current or  previous value, which is more often null
                new MethodMatcher("java.util.Map get(..)"),
                new MethodMatcher("java.util.Map merge(..)"),
                new MethodMatcher("java.util.Map put(..)"),
                new MethodMatcher("java.util.Map putIfAbsent(..)"),

                // These two return the current or computed value, which is less likely to be null in common usage
                //new MethodMatcher("java.util.Map computeIfAbsent(..)"),
                //new MethodMatcher("java.util.Map computeIfPresent(..)"),

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

        private final String nullableAnnotationClass;

        private FindNullableReturnStatements(@Nullable String nullableAnnotationClass) {
            this.nullableAnnotationClass = Optional.ofNullable(nullableAnnotationClass).orElse(DEFAULT_NULLABLE_ANN_CLASS);
        }

        static boolean find(@Nullable J subtree, Cursor parentTreeCursor, @Nullable String nullableAnnotationClass) {
            return new FindNullableReturnStatements(nullableAnnotationClass).reduce(subtree, new AtomicBoolean(), parentTreeCursor).get();
        }

        @Override
        public J.Lambda visitLambda(J.Lambda lambda, AtomicBoolean atomicBoolean) {
            // Do not evaluate return statements in lambdas
            return lambda;
        }

        @Override
        public J.NewClass visitNewClass(J.NewClass newClass, AtomicBoolean atomicBoolean) {
            // Do not evaluate return statements in new class expressions
            return newClass;
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
                return isLocalNullableMethod((J.MethodInvocation) returnExpression) || isKnownNullableMethod((J.MethodInvocation) returnExpression);
            }
            if (returnExpression instanceof J.Ternary) {
                J.Ternary ternary = (J.Ternary) returnExpression;
                return maybeIsNull(ternary.getTruePart()) || maybeIsNull(ternary.getFalsePart());
            }
            return false;
        }

        private boolean isKnownNullableMethod(J.MethodInvocation methodInvocation) {
            for (MethodMatcher m : KNOWN_NULLABLE_METHODS) {
                if (m.matches(methodInvocation)) {
                    return true;
                }
            }
            return false;
        }

        private boolean isLocalNullableMethod(J.MethodInvocation methodInvocation) {
            JavaType.Method targetMethod = methodInvocation.getMethodType();
            if (targetMethod == null) {
                return false;
            }

            // Visit the entire compilation unit to find the method declaration
            AnnotationMatcher annotationMatcher = new AnnotationMatcher("@" + nullableAnnotationClass);
            SourceFile sf = getCursor().firstEnclosingOrThrow(SourceFile.class);
            return new JavaIsoVisitor<AtomicBoolean>() {
                @Override
                public J.MethodDeclaration visitMethodDeclaration(J.MethodDeclaration method, AtomicBoolean p) {
                    if (p.get()) {
                        return method;
                    }
                    if (targetMethod.equals(method.getMethodType()) && method.getReturnTypeExpression() instanceof J.AnnotatedType) {
                        for (J.Annotation annotation : ((J.AnnotatedType) method.getReturnTypeExpression()).getAnnotations()) {
                            if (annotationMatcher.matches(annotation)) {
                                p.set(true);
                                break;
                            }
                        }
                        return method;
                    }
                    return super.visitMethodDeclaration(method, p);
                }
            }.reduce(sf, new AtomicBoolean(false)).get();
        }
    }
}
