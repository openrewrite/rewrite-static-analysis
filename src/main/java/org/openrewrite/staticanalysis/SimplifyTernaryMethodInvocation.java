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
package org.openrewrite.staticanalysis;

import lombok.Getter;
import org.openrewrite.ExecutionContext;
import org.openrewrite.Preconditions;
import org.openrewrite.Recipe;
import org.openrewrite.TreeVisitor;
import org.openrewrite.java.JavaIsoVisitor;
import org.openrewrite.java.JavaTemplate;
import org.openrewrite.java.JavaVisitor;
import org.openrewrite.java.search.SemanticallyEqual;
import org.openrewrite.java.tree.Expression;
import org.openrewrite.java.tree.Flag;
import org.openrewrite.java.tree.J;
import org.openrewrite.java.tree.JavaType;
import org.openrewrite.java.tree.Space;
import org.openrewrite.java.tree.TypeUtils;
import org.openrewrite.staticanalysis.java.JavaFileChecker;

import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

import static java.util.Collections.emptyList;

@Getter
public class SimplifyTernaryMethodInvocation extends Recipe {
    final String displayName = "Move a common method invocation out of a ternary";

    final String description = "Replace `condition ? a.method(args) : b.method(args)` with " +
            "`(condition ? a : b).method(args)` when both calls have the same arguments and the " +
            "resulting call resolves to the same method signature. This reduces duplicated code " +
            "without changing overload resolution, return type, or checked exceptions.";

    @Override
    public TreeVisitor<?, ExecutionContext> getVisitor() {
        return Preconditions.check(new JavaFileChecker<>(), new JavaVisitor<ExecutionContext>() {
            @Override
            public J visitTernary(J.Ternary ternary, ExecutionContext ctx) {
                J.Ternary t = (J.Ternary) super.visitTernary(ternary, ctx);
                if (!(t.getTruePart() instanceof J.MethodInvocation) ||
                        !(t.getFalsePart() instanceof J.MethodInvocation)) {
                    return t;
                }

                J.MethodInvocation whenTrue = (J.MethodInvocation) t.getTruePart();
                J.MethodInvocation whenFalse = (J.MethodInvocation) t.getFalsePart();
                List<Expression> trueArguments = arguments(whenTrue);
                List<Expression> falseArguments = arguments(whenFalse);
                if (whenTrue.getSelect() == null || whenFalse.getSelect() == null ||
                        whenTrue.getMethodType() == null || whenFalse.getMethodType() == null ||
                        t.getType() == null ||
                        !whenTrue.getSimpleName().equals(whenFalse.getSimpleName()) ||
                        (whenTrue.getTypeParameters() != null && !whenTrue.getTypeParameters().isEmpty()) ||
                        (whenFalse.getTypeParameters() != null && !whenFalse.getTypeParameters().isEmpty()) ||
                        !safeMethod(whenTrue.getMethodType()) || !safeMethod(whenFalse.getMethodType()) ||
                        !sameSignature(whenTrue.getMethodType(), whenFalse.getMethodType()) ||
                        !TypeUtils.isOfType(t.getType(), whenTrue.getType()) ||
                        !TypeUtils.isOfType(t.getType(), whenFalse.getType()) ||
                        !sameArguments(trueArguments, falseArguments) ||
                        hasComments(whenTrue) || hasComments(whenFalse)) {
                    return t;
                }

                JavaType trueReceiverType = whenTrue.getSelect().getType();
                JavaType falseReceiverType = whenFalse.getSelect().getType();
                if (!knownType(trueReceiverType) || !knownType(falseReceiverType)) {
                    return t;
                }

                StringBuilder code = new StringBuilder("(#{any(boolean)} ? #{any()} : #{any()}).")
                        .append(whenTrue.getSimpleName()).append('(');
                Object[] parameters = new Object[3 + trueArguments.size()];
                parameters[0] = t.getCondition();
                parameters[1] = whenTrue.getSelect();
                parameters[2] = whenFalse.getSelect();
                for (int i = 0; i < trueArguments.size(); i++) {
                    if (i > 0) {
                        code.append(", ");
                    }
                    code.append("#{any()}");
                    parameters[3 + i] = trueArguments.get(i);
                }
                code.append(')');

                J.MethodInvocation replacement = JavaTemplate.builder(code.toString())
                        .contextSensitive()
                        .build()
                        .apply(updateCursor(t), t.getCoordinates().replace(), parameters);
                JavaType.Method resolved = replacement.getMethodType();
                if (resolved == null || !safeMethod(resolved) ||
                        !TypeUtils.isAssignableTo(resolved.getDeclaringType(), trueReceiverType) ||
                        !TypeUtils.isAssignableTo(resolved.getDeclaringType(), falseReceiverType) ||
                        !sameSignature(resolved, whenTrue.getMethodType()) ||
                        !TypeUtils.isOfType(t.getType(), replacement.getType())) {
                    return t;
                }
                return replacement.withPrefix(t.getPrefix());
            }
        });
    }

    private static boolean safeMethod(JavaType.Method method) {
        return method.getDeclaringType() != null && method.hasFlags(Flag.Public) &&
                !method.hasFlags(Flag.Static) && !method.hasFlags(Flag.Varargs) &&
                !hasGenericDeclaration(method) && knownType(method.getReturnType()) &&
                method.getParameterTypes().stream().allMatch(SimplifyTernaryMethodInvocation::knownType);
    }

    private static boolean hasGenericDeclaration(JavaType.Method method) {
        if (!method.getDeclaredFormalTypeNames().isEmpty()) {
            return true;
        }
        for (JavaType.Method declaration : method.getDeclaringType().getMethods()) {
            if (declaration.getName().equals(method.getName()) &&
                    !declaration.getDeclaredFormalTypeNames().isEmpty()) {
                return true;
            }
        }
        return false;
    }

    private static boolean knownType(JavaType type) {
        return type != null && !(type instanceof JavaType.Unknown);
    }

    private static boolean sameSignature(JavaType.Method a, JavaType.Method b) {
        return a.getName().equals(b.getName()) &&
                sameTypes(a.getParameterTypes(), b.getParameterTypes()) &&
                TypeUtils.isOfType(a.getReturnType(), b.getReturnType()) &&
                sameTypes(a.getThrownExceptions(), b.getThrownExceptions());
    }

    private static boolean sameTypes(List<? extends JavaType> a, List<? extends JavaType> b) {
        if (a.size() != b.size()) {
            return false;
        }
        for (int i = 0; i < a.size(); i++) {
            if (!TypeUtils.isOfType(a.get(i), b.get(i))) {
                return false;
            }
        }
        return true;
    }

    private static boolean sameArguments(List<Expression> a, List<Expression> b) {
        if (a.size() != b.size()) {
            return false;
        }
        for (int i = 0; i < a.size(); i++) {
            if (!knownType(a.get(i).getType()) || !knownType(b.get(i).getType()) ||
                    a.get(i) instanceof J.Lambda || b.get(i) instanceof J.Lambda ||
                    a.get(i) instanceof J.MemberReference || b.get(i) instanceof J.MemberReference ||
                    !TypeUtils.isOfType(a.get(i).getType(), b.get(i).getType()) ||
                    !SemanticallyEqual.areEqual(a.get(i), b.get(i))) {
                return false;
            }
        }
        return true;
    }

    private static List<Expression> arguments(J.MethodInvocation invocation) {
        List<Expression> arguments = invocation.getArguments();
        return arguments.size() == 1 && arguments.get(0) instanceof J.Empty ? emptyList() : arguments;
    }

    private static boolean hasComments(J tree) {
        return new JavaIsoVisitor<AtomicBoolean>() {
            @Override
            public Space visitSpace(Space space, Space.Location loc, AtomicBoolean found) {
                if (!space.getComments().isEmpty()) {
                    found.set(true);
                }
                return space;
            }
        }.reduce(tree, new AtomicBoolean(false)).get();
    }
}
