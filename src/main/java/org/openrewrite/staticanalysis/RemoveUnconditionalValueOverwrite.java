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
import org.openrewrite.Recipe;
import org.openrewrite.TreeVisitor;
import org.openrewrite.internal.ListUtils;
import org.openrewrite.java.JavaIsoVisitor;
import org.openrewrite.java.MethodMatcher;
import org.openrewrite.java.search.SemanticallyEqual;
import org.openrewrite.java.tree.Expression;
import org.openrewrite.java.tree.J;
import org.openrewrite.java.tree.Statement;

import java.time.Duration;
import java.util.List;
import java.util.Set;

import static java.util.Collections.singleton;

public class RemoveUnconditionalValueOverwrite extends Recipe {

    @Getter
    final String displayName = "Map values should not be replaced unconditionally";

    @Getter
    final String description = "When `map.put(key, value)` is called twice in a row with the same key, " +
            "the first call is dead code because its value is immediately overwritten. " +
            "Remove the first call to make the intent clear.";

    @Getter
    final Set<String> tags = singleton("RSPEC-S4143");

    @Getter
    final Duration estimatedEffortPerOccurrence = Duration.ofMinutes(5);

    private static final MethodMatcher MAP_PUT = new MethodMatcher("java.util.Map put(..)", true);
    private static final MethodMatcher MAP_SET = new MethodMatcher("java.util.Map set(..)", true);

    @Override
    public TreeVisitor<?, ExecutionContext> getVisitor() {
        return new JavaIsoVisitor<ExecutionContext>() {

            @Override
            public J.Block visitBlock(J.Block block, ExecutionContext ctx) {
                J.Block b = super.visitBlock(block, ctx);
                List<Statement> statements = b.getStatements();

                return b.withStatements(ListUtils.map(statements, (i, stmt) -> {
                    if (i >= statements.size() - 1) {
                        return stmt;
                    }

                    Expression key = extractMapPutKey(stmt);
                    Expression receiver = extractMapPutReceiver(stmt);
                    if (key == null || receiver == null) {
                        return stmt;
                    }

                    Statement next = statements.get(i + 1);
                    Expression nextKey = extractMapPutKey(next);
                    Expression nextReceiver = extractMapPutReceiver(next);
                    if (nextKey == null || nextReceiver == null) {
                        return stmt;
                    }

                    if (SemanticallyEqual.areEqual(key, nextKey) &&
                        SemanticallyEqual.areEqual(receiver, nextReceiver)) {
                        //noinspection DataFlowIssue
                        return null;
                    }
                    return stmt;
                }));
            }

            private Expression extractMapPutKey(Statement stmt) {
                if (!(stmt instanceof J.MethodInvocation)) {
                    return null;
                }
                J.MethodInvocation method = (J.MethodInvocation) stmt;
                if ((MAP_PUT.matches(method) || MAP_SET.matches(method)) &&
                    method.getArguments().size() >= 2) {
                    return method.getArguments().get(0);
                }
                return null;
            }

            private Expression extractMapPutReceiver(Statement stmt) {
                if (!(stmt instanceof J.MethodInvocation)) {
                    return null;
                }
                J.MethodInvocation method = (J.MethodInvocation) stmt;
                if ((MAP_PUT.matches(method) || MAP_SET.matches(method)) &&
                    method.getSelect() != null) {
                    return method.getSelect();
                }
                return null;
            }
        };
    }
}
