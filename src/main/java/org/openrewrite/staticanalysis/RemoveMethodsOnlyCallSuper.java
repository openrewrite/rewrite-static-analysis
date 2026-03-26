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
import org.openrewrite.ExecutionContext;
import org.openrewrite.Preconditions;
import org.openrewrite.Recipe;
import org.openrewrite.TreeVisitor;
import org.openrewrite.java.JavaVisitor;
import org.openrewrite.java.tree.*;
import org.openrewrite.staticanalysis.kotlin.KotlinFileChecker;

import java.time.Duration;
import java.util.List;
import java.util.Set;

import static java.util.Collections.singleton;

@EqualsAndHashCode(callSuper = false)
@Value
public class RemoveMethodsOnlyCallSuper extends Recipe {

    String displayName = "Remove methods that only call super";

    String description = "Methods that override a parent method but only call `super` with the same arguments are redundant and should be removed.";

    Set<String> tags = singleton("RSPEC-S1185");

    Duration estimatedEffortPerOccurrence = Duration.ofMinutes(2);

    @Override
    public TreeVisitor<?, ExecutionContext> getVisitor() {
        return Preconditions.check(Preconditions.not(new KotlinFileChecker<>()), new JavaVisitor<ExecutionContext>() {
            @Override
            public J.@Nullable MethodDeclaration visitMethodDeclaration(J.MethodDeclaration method, ExecutionContext ctx) {
                J.MethodDeclaration md = (J.MethodDeclaration) super.visitMethodDeclaration(method, ctx);
                JavaType.Method methodType = md.getMethodType();
                if (md.isConstructor() || methodType == null || !TypeUtils.isOverride(methodType)) {
                    return md;
                }

                J.Block body = md.getBody();
                if (body == null || body.getStatements().size() != 1) {
                    return md;
                }

                J.MethodInvocation superCall = extractSuperCall(body.getStatements().get(0));
                if (superCall == null) {
                    return md;
                }

                // Verify the call target is "super"
                Expression select = superCall.getSelect();
                if (!(select instanceof J.Identifier) || !"super".equals(((J.Identifier) select).getSimpleName())) {
                    return md;
                }

                // Method name must match
                if (!superCall.getSimpleName().equals(md.getSimpleName())) {
                    return md;
                }

                // Arguments must match parameters in same order
                if (!argumentsMatchParameters(md.getParameters(), superCall.getArguments())) {
                    return md;
                }

                // Skip if method has annotations other than @Override
                for (J.Annotation annotation : md.getLeadingAnnotations()) {
                    JavaType annotationType = annotation.getAnnotationType().getType();
                    if (annotationType == null || !TypeUtils.isOfClassType(annotationType, "java.lang.Override")) {
                        return md;
                    }
                }

                // Skip if method has Javadoc comments
                for (Comment comment : md.getPrefix().getComments()) {
                    if (comment instanceof Javadoc) {
                        return md;
                    }
                }

                // Skip if method widens visibility compared to the overridden method
                if (widensVisibility(methodType)) {
                    return md;
                }

                //noinspection DataFlowIssue
                return null;
            }

            private J.@Nullable MethodInvocation extractSuperCall(Statement statement) {
                if (statement instanceof J.MethodInvocation) {
                    return (J.MethodInvocation) statement;
                }
                if (statement instanceof J.Return) {
                    Expression expr = ((J.Return) statement).getExpression();
                    if (expr instanceof J.MethodInvocation) {
                        return (J.MethodInvocation) expr;
                    }
                }
                return null;
            }

            private boolean argumentsMatchParameters(List<Statement> parameters, List<Expression> arguments) {
                int argIndex = 0;
                int paramCount = 0;
                for (Statement param : parameters) {
                    if (!(param instanceof J.VariableDeclarations)) {
                        continue;
                    }
                    paramCount++;
                    J.VariableDeclarations varDecls = (J.VariableDeclarations) param;
                    if (varDecls.getVariables().size() != 1) {
                        return false;
                    }
                    String paramName = varDecls.getVariables().get(0).getSimpleName();

                    // Find next non-empty argument
                    while (argIndex < arguments.size() && arguments.get(argIndex) instanceof J.Empty) {
                        argIndex++;
                    }
                    if (argIndex >= arguments.size()) {
                        return false;
                    }

                    Expression arg = arguments.get(argIndex);
                    if (!(arg instanceof J.Identifier) || !paramName.equals(((J.Identifier) arg).getSimpleName())) {
                        return false;
                    }
                    argIndex++;
                }

                // Verify no extra non-empty arguments remain
                while (argIndex < arguments.size()) {
                    if (!(arguments.get(argIndex) instanceof J.Empty)) {
                        return false;
                    }
                    argIndex++;
                }
                return paramCount > 0 || arguments.isEmpty() || arguments.stream().allMatch(a -> a instanceof J.Empty);
            }

            private boolean widensVisibility(JavaType.Method methodType) {
                int childVisibility = visibilityRank(methodType);
                JavaType.FullyQualified declaringType = methodType.getDeclaringType();

                // Check supertype methods
                JavaType.FullyQualified supertype = declaringType.getSupertype();
                if (supertype != null) {
                    for (JavaType.Method parentMethod : supertype.getMethods()) {
                        if (isMatchingMethod(methodType, parentMethod) && childVisibility > visibilityRank(parentMethod)) {
                            return true;
                        }
                    }
                }

                // Check interface default methods
                for (JavaType.FullyQualified iface : declaringType.getInterfaces()) {
                    for (JavaType.Method ifaceMethod : iface.getMethods()) {
                        if (isMatchingMethod(methodType, ifaceMethod) && childVisibility > visibilityRank(ifaceMethod)) {
                            return true;
                        }
                    }
                }

                return false;
            }

            private boolean isMatchingMethod(JavaType.Method child, JavaType.Method parent) {
                if (!child.getName().equals(parent.getName())) {
                    return false;
                }
                List<JavaType> childParams = child.getParameterTypes();
                List<JavaType> parentParams = parent.getParameterTypes();
                if (childParams.size() != parentParams.size()) {
                    return false;
                }
                for (int i = 0; i < childParams.size(); i++) {
                    if (!TypeUtils.isOfType(childParams.get(i), parentParams.get(i))) {
                        return false;
                    }
                }
                return true;
            }

            private int visibilityRank(JavaType.Method method) {
                if (method.hasFlags(Flag.Public)) {
                    return 3;
                }
                if (method.hasFlags(Flag.Protected)) {
                    return 2;
                }
                if (method.hasFlags(Flag.Private)) {
                    return 0;
                }
                return 1; // package-private
            }
        });
    }
}
