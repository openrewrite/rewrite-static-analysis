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

import org.openrewrite.ExecutionContext;
import org.openrewrite.Preconditions;
import org.openrewrite.Recipe;
import org.openrewrite.TreeVisitor;
import org.openrewrite.internal.ListUtils;
import org.openrewrite.java.JavaIsoVisitor;
import org.openrewrite.java.tree.J;
import org.openrewrite.marker.SearchResult;
import org.openrewrite.staticanalysis.csharp.CSharpFileChecker;

import java.time.Duration;
import java.util.Set;

import static java.util.Collections.singleton;

public class NestedEnumsAreNotStatic extends Recipe {

    @Override
    public String getDisplayName() {
        return "Nested enums are not static";
    }

    @Override
    public String getDescription() {
        return "Remove static modifier from nested enum types since they are implicitly static.";
    }

    @Override
    public Set<String> getTags() {
        return singleton("RSPEC-S2786");
    }

    @Override
    public Duration getEstimatedEffortPerOccurrence() {
        return Duration.ofMinutes(2);
    }

    @Override
    public TreeVisitor<?, ExecutionContext> getVisitor() {
        TreeVisitor<?, ExecutionContext> preconditions =
                Preconditions.and(new HasNestedEnum(), Preconditions.not(new CSharpFileChecker<>()));

        return Preconditions.check(preconditions, new JavaIsoVisitor<ExecutionContext>() {
            @Override
            public J.ClassDeclaration visitClassDeclaration(J.ClassDeclaration classDecl,
                                                            ExecutionContext ctx) {

                // Critical NPE guard (fixes issue #796)
                if (classDecl.getBody() == null) {
                    return classDecl;
                }

                J.ClassDeclaration cd = super.visitClassDeclaration(classDecl, ctx);

                if (cd.getKind() == J.ClassDeclaration.Kind.Type.Enum &&
                        cd.getType() != null &&
                        cd.getType().getOwningClass() != null &&
                        J.Modifier.hasModifier(cd.getModifiers(), J.Modifier.Type.Static)) {

                    cd = maybeAutoFormat(
                            cd,
                            cd.withModifiers(ListUtils.map(cd.getModifiers(), mod ->
                                    mod.getType() == J.Modifier.Type.Static ? null : mod)),
                            cd.getName(),
                            ctx,
                            getCursor().getParent()
                    );
                }
                return cd;
            }
        });
    }


     // Preconditions visitor: detects presence of nested enums.
     // Must not mutate AST.

    private static class HasNestedEnum extends JavaIsoVisitor<ExecutionContext> {

        @Override
        public J.ClassDeclaration visitClassDeclaration(J.ClassDeclaration classDecl,
                                                        ExecutionContext ctx) {

            // 🔒 Defensive guard for malformed / body-less declarations
            if (classDecl.getBody() == null) {
                return classDecl;
            }

            J.ClassDeclaration cd = super.visitClassDeclaration(classDecl, ctx);

            if (cd.getKind() == J.ClassDeclaration.Kind.Type.Enum &&
                    cd.getType() != null &&
                    cd.getType().getOwningClass() != null) {

                cd = SearchResult.found(cd);
            }
            return cd;
        }
    }
}
