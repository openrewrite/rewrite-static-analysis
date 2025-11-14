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
import lombok.RequiredArgsConstructor;
import lombok.Value;
import org.jspecify.annotations.Nullable;
import org.openrewrite.*;
import org.openrewrite.internal.ListUtils;
import org.openrewrite.java.*;
import org.openrewrite.java.search.FindAnnotations;
import org.openrewrite.java.search.SemanticallyEqual;
import org.openrewrite.java.tree.*;
import org.openrewrite.staticanalysis.java.MoveFieldAnnotationToType;

import java.util.*;

import static java.util.Comparator.comparing;

@EqualsAndHashCode(callSuper = false)
@Value
public class AnnotateRequiredParameters extends Recipe {

    private static final String DEFAULT_NONNULL_ANN_CLASS = "org.jspecify.annotations.NonNull";

    @Option(displayName = "`@NonNull` annotation class",
            description = "The fully qualified name of the @NonNull annotation. The annotation should be meta annotated with `@Target(TYPE_USE)`. Defaults to `org.jspecify.annotations.NonNull`",
            example = "org.jspecify.annotations.NonNull",
            required = false)
    @Nullable
    String nonNullAnnotationClass;

    @Override
    public String getDisplayName() {
        return "Annotate required method parameters with `@NonNull`";
    }

    @Override
    public String getDescription() {
        return "Add `@NonNull` to parameters of public methods that are explicitly checked for `null` and throw an exception if null. " +
                "By default `org.jspecify.annotations.NonNull` is used, but through the `nonNullAnnotationClass` option a custom annotation can be provided. " +
                "When providing a custom `nonNullAnnotationClass` that annotation should be meta annotated with `@Target(TYPE_USE)`. " +
                "This recipe scans for methods that do not already have parameters annotated with `@NonNull` annotation and checks for " +
                "null validation patterns that throw exceptions, such as `if (param == null) throw new IllegalArgumentException()`.";
    }

    @Override
    public Validated<Object> validate() {
        return super.validate().and(Validated.test(
                "nonNullAnnotationClass",
                "Property `nonNullAnnotationClass` must be a fully qualified classname.",
                nonNullAnnotationClass,
                it -> it == null || it.contains(".")));
    }

    @Override
    public TreeVisitor<?, ExecutionContext> getVisitor() {
        String fullyQualifiedName = nonNullAnnotationClass != null ? nonNullAnnotationClass : DEFAULT_NONNULL_ANN_CLASS;
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

                // Analyze all parameters to find required ones and statements to remove
                RequiredParameterAnalysis analysis = new RequiredParameterVisitor(getAllParameters(md))
                        .reduce(md.getBody(), new RequiredParameterAnalysis());

                if (analysis.requiredIdentifiers.isEmpty()) {
                    return md;
                }

                // Find candidates that need annotation (not already annotated)
                List<J.VariableDeclarations> candidateParameters = findCandidateParameters(md, fullyQualifiedName);
                Map<J.VariableDeclarations, J.Identifier> candidateIdentifiers = buildIdentifierMap(candidateParameters);

                // Annotate parameters that need annotation
                maybeAddImport(fullyQualifiedName);
                md = md.withParameters(ListUtils.map(md.getParameters(), stm -> {
                    if (stm instanceof J.VariableDeclarations) {
                        J.VariableDeclarations vd = (J.VariableDeclarations) stm;
                        if (containsIdentifierByName(analysis.requiredIdentifiers, candidateIdentifiers.get(vd))) {
                            J.VariableDeclarations annotated = JavaTemplate.builder("@" + fullyQualifiedName)
                                    .javaParser(JavaParser.fromJavaVersion().dependsOn(
                                            String.format("package %s;public @interface %s {}", fullyQualifiedPackage, simpleName)))
                                    .build()
                                    .apply(new Cursor(getCursor(), vd),
                                            vd.getCoordinates().addAnnotation(comparing(J.Annotation::getSimpleName)));
                            doAfterVisit(ShortenFullyQualifiedTypeReferences.modifyOnly(annotated));
                            doAfterVisit(new MoveFieldAnnotationToType(fullyQualifiedName).getVisitor());
                            return annotated.withModifiers(ListUtils.mapFirst(annotated.getModifiers(), first -> first.withPrefix(Space.SINGLE_SPACE)));
                        }
                    }
                    return stm;
                }));

                // Replace null checks on required parameters with false
                md = md.withBody((J.Block) new ReplaceNullChecksWithFalse(analysis.requiredIdentifiers).visit(md.getBody(), ctx));

                // Simplify boolean expressions (e.g., "false || x" becomes "x")
                doAfterVisit(new SimplifyBooleanExpression().getVisitor());
                // Simplify constant if branches (e.g., "if (false) throw ..." will be removed)
                doAfterVisit(new SimplifyConstantIfBranchExecution().getVisitor());

                return md;
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
     * Gets all method parameters.
     *
     * @param md the method declaration to analyze
     * @return set of all parameter declarations
     */
    private Set<J.Identifier> getAllParameters(J.MethodDeclaration md) {
        Set<J.Identifier> allParams = new LinkedHashSet<>();
        for (Statement parameter : md.getParameters()) {
            if (parameter instanceof J.VariableDeclarations) {
                allParams.add(((J.VariableDeclarations) parameter).getVariables().get(0).getName());
            }
        }
        return allParams;
    }

    /**
     * Finds method parameters that are candidates for @NonNull annotation.
     * A parameter is a candidate if it doesn't already have the target non-null annotation.
     *
     * @param md  the method declaration to analyze
     * @param fqn the fully qualified name of the non-null annotation
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
     * Result of analyzing a method body for required parameters.
     */
    private static class RequiredParameterAnalysis {
        final Set<J.Identifier> requiredIdentifiers = new HashSet<>();
    }

    /**
     * Visitor that replaces null checks on required parameters with false.
     * This allows SimplifyConstantIfBranchExecution to clean up the dead code.
     */
    @RequiredArgsConstructor
    private static class ReplaceNullChecksWithFalse extends JavaVisitor<ExecutionContext> {
        private static final MethodMatcher REQUIRE_NON_NULL = new MethodMatcher("java.util.Objects requireNonNull(..)");
        private final Set<J.Identifier> requiredIdentifiers;

        @Override
        public J visitBinary(J.Binary binary, ExecutionContext ctx) {
            J.Binary b = (J.Binary) super.visitBinary(binary, ctx);

            // Replace "param == null" or "null == param" with false for required parameters
            if (b.getOperator() == J.Binary.Type.Equal) {
                J.Identifier paramIdentifier = null;

                if (J.Literal.isLiteralValue(b.getLeft(), null) && b.getRight() instanceof J.Identifier) {
                    paramIdentifier = (J.Identifier) b.getRight();
                } else if (J.Literal.isLiteralValue(b.getRight(), null) && b.getLeft() instanceof J.Identifier) {
                    paramIdentifier = (J.Identifier) b.getLeft();
                }

                if (containsIdentifierByName(requiredIdentifiers, paramIdentifier)) {
                    // Replace with false literal
                    return new J.Literal(
                            Tree.randomId(),
                            b.getPrefix(),
                            b.getMarkers(),
                            false,
                            "false",
                            null,
                            JavaType.Primitive.Boolean
                    );
                }
            }

            return b;
        }

        @Override
        public @Nullable J visitMethodInvocation(J.MethodInvocation method, ExecutionContext ctx) {
            J.MethodInvocation m = (J.MethodInvocation) super.visitMethodInvocation(method, ctx);

            // Remove Objects.requireNonNull calls on required parameters
            if (REQUIRE_NON_NULL.matches(m) && !m.getArguments().isEmpty() &&
                    m.getArguments().get(0) instanceof J.Identifier) {
                J.Identifier firstArgument = (J.Identifier) m.getArguments().get(0);
                if (containsIdentifierByName(requiredIdentifiers, firstArgument)) {
                    return null;
                }
            }

            return m;
        }
    }

    /**
     * Visitor that traverses method bodies to identify parameters that are required (non-null).
     * This visitor looks for:
     * <ul>
     *   <li>If statements with null checks that throw exceptions (if (param == null) throw new Exception())</li>
     *   <li>Objects.requireNonNull calls which implicitly throw NullPointerException</li>
     * </ul>
     */
    @RequiredArgsConstructor
    private static class RequiredParameterVisitor extends JavaIsoVisitor<RequiredParameterAnalysis> {
        private static final MethodMatcher REQUIRE_NON_NULL = new MethodMatcher("java.util.Objects requireNonNull(..)");

        private final Collection<J.Identifier> identifiers;

        @Override
        public J.If visitIf(J.If iff, RequiredParameterAnalysis analysis) {
            iff = super.visitIf(iff, analysis);

            // Check if the condition contains null checks
            Expression condition = iff.getIfCondition().getTree();
            List<J.Identifier> nullCheckedParams = extractNullCheckedParameters(condition);

            if (!nullCheckedParams.isEmpty()) {
                // Check if the then-body throws an exception
                if (bodyThrowsException(iff.getThenPart())) {
                    // Add all null-checked parameters as required
                    for (J.Identifier param : nullCheckedParams) {
                        if (containsIdentifierByName(identifiers, param)) {
                            analysis.requiredIdentifiers.add(param);
                        }
                    }
                }
            }

            return iff;
        }

        @Override
        public Statement visitStatement(Statement statement, RequiredParameterAnalysis analysis) {
            // Handle standalone Objects.requireNonNull calls (as expression statements)
            if (statement instanceof J.MethodInvocation) {
                J.MethodInvocation method = (J.MethodInvocation) statement;
                if (REQUIRE_NON_NULL.matches(method) && !method.getArguments().isEmpty() &&
                        method.getArguments().get(0) instanceof J.Identifier) {
                    J.Identifier firstArgument = (J.Identifier) method.getArguments().get(0);
                    if (containsIdentifierByName(identifiers, firstArgument)) {
                        analysis.requiredIdentifiers.add(firstArgument);
                    }
                }
            }
            return super.visitStatement(statement, analysis);
        }

        /**
         * Extracts all parameter identifiers from a null check condition.
         * Handles patterns like:
         * - param == null
         * - null == param
         * - param1 == null || param2 == null (both required)
         * <p>
         * Does NOT handle:
         * - param1 == null && param2 == null (at most one may be null, not both required)
         */
        private List<J.Identifier> extractNullCheckedParameters(Expression condition) {
            List<J.Identifier> params = new ArrayList<>();
            extractNullCheckedParametersRecursive(condition, params);
            return params;
        }

        private void extractNullCheckedParametersRecursive(Expression condition, List<J.Identifier> params) {
            if (condition instanceof J.Binary) {
                J.Binary binary = (J.Binary) condition;
                J.Binary.Type operator = binary.getOperator();

                // Only handle OR operator - means any parameter being null throws exception
                // Do NOT handle AND - "if (a == null && b == null)" means only when BOTH are null is there a problem
                if (operator == J.Binary.Type.Or) {
                    extractNullCheckedParametersRecursive(binary.getLeft(), params);
                    extractNullCheckedParametersRecursive(binary.getRight(), params);
                }
                // Check for == null comparisons
                else if (operator == J.Binary.Type.Equal) {
                    if (J.Literal.isLiteralValue(binary.getLeft(), null) && binary.getRight() instanceof J.Identifier) {
                        params.add((J.Identifier) binary.getRight());
                    } else if (J.Literal.isLiteralValue(binary.getRight(), null) && binary.getLeft() instanceof J.Identifier) {
                        params.add((J.Identifier) binary.getLeft());
                    }
                }
            }
        }

        /**
         * Checks if a statement block throws an exception.
         */
        private boolean bodyThrowsException(Statement body) {
            if (body instanceof J.Throw) {
                return true;
            }
            if (body instanceof J.Block) {
                J.Block block = (J.Block) body;
                // Check if any statement in the block is a throw
                for (Statement statement : block.getStatements()) {
                    if (statement instanceof J.Throw) {
                        return true;
                    }
                }
            }
            return false;
        }
    }
}
