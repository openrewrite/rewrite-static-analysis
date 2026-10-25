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
import org.openrewrite.java.JavaIsoVisitor;
import org.openrewrite.java.MethodMatcher;
import org.openrewrite.java.VariableNameUtils;
import org.openrewrite.java.marker.JavaSourceSet;
import org.openrewrite.java.tree.Expression;
import org.openrewrite.java.tree.J;
import org.openrewrite.java.tree.JavaSourceFile;
import org.openrewrite.java.tree.JavaType;
import org.openrewrite.java.tree.TypeTree;
import org.openrewrite.java.tree.TypeUtils;

import javax.lang.model.SourceVersion;
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

    String displayName = "Standardize method name casing";

    String description = "Fixes method names that do not follow standard naming " +
               "conventions. For example, `String getFoo_bar()` would be adjusted " +
               "to `String getFooBar()` and `int DoSomething()` would be adjusted " +
               "to `int doSomething()`. Following a consistent casing convention " +
               "for method names improves code readability and helps developers " +
               "quickly distinguish methods from classes or constants.";

    Set<String> tags = singleton("RSPEC-S100");

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
                        SourceVersion.isIdentifier(toName) &&
                        !SourceVersion.isKeyword(toName) &&
                        !methodExists(method.getMethodType(), toName)) {
                        changes.add(new MethodNameChange(
                                scope,
                                method.hasModifier(J.Modifier.Type.Private),
                                simpleName,
                                toName,
                                new MethodMatcher(MethodMatcher.methodPattern(method), false))
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
                if (!(tree instanceof JavaSourceFile)) {
                    return (J) tree;
                }
                UUID id = tree.getId();
                Map<String, List<MethodNameChange>> byName = new HashMap<>();
                for (MethodNameChange nameChange : changes) {
                    if (!nameChange.isPrivateMethod() || id.equals(nameChange.getScope())) {
                        byName.computeIfAbsent(nameChange.getFromName(), k -> new ArrayList<>()).add(nameChange);
                    }
                }
                if (byName.isEmpty()) {
                    return (J) tree;
                }
                return new RenameMethods(byName).visitNonNull(tree, ctx);
            }
        };
    }

    static class RenameMethods extends JavaIsoVisitor<ExecutionContext> {
        private final Map<String, List<MethodNameChange>> byName;

        RenameMethods(Map<String, List<MethodNameChange>> byName) {
            this.byName = byName;
        }

        @Override
        public J.MethodDeclaration visitMethodDeclaration(J.MethodDeclaration method, ExecutionContext ctx) {
            J.MethodDeclaration m = super.visitMethodDeclaration(method, ctx);
            List<MethodNameChange> candidates = byName.get(method.getSimpleName());
            if (candidates != null) {
                J.NewClass newClass = getCursor().firstEnclosing(J.NewClass.class);
                J.ClassDeclaration classDecl = getCursor().firstEnclosing(J.ClassDeclaration.class);
                for (MethodNameChange change : candidates) {
                    MethodMatcher matcher = change.getMatcher();
                    if (newClass != null && matcher.matches(method, newClass) ||
                        classDecl != null && matcher.matches(method, classDecl)) {
                        JavaType.Method type = m.getMethodType();
                        if (type != null) {
                            type = type.withName(change.getToName());
                        }
                        m = m.withName(m.getName().withSimpleName(change.getToName()).withType(type))
                                .withMethodType(type);
                        break;
                    }
                }
            }
            return m;
        }

        @Override
        public J.MethodInvocation visitMethodInvocation(J.MethodInvocation method, ExecutionContext ctx) {
            J.MethodInvocation m = super.visitMethodInvocation(method, ctx);
            List<MethodNameChange> candidates = byName.get(method.getSimpleName());
            if (candidates != null) {
                for (MethodNameChange change : candidates) {
                    if (change.getMatcher().matches(method) && !method.getSimpleName().equals(change.getToName())) {
                        JavaType.Method type = m.getMethodType();
                        if (type != null) {
                            type = type.withName(change.getToName());
                        }
                        m = m.withName(m.getName().withSimpleName(change.getToName()).withType(type))
                                .withMethodType(type);
                        break;
                    }
                }
            }
            return m;
        }

        @Override
        public J.MemberReference visitMemberReference(J.MemberReference memberRef, ExecutionContext ctx) {
            J.MemberReference m = super.visitMemberReference(memberRef, ctx);
            List<MethodNameChange> candidates = byName.get(m.getReference().getSimpleName());
            if (candidates != null) {
                for (MethodNameChange change : candidates) {
                    if (change.getMatcher().matches(m.getMethodType()) && !m.getReference().getSimpleName().equals(change.getToName())) {
                        JavaType.Method type = m.getMethodType();
                        if (type != null) {
                            type = type.withName(change.getToName());
                        }
                        m = m.withReference(m.getReference().withSimpleName(change.getToName())).withMethodType(type);
                        break;
                    }
                }
            }
            return m;
        }

        /**
         * The only time field access should be relevant to changing method names is static imports.
         */
        @Override
        public J.FieldAccess visitFieldAccess(J.FieldAccess fieldAccess, ExecutionContext ctx) {
            J.FieldAccess f = super.visitFieldAccess(fieldAccess, ctx);
            List<MethodNameChange> candidates = byName.get(f.getSimpleName());
            if (candidates != null && getCursor().getParentTreeCursor().getValue() instanceof J.Import) {
                for (MethodNameChange change : candidates) {
                    if (change.getMatcher().isFullyQualifiedClassReference(f)) {
                        Expression target = f.getTarget();
                        if (target instanceof J.FieldAccess) {
                            String className = target.printTrimmed(getCursor());
                            String fullyQualified = className + "." + change.getToName();
                            return TypeTree.build(fullyQualified)
                                    .withPrefix(f.getPrefix());
                        }
                    }
                }
            }
            return f;
        }
    }

    @Value
    public static class MethodNameChange {
        UUID scope;
        boolean privateMethod;
        String fromName;
        String toName;
        MethodMatcher matcher;
    }
}
