/*
 * Copyright 2026 the original author or authors.
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
package org.openrewrite.staticanalysis.kotlin;

import lombok.Getter;
import org.jspecify.annotations.Nullable;
import org.openrewrite.*;
import org.openrewrite.java.marker.OmitParentheses;
import org.openrewrite.java.service.ImportService;
import org.openrewrite.java.tree.*;
import org.openrewrite.kotlin.KotlinVisitor;
import org.openrewrite.kotlin.marker.AnonymousFunction;
import org.openrewrite.kotlin.marker.Extension;
import org.openrewrite.kotlin.marker.IsNullSafe;
import org.openrewrite.kotlin.marker.TrailingLambdaArgument;
import org.openrewrite.kotlin.tree.K;
import org.openrewrite.marker.Markers;

import java.time.Duration;
import java.util.*;

import static java.util.Arrays.asList;
import static java.util.Collections.*;
import static org.openrewrite.Tree.randomId;
import static org.openrewrite.staticanalysis.JavaElementFactory.className;

public class ReplaceLambdaWithCallableReference extends Recipe {

    @Getter
    final String displayName = "Use callable references in Kotlin lambdas";

    @Getter
    final String description = "Replaces Kotlin lambdas whose body only passes the lambda parameters on to a single call, " +
            "such as `{ it.name }`, `{ p -> p.greet() }`, `{ println(it) }` or `{ Box(it) }`, with the equivalent callable " +
            "reference (`Person::name`, `Person::greet`, `::println`, `::Box`). Callable references are often more " +
            "concise and readable than their lambda equivalents, making the code's intent clearer at a glance.";

    @Getter
    final Duration estimatedEffortPerOccurrence = Duration.ofMinutes(2);

    @Override
    public TreeVisitor<?, ExecutionContext> getVisitor() {
        return Preconditions.check(new KotlinFileChecker<>(), ReplaceLambdaWithCallableReferenceVisitor.create());
    }

    private static class ReplaceLambdaWithCallableReferenceVisitor extends KotlinVisitor<ExecutionContext> {
        private static final Set<String> DEFAULT_IMPORTED_PACKAGES = new HashSet<>(asList(
                "kotlin", "kotlin.annotation", "kotlin.collections", "kotlin.comparisons", "kotlin.io",
                "kotlin.ranges", "kotlin.sequences", "kotlin.text", "kotlin.jvm", "java.lang"));

        /**
         * rewrite-kotlin is an optional dependency, so only this class may refer to {@link KotlinVisitor} as a
         * {@link TreeVisitor}; otherwise the recipe class itself could not be loaded without rewrite-kotlin.
         */
        static TreeVisitor<?, ExecutionContext> create() {
            return new ReplaceLambdaWithCallableReferenceVisitor();
        }

        @Override
        public J visitMethodInvocation(J.MethodInvocation method, ExecutionContext ctx) {
            J.MethodInvocation m = (J.MethodInvocation) super.visitMethodInvocation(method, ctx);
            JContainer<Expression> arguments = m.getPadding().getArguments();
            List<JRightPadded<Expression>> args = new ArrayList<>(arguments.getPadding().getElements());
            int last = args.size() - 1;
            if (last < 0 || !(args.get(last).getElement() instanceof J.MemberReference) ||
                    !args.get(last).getElement().getMarkers().findFirst(TrailingLambdaArgument.class).isPresent()) {
                return m;
            }
            JRightPadded<Expression> trailing = args.get(last);
            Expression reference = trailing.getElement().withMarkers(trailing.getElement().getMarkers().removeByType(TrailingLambdaArgument.class));
            if (last == 0 || last == 1 && args.get(0).getElement() instanceof J.Empty) {
                boolean omitParentheses = arguments.getMarkers().findFirst(OmitParentheses.class).isPresent();
                args = singletonList(trailing.withElement(reference.withPrefix(Space.EMPTY)).withAfter(Space.EMPTY));
                arguments = arguments.withMarkers(arguments.getMarkers().removeByType(OmitParentheses.class))
                        .withBefore(omitParentheses ? Space.EMPTY : arguments.getBefore());
            } else {
                args.set(last, trailing.withElement(reference.withPrefix(Space.SINGLE_SPACE)));
            }
            return m.getPadding().withArguments(arguments.getPadding().withElements(args));
        }

        @Override
        public J visitLambda(J.Lambda lambda, ExecutionContext ctx) {
            J.Lambda l = (J.Lambda) super.visitLambda(lambda, ctx);
            if (l.getMarkers().findFirst(AnonymousFunction.class).isPresent() ||
                    !(l.getBody() instanceof J.Block) || ((J.Block) l.getBody()).getStatements().size() != 1) {
                return l;
            }
            Optional<TrailingLambdaArgument> trailing = l.getMarkers().findFirst(TrailingLambdaArgument.class);
            Object parent = getCursor().getParentTreeCursor().getValue();
            if (parent instanceof J.Label || trailing.isPresent() && !isLastPositionalArgument(parent)) {
                return l;
            }
            List<String> params = getLambdaParameterNames(l);
            if (params == null || params.isEmpty()) {
                return l;
            }

            J body = ((J.Block) l.getBody()).getStatements().get(0);
            if (body instanceof K.ExpressionStatement) {
                body = ((K.ExpressionStatement) body).getExpression();
            } else if (body instanceof J.Return) {
                body = ((J.Return) body).getExpression();
            }

            J.MemberReference reference = null;
            if (body instanceof J.FieldAccess) {
                J.FieldAccess fieldAccess = (J.FieldAccess) body;
                JavaType.Variable fieldType = fieldAccess.getName().getFieldType();
                if (fieldType != null && params.size() == 1 && isParameter(fieldAccess.getTarget(), params.get(0)) &&
                        expectsNonUnitResult(lambda) &&
                        !fieldAccess.getMarkers().findFirst(IsNullSafe.class).isPresent() &&
                        isMemberOf(fieldType.getOwner(), fieldAccess.getTarget().getType()) &&
                        !isSyntheticJavaProperty(fieldType)) {
                    reference = newKotlinReference(typeName((JavaType.Class) fieldAccess.getTarget().getType()),
                            fieldAccess.getSimpleName(), null, fieldType);
                }
            } else if (body instanceof J.MethodInvocation) {
                reference = replaceMethodInvocation((J.MethodInvocation) body, params);
            } else if (body instanceof J.NewClass) {
                J.NewClass newClass = (J.NewClass) body;
                JavaType.Method constructorType = newClass.getConstructorType();
                JavaType.Class classType = newClass.getType() instanceof JavaType.Class ? (JavaType.Class) newClass.getType() : null;
                if (newClass.getBody() == null && newClass.getEnclosing() == null && newClass.getClazz() instanceof J.Identifier &&
                        classType != null && constructorType != null &&
                        classType.getMethods().stream().filter(JavaType.Method::isConstructor).count() == 1 &&
                        argumentsMatchParameters(newClass.getArguments(), params, constructorType)) {
                    reference = newKotlinReference(new J.Empty(randomId(), Space.EMPTY, Markers.EMPTY),
                            ((J.Identifier) newClass.getClazz()).getSimpleName(), constructorType, null);
                }
            }

            if (reference == null) {
                return l;
            }
            reference = reference.withPrefix(l.getPrefix());
            return trailing.isPresent() ? reference.withMarkers(reference.getMarkers().add(trailing.get())) : reference;
        }

        /**
         * A trailing lambda is always bound to the last parameter, while a reference moved into the argument list is bound
         * by position, so only move it when no parameter with a default value is skipped and no argument is named.
         */
        private static boolean isLastPositionalArgument(Object parent) {
            if (!(parent instanceof J.MethodInvocation) || ((J.MethodInvocation) parent).getMethodType() == null) {
                return false;
            }
            List<String> parameterNames = ((J.MethodInvocation) parent).getMethodType().getParameterNames();
            int parameterCount = parameterNames.size() - (!parameterNames.isEmpty() && parameterNames.get(0).startsWith("$this") ? 1 : 0);
            int argumentCount = 0;
            for (Expression argument : ((J.MethodInvocation) parent).getArguments()) {
                if (argument instanceof J.Assignment) {
                    return false;
                }
                if (!(argument instanceof J.Empty)) {
                    argumentCount++;
                }
            }
            return argumentCount == parameterCount;
        }

        /**
         * Unlike function references, property references are not adapted to a function type returning `Unit`.
         */
        private boolean expectsNonUnitResult(J.Lambda lambda) {
            Object parent = getCursor().getParentTreeCursor().getValue();
            JavaType expected = null;
            if (parent instanceof J.VariableDeclarations.NamedVariable) {
                expected = ((J.VariableDeclarations.NamedVariable) parent).getType();
            } else if (parent instanceof J.MethodInvocation && ((J.MethodInvocation) parent).getMethodType() != null) {
                JavaType.Method methodType = ((J.MethodInvocation) parent).getMethodType();
                List<String> parameterNames = methodType.getParameterNames();
                int index = !parameterNames.isEmpty() && parameterNames.get(0).startsWith("$this") ? 1 : 0;
                for (Expression argument : ((J.MethodInvocation) parent).getArguments()) {
                    if (argument == lambda) {
                        expected = index < methodType.getParameterTypes().size() ? methodType.getParameterTypes().get(index) : null;
                        break;
                    }
                    if (!(argument instanceof J.Empty)) {
                        index++;
                    }
                }
            }
            if (!(expected instanceof JavaType.Parameterized) ||
                    !((JavaType.Parameterized) expected).getFullyQualifiedName().startsWith("kotlin.Function")) {
                return false;
            }
            List<JavaType> typeParameters = ((JavaType.Parameterized) expected).getTypeParameters();
            return !typeParameters.isEmpty() && typeParameters.get(typeParameters.size() - 1) != JavaType.Primitive.Void;
        }

        private J.@Nullable MemberReference replaceMethodInvocation(J.MethodInvocation method, List<String> params) {
            JavaType.Method methodType = method.getMethodType();
            if (methodType == null || methodType.hasFlags(Flag.Varargs) || method.getTypeParameters() != null ||
                    !methodType.getName().equals(method.getSimpleName()) ||
                    method.getMarkers().findFirst(IsNullSafe.class).isPresent()) {
                return null;
            }
            JavaType.FullyQualified declaringType = methodType.getDeclaringType();
            Expression select = method.getSelect();
            if (select == null && methodType.hasFlags(Flag.Static)) {
                // Overloads of top-level functions are not listed on the file facade type, so rely on
                // the expected parameter type of the enclosing call to resolve the function reference
                return getCursor().getParentTreeCursor().getValue() instanceof J.MethodInvocation &&
                        argumentsMatchParameters(method.getArguments(), params, methodType) ?
                        newKotlinReference(new J.Empty(randomId(), Space.EMPTY, Markers.EMPTY), method.getSimpleName(), methodType, null) :
                        null;
            }
            if (declaringType.getMethods().stream().filter(m -> m.getName().equals(methodType.getName()) && !m.isConstructor()).count() != 1) {
                return null;
            }
            if (select == null) {
                if (!argumentsMatchParameters(method.getArguments(), params, methodType)) {
                    return null;
                }
                JavaType.FullyQualified thisType = enclosingThisType();
                if (thisType != null && TypeUtils.isAssignableTo(declaringType, thisType)) {
                    return newKotlinReference(new K.This(randomId(), Space.EMPTY, Markers.EMPTY, null, thisType), method.getSimpleName(), methodType, null);
                }
                return null;
            }
            if (isParameter(select, params.get(0))) {
                if (isMemberOf(declaringType, select.getType()) &&
                        argumentsMatchParameters(method.getArguments(), params.subList(1, params.size()), methodType)) {
                    return newKotlinReference(typeName((JavaType.Class) select.getType()), method.getSimpleName(), methodType, null);
                }
                return null;
            }
            if (select instanceof J.Identifier && ((J.Identifier) select).getFieldType() == null &&
                    TypeUtils.isOfClassType(select.getType(), declaringType.getFullyQualifiedName()) &&
                    argumentsMatchParameters(method.getArguments(), params, methodType)) {
                return newKotlinReference(select.withPrefix(Space.EMPTY), method.getSimpleName(), methodType, null);
            }
            return null;
        }

        private @Nullable List<String> getLambdaParameterNames(J.Lambda lambda) {
            List<String> names = new ArrayList<>();
            for (J param : lambda.getParameters().getParameters()) {
                if (param instanceof J.Empty) {
                    continue;
                }
                if (!(param instanceof J.VariableDeclarations) || ((J.VariableDeclarations) param).getVariables().size() != 1 ||
                        !(((J.VariableDeclarations) param).getVariables().get(0).getDeclarator() instanceof J.Identifier)) {
                    return null;
                }
                names.add(((J.VariableDeclarations) param).getVariables().get(0).getSimpleName());
            }
            if (names.isEmpty()) {
                // Lambdas with a receiver or without parameters cannot be told apart from lambdas with an implicit `it`,
                // so only treat `it` as this lambda's parameter when no enclosing lambda could own it instead
                Iterator<Cursor> cursors = getCursor().getParentTreeCursor().getPathAsCursors();
                while (cursors.hasNext()) {
                    Object value = cursors.next().getValue();
                    if (value instanceof J.ClassDeclaration || value instanceof J.MethodDeclaration) {
                        break;
                    }
                    if (value instanceof J.Lambda &&
                            ((J.Lambda) value).getParameters().getParameters().stream().allMatch(J.Empty.class::isInstance)) {
                        return null;
                    }
                }
                names.add("it");
            }
            return names;
        }

        private JavaType.@Nullable FullyQualified enclosingThisType() {
            Iterator<Cursor> cursors = getCursor().getParentTreeCursor().getPathAsCursors();
            while (cursors.hasNext()) {
                Object value = cursors.next().getValue();
                if (value instanceof J.Lambda ||
                        value instanceof J.NewClass && ((J.NewClass) value).getBody() != null ||
                        value instanceof J.MethodDeclaration && ((J.MethodDeclaration) value).getMarkers().findFirst(Extension.class).isPresent()) {
                    return null;
                }
                if (value instanceof J.ClassDeclaration) {
                    return ((J.ClassDeclaration) value).getType();
                }
            }
            return null;
        }

        private static boolean isSyntheticJavaProperty(JavaType.Variable property) {
            String name = property.getName();
            String capitalized = Character.toUpperCase(name.charAt(0)) + name.substring(1);
            return ((JavaType.FullyQualified) property.getOwner()).getMethods().stream()
                    .anyMatch(m -> m.getParameterTypes().isEmpty() &&
                            (m.getName().equals("get" + capitalized) || m.getName().equals("is" + capitalized) ||
                                    name.startsWith("is") && m.getName().equals(name)));
        }

        private static boolean isParameter(Expression expression, String name) {
            return expression instanceof J.Identifier && ((J.Identifier) expression).getSimpleName().equals(name);
        }

        private static boolean isMemberOf(@Nullable JavaType owner, @Nullable JavaType receiverType) {
            return receiverType instanceof JavaType.Class && owner instanceof JavaType.FullyQualified &&
                    TypeUtils.isAssignableTo(owner, receiverType);
        }

        private static boolean argumentsMatchParameters(List<Expression> arguments, List<String> params, JavaType.Method methodType) {
            List<Expression> args = new ArrayList<>();
            for (Expression argument : arguments) {
                if (!(argument instanceof J.Empty)) {
                    args.add(argument);
                }
            }
            if (args.size() != params.size() || methodType.getParameterTypes().size() != params.size()) {
                return false;
            }
            for (int i = 0; i < params.size(); i++) {
                if (!isParameter(args.get(i), params.get(i))) {
                    return false;
                }
            }
            return true;
        }

        private Expression typeName(JavaType.Class type) {
            if (type.getOwningClass() == null && DEFAULT_IMPORTED_PACKAGES.contains(type.getPackageName())) {
                return new J.Identifier(randomId(), Space.EMPTY, Markers.EMPTY, emptyList(), type.getClassName(), type, null);
            }
            Expression name = className(type, true);
            doAfterVisit(service(ImportService.class).shortenFullyQualifiedTypeReferencesIn(name));
            return name;
        }

        private static J.MemberReference newKotlinReference(Expression containing, String name,
                                                            JavaType.@Nullable Method methodType, JavaType.@Nullable Variable variableType) {
            return new J.MemberReference(
                    randomId(),
                    Space.EMPTY,
                    Markers.EMPTY,
                    new JRightPadded<>(containing, Space.EMPTY, Markers.EMPTY),
                    null,
                    new JLeftPadded<>(Space.EMPTY, new J.Identifier(randomId(), Space.EMPTY, Markers.EMPTY, emptyList(), name, null, variableType), Markers.EMPTY),
                    null,
                    methodType,
                    variableType
            );
        }
    }
}
