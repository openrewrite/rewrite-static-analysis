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
import org.openrewrite.internal.ListUtils;
import org.openrewrite.internal.StringUtils;
import org.openrewrite.java.JavaIsoVisitor;
import org.openrewrite.java.tree.*;
import org.openrewrite.marker.Markers;

import java.util.Arrays;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

import static java.util.stream.Collectors.toList;
import static org.openrewrite.java.tree.J.Wildcard.Bound.Extends;
import static org.openrewrite.java.tree.J.Wildcard.Bound.Super;

@EqualsAndHashCode(callSuper = false)
@Value
public class DeclarationSiteTypeVariance extends Recipe {

    @Option(displayName = "Variant types",
            description = "A list of well-known classes that have in/out type variance.",
            example = "java.util.function.Function<IN, OUT>")
    List<String> variantTypes;

    @Option(displayName = "Excluded bounds",
            description = "A list of bounds that should not receive explicit variance. Globs supported.",
            example = "java.lang.*",
            required = false)
    @Nullable
    List<String> excludedBounds;

    @Option(displayName = "Exclude final classes",
            description = "If true, do not add `? extends` variance to final classes. " +
                          "`? super` variance will be added regardless of finality.",
            required = false)
    @Nullable
    Boolean excludeFinalClasses;

    String displayName = "Properly use declaration-site type variance";

    String description = "Currently, Java requires use-site type variance, so if someone has `Function<IN, OUT>` method parameter, it should rather be `Function<? super IN, ? extends OUT>`. " +
               "Unfortunately, it is not easy to notice that `? super` and `? extends` is missing, so this recipe adds it where that would improve the situation.";

    @Override
    public Validated<Object> validate() {
        Validated<Object> v = super.validate();
        v = v.and(Validated.required("variantTypes", variantTypes));
        if (v.isValid()) {
            for (String variantType : variantTypes) {
                v = v.and(Validated.test("variantTypes", "Must be a valid variant type", variantType, vt -> {
                    try {
                        VariantTypeSpec.build(vt);
                        return true;
                    } catch (Throwable ignored) {
                        return false;
                    }
                }));
            }
        }
        return v;
    }

    @Override
    public TreeVisitor<?, ExecutionContext> getVisitor() {
        List<VariantTypeSpec> variantTypeSpecs = variantTypes.stream().map(VariantTypeSpec::build).collect(toList());
        return new JavaIsoVisitor<ExecutionContext>() {
            @Override
            public J.MethodDeclaration visitMethodDeclaration(J.MethodDeclaration method, ExecutionContext ctx) {
                J.MethodDeclaration m = super.visitMethodDeclaration(method, ctx);
                if (m.getMethodType() != null && m.getMethodType().isOverride()) {
                    return m;
                }
                return m.withParameters(ListUtils.map(m.getParameters(), param -> {
                    if (param instanceof J.VariableDeclarations) {
                        J.VariableDeclarations varParam = (J.VariableDeclarations) param;
                        if (varParam.getTypeExpression() instanceof J.ParameterizedType) {
                            J.ParameterizedType pt = (J.ParameterizedType) varParam.getTypeExpression();
                            for (VariantTypeSpec variantTypeSpec : variantTypeSpecs) {
                                if (variantTypeSpec.hasType(pt) && !isStoredInvariantly(m, varParam)) {
                                    return varParam.withTypeExpression(useDeclarationSiteVariance(pt, variantTypeSpec));
                                }
                            }

                        }
                    }
                    return param;
                }));
            }

            private boolean isStoredInvariantly(J.MethodDeclaration method, J.VariableDeclarations parameter) {
                if (method.getBody() == null || parameter.getVariables().size() != 1) {
                    return false;
                }
                JavaType.Variable parameterType = parameter.getVariables().get(0).getVariableType();
                if (parameterType == null) {
                    return false;
                }
                return new JavaIsoVisitor<AtomicBoolean>() {
                    @Override
                    public J.Assignment visitAssignment(J.Assignment assignment, AtomicBoolean stored) {
                        J.Assignment visitedAssignment = super.visitAssignment(assignment, stored);
                        if (TypeUtils.isOfType(visitedAssignment.getVariable().getType(), parameterType.getType()) &&
                            !references(visitedAssignment.getVariable(), parameterType) &&
                            directlyReferences(visitedAssignment.getAssignment(), parameterType)) {
                            stored.set(true);
                        }
                        return visitedAssignment;
                    }

                    @Override
                    public J.VariableDeclarations.NamedVariable visitVariable(
                            J.VariableDeclarations.NamedVariable variable, AtomicBoolean stored) {
                        J.VariableDeclarations.NamedVariable visitedVariable = super.visitVariable(variable, stored);
                        J.VariableDeclarations declarations = getCursor().firstEnclosing(J.VariableDeclarations.class);
                        boolean explicitlyTyped = declarations != null &&
                                !(declarations.getTypeExpression() instanceof J.Identifier &&
                                  "var".equals(((J.Identifier) declarations.getTypeExpression()).getSimpleName()));
                        if (explicitlyTyped && visitedVariable.getInitializer() != null &&
                            TypeUtils.isOfType(visitedVariable.getType(), parameterType.getType()) &&
                            directlyReferences(visitedVariable.getInitializer(), parameterType)) {
                            stored.set(true);
                        }
                        return visitedVariable;
                    }
                }.reduce(method.getBody(), new AtomicBoolean()).get();
            }

            private boolean references(Expression expression, JavaType.Variable parameterType) {
                return new JavaIsoVisitor<AtomicBoolean>() {
                    @Override
                    public J.Lambda visitLambda(J.Lambda lambda, AtomicBoolean found) {
                        return lambda;
                    }

                    @Override
                    public J.MemberReference visitMemberReference(J.MemberReference memberRef, AtomicBoolean found) {
                        return memberRef;
                    }

                    @Override
                    public J.Identifier visitIdentifier(J.Identifier identifier, AtomicBoolean found) {
                        if (parameterType.equals(identifier.getFieldType())) {
                            found.set(true);
                        }
                        return identifier;
                    }
                }.reduce(expression, new AtomicBoolean()).get();
            }

            private boolean directlyReferences(Expression expression, JavaType.Variable parameterType) {
                Expression unwrapped = expression.unwrap();
                return unwrapped instanceof J.Identifier &&
                       parameterType.equals(((J.Identifier) unwrapped).getFieldType());
            }

            private J.ParameterizedType useDeclarationSiteVariance(J.ParameterizedType pt, VariantTypeSpec spec) {
                return pt.withTypeParameters(ListUtils.map(pt.getTypeParameters(), (i, tp) -> {
                    VariantTypeSpec.Variance variance = spec.getVariances().get(i);
                    if (tp instanceof J.Wildcard ||
                        !(tp instanceof NameTree) ||
                        variance == VariantTypeSpec.Variance.INVARIANT) {
                        return tp;
                    }

                    JavaType.FullyQualified fq = TypeUtils.asFullyQualified(tp.getType());
                    if (fq != null) {
                        if (excludedBounds != null) {
                            for (String excludedBound : excludedBounds) {
                                if (StringUtils.matchesGlob(fq.getFullyQualifiedName(), excludedBound)) {
                                    return tp;
                                }
                            }
                        }
                        if (Boolean.TRUE.equals(excludeFinalClasses) && fq.getFlags().contains(Flag.Final) &&
                            variance == VariantTypeSpec.Variance.OUT) {
                            return tp;
                        }
                    }

                    return new J.Wildcard(
                            Tree.randomId(),
                            tp.getPrefix(),
                            Markers.EMPTY,
                            JLeftPadded.build(variance == VariantTypeSpec.Variance.OUT ? Extends : Super)
                                    .withBefore(Space.format(" ")),
                            tp.withPrefix(Space.format(" "))
                    );
                }));
            }
        };
    }

    @Value
    private static class VariantTypeSpec {
        String fullyQualifiedName;
        List<Variance> variances;

        enum Variance {
            IN,
            OUT,
            INVARIANT
        }

        public boolean hasType(J.ParameterizedType pt) {
            return TypeUtils.isOfClassType(pt.getType(), fullyQualifiedName);
        }

        public static VariantTypeSpec build(String pattern) {
            String fqn = pattern.substring(0, pattern.indexOf('<'));
            String variancesStr = pattern.substring(pattern.indexOf('<') + 1, pattern.lastIndexOf('>'));
            return new VariantTypeSpec(fqn, Arrays.stream(variancesStr.split(","))
                    .map(String::trim)
                    .map(Variance::valueOf)
                    .collect(toList()));
        }
    }
}
