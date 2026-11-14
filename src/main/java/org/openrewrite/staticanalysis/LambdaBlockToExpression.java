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

import java.util.List;
import java.util.Optional;
import java.util.Set;

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
                        if (!(l.getBody() instanceof J.Block) || isArgumentOfAmbiguousOverload(getCursor())) {
                            return l;
                        }
                        List<Statement> statements = ((J.Block) l.getBody()).getStatements();
                        if (statements.size() == 1) {
                            Statement statement = statements.get(0);
                            Space prefix = statement.getPrefix();
                            if (statement instanceof J.Return) {
                                Expression expression = ((J.Return) statement).getExpression();
                                if (expression != null) {
                                    return l.withBody(prefix.getComments().isEmpty() ? expression : expression.withPrefix(prefix));
                                }
                            } else if (statement instanceof J.MethodInvocation) {
                                return l.withBody(statement);
                            }
                        }
                        return l;
                    }
                }
        );
    }

    /**
     * An expression lambda can be both void- and value-compatible where the block it replaces was only one of the two,
     * so it may no longer pick a single overload among those that take different functional interfaces.
     */
    private static boolean isArgumentOfAmbiguousOverload(Cursor cursor) {
        Object argument = cursor.getValue();
        Cursor parent = cursor.getParentTreeCursor();
        while (parent.getValue() instanceof J.Parentheses || parent.getValue() instanceof J.Ternary) {
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
        String target = interfaceAt(methodType, index);
        for (JavaType.Method overload : methodType.getDeclaringType().getMethods()) {
            if (overload.getName().equals(methodType.getName()) &&
                overload.getParameterTypes().size() == methodType.getParameterTypes().size()) {
                String other = interfaceAt(overload, index);
                if (other != null && !other.equals(target)) {
                    return true;
                }
            }
        }
        return false;
    }

    private static @Nullable String interfaceAt(JavaType.Method method, int index) {
        List<JavaType> parameterTypes = method.getParameterTypes();
        if (parameterTypes.isEmpty()) {
            return null;
        }
        JavaType parameterType = parameterTypes.get(Math.min(index, parameterTypes.size() - 1));
        JavaType.Array array = TypeUtils.asArray(parameterType);
        JavaType.FullyQualified fq = TypeUtils.asFullyQualified(array == null ? parameterType : array.getElemType());
        return fq != null && fq.getKind() == JavaType.FullyQualified.Kind.Interface ? fq.getFullyQualifiedName() : null;
    }

    // TODO this is actually more complex in the presence of generics and inheritance
    static boolean hasMethodOverloading(JavaType.Method methodType) {
        String methodName = methodType.getName();
        int numberOfParameters = methodType.getParameterNames().size();
        return Optional.of(methodType)
                .map(JavaType.Method::getDeclaringType)
                .map(JavaType.FullyQualified::getMethods)
                .map(methods -> {
                    int overloadingCount = 0;
                    for (JavaType.Method dm : methods) {
                        if (methodName.equals(dm.getName()) &&
                            numberOfParameters == dm.getParameterNames().size()) {
                            if (++overloadingCount > 1) {
                                return true;
                            }
                        }
                    }
                    return false;
                })
                .orElse(false);
    }
}
