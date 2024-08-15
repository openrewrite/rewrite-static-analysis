/*
 * Copyright 2021 the original author or authors.
 * <p>
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * <p>
 * https://www.apache.org/licenses/LICENSE-2.0
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
import org.openrewrite.internal.NameCaseConvention;
import org.openrewrite.internal.StringUtils;
import org.openrewrite.java.ChangeMethodName;
import org.openrewrite.java.JavaIsoVisitor;
import org.openrewrite.java.MethodMatcher;
import org.openrewrite.java.VariableNameUtils;
import org.openrewrite.java.marker.JavaSourceSet;
import org.openrewrite.java.tree.J;
import org.openrewrite.java.tree.JavaSourceFile;
import org.openrewrite.java.tree.JavaType;
import org.openrewrite.java.tree.TypeUtils;

import java.time.Duration;
import java.util.*;
import java.util.regex.Pattern;

@Value
@EqualsAndHashCode(callSuper = false)
public class MethodNameCasing extends ScanningRecipe<List<MethodNameCasing.MethodNameChange>> {

    private static final Pattern STANDARD_METHOD_NAME = Pattern.compile("^[a-z][a-zA-Z0-9]*$");
    private static final Pattern SNAKE_CASE = Pattern.compile("^[a-zA-Z0-9]+_\\w+$");

    @Option(displayName = "Apply recipe to test source set",
            description = "Changes only apply to main by default. `includeTestSources` will apply the recipe to `test` source files.",
            required = false)
    @Nullable
    Boolean includeTestSources;

    @Option(displayName = "Rename public methods",
            description = "Changes are not applied to public methods unless specified.",
            required = false)
    @Nullable
    Boolean renamePublicMethods;

    @Override
    public String getDisplayName() {
        return "Standardize method name casing";
    }

    @Override
    public String getDescription() {
        return "Fixes method names that do not follow standard naming conventions. " +
                "For example, `String getFoo_bar()` would be adjusted to `String getFooBar()` " +
                "and `int DoSomething()` would be adjusted to `int doSomething()`.";
    }

    @Override
    public Set<String> getTags() {
        return Collections.singleton("RSPEC-S100");
    }

    @Override
    public Duration getEstimatedEffortPerOccurrence() {
        return Duration.ofMinutes(5);
    }

    @Override
    public List<MethodNameChange> getInitialValue(ExecutionContext ctx) {
        return new ArrayList<>();
    }

    @Override
    public TreeVisitor<?, ExecutionContext> getScanner(List<MethodNameChange> changes) {
        return new JavaIsoVisitor<ExecutionContext>() {
            UUID scope;
            @Override
            public J preVisit(J tree, ExecutionContext ctx) {
                if (tree instanceof JavaSourceFile) {
                    scope = tree.getId();
                    JavaSourceFile cu = (JavaSourceFile) tree;
                    Optional<JavaSourceSet> sourceSet = cu.getMarkers().findFirst(JavaSourceSet.class);
                    if (!sourceSet.isPresent()) {
                        stopAfterPreVisit();
                    } else if (!Boolean.TRUE.equals(includeTestSources) && !"main".equals(sourceSet.get().getName())) {
                        stopAfterPreVisit();
                    }
                }
                return super.preVisit(tree, ctx);
            }

            @Override
            public J.MethodDeclaration visitMethodDeclaration(J.MethodDeclaration method, ExecutionContext ctx) {
                J.ClassDeclaration enclosingClass = getCursor().firstEnclosing(J.ClassDeclaration.class);
                if (enclosingClass == null || enclosingClass.getKind() != J.ClassDeclaration.Kind.Type.Class) {
                    return method;
                }
                String simpleName = method.getSimpleName();
                if (containsValidModifiers(method) &&
                    method.getMethodType() != null &&
                    enclosingClass.getType() != null &&
                    !method.isConstructor() &&
                    !simpleName.startsWith("_") &&
                    !STANDARD_METHOD_NAME.matcher(simpleName).matches()) {
                    StringBuilder standardized = new StringBuilder();
                    String normalized = VariableNameUtils.normalizeName(simpleName);

                    if (SNAKE_CASE.matcher(normalized).matches()) {
                        standardized.append(NameCaseConvention.format(NameCaseConvention.LOWER_CAMEL, normalized));
                    } else {
                        int nameLength = normalized.length();
                        for (int i = 0; i < nameLength; i++) {
                            char c = normalized.charAt(i);

                            if (i == 0) {
                                // the java specification requires identifiers to start with [a-zA-Z$_]
                                if (c != '$' && c != '_') {
                                    standardized.append(Character.toLowerCase(c));
                                }
                            } else {
                                if (!Character.isLetterOrDigit(c)) {
                                    while (i < nameLength && (!Character.isLetterOrDigit(c) || c > 'z')) {
                                        c = normalized.charAt(i++);
                                    }
                                    if (i < nameLength) {
                                        standardized.append(Character.toUpperCase(c));
                                    }
                                } else {
                                    standardized.append(c);
                                }
                            }
                        }
                    }

                    String toName = standardized.toString();
                    if (!StringUtils.isBlank(toName) && !StringUtils.isNumeric(toName) &&
                        !methodExists(method.getMethodType(), toName)) {
                        changes.add(new MethodNameChange(
                                scope,
                                method.hasModifier(J.Modifier.Type.Private),
                                new ChangeMethodName(MethodMatcher.methodPattern(method), toName, false, false))
                        );
                    }
                }

                return super.visitMethodDeclaration(method, ctx);
            }

            private boolean containsValidModifiers(J.MethodDeclaration method) {
                return !method.hasModifier(J.Modifier.Type.Public) || Boolean.TRUE.equals(renamePublicMethods);
            }

            private boolean methodExists(JavaType.Method method, String newName) {
                return TypeUtils.findDeclaredMethod(method.getDeclaringType(), newName, method.getParameterTypes()).isPresent();
            }
        };
    }

    @Override
    public TreeVisitor<?, ExecutionContext> getVisitor(List<MethodNameChange> changes) {
        return new JavaIsoVisitor<ExecutionContext>() {
            @Override
            public J visit(@Nullable Tree tree, ExecutionContext ctx) {
                if (tree instanceof JavaSourceFile) {
                    JavaSourceFile cu = (JavaSourceFile) tree;
                    for (MethodNameChange nameChange : changes) {
                        if (!nameChange.isPrivateMethod() || tree.getId().equals(nameChange.getScope())) {
                            cu = (JavaSourceFile) nameChange.getRecipe().getVisitor().visitNonNull(cu, ctx);
                        }
                    }
                    return cu;
                }
                return (J) tree;
            }
        };
    }

    @Value
    public static class MethodNameChange {
        UUID scope;
        boolean privateMethod;
        ChangeMethodName recipe;
    }
}
