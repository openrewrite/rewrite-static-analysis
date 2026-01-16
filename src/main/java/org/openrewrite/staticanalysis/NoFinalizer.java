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
import org.openrewrite.java.MethodMatcher;
import org.openrewrite.java.search.DeclaresMethod;
import org.openrewrite.java.tree.J;

import java.time.Duration;
import java.util.Set;

import static java.util.Collections.singleton;

public class NoFinalizer extends Recipe {

    private static final MethodMatcher FINALIZER = new MethodMatcher("java.lang.Object finalize()", true);

    @Getter
    final String displayName = "Remove `finalize()` method";

    @Getter
    final String description = "Finalizers are deprecated. Use of `finalize()` can lead to performance issues, deadlocks, hangs, and other undesirable behavior.";

    @Override
    public Set<String> getTags() {
        return singleton("RSPEC-S1111");
    }

    @Getter
    final Duration estimatedEffortPerOccurrence = Duration.ofMinutes(20);

    @Override
    public TreeVisitor<?, ExecutionContext> getVisitor() {
        return Preconditions.check(new DeclaresMethod<>(FINALIZER), new JavaIsoVisitor<ExecutionContext>() {
            @Override
            public J.ClassDeclaration visitClassDeclaration(J.ClassDeclaration classDecl, ExecutionContext ctx) {
                J.ClassDeclaration cd = super.visitClassDeclaration(classDecl, ctx);

                return cd.withBody(cd.getBody().withStatements(ListUtils.map(cd.getBody().getStatements(), stmt -> {
                    if (stmt instanceof J.MethodDeclaration) {
                        if (FINALIZER.matches((J.MethodDeclaration) stmt, classDecl)) {
                            return null;
                        }
                    }
                    return stmt;
                })));
            }
        });
    }
}
