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
import org.openrewrite.java.search.FindAnnotations;
import org.openrewrite.java.search.SemanticallyEqual;
import org.openrewrite.java.tree.Expression;
import org.openrewrite.java.tree.J;
import org.openrewrite.java.tree.Space;
import org.openrewrite.java.tree.Statement;
import org.openrewrite.staticanalysis.java.MoveFieldAnnotationToType;

import java.util.*;

import static java.util.stream.Collectors.toList;

@EqualsAndHashCode(callSuper = false)
@Value
public class AnnotateNullableParameters extends Recipe {

    private static final String DEFAULT_NULLABLE_ANN_CLASS = "org.jspecify.annotations.Nullable";

    @Option(displayName = "`@Nullable` annotation class",
            description = "The fully qualified name of the @Nullable annotation. The annotation should be meta annotated with `@Target(TYPE_USE)`. Defaults to `org.jspecify.annotations.Nullable`",
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

    @Override
    public String getDisplayName() {
        return "Annotate null-checked method parameters with `@Nullable`";
    }

    @Override
    public String getDescription() {
        return "Add `@Nullable` to parameters of public methods that are explicitly checked for `null`. " +
                "By default `org.jspecify.annotations.Nullable` is used, but through the `nullableAnnotationClass` option a custom annotation can be provided. " +
                "When providing a custom `nullableAnnotationClass` that annotation should be meta annotated with `@Target(TYPE_USE)`. " +
                "This recipe scans for methods that do not already have parameters annotated with `@Nullable` annotation and checks their usages " +
                "for potential null checks. Additional null-checking methods can be specified via the `additionalNullCheckingMethods` option.";
    }

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

                Map<J.VariableDeclarations, J.Identifier> candidateIdentifiers = buildIdentifierMap(findCandidateParameters(md, fullyQualifiedName));
                Set<J.Identifier> nullCheckedIdentifiers = new NullCheckVisitor(candidateIdentifiers.values(), additionalNullCheckingMethods)
                        .reduce(md.getBody(), new HashSet<>());

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
                                            vd.getCoordinates().addAnnotation(Comparator.comparing(J.Annotation::getSimpleName)));
                            doAfterVisit(ShortenFullyQualifiedTypeReferences.modifyOnly(annotated));
                            doAfterVisit(new MoveFieldAnnotationToType(fullyQualifiedName).getVisitor());
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
     * A parameter is a candidate if it doesn't already have the target nullable annotation.
     *
     * @param md  the method declaration to analyze
     * @param fqn the fully qualified name of the nullable annotation
     * @return list of parameter declarations that could receive the annotation
     */
    private List<J.VariableDeclarations> findCandidateParameters(J.MethodDeclaration md, String fqn) {
        List<J.VariableDeclarations> candidates = new ArrayList<>();
        for (Statement parameter : md.getParameters()) {
            if (parameter instanceof J.VariableDeclarations) {
                J.VariableDeclarations vd = (J.VariableDeclarations) parameter;
                if (FindAnnotations.find(vd, "@" + fqn).isEmpty()) {
                    candidates.add(vd);
                }
            }
        }
        return candidates;
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
     * Visitor that traverses method bodies to identify parameters that are explicitly checked for null.
     * This visitor looks for:
     * <ul>
     *   <li>Direct null comparisons (param == null, param != null)</li>
     *   <li>Known null-checking method calls (Objects.isNull, StringUtils.isBlank, etc.)</li>
     *   <li>Methods that provide default values for null parameters (Objects.requireNonNullElse, Objects.requireNonNullElseGet)</li>
     *   <li>Methods that handle nullable values (Optional.ofNullable)</li>
     *   <li>Negated null-checking method calls (!Objects.isNull, !StringUtils.isBlank, etc.)</li>
     * </ul>
     */
    private static class NullCheckVisitor extends JavaIsoVisitor<Set<J.Identifier>> {
        private static final List<MethodMatcher> NULL_SAFETY_METHOD_MATCHERS = Arrays.asList(
                new MethodMatcher("com.google.common.base.Strings isNullOrEmpty(..)"), // Guava
                new MethodMatcher("java.util.Objects isNull(..)"),
                new MethodMatcher("java.util.Objects nonNull(..)"),
                new MethodMatcher("java.util.Objects requireNonNullElse(..)"), // Provides default for null
                new MethodMatcher("java.util.Objects requireNonNullElseGet(..)"), // Provides default for null
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

        private final Collection<J.Identifier> identifiers;
        private final Collection<MethodMatcher> nullCheckingMethodMatchers;

        public NullCheckVisitor(Collection<J.Identifier> identifiers, @Nullable Collection<String> additionalNullCheckingMethods) {
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

        @Override
        public J.If visitIf(J.If iff, Set<J.Identifier> nullCheckedParams) {
            iff = super.visitIf(iff, nullCheckedParams);
            handleCondition(iff.getIfCondition().getTree(), nullCheckedParams);
            return iff;
        }

        @Override
        public J.MethodInvocation visitMethodInvocation(J.MethodInvocation method, Set<J.Identifier> nullCheckedParams) {
            // Handle standalone Objects.requireNonNull calls (not just in if conditions)
            if (isKnownNullMethodChecker(method) && method.getArguments().get(0) instanceof J.Identifier) {
                J.Identifier firstArgument = (J.Identifier) method.getArguments().get(0);
                if (containsIdentifierByName(identifiers, firstArgument)) {
                    nullCheckedParams.add(firstArgument);
                }
            }
            return super.visitMethodInvocation(method, nullCheckedParams);
        }

        private void handleCondition(Expression condition, Set<J.Identifier> nullCheckedParams) {
            if (condition instanceof J.Binary) {
                handleBinary((J.Binary) condition, nullCheckedParams);
            } else if (condition instanceof J.MethodInvocation) {
                handleMethodInvocation((J.MethodInvocation) condition, nullCheckedParams);
            } else if (condition instanceof J.Unary) {
                handleUnary((J.Unary) condition, nullCheckedParams);
            }
        }

        private void handleBinary(J.Binary binary, Set<J.Identifier> nullCheckedParams) {
            Expression maybeParam = null;

            if (J.Literal.isLiteralValue(binary.getLeft(), null)) {
                maybeParam = binary.getRight();
            } else if (J.Literal.isLiteralValue(binary.getRight(), null)) {
                maybeParam = binary.getLeft();
            } else {
                handleCondition(binary.getLeft(), nullCheckedParams);
                handleCondition(binary.getRight(), nullCheckedParams);
            }

            if (maybeParam instanceof J.Identifier) {
                J.Identifier identifier = (J.Identifier) maybeParam;
                if (containsIdentifierByName(identifiers, identifier)) {
                    nullCheckedParams.add((J.Identifier) maybeParam);
                }
            }
        }

        private void handleMethodInvocation(J.MethodInvocation mi, Set<J.Identifier> nullCheckedParams) {
            if (isKnownNullMethodChecker(mi)) {
                new JavaIsoVisitor<Set<J.Identifier>>() {
                    @Override
                    public J.Identifier visitIdentifier(J.Identifier identifier, Set<J.Identifier> set) {
                        if (containsIdentifierByName(identifiers, identifier)) {
                            set.add(identifier);
                        }
                        return identifier;
                    }
                }.visit(mi.getArguments(), nullCheckedParams);
            }
        }

        private void handleUnary(J.Unary unary, Set<J.Identifier> nullCheckedParams) {
            if (unary.getExpression() instanceof J.MethodInvocation) {
                handleMethodInvocation((J.MethodInvocation) unary.getExpression(), nullCheckedParams);
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
    }
}
