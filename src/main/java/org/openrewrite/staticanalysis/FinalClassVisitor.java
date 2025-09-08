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

import org.jspecify.annotations.Nullable;
import org.openrewrite.ExecutionContext;
import org.openrewrite.Tree;
import org.openrewrite.java.JavaIsoVisitor;
import org.openrewrite.java.tree.*;
import org.openrewrite.marker.Markers;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static java.util.Collections.emptyList;
import static org.openrewrite.Tree.randomId;
import static org.openrewrite.staticanalysis.ModifierOrder.sortModifiers;

public class FinalClassVisitor extends JavaIsoVisitor<ExecutionContext> {

    Tree visitRoot;

    final Set<String> typesToFinalize = new HashSet<>();
    final Set<String> typesToNotFinalize = new HashSet<>();
    private final boolean includeNeverExtended;
    private final List<String> excludePackages;
    private final List<String> excludeAnnotations;

    public FinalClassVisitor() {
        this(false, emptyList(), emptyList());
    }

    public FinalClassVisitor(boolean includeNeverExtended,
                             List<String> excludePackages,
                             List<String> excludeAnnotations) {
        this.includeNeverExtended = includeNeverExtended;
        this.excludePackages = excludePackages != null ? excludePackages : emptyList();
        this.excludeAnnotations = excludeAnnotations != null ? excludeAnnotations : emptyList();
    }

    @Override
    public @Nullable J visit(@Nullable Tree tree, ExecutionContext ctx) {
        boolean root = false;
        if (visitRoot == null && tree != null) {
            visitRoot = tree;
            root = true;
            typesToFinalize.clear();
            typesToNotFinalize.clear();
        }
        J result = super.visit(tree, ctx);
        if (root) {
            visitRoot = null;
            typesToFinalize.removeAll(typesToNotFinalize);
            if (!typesToFinalize.isEmpty()) {
                result = new FinalizingVisitor(typesToFinalize).visit(tree, ctx);
            }
        }
        return result;
    }

    @Override
    public J.ClassDeclaration visitClassDeclaration(J.ClassDeclaration classDeclaration, ExecutionContext ctx) {
        J.ClassDeclaration cd = super.visitClassDeclaration(classDeclaration, ctx);

        if (cd.getType() != null) {
            excludeSupertypes(cd.getType());
        }

        if (cd.getKind() != J.ClassDeclaration.Kind.Type.Class || cd.hasModifier(J.Modifier.Type.Abstract) ||
                cd.hasModifier(J.Modifier.Type.Final) || cd.getType() == null) {
            return cd;
        }

        if (cd.hasModifier(J.Modifier.Type.Sealed) || cd.hasModifier(J.Modifier.Type.NonSealed)) {
            return cd;
        }

        if (!includeNeverExtended) {
            boolean allPrivate = true;
            int constructorCount = 0;
            for (Statement s : cd.getBody().getStatements()) {
                if (s instanceof J.MethodDeclaration && ((J.MethodDeclaration) s).isConstructor()) {
                    J.MethodDeclaration constructor = (J.MethodDeclaration) s;
                    constructorCount++;
                    if (!constructor.hasModifier(J.Modifier.Type.Private)) {
                        allPrivate = false;
                    }
                }
                if (constructorCount > 0 && !allPrivate) {
                    return cd;
                }
            }

            if (constructorCount > 0) {
                typesToFinalize.add(cd.getType().getFullyQualifiedName());
            }
        } else {
            handleExtendedFinalization(cd);
        }

        return cd;
    }

    private void handleExtendedFinalization(J.ClassDeclaration cd) {
        String fullyQualifiedName = cd.getType().getFullyQualifiedName();
        boolean allPrivate = true;
        int constructorCount = 0;
        for (Statement s : cd.getBody().getStatements()) {
            if (s instanceof J.MethodDeclaration && ((J.MethodDeclaration) s).isConstructor()) {
                J.MethodDeclaration constructor = (J.MethodDeclaration) s;
                constructorCount++;
                if (!constructor.hasModifier(J.Modifier.Type.Private)) {
                    allPrivate = false;
                }
            }
            if (constructorCount > 0 && !allPrivate) {
                break;
            }
        }

        if (constructorCount > 0 && allPrivate) {
            typesToFinalize.add(fullyQualifiedName);
        }

        if (passesExclusionFilters(cd)) {
            typesToFinalize.add(fullyQualifiedName);
        }
    }

    private boolean passesExclusionFilters(J.ClassDeclaration cd) {
        if (cd.getType() == null) {
            return false;
        }

        if (isPackageExcluded(cd.getType().getFullyQualifiedName())) {
            return false;
        }

        return !hasExcludedAnnotation(cd);
    }

    private boolean isPackageExcluded(String className) {
        return excludePackages.stream()
                .anyMatch(pattern -> matchesPattern(className, pattern));
    }

    private boolean matchesPattern(String className, String pattern) {
        if (pattern.endsWith("*")) {
            String prefix = pattern.substring(0, pattern.length() - 1);
            return className.startsWith(prefix);
        }
        return className.equals(pattern);
    }

    private boolean hasExcludedAnnotation(J.ClassDeclaration classDecl) {
        return classDecl.getLeadingAnnotations().stream()
                .anyMatch(annotation -> {
                    String annotationName = annotation.getAnnotationType().toString();
                    if (excludeAnnotations.contains(annotationName) || excludeAnnotations.contains("@" + annotationName)) {
                        return true;
                    }

                    // Check simple name (e.g., "Configuration" for "org.springframework.context.annotation.Configuration")
                    String simpleName = getSimpleName(annotationName);
                    if (excludeAnnotations.contains(simpleName) || excludeAnnotations.contains("@" + simpleName)) {
                        return true;
                    }
                    return false;
                });
    }

    private String getSimpleName(String fullyQualifiedName) {
        int lastDot = fullyQualifiedName.lastIndexOf('.');
        return lastDot >= 0 ? fullyQualifiedName.substring(lastDot + 1) : fullyQualifiedName;
    }

    private void excludeSupertypes(JavaType.FullyQualified type) {
        if (type.getSupertype() != null && typesToNotFinalize.add(type.getSupertype().getFullyQualifiedName())) {
            excludeSupertypes(type.getSupertype());
        }
    }

    /**
     * Adding the `final` modifier is performed in a second phase, because we first need to check if any of the
     * classes need to remain non-final due to inheritance.
     */
    private static class FinalizingVisitor extends JavaIsoVisitor<ExecutionContext> {
        private final Set<String> typesToFinalize;

        public FinalizingVisitor(Set<String> typesToFinalize) {
            this.typesToFinalize = new HashSet<>(typesToFinalize);
        }

        @Override
        public J.ClassDeclaration visitClassDeclaration(J.ClassDeclaration classDecl, ExecutionContext ctx) {
            J.ClassDeclaration cd = super.visitClassDeclaration(classDecl, ctx);
            if (cd.getType() != null) {
                String fullyQualifiedName = cd.getType().getFullyQualifiedName();
                if (typesToFinalize.remove(fullyQualifiedName)) {
                    List<J.Modifier> modifiers = new ArrayList<>(cd.getModifiers());
                    modifiers.add(new J.Modifier(randomId(), Space.EMPTY, Markers.EMPTY, null, J.Modifier.Type.Final, emptyList()));
                    modifiers = sortModifiers(modifiers);
                    cd = cd.withModifiers(modifiers);
                    if (cd.getType() instanceof JavaType.Class && !cd.getType().hasFlags(Flag.Final)) {
                        Set<Flag> flags = new HashSet<>(cd.getType().getFlags());
                        flags.add(Flag.Final);
                        cd = cd.withType(((JavaType.Class) cd.getType()).withFlags(flags));
                    }

                    // Temporary work around until issue https://github.com/openrewrite/rewrite/issues/2348 is implemented.
                    if (!cd.getLeadingAnnotations().isEmpty()) {
                        // Setting the prefix to empty will cause the `Spaces` visitor to fix the formatting.
                        cd = cd.getPadding().withKind(cd.getPadding().getKind().withPrefix(Space.EMPTY));
                    }

                    assert getCursor().getParent() != null;
                    cd = autoFormat(cd, cd.getName(), ctx, getCursor().getParent());
                }
            }
            return cd;
        }
    }
}
