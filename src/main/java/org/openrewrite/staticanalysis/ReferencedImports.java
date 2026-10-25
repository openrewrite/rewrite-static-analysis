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

import org.jspecify.annotations.Nullable;
import org.openrewrite.java.JavaIsoVisitor;
import org.openrewrite.java.tree.Flag;
import org.openrewrite.java.tree.J;
import org.openrewrite.java.tree.JavaType;

import java.util.HashSet;
import java.util.Set;

/**
 * The names to pass to {@link org.openrewrite.java.JavaVisitor#maybeRemoveImport(String)} after removing
 * {@code tree}, so that only the imports it was the last user of go, rather than every unused import in the file.
 * Mirrors the package-private {@code RemoveImport.namesReferencedBy} of rewrite-java.
 */
final class ReferencedImports {

    private ReferencedImports() {
    }

    static Set<String> namesReferencedBy(J tree) {
        return new JavaIsoVisitor<Set<String>>() {
            @Override
            public @Nullable JavaType visitType(@Nullable JavaType javaType, Set<String> names) {
                if (javaType instanceof JavaType.Class) {
                    JavaType.Class type = (JavaType.Class) javaType;
                    if (!"java.lang".equals(type.getPackageName())) {
                        names.add(type.getFullyQualifiedName());
                    }
                } else if (javaType instanceof JavaType.Variable) {
                    JavaType.Variable variable = (JavaType.Variable) javaType;
                    if (variable.hasFlags(Flag.Static) && variable.getOwner() instanceof JavaType.Class) {
                        names.add(((JavaType.Class) variable.getOwner()).getFullyQualifiedName() + "." + variable.getName());
                    }
                }
                return super.visitType(javaType, names);
            }

            @Override
            public J.MethodInvocation visitMethodInvocation(J.MethodInvocation mi, Set<String> names) {
                if (mi.getMethodType() != null && mi.getMethodType().hasFlags(Flag.Static) && mi.getSelect() == null) {
                    names.add(mi.getMethodType().getDeclaringType().getFullyQualifiedName() + "." + mi.getSimpleName());
                }
                return super.visitMethodInvocation(mi, names);
            }
        }.reduce(tree, new HashSet<>());
    }
}
