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
import org.openrewrite.internal.NamingService;
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

import java.util.*;

import static java.util.Collections.singleton;

@EqualsAndHashCode(callSuper = false)
@Value
public class MethodNameCasing extends ScanningRecipe<List<MethodNameCasing.MethodNameChange>> {

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
        return singleton("RSPEC-S100");
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
                    !method.isConstructor()) {
                    String normalized = VariableNameUtils.normalizeName(simpleName);
                    NamingService service = service(NamingService.class);
                    String toName = service.standardizeMethodName(normalized);
                    if (!StringUtils.isBlank(toName) &&
                        !toName.equals(simpleName) &&
                        !StringUtils.isNumeric(toName) &&
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
