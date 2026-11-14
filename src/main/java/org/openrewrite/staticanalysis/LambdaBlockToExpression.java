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

import lombok.Getter;
import org.jspecify.annotations.Nullable;
import org.openrewrite.Cursor;
import org.openrewrite.ExecutionContext;
import org.openrewrite.Preconditions;
import org.openrewrite.Recipe;
import org.openrewrite.TreeVisitor;
import org.openrewrite.java.JavaIsoVisitor;
import org.openrewrite.java.tree.*;
import org.openrewrite.staticanalysis.java.JavaFileChecker;

import java.util.*;
import java.util.function.Predicate;

import static java.util.Collections.singleton;

public class LambdaBlockToExpression extends Recipe {
    @Getter
    final String displayName = "Simplify lambda blocks to expressions";

    @Getter
    final String description = "Single-line statement lambdas returning a value can be replaced with expression lambdas. " +
            "Expression-form lambdas are more concise and consistent with a functional programming style, making the code easier to scan.";

    @Getter
    final Set<String> tags = singleton("RSPEC-S1602");

    @Override
    public TreeVisitor<?, ExecutionContext> getVisitor() {
        return Preconditions.check(new JavaFileChecker<>(),
                new JavaIsoVisitor<ExecutionContext>() {
                    @Override
                    public J.Lambda visitLambda(J.Lambda lambda, ExecutionContext ctx) {
                        J.Lambda l = super.visitLambda(lambda, ctx);
                        if (!(l.getBody() instanceof J.Block) || ((J.Block) l.getBody()).getStatements().size() != 1) {
                            return l;
                        }
                        Statement statement = ((J.Block) l.getBody()).getStatements().get(0);
                        Space prefix = statement.getPrefix();
                        J body = null;
                        if (statement instanceof J.Return) {
                            Expression expression = ((J.Return) statement).getExpression();
                            if (expression != null) {
                                body = prefix.getComments().isEmpty() ? expression : expression.withPrefix(prefix);
                            }
                        } else if (statement instanceof J.MethodInvocation) {
                            body = statement;
                        }
                        return body == null || isArgumentOfAmbiguousOverload(getCursor()) ? l : l.withBody(body);
                    }
                }
        );
    }

    /// An expression lambda can be both void- and value-compatible where the block it replaces was only one of the two,
    /// so it may no longer pick a single overload among those whose functional interfaces take as many parameters as
    /// the lambda, but differ in whether they return `void`.
    private static boolean isArgumentOfAmbiguousOverload(Cursor cursor) {
        J.Lambda lambda = cursor.getValue();
        Object argument = lambda;
        Cursor parent = cursor.getParentTreeCursor();
        while (true) {
            if (parent.getValue() instanceof J.Case || parent.getValue() instanceof J.Yield) {
                while (!(parent.getValue() instanceof J.SwitchExpression)) {
                    if (parent.isRoot()) {
                        return false;
                    }
                    parent = parent.getParentTreeCursor();
                }
            } else if (!(parent.getValue() instanceof J.Parentheses || parent.getValue() instanceof J.Ternary)) {
                break;
            }
            argument = parent.getValue();
            parent = parent.getParentTreeCursor();
        }
        if (!(parent.getValue() instanceof MethodCall)) {
            return false;
        }
        MethodCall call = parent.getValue();
        JavaType.Method methodType = call.getMethodType();
        int index = call.getArguments().indexOf(argument);
        if (methodType == null || index < 0) {
            return false;
        }
        JavaType.FullyQualified searchFrom = null;
        if (call instanceof J.NewClass && ((J.NewClass) call).getClazz() != null) {
            searchFrom = TypeUtils.asFullyQualified(((J.NewClass) call).getClazz().getType());
        } else if (call instanceof J.MethodInvocation && ((J.MethodInvocation) call).getSelect() != null) {
            searchFrom = TypeUtils.asFullyQualified(((J.MethodInvocation) call).getSelect().getType());
        }
        long arity = lambda.getParameters().getParameters().stream().filter(p -> !(p instanceof J.Empty)).count();
        JavaType.FullyQualified target = interfaceAt(methodType, index);
        JavaType.Method targetFunction = target == null ? null : functionalMethod(target);
        Predicate<JavaType.FullyQualified> competes = other -> {
            if (target != null && target.getFullyQualifiedName().equals(other.getFullyQualifiedName())) {
                return false;
            }
            JavaType.Method function = functionalMethod(other);
            return function == null || function.getParameterTypes().size() == arity &&
                    (targetFunction == null || (function.getReturnType() == JavaType.Primitive.Void) != (targetFunction.getReturnType() == JavaType.Primitive.Void));
        };
        return declaresCompetingOverload(searchFrom == null ? methodType.getDeclaringType() : searchFrom,
                methodType, call.getArguments().size(), index, competes, new HashSet<>());
    }

    private static boolean declaresCompetingOverload(JavaType.@Nullable FullyQualified type, JavaType.Method methodType,
                                                     int argumentCount, int index, Predicate<JavaType.FullyQualified> competes,
                                                     Set<String> seen) {
        if (type == null || !seen.add(type.getFullyQualifiedName())) {
            return false;
        }
        for (JavaType.Method overload : type.getMethods()) {
            int parameterCount = overload.getParameterTypes().size();
            if (overload.getName().equals(methodType.getName()) &&
                (parameterCount == argumentCount || overload.hasFlags(Flag.Varargs) && argumentCount >= parameterCount - 1)) {
                JavaType.FullyQualified other = interfaceAt(overload, index);
                if (other != null && competes.test(other)) {
                    return true;
                }
            }
        }
        if (methodType.isConstructor()) {
            return false;
        }
        if (declaresCompetingOverload(type.getSupertype(), methodType, argumentCount, index, competes, seen)) {
            return true;
        }
        for (JavaType.FullyQualified anInterface : type.getInterfaces()) {
            if (declaresCompetingOverload(anInterface, methodType, argumentCount, index, competes, seen)) {
                return true;
            }
        }
        return false;
    }

    private static JavaType.@Nullable FullyQualified interfaceAt(JavaType.Method method, int index) {
        List<JavaType> parameterTypes = method.getParameterTypes();
        if (parameterTypes.isEmpty()) {
            return null;
        }
        JavaType parameterType = parameterTypes.get(Math.min(index, parameterTypes.size() - 1));
        JavaType.Array array = TypeUtils.asArray(parameterType);
        JavaType.FullyQualified fq = TypeUtils.asFullyQualified(array == null ? parameterType : array.getElemType());
        return fq != null && fq.getKind() == JavaType.FullyQualified.Kind.Interface ? fq : null;
    }

    /// The single abstract method of a functional interface, or `null` when there isn't exactly one.
    private static JavaType.@Nullable Method functionalMethod(JavaType.FullyQualified type) {
        Map<String, JavaType.Method> abstractMethods = new HashMap<>();
        Deque<JavaType.FullyQualified> types = new ArrayDeque<>(singleton(type));
        while (!types.isEmpty()) {
            JavaType.FullyQualified t = types.pop();
            for (JavaType.Method method : t.getMethods()) {
                int parameterCount = method.getParameterTypes().size();
                boolean objectMethod = "equals".equals(method.getName()) && parameterCount == 1 ||
                        ("hashCode".equals(method.getName()) || "toString".equals(method.getName())) && parameterCount == 0;
                if (method.hasFlags(Flag.Abstract) && !method.hasFlags(Flag.Default) && !objectMethod) {
                    abstractMethods.putIfAbsent(method.getName() + "/" + parameterCount, method);
                }
            }
            types.addAll(t.getInterfaces());
        }
        return abstractMethods.size() == 1 ? abstractMethods.values().iterator().next() : null;
    }
}
