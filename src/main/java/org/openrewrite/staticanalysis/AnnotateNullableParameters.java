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
import org.jspecify.annotations.Nullable;
import org.openrewrite.*;
import org.openrewrite.internal.ListUtils;
import org.openrewrite.java.*;
import org.openrewrite.java.search.SemanticallyEqual;
import org.openrewrite.java.tree.Expression;
import org.openrewrite.java.tree.J;
import org.openrewrite.java.tree.Space;
import org.openrewrite.java.tree.Statement;
import org.openrewrite.staticanalysis.java.MoveFieldAnnotationToType;

import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;

import static java.util.Collections.singletonList;
import static java.util.Comparator.comparing;
import static java.util.stream.Collectors.toList;

@EqualsAndHashCode(callSuper = false)
@Value
public class AnnotateNullableParameters extends Recipe {

    private static final String DEFAULT_NULLABLE_ANN_CLASS = "org.jspecify.annotations.Nullable";

    /**
     * Subset of nullable annotations that are meta-annotated with {@code @Target(TYPE_USE)}.
     * Only these annotations can be positioned before the inner type of nested type
     * (e.g. {@code Outer.@Nullable Inner}) or on array brackets (e.g. {@code String @Nullable[]}).
     * Declaration-target annotations like {@code @CheckForNull} must remain as leading annotations.
     */
    private static final Set<String> TYPE_USE_NULLABLE_ANNOTATIONS = new HashSet<>(Arrays.asList(
            "jakarta.annotation.Nullable",
            "org.checkerframework.checker.nullness.qual.Nullable",
            "org.eclipse.jdt.annotation.Nullable",
            "org.jspecify.annotations.Nullable"
    ));

    @Option(displayName = "`@Nullable` annotation class",
            description = "The fully qualified name of the @Nullable annotation to add. " +
                    "Both `@Target(TYPE_USE)` and declaration annotations (e.g. `javax.annotation.CheckForNull`) are supported. " +
                    "Defaults to `org.jspecify.annotations.Nullable`.",
            example = "org.jspecify.annotations.Nullable",
            required = false)
    @Nullable
    String nullableAnnotationClass;

    @Option(
            displayName = "Additional null-checking methods",
            description = "A list of method patterns (in OpenRewrite MethodMatcher format) that should be considered as null-checking methods. " +
                    "These will be added to the built-in list of known null-checking methods. " +
                    "Use `..` for any parameters, e.g., `com.mycompany.utils.StringUtil isEmpty(..)` or `com.mycompany.utils.CollectionUtil isNullOrEmpty(java.util.Collection)`",
            example = "com.mycompany.utils.StringUtil isEmpty(..), com.mycompany.utils.CollectionUtil isNullOrEmpty(..)",
            required = false)
    @Nullable
    List<String> additionalNullCheckingMethods;

    String displayName = "Annotate null-checked method parameters with `@Nullable`";

    String description = "Add `@Nullable` to parameters of public methods that are explicitly checked for `null`. " +
            "By default `org.jspecify.annotations.Nullable` is used, but through the `nullableAnnotationClass` option a custom annotation can be provided. " +
            "Both `@Target(TYPE_USE)` and declaration annotations (e.g. `javax.annotation.CheckForNull`) are supported. " +
            "Parameters that already carry a known nullable annotation are skipped to avoid duplication. " +
            "This recipe scans for methods that do not already have parameters annotated with a nullable annotation and checks their usages " +
            "for potential null checks. Additional null-checking methods can be specified via the `additionalNullCheckingMethods` option.";

    @Override
    public Validated<Object> validate() {
        return super.validate().and(Validated.test(
                "nullableAnnotationClass",
                "Property `nullableAnnotationClass` must be a fully qualified classname.",
                nullableAnnotationClass,
                it -> it == null || it.contains(".")));
    }

    @Override
    public TreeVisitor<?, ExecutionContext> getVisitor() {
        String fullyQualifiedName = nullableAnnotationClass != null ? nullableAnnotationClass : DEFAULT_NULLABLE_ANN_CLASS;
        String fullyQualifiedPackage = fullyQualifiedName.substring(0, fullyQualifiedName.lastIndexOf('.'));
        String simpleName = fullyQualifiedName.substring(fullyQualifiedName.lastIndexOf('.') + 1);
        return new JavaIsoVisitor<ExecutionContext>() {
            @Override
            public J.MethodDeclaration visitMethodDeclaration(J.MethodDeclaration methodDeclaration, ExecutionContext ctx) {
                J.MethodDeclaration md = super.visitMethodDeclaration(methodDeclaration, ctx);

                // Supporting only public methods atm
                if (!md.hasModifier(J.Modifier.Type.Public) || md.getBody() == null ||
                        md.getParameters().isEmpty() || md.getParameters().get(0) instanceof J.Empty ||
                        md.getMethodType() == null || md.getMethodType().isOverride()) {
                    return md;
                }

                Map<J.VariableDeclarations, J.Identifier> candidateIdentifiers = buildIdentifierMap(findCandidateParameters(md));

                // Use a single visitor pass to collect both null checks and dereferences
                NullCheckAndDereferenceVisitor visitor = new NullCheckAndDereferenceVisitor(candidateIdentifiers.values(), additionalNullCheckingMethods);
                visitor.visit(md.getBody(), ctx, getCursor());

                Set<J.Identifier> nullCheckedIdentifiers = visitor.getNullCheckedIdentifiers(getCursor());

                maybeAddImport(fullyQualifiedName);
                return md.withParameters(ListUtils.map(md.getParameters(), stm -> {
                    if (stm instanceof J.VariableDeclarations) {
                        J.VariableDeclarations vd = (J.VariableDeclarations) stm;
                        if (containsIdentifierByName(nullCheckedIdentifiers, candidateIdentifiers.get(vd))) {
                            J.VariableDeclarations annotated = JavaTemplate.builder("@" + fullyQualifiedName)
                                    .javaParser(JavaParser.fromJavaVersion().dependsOn(
                                            String.format("package %s;public @interface %s {}", fullyQualifiedPackage, simpleName)))
                                    .build()
                                    .apply(new Cursor(getCursor(), vd),
                                            vd.getCoordinates().addAnnotation(comparing(J.Annotation::getSimpleName)));

                            // TYPE_USE annotations can be positioned on array brackets and before inner types
                            // of nested types; declaration-target annotations stay as leading annotations
                            if (TYPE_USE_NULLABLE_ANNOTATIONS.contains(fullyQualifiedName)) {
                                // For array types, move annotation from leading annotations to array brackets
                                if (annotated.getTypeExpression() instanceof J.ArrayType) {
                                    // Find the annotation we just added
                                    J.Annotation nullableAnnotation = null;
                                    for (J.Annotation ann : annotated.getLeadingAnnotations()) {
                                        if (ann.getSimpleName().equals(simpleName)) {
                                            nullableAnnotation = ann;
                                            break;
                                        }
                                    }
                                    if (nullableAnnotation != null) {
                                        J.Annotation finalAnnotation = nullableAnnotation;
                                        J.ArrayType arrayType = (J.ArrayType) annotated.getTypeExpression();
                                        annotated = annotated.withLeadingAnnotations(ListUtils.map(annotated.getLeadingAnnotations(),
                                                a -> a == finalAnnotation ? null : a));
                                        arrayType = arrayType.withAnnotations(singletonList(finalAnnotation.withPrefix(Space.SINGLE_SPACE)));
                                        if (annotated.getLeadingAnnotations().isEmpty()) {
                                            arrayType = arrayType.withPrefix(Space.EMPTY);
                                        }
                                        annotated = annotated.withTypeExpression(arrayType);
                                    }
                                }

                                // For nested types, move annotation before the inner type (e.g. Outer.@Nullable Inner)
                                doAfterVisit(new MoveFieldAnnotationToType(fullyQualifiedName).getVisitor());
                            }

                            doAfterVisit(ShortenFullyQualifiedTypeReferences.modifyOnly(annotated));
                            return annotated.withModifiers(ListUtils.mapFirst(annotated.getModifiers(), first -> first.withPrefix(Space.SINGLE_SPACE)));
                        }
                    }
                    return stm;
                }));
            }
        };
    }

    private static boolean containsIdentifierByName(Collection<J.Identifier> identifiers, J.@Nullable Identifier target) {
        if (target == null) {
            return false;
        }
        return identifiers.stream().anyMatch(identifier -> SemanticallyEqual.areEqual(identifier, target));
    }

    /**
     * Finds method parameters that are candidates for @Nullable annotation.
     * A parameter is a candidate if it doesn't already have any annotation whose simple name
     * contains "null" (case-insensitive). This catches @Nullable, @CheckForNull, @NullableDecl,
     * @NonNull, @NotNull, etc. from any framework, preventing duplication and conflicts.
     */
    private static List<J.VariableDeclarations> findCandidateParameters(J.MethodDeclaration md) {
        List<J.VariableDeclarations> candidates = new ArrayList<>();
        for (Statement parameter : md.getParameters()) {
            if (parameter instanceof J.VariableDeclarations) {
                J.VariableDeclarations vd = (J.VariableDeclarations) parameter;
                if (!hasNullRelatedAnnotation(vd)) {
                    candidates.add(vd);
                }
            }
        }
        return candidates;
    }

    private static boolean hasNullRelatedAnnotation(J.VariableDeclarations vd) {
        for (J.Annotation ann : vd.getLeadingAnnotations()) {
            if (isNullAnnotation(ann)) {
                return true;
            }
        }
        // Also check type-use annotations on the type expression (e.g., String @Nullable[] or Outer.@Nullable Inner)
        return new JavaIsoVisitor<AtomicBoolean>() {
            @Override
            public J.Annotation visitAnnotation(J.Annotation annotation, AtomicBoolean f) {
                if (isNullAnnotation(annotation)) {
                    f.set(true);
                }
                return annotation;
            }
        }.reduce(vd.getTypeExpression(), new AtomicBoolean(false)).get();
    }

    private static boolean isNullAnnotation(J.Annotation ann) {
        return ann.getSimpleName().toLowerCase(Locale.ROOT).contains("null");
    }

    private Map<J.VariableDeclarations, J.Identifier> buildIdentifierMap(List<J.VariableDeclarations> parameters) {
        Map<J.VariableDeclarations, J.Identifier> identifierMap = new HashMap<>();
        for (J.VariableDeclarations vd : parameters) {
            vd.getVariables().stream()
                    .map(J.VariableDeclarations.NamedVariable::getName)
                    .findFirst()
                    .ifPresent(identifier -> identifierMap.put(vd, identifier));
        }
        return identifierMap;
    }

    /**
     * Combined visitor that tracks both null checks and dereferences in a single pass.
     * Stores state in Cursor messaging to track:
     * - Parameters that are null-checked
     * - Parameters that are dereferenced before being null-checked
     * <p>
     * This visitor maintains state as it traverses the method body, marking parameters as
     * null-checked when it encounters checks, and flagging parameters that are dereferenced
     * (via method calls or field access) before any null check.
     */
    private static class NullCheckAndDereferenceVisitor extends JavaIsoVisitor<ExecutionContext> {
        private static final String NULL_CHECKED_KEY = "NULL_CHECKED_PARAMS";
        private static final String DEREFERENCED_KEY = "DEREFERENCED_PARAMS";
        private static final String NULL_CHECK_STATE_KEY = "NULL_CHECK_STATE"; // Tracks whether each parameter has been null-checked at any given point during AST traversal.

        private static final List<MethodMatcher> NULL_SAFETY_METHOD_MATCHERS = Arrays.asList(
                new MethodMatcher("com.google.common.base.Strings isNullOrEmpty(..)"), // Guava
                new MethodMatcher("java.util.Objects isNull(..)"),
                new MethodMatcher("java.util.Objects nonNull(..)"),
                new MethodMatcher("java.util.Optional ofNullable(..)"), // Handles nullable values
                new MethodMatcher("org.apache.commons.lang3.StringUtils isBlank(..)"),
                new MethodMatcher("org.apache.commons.lang3.StringUtils isEmpty(..)"),
                new MethodMatcher("org.apache.commons.lang3.StringUtils isNotBlank(..)"),
                new MethodMatcher("org.apache.commons.lang3.StringUtils isNotEmpty(..)"),
                new MethodMatcher("org.springframework.util.ObjectUtils hasText(..)"),
                new MethodMatcher("org.springframework.util.StringUtils isEmpty(..)"), // Deprecated
                new MethodMatcher("org.springframework.util.StringUtils hasLength(..)"),
                new MethodMatcher("org.springframework.util.StringUtils hasText(..)")
        );

        // Methods where only the first parameter can be null (e.g., Objects.requireNonNullElse(nullable, nonnull))
        private static final List<MethodMatcher> FIRST_ARG_NULLABLE_MATCHERS = Arrays.asList(
                new MethodMatcher("java.util.Objects requireNonNullElse(..)"), // First param nullable, second is fallback (non-null)
                new MethodMatcher("java.util.Objects requireNonNullElseGet(..)") // First param nullable, second is supplier (non-null)
        );

        private final Collection<J.Identifier> identifiers;
        private final Collection<MethodMatcher> nullCheckingMethodMatchers;
        private Cursor methodCursor;

        public NullCheckAndDereferenceVisitor(Collection<J.Identifier> identifiers, @Nullable Collection<String> additionalNullCheckingMethods) {
            this.identifiers = identifiers;
            if (additionalNullCheckingMethods == null) {
                nullCheckingMethodMatchers = NULL_SAFETY_METHOD_MATCHERS;
            } else {
                nullCheckingMethodMatchers = new ArrayList<>(NULL_SAFETY_METHOD_MATCHERS);
                nullCheckingMethodMatchers.addAll(
                        additionalNullCheckingMethods.stream()
                                .map(MethodMatcher::new)
                                .collect(toList()));
            }
        }

        public J.Block visit(J.Block block, ExecutionContext ctx, Cursor parentCursor) {
            this.methodCursor = parentCursor;
            // Initialize state tracking maps in cursor
            Map<String, Boolean> nullCheckState = new HashMap<>();
            for (J.Identifier id : identifiers) {
                nullCheckState.put(id.getSimpleName(), false);
            }
            methodCursor.putMessage(NULL_CHECK_STATE_KEY, nullCheckState);
            methodCursor.putMessage(NULL_CHECKED_KEY, new HashSet<J.Identifier>());
            methodCursor.putMessage(DEREFERENCED_KEY, new HashSet<J.Identifier>());

            return visitBlock(block, ctx);
        }

        @Override
        public J.If visitIf(J.If iff, ExecutionContext ctx) {
            // Process condition first to update null-check state
            handleCondition(iff.getIfCondition().getTree());
            // Then visit the rest
            return super.visitIf(iff, ctx);
        }

        @Override
        public J.MethodInvocation visitMethodInvocation(J.MethodInvocation method, ExecutionContext ctx) {
            // Check if this is a method where only the first argument can be null
            if (isFirstArgNullableMethod(method)) {
                if (!method.getArguments().isEmpty() && method.getArguments().get(0) instanceof J.Identifier) {
                    J.Identifier identifier = (J.Identifier) method.getArguments().get(0);
                    if (containsIdentifierByName(identifiers, identifier)) {
                        markAsNullChecked(identifier);
                    }
                }
            }
            // Check if this is a null-checking method call (standalone, not in condition)
            else if (isKnownNullMethodChecker(method)) {
                for (Expression arg : method.getArguments()) {
                    if (arg instanceof J.Identifier) {
                        J.Identifier identifier = (J.Identifier) arg;
                        if (containsIdentifierByName(identifiers, identifier)) {
                            markAsNullChecked(identifier);
                        }
                    }
                }
            }

            // Check if select is a parameter being dereferenced
            if (method.getSelect() instanceof J.Identifier) {
                J.Identifier select = (J.Identifier) method.getSelect();
                if (isParameterNotYetNullChecked(select)) {
                    markAsDereferenced(select);
                }
            }

            return super.visitMethodInvocation(method, ctx);
        }

        @Override
        public J.FieldAccess visitFieldAccess(J.FieldAccess fieldAccess, ExecutionContext ctx) {
            // Check if target is a parameter being dereferenced
            if (fieldAccess.getTarget() instanceof J.Identifier) {
                J.Identifier target = (J.Identifier) fieldAccess.getTarget();
                if (isParameterNotYetNullChecked(target)) {
                    markAsDereferenced(target);
                }
            }
            return super.visitFieldAccess(fieldAccess, ctx);
        }

        private void handleCondition(Expression condition) {
            if (condition instanceof J.Binary) {
                handleBinary((J.Binary) condition);
            } else if (condition instanceof J.MethodInvocation) {
                handleMethodInvocation((J.MethodInvocation) condition);
            } else if (condition instanceof J.Unary) {
                handleUnary((J.Unary) condition);
            }
        }

        private void handleBinary(J.Binary binary) {
            Expression maybeParam = null;

            if (J.Literal.isLiteralValue(binary.getLeft(), null)) {
                maybeParam = binary.getRight();
            } else if (J.Literal.isLiteralValue(binary.getRight(), null)) {
                maybeParam = binary.getLeft();
            } else {
                // Recursively handle compound conditions
                handleCondition(binary.getLeft());
                handleCondition(binary.getRight());
            }

            if (maybeParam instanceof J.Identifier) {
                J.Identifier identifier = (J.Identifier) maybeParam;
                if (containsIdentifierByName(identifiers, identifier)) {
                    markAsNullChecked(identifier);
                }
            }
        }

        private void handleMethodInvocation(J.MethodInvocation mi) {
            // Check if this is a method where only the first argument can be null
            if (isFirstArgNullableMethod(mi)) {
                if (!mi.getArguments().isEmpty() && mi.getArguments().get(0) instanceof J.Identifier) {
                    J.Identifier identifier = (J.Identifier) mi.getArguments().get(0);
                    if (containsIdentifierByName(identifiers, identifier)) {
                        markAsNullChecked(identifier);
                    }
                }
            }
            // Check if this is a null-checking method
            else if (isKnownNullMethodChecker(mi)) {
                for (Expression arg : mi.getArguments()) {
                    if (arg instanceof J.Identifier) {
                        J.Identifier identifier = (J.Identifier) arg;
                        if (containsIdentifierByName(identifiers, identifier)) {
                            markAsNullChecked(identifier);
                        }
                    }
                }
            }
        }

        private void handleUnary(J.Unary unary) {
            if (unary.getExpression() instanceof J.MethodInvocation) {
                handleMethodInvocation((J.MethodInvocation) unary.getExpression());
            }
        }

        private boolean isKnownNullMethodChecker(J.MethodInvocation methodInvocation) {
            for (MethodMatcher m : nullCheckingMethodMatchers) {
                if (m.matches(methodInvocation)) {
                    return true;
                }
            }
            return false;
        }

        private boolean isFirstArgNullableMethod(J.MethodInvocation methodInvocation) {
            for (MethodMatcher m : FIRST_ARG_NULLABLE_MATCHERS) {
                if (m.matches(methodInvocation)) {
                    return true;
                }
            }
            return false;
        }

        private boolean isParameterNotYetNullChecked(J.Identifier identifier) {
            @SuppressWarnings("unchecked")
            Map<String, Boolean> nullCheckState = methodCursor.getMessage(NULL_CHECK_STATE_KEY);
            if (nullCheckState == null) {
                return false;
            }
            Boolean isChecked = nullCheckState.get(identifier.getSimpleName());
            return isChecked != null && !isChecked;
        }

        private void markAsNullChecked(J.Identifier identifier) {
            @SuppressWarnings("unchecked")
            Map<String, Boolean> nullCheckState = methodCursor.getMessage(NULL_CHECK_STATE_KEY);
            if (nullCheckState != null) {
                nullCheckState.put(identifier.getSimpleName(), true);
            }

            @SuppressWarnings("unchecked")
            Set<J.Identifier> nullChecked = methodCursor.getMessage(NULL_CHECKED_KEY);
            if (nullChecked != null) {
                nullChecked.add(identifier);
            }
        }

        private void markAsDereferenced(J.Identifier identifier) {
            @SuppressWarnings("unchecked")
            Set<J.Identifier> dereferenced = methodCursor.getMessage(DEREFERENCED_KEY);
            if (dereferenced != null) {
                dereferenced.add(identifier);
            }
        }

        @SuppressWarnings("unchecked")
        public Set<J.Identifier> getNullCheckedIdentifiers(Cursor cursor) {
            Set<J.Identifier> nullChecked = cursor.getMessage(NULL_CHECKED_KEY);
            Set<J.Identifier> dereferenced = cursor.getMessage(DEREFERENCED_KEY);

            if (nullChecked == null) {
                return new HashSet<>();
            }

            // Filter out parameters that were dereferenced before null check
            Set<J.Identifier> result = new HashSet<>();
            for (J.Identifier id : nullChecked) {
                if (!containsIdentifierByName(dereferenced, id)) {
                    result.add(id);
                }
            }
            return result;
        }
    }
}
