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
import org.openrewrite.java.AnnotationMatcher;
import org.openrewrite.java.JavaTemplate;
import org.openrewrite.java.JavaVisitor;
import org.openrewrite.java.search.SemanticallyEqual;
import org.openrewrite.java.service.AnnotationService;
import org.openrewrite.java.service.ImportService;
import org.openrewrite.java.tree.*;
import org.openrewrite.kotlin.KotlinVisitor;
import org.openrewrite.kotlin.tree.K;

import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import static java.util.Collections.emptyMap;
import static java.util.Collections.emptySet;
import static java.util.Collections.singleton;
import static java.util.Objects.requireNonNull;
import static org.openrewrite.staticanalysis.JavaElementFactory.*;

@EqualsAndHashCode(callSuper = false)
@Value
public class ReplaceLambdaWithMethodReference extends Recipe {

    String displayName = "Use method references in lambda";

    String description = "Replaces the single statement lambdas `o -> o instanceof X`, " +
            "`o -> (A) o`, `o -> o != null`, " +
            "`o -> o == null` with the equivalent method reference. Method references are often " +
            "more concise and readable than their lambda equivalents, making the code's intent " +
            "clearer at a glance. Captured receivers are retained unless they are known to be stable and non-null.";

    Set<String> tags = singleton("RSPEC-S1612");

    Duration estimatedEffortPerOccurrence = Duration.ofMinutes(2);

    @Override
    public TreeVisitor<?, ExecutionContext> getVisitor() {
        return new TreeVisitor<Tree, ExecutionContext>() {
            @Override
            public @Nullable Tree visit(@Nullable Tree tree, ExecutionContext ctx, Cursor parent) {
                if (tree instanceof J.CompilationUnit) {
                    return new ReplaceLambdaWithMethodReferenceJavaVisitor().visit(tree, ctx);
                }
                if (tree instanceof K.CompilationUnit) {
                    return new ReplaceLambdaWithMethodReferenceKotlinVisitor().visit(tree, ctx);
                }
                return tree;
            }
        };
    }

    private static class ReplaceLambdaWithMethodReferenceKotlinVisitor extends KotlinVisitor<ExecutionContext> {
        // XXX Implement Me
    }

    private static class ReplaceLambdaWithMethodReferenceJavaVisitor extends JavaVisitor<ExecutionContext> {
        private static final AnnotationMatcher BEFORE_TEMPLATE =
                new AnnotationMatcher("@com.google.errorprone.refaster.annotation.BeforeTemplate");

        @Override
        public J visitMethodDeclaration(J.MethodDeclaration method, ExecutionContext ctx) {
            // A Refaster before-template describes the syntax to match, rather than code to modernize.
            if (service(AnnotationService.class).getAllAnnotations(getCursor()).stream().anyMatch(BEFORE_TEMPLATE::matches)) {
                return method;
            }
            return super.visitMethodDeclaration(method, ctx);
        }

        @Override
        public J visitLambda(J.Lambda lambda, ExecutionContext ctx) {
            J.Lambda l = (J.Lambda) super.visitLambda(lambda, ctx);
            updateCursor(l);


            if (insideAnonymousInnerClass() || preservesExplicitParameterType(l)) {
                return l;
            }

            J body = l.getBody();
            if (body instanceof J.Block && ((J.Block) body).getStatements().size() == 1) {
                Statement statement = ((J.Block) body).getStatements().get(0);
                if (statement instanceof J.Return) {
                    body = ((J.Return) statement).getExpression();
                } else {
                    body = statement;
                }
            }

            if (body instanceof J.InstanceOf) {
                J.InstanceOf instanceOf = (J.InstanceOf) body;
                List<J.VariableDeclarations.NamedVariable> lambdaParameters = getLambdaParameters(lambda);
                if (lambdaParameters.size() != 1) {
                    return l;
                }
                J j = instanceOf.getClazz();
                if ((j instanceof J.Identifier || j instanceof J.FieldAccess) &&
                        SemanticallyEqual.areEqual(instanceOf.getExpression(), lambdaParameters.get(0).getName())) {
                    // Create the class literal directly from the original expression
                    JavaType originalType = ((TypeTree) j).getType();
                    JavaType.Class classType = getClassType(originalType);
                    if (classType != null) {
                        J.FieldAccess classLiteral = newClassLiteral(classType, originalType, j);
                        JavaType.FullyQualified rawClassType = ((JavaType.Parameterized) classLiteral.getType()).getType();
                        Optional<JavaType.Method> isInstanceMethod = rawClassType.getMethods().stream().filter(m -> "isInstance".equals(m.getName())).findFirst();
                        if (isInstanceMethod.isPresent()) {
                            J.MemberReference updated = newInstanceMethodReference(classLiteral, isInstanceMethod.get(), lambda.getType()).withPrefix(lambda.getPrefix());
                            doAfterVisit(service(ImportService.class).shortenFullyQualifiedTypeReferencesIn(updated));
                            return updated;
                        }
                    }
                }
                return l;
            }
            if (body instanceof J.TypeCast && l.getParameters().getParameters().size() == 1) {
                J.TypeCast cast = (J.TypeCast) body;
                J param = l.getParameters().getParameters().get(0);
                if (param instanceof J.VariableDeclarations &&
                        SemanticallyEqual.areEqual(cast.getExpression(), ((J.VariableDeclarations) param).getVariables().get(0).getName())) {
                    J.ControlParentheses<TypeTree> j = cast.getClazz();
                    J tree = j.getTree();
                    if ((tree instanceof J.Identifier || tree instanceof J.FieldAccess) &&
                            !(j.getType() instanceof JavaType.GenericTypeVariable)) {
                        // Create the class literal directly from the original expression
                        JavaType.Class classType = getClassType(((Expression) tree).getType());
                        if (classType != null) {
                            J.FieldAccess classLiteral = newClassLiteral(classType, ((Expression) tree).getType(), tree);
                            JavaType.FullyQualified fullClassType = ((JavaType.Parameterized) classLiteral.getType()).getType();
                            Optional<JavaType.Method> castMethod = fullClassType.getMethods().stream().filter(m -> "cast".equals(m.getName())).findFirst();
                            if (castMethod.isPresent()) {
                                J.MemberReference updated = newInstanceMethodReference(classLiteral, castMethod.get(), lambda.getType()).withPrefix(lambda.getPrefix());
                                doAfterVisit(service(ImportService.class).shortenFullyQualifiedTypeReferencesIn(updated));
                                return updated;
                            }
                        }
                    }
                }
                return l;
            }

            String code = "";
            if (body instanceof J.Binary) {
                J.Binary binary = (J.Binary) body;
                if ((binary.getOperator() == J.Binary.Type.Equal || binary.getOperator() == J.Binary.Type.NotEqual) &&
                        (isNullCheck(binary.getLeft(), binary.getRight()) ||
                                isNullCheck(binary.getRight(), binary.getLeft()))) {
                    List<J.VariableDeclarations.NamedVariable> lambdaParameters = getLambdaParameters(lambda);
                    if (lambdaParameters.size() != 1) {
                        return l;
                    }
                    Expression nonNullSide = isNullCheck(binary.getLeft(), binary.getRight()) ? binary.getLeft() : binary.getRight();
                    if (!SemanticallyEqual.areEqual(nonNullSide, lambdaParameters.get(0).getName())) {
                        return l;
                    }
                    code = J.Binary.Type.Equal == binary.getOperator() ? "java.util.Objects::isNull" :
                            "java.util.Objects::nonNull";
                    J updated = JavaTemplate.builder(code)
                            .contextSensitive()
                            .build()
                            .apply(getCursor(), l.getCoordinates().replace());
                    doAfterVisit(service(ImportService.class).shortenFullyQualifiedTypeReferencesIn(updated));
                    return updated;
                }
            } else if (body instanceof MethodCall) {
                MethodCall method = (MethodCall) body;
                if (method instanceof J.NewClass) {
                    J.NewClass nc = (J.NewClass) method;
                    if (nc.getBody() != null) {
                        return l;
                    }
                    if (isAMethodInvocationArgument(l, getCursor())) {
                        JavaType.FullyQualified clazz = TypeUtils.asFullyQualified(nc.getType());
                        if (clazz != null) {
                            boolean hasMultipleConstructors = clazz.getMethods().stream().filter(JavaType.Method::isConstructor).count() > 1;
                            if (hasMultipleConstructors) {
                                return l;
                            }
                        }
                    }
                } else if (method instanceof J.MemberReference) {
                    return l;
                }

                if (method.getMethodType() == null ||
                        hasSelectWithPotentialSideEffects(method) ||
                        hasSelectWhoseReferenceMightChange(method) ||
                        !methodArgumentsMatchLambdaParameters(method, lambda)) {
                    return l;
                }

                JavaType.Method methodType = method.getMethodType();
                if (methodType != null && !isMethodReferenceAmbiguous(methodType)) {
                    Expression select =
                            method instanceof J.MethodInvocation ? ((J.MethodInvocation) method).getSelect() : null;
                    if (methodType.hasFlags(Flag.Static) && select != null && !canDiscardStaticQualifier(select)) {
                        // A value-qualified static call still evaluates its qualifier inside the lambda.
                        return l;
                    }
                    if (!methodType.hasFlags(Flag.Static) && select != null &&
                        !methodSelectMatchesFirstLambdaParameter(method, lambda) && !isStableNonNullReceiver(select)) {
                        // A bound reference evaluates and null-checks its receiver when it is created.
                        return l;
                    }
                    if (methodType.hasFlags(Flag.Static) ||
                            methodSelectMatchesFirstLambdaParameter(method, lambda)) {
                        if (method.getType() instanceof JavaType.Parameterized &&
                                ((JavaType.Parameterized) method.getType()).getTypeParameters().stream()
                                        .anyMatch(JavaType.GenericTypeVariable.class::isInstance)) {
                            return l;
                        }
                        // Check if transforming would break type inference in nested generic/overloaded context
                        if (isLambdaInGenericAndOverloadedContext()) {
                            return l;
                        }
                        JavaType.FullyQualified containingType = methodType.getDeclaringType();
                        if (!methodType.hasFlags(Flag.Static) && select != null) {
                            JavaType.FullyQualified selectType = TypeUtils.asFullyQualified(select.getType());
                            if (selectType != null) {
                                containingType = selectType;
                            }
                        }
                        J.MemberReference updated = newInstanceMethodReference(className(containingType, true), methodType, lambda.getType()).withPrefix(lambda.getPrefix());
                        doAfterVisit(service(ImportService.class).shortenFullyQualifiedTypeReferencesIn(updated));
                        return updated;
                    }
                    if (method instanceof J.NewClass) {
                        NameTree clazz = ((J.NewClass) method).getClazz();
                        clazz = clazz instanceof J.ParameterizedType ? ((J.ParameterizedType) clazz).getClazz() : clazz;
                        return newInstanceMethodReference(clazz.withPrefix(Space.EMPTY), "new", methodType, lambda.getType()).withPrefix(lambda.getPrefix());
                    }
                    if (select != null) {
                        return newInstanceMethodReference(select, methodType, lambda.getType()).withPrefix(lambda.getPrefix());
                    }

                    // Anonymous classes were excluded above, so the innermost class declaration owns `this`
                    J.ClassDeclaration owner = getCursor().firstEnclosing(J.ClassDeclaration.class);
                    if (owner == null) {
                        return l;
                    }
                    String declaringType = methodType.getDeclaringType().getFullyQualifiedName();
                    Expression thisExpression;
                    if (owner.getType() == null || TypeUtils.isAssignableTo(declaringType, owner.getType())) {
                        thisExpression = JavaElementFactory.newThis(owner.getType());
                    } else {
                        // Qualify `this` with the enclosing class that inherits the method, which may not be its declaring
                        // type; an anonymous class in between cannot be named, so leave the lambda as it is
                        Cursor enclosing = getCursor().dropParentUntil(is -> is == Cursor.ROOT_VALUE ||
                                (is instanceof J.ClassDeclaration || is instanceof J.NewClass && ((J.NewClass) is).getBody() != null) &&
                                TypeUtils.isAssignableTo(declaringType, ((TypedTree) is).getType()));
                        if (!(enclosing.getValue() instanceof J.ClassDeclaration)) {
                            return l;
                        }
                        thisExpression = JavaElementFactory.newQualifiedThis(requireNonNull(enclosing.<J.ClassDeclaration>getValue().getType()));
                    }
                    return JavaElementFactory.newInstanceMethodReference(
                            thisExpression,
                            methodType,
                            lambda.getType()
                    ).withPrefix(lambda.getPrefix());
                }
            }

            return l;
        }

        private boolean preservesExplicitParameterType(J.Lambda lambda) {
            boolean explicit = lambda.getParameters().getParameters().stream().anyMatch(parameter ->
                    parameter instanceof J.VariableDeclarations &&
                            ((J.VariableDeclarations) parameter).getTypeExpression() != null &&
                            !((J.VariableDeclarations) parameter).getTypeExpression().getMarkers()
                                    .findFirst(JavaVarKeyword.class).isPresent());
            if (!explicit) {
                return false;
            }
            int lambdaDepth = 0;
            Cursor expression = getCursor();
            while (true) {
                Cursor parent = expression.getParentTreeCursor();
                Object target = parent.getValue();
                if (target instanceof J.Parentheses) {
                    expression = parent;
                } else if (target instanceof J.Ternary) {
                    J.Ternary ternary = (J.Ternary) target;
                    if (expression.getValue() != ternary.getTruePart() &&
                            expression.getValue() != ternary.getFalsePart()) {
                        return false;
                    }
                    expression = parent;
                } else if (target instanceof J.Case && expression.getValue() == ((J.Case) target).getBody() ||
                        target instanceof J.Yield && expression.getValue() == ((J.Yield) target).getValue()) {
                    Cursor switchExpr = parent.dropParentUntil(value -> value instanceof J.SwitchExpression ||
                            (target instanceof J.Case && value instanceof J.Switch) ||
                            value instanceof J.Lambda || value instanceof J.MethodDeclaration ||
                            value == Cursor.ROOT_VALUE);
                    if (!(switchExpr.getValue() instanceof J.SwitchExpression)) {
                        return false;
                    }
                    expression = switchExpr;
                } else if (target instanceof J.Lambda) {
                    if (expression.getValue() != ((J.Lambda) target).getBody()) {
                        return false;
                    }
                    lambdaDepth++;
                    expression = parent;
                } else if (target instanceof J.Return) {
                    Cursor function = parent.dropParentUntil(value -> value instanceof J.MethodDeclaration ||
                            value instanceof J.Lambda || value == Cursor.ROOT_VALUE);
                    if (function.getValue() instanceof J.Lambda) {
                        lambdaDepth++;
                        expression = function;
                    } else if (function.getValue() instanceof J.MethodDeclaration) {
                        TypeTree returnType = function.<J.MethodDeclaration>getValue().getReturnTypeExpression();
                        return targetDependsOnWildcardOrInferredType(
                                returnType == null ? null : returnType.getType(), emptySet(), lambdaDepth, 0);
                    } else {
                        return false;
                    }
                } else if (target instanceof MethodCall) {
                    return methodCallTargetDependsOnWildcardOrInferredType(
                            (MethodCall) target, expression.getValue(), lambdaDepth);
                } else if (target instanceof J.VariableDeclarations.NamedVariable) {
                    return targetDependsOnWildcardOrInferredType(
                            ((J.VariableDeclarations.NamedVariable) target).getType(), emptySet(), lambdaDepth, 0);
                } else if (target instanceof J.Assignment) {
                    J.Assignment assignment = (J.Assignment) target;
                    return expression.getValue() == assignment.getAssignment() &&
                            targetDependsOnWildcardOrInferredType(assignment.getVariable().getType(), emptySet(), lambdaDepth, 0);
                } else if (target instanceof J.TypeCast) {
                    return targetDependsOnWildcardOrInferredType(
                            ((J.TypeCast) target).getType(), emptySet(), lambdaDepth, 0);
                } else {
                    return false;
                }
            }
        }

        private boolean methodCallTargetDependsOnWildcardOrInferredType(MethodCall call, Object argument, int lambdaDepth) {
            JavaType.Method invoked = call.getMethodType();
            int index = call.getArguments().indexOf(argument);
            if (invoked == null || index < 0) {
                return false;
            }
            if (targetDependsOnWildcardOrInferredType(parameterTypeAt(invoked, index), emptySet(), lambdaDepth, 0)) {
                return true;
            }
            JavaType.FullyQualified declaringType = invoked.getDeclaringType();
            if (declaringType == null) {
                return false;
            }
            boolean diamond = false;
            if (call instanceof J.NewClass && ((J.NewClass) call).getClazz() instanceof J.ParameterizedType) {
                List<Expression> typeParameters = ((J.ParameterizedType) ((J.NewClass) call).getClazz()).getTypeParameters();
                diamond = typeParameters != null && typeParameters.size() == 1 && typeParameters.get(0) instanceof J.Empty;
            }
            JavaType.Parameterized parameterizedDeclaring = diamond ? null : TypeUtils.asParameterized(declaringType);
            Map<String, JavaType> baseClassParams = parameterizedDeclaring == null ? emptyMap() :
                    mapTypeParameters(parameterizedDeclaring.getType().getTypeParameters(), parameterizedDeclaring.getTypeParameters());
            boolean explicitCallTypeArgs = call instanceof J.MethodInvocation &&
                    ((J.MethodInvocation) call).getTypeParameters() != null &&
                    !((J.MethodInvocation) call).getTypeParameters().isEmpty();

            for (JavaType.Method candidate : findCandidateDeclarations(invoked, declaringType, baseClassParams)) {
                Set<String> inferredNames = new HashSet<>();
                if (!explicitCallTypeArgs) {
                    inferredNames.addAll(candidate.getDeclaredFormalTypeNames());
                }
                if (diamond) {
                    JavaType.Parameterized p = TypeUtils.asParameterized(declaringType);
                    JavaType.FullyQualified raw = p != null ? p.getType() : declaringType;
                    for (JavaType param : raw.getTypeParameters()) {
                        JavaType.GenericTypeVariable gtv = TypeUtils.asGeneric(param);
                        if (gtv != null) {
                            inferredNames.add(gtv.getName());
                        }
                    }
                }
                if (inferredNames.isEmpty()) {
                    continue;
                }
                Map<String, JavaType> classParams = classTypeParametersForMethod(baseClassParams, candidate);
                JavaType declaredParam = substituteType(parameterTypeAt(candidate, index), classParams, 0);
                if (targetDependsOnWildcardOrInferredType(declaredParam, inferredNames, lambdaDepth, 0)) {
                    return true;
                }
            }
            return false;
        }

        private boolean targetDependsOnWildcardOrInferredType(@Nullable JavaType targetType,
                                                              Set<String> inferredNames,
                                                              int lambdaDepth,
                                                              int depth) {
            if (targetType == null || depth > 8) {
                return false;
            }
            // Wildcards and uninstantiated method/class type parameters in SAM inputs rely on an
            // explicit lambda parameter type to constrain inference or narrow a wildcard target.
            JavaType.GenericTypeVariable gtv = TypeUtils.asGeneric(targetType);
            if (gtv != null) {
                if ("?".equals(gtv.getName()) || (lambdaDepth == 0 && inferredNames.contains(gtv.getName()))) {
                    return true;
                }
                for (JavaType bound : gtv.getBounds()) {
                    if (targetDependsOnWildcardOrInferredType(bound, inferredNames, lambdaDepth, depth + 1)) {
                        return true;
                    }
                }
                return false;
            }
            JavaType.FullyQualified fq = TypeUtils.asFullyQualified(targetType);
            JavaType.Method sam = fq != null && fq.getKind() == JavaType.FullyQualified.Kind.Interface ?
                    UseLambdaForFunctionalInterface.functionalInterfaceMethod(fq) : null;
            if (sam == null) {
                return lambdaDepth == 0 && containsWildcardOrInferred(targetType, inferredNames, 0);
            }
            Map<String, JavaType> samMap = findSamSubstitutionMap(fq, interfaceTypeParameterMap(fq, emptyMap()),
                    sam.getDeclaringType().getFullyQualifiedName(), new HashSet<>());
            if (samMap == null) {
                samMap = emptyMap();
            }
            if (lambdaDepth > 0) {
                JavaType returnType = substituteType(sam.getReturnType(), samMap, 0);
                return targetDependsOnWildcardOrInferredType(returnType, inferredNames, lambdaDepth - 1, depth + 1);
            }
            for (JavaType paramType : sam.getParameterTypes()) {
                JavaType resolvedParamType = substituteType(paramType, samMap, 0);
                if (containsWildcardOrInferred(resolvedParamType, inferredNames, 0)) {
                    return true;
                }
            }
            return false;
        }

        private @Nullable JavaType parameterTypeAt(JavaType.Method method, int index) {
            if (method.getParameterTypes().isEmpty()) {
                return null;
            }
            int last = method.getParameterTypes().size() - 1;
            JavaType targetType = method.getParameterTypes().get(Math.min(index, last));
            JavaType.Array array = method.hasFlags(Flag.Varargs) && index >= last ? TypeUtils.asArray(targetType) : null;
            return array != null ? array.getElemType() : targetType;
        }

        private Map<String, JavaType> mapTypeParameters(List<JavaType> formals, List<JavaType> actuals) {
            int count = Math.min(formals.size(), actuals.size());
            if (count == 0) {
                return emptyMap();
            }
            Map<String, JavaType> map = new HashMap<>(count);
            for (int i = 0; i < count; i++) {
                JavaType.GenericTypeVariable gtv = TypeUtils.asGeneric(formals.get(i));
                if (gtv != null) {
                    map.put(gtv.getName(), actuals.get(i));
                }
            }
            return map;
        }

        private Map<String, JavaType> classTypeParametersForMethod(Map<String, JavaType> baseClassParams,
                                                                     JavaType.Method method) {
            if (baseClassParams.isEmpty() || method.getDeclaredFormalTypeNames().isEmpty()) {
                return baseClassParams;
            }
            Map<String, JavaType> scoped = new HashMap<>(baseClassParams);
            scoped.keySet().removeAll(method.getDeclaredFormalTypeNames());
            return scoped;
        }

        private List<JavaType.Method> findCandidateDeclarations(JavaType.Method invoked,
                                                                JavaType.FullyQualified declaringType,
                                                                Map<String, JavaType> baseClassParams) {
            List<JavaType.Method> candidates = new ArrayList<>();
            for (JavaType.Method m : declaringType.getMethods()) {
                if (m.isConstructor() != invoked.isConstructor() ||
                        !m.getName().equals(invoked.getName()) ||
                        m.hasFlags(Flag.Varargs) != invoked.hasFlags(Flag.Varargs) ||
                        m.getParameterTypes().size() != invoked.getParameterTypes().size()) {
                    continue;
                }
                Map<String, JavaType> classParams = classTypeParametersForMethod(baseClassParams, m);
                if (matchesParameters(m, invoked, classParams)) {
                    candidates.add(m);
                }
            }
            if (candidates.size() <= 1) {
                return candidates;
            }
            List<JavaType.Method> mostSpecific = new ArrayList<>(candidates.size());
            for (JavaType.Method candidate : candidates) {
                Map<String, JavaType> candidateParams = classTypeParametersForMethod(baseClassParams, candidate);
                boolean dominated = false;
                for (JavaType.Method other : candidates) {
                    if (other != candidate && isMoreSpecificCandidate(other, candidate,
                            classTypeParametersForMethod(baseClassParams, other), candidateParams)) {
                        dominated = true;
                        break;
                    }
                }
                if (!dominated) {
                    mostSpecific.add(candidate);
                }
            }
            return mostSpecific;
        }

        private boolean matchesParameters(JavaType.Method candidate, JavaType.Method invoked,
                                          Map<String, JavaType> classParams) {
            for (int i = 0; i < candidate.getParameterTypes().size(); i++) {
                JavaType declared = substituteType(candidate.getParameterTypes().get(i), classParams, 0);
                if (!typeMatchesPattern(declared, invoked.getParameterTypes().get(i), 0)) {
                    return false;
                }
            }
            return true;
        }

        private boolean isMoreSpecificCandidate(JavaType.Method specific, JavaType.Method general,
                                                Map<String, JavaType> specificClassParams,
                                                Map<String, JavaType> generalClassParams) {
            boolean strictlyMoreSpecific = false;
            for (int i = 0; i < specific.getParameterTypes().size(); i++) {
                JavaType specParam = substituteType(specific.getParameterTypes().get(i), specificClassParams, 0);
                JavaType genParam = substituteType(general.getParameterTypes().get(i), generalClassParams, 0);
                if (!isAsSpecificType(specParam, genParam, 0)) {
                    return false;
                }
                if (!isAsSpecificType(genParam, specParam, 0)) {
                    strictlyMoreSpecific = true;
                }
            }
            return strictlyMoreSpecific;
        }

        private boolean isAsSpecificType(@Nullable JavaType specific, @Nullable JavaType general, int depth) {
            if (depth > 8 || specific == null || general == null) {
                return false;
            }
            if (TypeUtils.asGeneric(general) != null) {
                return TypeUtils.asPrimitive(specific) == null;
            }
            if (TypeUtils.asGeneric(specific) != null) {
                return false;
            }
            JavaType.Array specificArray = TypeUtils.asArray(specific);
            JavaType.Array generalArray = TypeUtils.asArray(general);
            if (specificArray != null || generalArray != null) {
                return specificArray != null && generalArray != null &&
                        isAsSpecificType(specificArray.getElemType(), generalArray.getElemType(), depth + 1);
            }
            JavaType.Parameterized specificParam = TypeUtils.asParameterized(specific);
            JavaType.Parameterized generalParam = TypeUtils.asParameterized(general);
            if (specificParam != null && generalParam != null) {
                if (!specificParam.getFullyQualifiedName().equals(generalParam.getFullyQualifiedName()) ||
                        specificParam.getTypeParameters().size() != generalParam.getTypeParameters().size()) {
                    return false;
                }
                for (int i = 0; i < specificParam.getTypeParameters().size(); i++) {
                    if (!isAsSpecificType(specificParam.getTypeParameters().get(i),
                            generalParam.getTypeParameters().get(i), depth + 1)) {
                        return false;
                    }
                }
                return true;
            }
            if (specificParam != null) {
                JavaType.FullyQualified generalFq = TypeUtils.asFullyQualified(general);
                return generalFq != null && specificParam.getFullyQualifiedName().equals(generalFq.getFullyQualifiedName());
            }
            return TypeUtils.isOfType(specific, general);
        }

        private boolean typeMatchesPattern(@Nullable JavaType declared, @Nullable JavaType invoked, int depth) {
            if (depth > 8 || invoked == null || invoked instanceof JavaType.Unknown) {
                return true;
            }
            if (TypeUtils.asGeneric(declared) != null) {
                return TypeUtils.asPrimitive(invoked) == null;
            }
            if (TypeUtils.asGeneric(invoked) != null) {
                return TypeUtils.asPrimitive(declared) == null;
            }
            if (TypeUtils.asPrimitive(declared) != null) {
                return declared.equals(invoked);
            }
            JavaType.Array declaredArray = TypeUtils.asArray(declared);
            JavaType.Array invokedArray = TypeUtils.asArray(invoked);
            if (declaredArray != null || invokedArray != null) {
                return declaredArray != null && invokedArray != null &&
                        typeMatchesPattern(declaredArray.getElemType(), invokedArray.getElemType(), depth + 1);
            }
            JavaType.FullyQualified declaredFq = TypeUtils.asFullyQualified(declared);
            JavaType.FullyQualified invokedFq = TypeUtils.asFullyQualified(invoked);
            if (declaredFq != null && invokedFq != null) {
                if (!declaredFq.getFullyQualifiedName().equals(invokedFq.getFullyQualifiedName())) {
                    return false;
                }
                JavaType.Parameterized dParam = TypeUtils.asParameterized(declaredFq);
                JavaType.Parameterized iParam = TypeUtils.asParameterized(invokedFq);
                if (dParam != null && iParam != null) {
                    List<JavaType> dParams = dParam.getTypeParameters();
                    List<JavaType> iParams = iParam.getTypeParameters();
                    if (dParams.size() != iParams.size()) {
                        return false;
                    }
                    for (int i = 0; i < dParams.size(); i++) {
                        if (!typeMatchesPattern(dParams.get(i), iParams.get(i), depth + 1)) {
                            return false;
                        }
                    }
                }
                return true;
            }
            return false;
        }

        private Map<String, JavaType> interfaceTypeParameterMap(JavaType.FullyQualified fq, Map<String, JavaType> outerMap) {
            JavaType.Parameterized p = TypeUtils.asParameterized(fq);
            if (p != null) {
                List<JavaType> substitutedActuals = outerMap.isEmpty() ? p.getTypeParameters() :
                        ListUtils.map(p.getTypeParameters(), actual -> substituteType(actual, outerMap, 0));
                return mapTypeParameters(p.getType().getTypeParameters(), substitutedActuals);
            }
            if (fq.getTypeParameters().isEmpty()) {
                return emptyMap();
            }
            Map<String, JavaType> erased = new HashMap<>(fq.getTypeParameters().size());
            JavaType.FullyQualified objectType = JavaType.ShallowClass.build("java.lang.Object");
            for (JavaType tp : fq.getTypeParameters()) {
                JavaType.GenericTypeVariable gtv = TypeUtils.asGeneric(tp);
                if (gtv != null) {
                    erased.put(gtv.getName(), objectType);
                }
            }
            return erased;
        }

        private @Nullable Map<String, JavaType> findSamSubstitutionMap(JavaType.FullyQualified current,
                                                                       Map<String, JavaType> currentMap,
                                                                       String targetDeclaringFqn,
                                                                       Set<String> visited) {
            if (!visited.add(current.getFullyQualifiedName())) {
                return null;
            }
            if (current.getFullyQualifiedName().equals(targetDeclaringFqn)) {
                return currentMap;
            }
            for (JavaType.FullyQualified superIface : current.getInterfaces()) {
                Map<String, JavaType> superMap = interfaceTypeParameterMap(superIface, currentMap);
                Map<String, JavaType> found = findSamSubstitutionMap(superIface, superMap, targetDeclaringFqn, visited);
                if (found != null) {
                    return found;
                }
            }
            return null;
        }

        private @Nullable JavaType substituteType(@Nullable JavaType type, Map<String, JavaType> map, int depth) {
            if (type == null || map.isEmpty() || depth > 8) {
                return type;
            }
            JavaType.GenericTypeVariable gtv = TypeUtils.asGeneric(type);
            if (gtv != null) {
                return map.getOrDefault(gtv.getName(), type);
            }
            JavaType.Array array = TypeUtils.asArray(type);
            if (array != null) {
                JavaType elem = substituteType(array.getElemType(), map, depth + 1);
                return elem == null || elem == array.getElemType() ? array : new JavaType.Array(null, elem, null);
            }
            JavaType.Parameterized parameterized = TypeUtils.asParameterized(type);
            if (parameterized != null) {
                List<JavaType> updated = ListUtils.map(parameterized.getTypeParameters(),
                        tp -> substituteType(tp, map, depth + 1));
                return updated == parameterized.getTypeParameters() ?
                        parameterized : parameterized.withTypeParameters(updated);
            }
            return type;
        }

        private boolean containsWildcardOrInferred(@Nullable JavaType type, Set<String> inferredNames, int depth) {
            if (depth > 8) {
                return false;
            }
            JavaType.GenericTypeVariable gtv = TypeUtils.asGeneric(type);
            if (gtv != null) {
                return "?".equals(gtv.getName()) || inferredNames.contains(gtv.getName());
            }
            JavaType.Array array = TypeUtils.asArray(type);
            if (array != null) {
                return containsWildcardOrInferred(array.getElemType(), inferredNames, depth + 1);
            }
            JavaType.Parameterized parameterized = TypeUtils.asParameterized(type);
            return parameterized != null && parameterized.getTypeParameters().stream()
                    .anyMatch(parameter -> containsWildcardOrInferred(parameter, inferredNames, depth + 1));
        }

        private boolean insideAnonymousInnerClass() {
            Cursor current = getCursor();
            while (current != null) {
                if (current.getValue() instanceof J.ClassDeclaration) {
                    return false;
                }
                if (current.getValue() instanceof J.NewClass &&
                        current.<J.NewClass>getValue().getBody() != null) {
                    // Don't replace lambdas inside anonymous inner classes that call unqualified methods
                    // as the "this" reference semantics might change
                    return true;
                }
                current = current.getParent();
            }
            return false;
        }

        private boolean canDiscardStaticQualifier(Expression qualifier) {
            if (qualifier instanceof J.Identifier) {
                JavaType.Variable variable = ((J.Identifier) qualifier).getFieldType();
                return variable == null || variable.getOwner() instanceof JavaType.Method;
            }
            if (qualifier instanceof J.FieldAccess) {
                J.FieldAccess field = (J.FieldAccess) qualifier;
                return field.getName().getFieldType() == null && !"class".equals(field.getSimpleName());
            }
            return qualifier instanceof J.Literal;
        }

        private boolean isStableNonNullReceiver(Expression receiver) {
            if (receiver instanceof J.Identifier) {
                String name = ((J.Identifier) receiver).getSimpleName();
                return "this".equals(name) || "super".equals(name);
            }
            if (receiver instanceof J.FieldAccess) {
                String name = ((J.FieldAccess) receiver).getSimpleName();
                return "this".equals(name) || "super".equals(name) || "class".equals(name);
            }
            return receiver instanceof J.Literal && ((J.Literal) receiver).getValue() != null;
        }

        private boolean hasSelectWithPotentialSideEffects(MethodCall method) {
            return method instanceof J.MethodInvocation &&
                   ((J.MethodInvocation) method).getSelect() instanceof MethodCall;
        }

        private boolean hasSelectWhoseReferenceMightChange(MethodCall method) {
            if (method instanceof J.MethodInvocation) {
                Expression select = ((J.MethodInvocation) method).getSelect();
                if (select instanceof J.Identifier) {
                    JavaType.Variable fieldType = ((J.Identifier) select).getFieldType();
                    return fieldType != null && fieldType.getOwner() instanceof JavaType.Class && !fieldType.hasFlags(Flag.Final);
                }
                if (select instanceof J.FieldAccess) {
                    JavaType.Variable fieldType = ((J.FieldAccess) select).getName().getFieldType();
                    return fieldType != null && fieldType.getOwner() instanceof JavaType.Class && !fieldType.hasFlags(Flag.Final);
                }
                return select instanceof J.NewClass || select instanceof J.Parentheses;
            }
            return false;
        }

        private boolean methodArgumentsMatchLambdaParameters(MethodCall method, J.Lambda lambda) {
            JavaType.Method methodType = method.getMethodType();
            if (methodType == null) {
                return false;
            }
            boolean static_ = methodType.hasFlags(Flag.Static);
            List<Expression> methodArgs = ListUtils.filter(method.getArguments(), a -> !(a instanceof J.Empty));
            List<J.VariableDeclarations.NamedVariable> lambdaParameters = getLambdaParameters(lambda);
            if (methodArgs.isEmpty() && lambdaParameters.isEmpty()) {
                return true;
            }
            if (!static_ && methodSelectMatchesFirstLambdaParameter(method, lambda)) {
                methodArgs = ListUtils.concat(((J.MethodInvocation) method).getSelect(), methodArgs);
            }
            if (methodArgs.size() != lambdaParameters.size()) {
                return false;
            }
            for (int i = 0; i < lambdaParameters.size(); i++) {
                JavaType lambdaParam = lambdaParameters.get(i).getVariableType();
                if (!(methodArgs.get(i) instanceof J.Identifier)) {
                    return false;
                }
                JavaType methodArgument = ((J.Identifier) methodArgs.get(i)).getFieldType();
                if (lambdaParam != methodArgument) {
                    return false;
                }
            }
            return true;
        }

        private static List<J.VariableDeclarations.NamedVariable> getLambdaParameters(J.Lambda lambda) {
            List<J.VariableDeclarations.NamedVariable> list = new ArrayList<>();
            for (J j : lambda.getParameters().getParameters()) {
                if (j instanceof J.VariableDeclarations) {
                    J.VariableDeclarations.NamedVariable namedVariable = ((J.VariableDeclarations) j).getVariables().get(0);
                    list.add(namedVariable);
                }
            }
            return list;
        }

        private boolean methodSelectMatchesFirstLambdaParameter(MethodCall method, J.Lambda lambda) {
            if (!(method instanceof J.MethodInvocation) ||
                !(((J.MethodInvocation) method).getSelect() instanceof J.Identifier) ||
                lambda.getParameters().getParameters().isEmpty() ||
                !(lambda.getParameters().getParameters().get(0) instanceof J.VariableDeclarations)) {
                return false;
            }
            J.VariableDeclarations firstLambdaParameter = (J.VariableDeclarations) lambda.getParameters()
                    .getParameters().get(0);
            return ((J.Identifier) ((J.MethodInvocation) method).getSelect()).getFieldType() ==
                   firstLambdaParameter.getVariables().get(0).getVariableType();
        }

        private boolean isNullCheck(J j1, J j2) {
            return j1 instanceof J.Identifier && j2 instanceof J.Literal &&
                   "null".equals(((J.Literal) j2).getValueSource());
        }

        private boolean isMethodReferenceAmbiguous(JavaType.Method method) {
            int count = 0;
            for (JavaType.Method meth : method.getDeclaringType().getMethods()) {
                if (meth.getName().equals(method.getName()) && !"println".equals(meth.getName()) && !meth.isConstructor()) {
                    if (++count > 1) {
                        return true;
                    }
                }
            }
            return false;
        }

        /**
         * Check if the lambda is in a context where converting it to a method reference
         * would break type inference. This occurs when a lambda's return type depends on
         * generic type inference, and the lambda is passed through method calls where
         * one of the enclosing methods is overloaded.
         * <p>
         * Example: foo(fold(() -> Optional.empty()))
         * where fold is generic and foo is overloaded.
         */
        private boolean isLambdaInGenericAndOverloadedContext() {
            Cursor cursor = getCursor();

            Cursor parent = cursor.dropParentUntil(p -> p instanceof J.MethodInvocation || p instanceof SourceFile);
            if (!(parent.getValue() instanceof J.MethodInvocation)) {
                return false;
            }

            J.MethodInvocation innerMethod = parent.getValue();

            Cursor grandparent = parent.getParent();
            if (grandparent == null) {
                return false;
            }

            grandparent = grandparent.dropParentUntil(p -> p instanceof J.MethodInvocation || p instanceof SourceFile);
            if (!(grandparent.getValue() instanceof J.MethodInvocation)) {
                return false;
            }

            J.MethodInvocation outerMethod = grandparent.getValue();

            // an argument to outerMethod, not just chained onto it
            boolean isInnerMethodAnArgument = outerMethod.getArguments().stream()
                    .anyMatch(arg -> arg == innerMethod);

            if (!isInnerMethodAnArgument) {
                return false;
            }

            JavaType.Method outerType = outerMethod.getMethodType();
            if (outerType == null) {
                return false;
            }

            long overloadCount = outerType.getDeclaringType().getMethods().stream()
                    .filter(m -> m.getName().equals(outerType.getName()) && !m.isConstructor())
                    .count();

            return overloadCount > 1;
        }
    }

    private static boolean isAMethodInvocationArgument(J.Lambda lambda, Cursor cursor) {
        Cursor parent = cursor.dropParentUntil(p -> p instanceof J.MethodInvocation || p instanceof SourceFile);
        if (parent.getValue() instanceof J.MethodInvocation) {
            J.MethodInvocation m = parent.getValue();
            return m.getArguments().stream().anyMatch(arg -> arg == lambda);
        }
        return false;
    }
}
