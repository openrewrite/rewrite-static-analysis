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
import org.openrewrite.ExecutionContext;
import org.openrewrite.Preconditions;
import org.openrewrite.Recipe;
import org.openrewrite.TreeVisitor;
import org.openrewrite.internal.ListUtils;
import org.openrewrite.java.JavaIsoVisitor;
import org.openrewrite.java.tree.J;
import org.openrewrite.staticanalysis.csharp.CSharpFileChecker;

import java.time.Duration;
import java.util.Set;

import static java.util.Collections.singleton;
import static org.openrewrite.java.tree.J.Modifier.Type.Static;

public class NestedEnumsAreNotStatic extends Recipe {
    @Getter
    final String displayName = "Nested enums are not static";

    @Getter
    final String description = "Remove static modifier from nested enum types since they are implicitly static.";

    @Override
    public Set<String> getTags() {
        return singleton("RSPEC-S2786");
    }

    @Getter
    final Duration estimatedEffortPerOccurrence = Duration.ofMinutes(2);

    @Override
    public TreeVisitor<?, ExecutionContext> getVisitor() {
        return Preconditions.check(Preconditions.not(new CSharpFileChecker<>()), new JavaIsoVisitor<ExecutionContext>() {
            @Override
            public J.ClassDeclaration visitClassDeclaration(J.ClassDeclaration classDecl, ExecutionContext ctx) {
                J.ClassDeclaration cd = super.visitClassDeclaration(classDecl, ctx);
                if (shouldRemoveStaticModifierFromClass(cd)) {
                    return maybeAutoFormat(
                            cd,
                            cd.withModifiers(ListUtils.filter(cd.getModifiers(), mod -> mod.getType() != Static)),
                            cd.getName(),
                            ctx,
                            getCursor().getParent());
                }
                return cd;
            }

            private boolean shouldRemoveStaticModifierFromClass(J.ClassDeclaration cd) {
                return cd.getKind() == J.ClassDeclaration.Kind.Type.Enum &&
                        cd.getType() != null &&
                        cd.getType().getOwningClass() != null &&
                        J.Modifier.hasModifier(cd.getModifiers(), Static);
            }
        });
    }
}
