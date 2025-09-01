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
import org.openrewrite.ExecutionContext;
import org.openrewrite.Option;
import org.openrewrite.Recipe;
import org.openrewrite.internal.ListUtils;
import org.openrewrite.java.JavaIsoVisitor;
import org.openrewrite.java.tree.J;
import org.openrewrite.java.tree.JavaType;
import org.openrewrite.java.tree.Space;
import org.openrewrite.java.tree.Statement;
import org.openrewrite.marker.Markers;

import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.regex.Pattern;

import static java.util.Collections.emptyList;
import static org.openrewrite.Tree.randomId;

@EqualsAndHashCode(callSuper = false)
@Value
public class FluentSetter extends Recipe {

    @Option(displayName = "Include all void methods", description = "Whether to convert all void methods to return `this`, not just setters. When false, only methods matching setter patterns will be converted.", required = false)
    @Nullable
    Boolean includeAllVoidMethods;

    @Option(displayName = "Method name pattern", description = "A regular expression pattern to match method names. Only methods matching this pattern will be converted. Defaults to setter pattern when includeAllVoidMethods is false.", example = "set.*", required = false)
    @Nullable
    String methodNamePattern;

    @Option(displayName = "Exclude method patterns", description = "A regular expression pattern for method names to exclude from conversion. Methods matching this pattern will not be converted.", example = "main|run", required = false)
    @Nullable
    String excludeMethodPattern;

    @Override
    public String getDisplayName() {
        return "Convert setters to return `this` for fluent interfaces";
    }

    @Override
    public String getDescription() {
        return "Converts void setter methods (and optionally other void methods) to return `this` to enable method chaining and fluent interfaces. " +
                "For safety, only converts methods in final classes by default to avoid breaking inheritance. Use methodNamePattern to opt-in for non-final classes.";
    }

    @Override
    public JavaIsoVisitor<ExecutionContext> getVisitor() {
        return new JavaIsoVisitor<ExecutionContext>() {

            @Override
            public J.MethodDeclaration visitMethodDeclaration(J.MethodDeclaration method, ExecutionContext ctx) {
                method = super.visitMethodDeclaration(method, ctx);
                if (!shouldConvertMethod(method)) {
                    return method;
                }

                J.ClassDeclaration containingClass = getCursor().firstEnclosing(J.ClassDeclaration.class);
                if (containingClass == null || containingClass.getType() == null) {
                    return method;
                }

                // Extract simple class name from fully qualified name or inner class name
                JavaType.FullyQualified classType = containingClass.getType();
                String className = classType.getClassName();
                if (className.contains(".")) {
                    className = className.substring(className.lastIndexOf('.') + 1);
                }

                Space returnTypeSpace = method.getReturnTypeExpression() == null ? Space.EMPTY : method.getReturnTypeExpression().getPrefix();
                Markers returnTypeMarkers = method.getReturnTypeExpression() == null ? Markers.EMPTY : method.getReturnTypeExpression().getMarkers();

                J.Identifier returnTypeIdentifier = new J.Identifier(randomId(), returnTypeSpace, returnTypeMarkers, emptyList(), className, classType, null);

                J.MethodDeclaration updatedMethod = method.withReturnTypeExpression(returnTypeIdentifier);

                if (updatedMethod.getBody() != null) {
                    Space indentation;
                    List<Statement> statements = updatedMethod.getBody().getStatements();
                    if (!statements.isEmpty()) {
                        indentation = statements.get(statements.size() - 1).getPrefix();
                    } else {
                        indentation = updatedMethod.getBody().getPrefix();
                    }

                    J.Return returnThis = new J.Return(randomId(), Space.format("\n" + indentation.getIndent()), Markers.EMPTY, new J.Identifier(randomId(), Space.SINGLE_SPACE, Markers.EMPTY, emptyList(), "this", classType, null));

                    updatedMethod = updatedMethod.withBody(updatedMethod.getBody().withStatements(ListUtils.concat(updatedMethod.getBody().getStatements(), returnThis)));
                }

                return updatedMethod;
            }

            private boolean shouldConvertMethod(J.MethodDeclaration method) {
                if (method.getReturnTypeExpression() == null || method.getReturnTypeExpression().getType() != JavaType.Primitive.Void) {
                    return false;
                }

                if (method.hasModifier(J.Modifier.Type.Static)) {
                    return false;
                }

                if (method.hasModifier(J.Modifier.Type.Abstract) || method.getBody() == null) {
                    return false;
                }

                if (method.isConstructor()) {
                    return false;
                }

                if (method.getBody() != null && (hasReturnStatement(method.getBody()) || onlyThrowsException(method.getBody()))) {
                    return false;
                }

                String methodName = method.getSimpleName();

                if (excludeMethodPattern != null && !excludeMethodPattern.trim().isEmpty()) {
                    Pattern excludePattern = Pattern.compile(excludeMethodPattern);
                    if (excludePattern.matcher(methodName).matches()) {
                        return false;
                    }
                }

                if (methodNamePattern != null && !methodNamePattern.trim().isEmpty()) {
                    Pattern namePattern = Pattern.compile(methodNamePattern);
                    return namePattern.matcher(methodName).matches();
                }

                J.ClassDeclaration containingClass = getCursor().firstEnclosing(J.ClassDeclaration.class);
                if (containingClass == null || !containingClass.hasModifier(J.Modifier.Type.Final)) {
                    return false;
                }

                if (includeAllVoidMethods != null && includeAllVoidMethods) {
                    return true;
                }

                return isSetterMethod(method);
            }

            private boolean isSetterMethod(J.MethodDeclaration method) {
                String methodName = method.getSimpleName();
                if (!methodName.startsWith("set") || methodName.length() <= 3) {
                    return false;
                }

                // Must have exactly one parameter
                if (method.getParameters().size() != 1) {
                    return false;
                }

                // The character after "set" should be uppercase (setName, not setup)
                char charAfterSet = methodName.charAt(3);
                return Character.isUpperCase(charAfterSet);
            }

            private boolean hasReturnStatement(J.Block body) {
                AtomicBoolean hasReturn = new AtomicBoolean(false);

                new JavaIsoVisitor<AtomicBoolean>() {
                    @Override
                    public J.Return visitReturn(J.Return returnStmt, AtomicBoolean found) {
                        found.set(true);
                        return returnStmt;
                    }
                }.visit(body, hasReturn);

                return hasReturn.get();
            }

            private boolean onlyThrowsException(J.Block body) {
                List<Statement> statements = body.getStatements();
                if (statements.isEmpty()) {
                    return false;
                }

                for (Statement statement : statements) {
                    if (!(statement instanceof J.Throw)) {
                        return false;
                    }
                }

                return true;
            }
        };
    }
}
