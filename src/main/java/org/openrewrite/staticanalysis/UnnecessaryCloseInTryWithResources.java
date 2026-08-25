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
import org.openrewrite.java.search.SemanticallyEqual;
import org.openrewrite.java.tree.J;
import org.openrewrite.java.tree.Space;
import org.openrewrite.java.tree.Statement;
import org.openrewrite.staticanalysis.groovy.GroovyFileChecker;
import org.openrewrite.staticanalysis.java.JavaFileChecker;
import org.openrewrite.staticanalysis.kotlin.KotlinFileChecker;

import java.time.Duration;
import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;

import static java.util.Collections.singleton;

public class UnnecessaryCloseInTryWithResources extends Recipe {
    @Getter
    final String displayName = "Unnecessary close in try-with-resources";

    @Getter
    final String description = "Remove `close()` calls at the end of a try-with-resources block that close the last " +
            "declared resource, when that resource is a `java.io.Closeable`, whose `close()` has no effect once the " +
            "resource is already closed. A `close()` in any other position, or on any other resource, is left in " +
            "place, because removing it would change when, or in what order, resources are closed.";

    @Getter
    final Duration estimatedEffortPerOccurrence = Duration.ofMinutes(2);

    @Getter
    final Set<String> tags = singleton("RSPEC-S4087");

    @Override
    public TreeVisitor<?, ExecutionContext> getVisitor() {
        return Preconditions.check(
                Preconditions.or(
                        new JavaFileChecker<>(),
                        new KotlinFileChecker<>(),
                        new GroovyFileChecker<>()
                ),
                new UnnecessaryAutoCloseableVisitor()
        );
    }

    private static class UnnecessaryAutoCloseableVisitor extends JavaIsoVisitor<ExecutionContext> {
        // Closeable requires close() to be idempotent; AutoCloseable does not make that guarantee.
        private static final MethodMatcher CLOSEABLE_CLOSE_METHOD_MATCHER = new MethodMatcher("java.io.Closeable close()", true);

        @Override
        public J.Try visitTry(J.Try aTry, ExecutionContext ctx) {
            J.Try tr = super.visitTry(aTry, ctx);
            if (tr.getResources() == null || tr.getResources().isEmpty()) {
                return tr;
            }

            // Resources close in reverse order of declaration, so dropping any but the last would reorder closes
            J lastResource = tr.getResources().get(tr.getResources().size() - 1).getVariableDeclarations();
            J.Identifier lastResourceName;
            if (lastResource instanceof J.VariableDeclarations) {
                lastResourceName = ((J.VariableDeclarations) lastResource).getVariables().get(0).getName();
            } else if (lastResource instanceof J.Identifier) {
                lastResourceName = (J.Identifier) lastResource;
            } else {
                return tr;
            }

            J.Block body = tr.getBody();
            if (!body.getEnd().getComments().isEmpty()) {
                return tr;
            }

            // Anything after an explicit close can observe the closed resource, so only trailing closes are redundant
            List<Statement> statements = body.getStatements();
            int keep = statements.size();
            while (keep > 0 && statements.get(keep - 1) instanceof J.MethodInvocation) {
                J.MethodInvocation mi = (J.MethodInvocation) statements.get(keep - 1);
                if (!CLOSEABLE_CLOSE_METHOD_MATCHER.matches(mi) ||
                        !(mi.getSelect() instanceof J.Identifier) ||
                        !SemanticallyEqual.areEqual(lastResourceName, mi.getSelect()) ||
                        containsComment(mi)) {
                    break;
                }
                keep--;
            }

            int firstRemoved = keep;
            return tr.withBody(body.withStatements(ListUtils.map(statements, (i, statement) -> i < firstRemoved ? statement : null)));
        }

        private static boolean containsComment(J tree) {
            return new JavaIsoVisitor<AtomicBoolean>() {
                @Override
                public Space visitSpace(Space space, Space.Location loc, AtomicBoolean found) {
                    if (!space.getComments().isEmpty()) {
                        found.set(true);
                    }
                    return space;
                }
            }.reduce(tree, new AtomicBoolean()).get();
        }
    }
}
