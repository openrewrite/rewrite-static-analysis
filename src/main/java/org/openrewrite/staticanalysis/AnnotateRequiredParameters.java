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
import java.util.concurrent.atomic.AtomicBoolean;

import static java.util.Collections.singletonList;
import static java.util.Comparator.comparing;

@EqualsAndHashCode(callSuper = false)
@Value
public class AnnotateRequiredParameters extends Recipe {

    private static final String DEFAULT_NONNULL_ANN_CLASS = "org.jspecify.annotations.NonNull";

    @Option(displayName = "`@NonNull` annotation class",
            description = "The fully qualified name of the `@NonNull` annotation. The annotation should be meta annotated with `@Target(TYPE_USE)`. Defaults to `org.jspecify.annotations.NonNull`.",
            example = "org.jspecify.annotations.NonNull",
            required = false)
    @Nullable
    String nonNullAnnotationClass;

    String displayName = "Annotate required method parameters with `@NonNull`";

    String description = "Add `@NonNull` to parameters of public methods that are explicitly checked for `null` and throw an exception if null. " +
                "By default `org.jspecify.annotations.NonNull` is used, but through the `nonNullAnnotationClass` option a custom annotation can be provided. " +
                "When providing a custom `nonNullAnnotationClass` that annotation should be meta annotated with `@Target(TYPE_USE)`. " +
                "This recipe scans for methods that do not already have parameters annotated with `@NonNull` annotation and checks for " +
                "null validation patterns that throw exceptions, such as `if (param == null) throw new IllegalArgumentException()`.";

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

                if (!md.hasModifier(J.Modifier.Type.Public) || md.getBody() == null ||
                        md.getParameters().isEmpty() || md.getParameters().get(0) instanceof J.Empty ||
                        md.getMethodType() == null || md.getMethodType().isOverride()) {
                    return md;
                }

                RequiredParameterAnalysis analysis = new RequiredParameterVisitor(getCandidateParameters(md))
                        .reduce(md.getBody(), new RequiredParameterAnalysis());

                if (analysis.requiredIdentifiers.isEmpty()) {
                    return md;
                }

                md = addAnnotationsToParameters(md, analysis, ctx);

                J.Block body = md.getBody();
                J.Block replaced = (J.Block) new ReplaceNullChecksWithFalse(analysis.requiredIdentifiers).visitNonNull(body, ctx);
                maybeRemoveImport("java.util.Objects");

                // Simplify only the statements with a replaced null check, e.g. "if (false || x) throw ..." becomes "if (x) throw ..."
                return md.withBody(replaced.withStatements(ListUtils.flatMap(replaced.getStatements(), statement -> {
                    if (body.getStatements().stream().anyMatch(original -> original == statement)) {
                        return statement;
                    }
                    J.Block single = replaced.withStatements(singletonList(statement));
                    single = (J.Block) new SimplifyBooleanExpression().getVisitor().visitNonNull(single, ctx, getCursor());
                    single = (J.Block) new SimplifyConstantIfBranchExecution().getVisitor().visitNonNull(single, ctx, getCursor());
                    return single.getStatements();
                })));
            }

            private J.MethodDeclaration addAnnotationsToParameters(J.MethodDeclaration md, RequiredParameterAnalysis analysis, ExecutionContext ctx) {
                maybeAddImport(fullyQualifiedName);
                String nullableFqn = fullyQualifiedPackage + ".Nullable";

                return md.withParameters(ListUtils.map(md.getParameters(), stm -> {
                    if (stm instanceof J.VariableDeclarations) {
                        J.VariableDeclarations vd = (J.VariableDeclarations) stm;
                        J.Identifier identifier = vd.getVariables().get(0).getName();
                        if (containsIdentifierByName(analysis.requiredIdentifiers, identifier) &&
                                FindAnnotations.find(vd, "@" + fullyQualifiedName).isEmpty()) {

                            maybeRemoveImport(nullableFqn);
                            vd = (J.VariableDeclarations) new RemoveAnnotationVisitor(new AnnotationMatcher(nullableFqn)).visit(vd, ctx, getCursor());

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
            }
        };
    }

    private static boolean containsIdentifierByName(Collection<J.Identifier> identifiers, J.@Nullable Identifier target) {
        if (target == null) {
            return false;
        }
        for (J.Identifier identifier : identifiers) {
            if (SemanticallyEqual.areEqual(identifier, target)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Excludes parameters marked `@Nullable`: the developer intends to allow nulls there,
     * so any null check and throw is intentional business logic that must not be removed.
     */
    private Set<J.Identifier> getCandidateParameters(J.MethodDeclaration md) {
        Set<J.Identifier> candidates = new LinkedHashSet<>();
        for (Statement parameter : md.getParameters()) {
            if (parameter instanceof J.VariableDeclarations) {
                J.VariableDeclarations vd = (J.VariableDeclarations) parameter;
                boolean nullable = new JavaIsoVisitor<AtomicBoolean>() {
                    @Override
                    public J.Annotation visitAnnotation(J.Annotation annotation, AtomicBoolean f) {
                        if ("Nullable".equals(annotation.getSimpleName())) {
                            f.set(true);
                        }
                        return annotation;
                    }
                }.reduce(vd, new AtomicBoolean()).get();
                if (nullable) {
                    continue;
                }
                candidates.add(vd.getVariables().get(0).getName());
            }
        }
        return candidates;
    }

    private static class RequiredParameterAnalysis {
        final Set<J.Identifier> requiredIdentifiers = new HashSet<>();
    }

    @RequiredArgsConstructor
    private static class RequiredParameterVisitor extends JavaIsoVisitor<RequiredParameterAnalysis> {
        private static final MethodMatcher REQUIRE_NON_NULL = new MethodMatcher("java.util.Objects requireNonNull(..)");

        private final Collection<J.Identifier> parameterIdentifiers;

        @Override
        public J.If visitIf(J.If iff, RequiredParameterAnalysis analysis) {
            Expression condition = iff.getIfCondition().getTree();
            List<J.Identifier> nullCheckedParams = extractNullCheckedParameters(condition);

            if (!nullCheckedParams.isEmpty()) {
                // Only treat the null check as redundant when the throw carries no information:
                // a JDK exception type with no constructor arguments. A custom exception type or
                // a non-empty message indicates intentional business logic that must be preserved.
                if (throwsInformationlessException(iff.getThenPart())) {
                    for (J.Identifier param : nullCheckedParams) {
                        if (containsIdentifierByName(parameterIdentifiers, param)) {
                            analysis.requiredIdentifiers.add(param);
                        }
                    }
                }
            }

            return iff; // Do not call super.visitIf as nested ifs are tough to account for
        }

        @Override
        public Statement visitStatement(Statement statement, RequiredParameterAnalysis analysis) {
            if (statement instanceof J.MethodInvocation) {
                J.MethodInvocation method = (J.MethodInvocation) statement;
                if (REQUIRE_NON_NULL.matches(method) && !method.getArguments().isEmpty() &&
                        method.getArguments().get(0) instanceof J.Identifier) {
                    J.Identifier firstArgument = (J.Identifier) method.getArguments().get(0);
                    if (containsIdentifierByName(parameterIdentifiers, firstArgument)) {
                        analysis.requiredIdentifiers.add(firstArgument);
                    }
                }
            }
            return super.visitStatement(statement, analysis);
        }

        private List<J.Identifier> extractNullCheckedParameters(Expression condition) {
            List<J.Identifier> params = new ArrayList<>();
            extractNullCheckedParametersRecursive(condition, params);
            return params;
        }

        private void extractNullCheckedParametersRecursive(Expression condition, List<J.Identifier> params) {
            if (condition instanceof J.Binary) {
                J.Binary binary = (J.Binary) condition;
                J.Binary.Type operator = binary.getOperator();

                // not `&&`: "if (a == null && b == null)" only throws when both are null, so neither is required
                if (operator == J.Binary.Type.Or) {
                    extractNullCheckedParametersRecursive(binary.getLeft(), params);
                    extractNullCheckedParametersRecursive(binary.getRight(), params);
                } else if (operator == J.Binary.Type.Equal) {
                    if (J.Literal.isLiteralValue(binary.getLeft(), null) && binary.getRight() instanceof J.Identifier) {
                        params.add((J.Identifier) binary.getRight());
                    } else if (J.Literal.isLiteralValue(binary.getRight(), null) && binary.getLeft() instanceof J.Identifier) {
                        params.add((J.Identifier) binary.getLeft());
                    }
                }
            }
        }

        private boolean throwsInformationlessException(Statement body) {
            J.Throw thr = findThrow(body);
            if (thr == null || !(thr.getException() instanceof J.NewClass)) {
                return false;
            }
            J.NewClass nc = (J.NewClass) thr.getException();
            JavaType.FullyQualified fq = TypeUtils.asFullyQualified(nc.getType());
            if (fq == null || !fq.getFullyQualifiedName().startsWith("java.")) {
                return false;
            }
            List<Expression> args = nc.getArguments();
            if (args == null || args.isEmpty()) {
                return true;
            }
            return args.size() == 1 && args.get(0) instanceof J.Empty;
        }

        private J.@Nullable Throw findThrow(Statement body) {
            if (body instanceof J.Throw) {
                return (J.Throw) body;
            }
            if (body instanceof J.Block) {
                for (Statement s : ((J.Block) body).getStatements()) {
                    if (s instanceof J.Throw) {
                        return (J.Throw) s;
                    }
                }
            }
            return null;
        }
    }

    @RequiredArgsConstructor
    private static class ReplaceNullChecksWithFalse extends JavaVisitor<ExecutionContext> {
        private static final MethodMatcher REQUIRE_NON_NULL = new MethodMatcher("java.util.Objects requireNonNull(..)");
        private final Set<J.Identifier> requiredIdentifiers;

        @Override
        public J visitBinary(J.Binary binary, ExecutionContext ctx) {
            J.Binary b = (J.Binary) super.visitBinary(binary, ctx);

            if (b.getOperator() == J.Binary.Type.Equal) {
                J.Identifier paramIdentifier = null;

                if (J.Literal.isLiteralValue(b.getLeft(), null) && b.getRight() instanceof J.Identifier) {
                    paramIdentifier = (J.Identifier) b.getRight();
                } else if (J.Literal.isLiteralValue(b.getRight(), null) && b.getLeft() instanceof J.Identifier) {
                    paramIdentifier = (J.Identifier) b.getLeft();
                }

                if (containsIdentifierByName(requiredIdentifiers, paramIdentifier)) {
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

            if (REQUIRE_NON_NULL.matches(m) &&
                    m.getArguments().get(0) instanceof J.Identifier &&
                    containsIdentifierByName(requiredIdentifiers, (J.Identifier) m.getArguments().get(0))) {
                Cursor parent = getCursor().getParentTreeCursor();
                if (parent.getValue() instanceof J.Block) {
                    return null;
                }
                return m.getArguments().get(0).withPrefix(m.getPrefix());
            }

            return m;
        }
    }
}
