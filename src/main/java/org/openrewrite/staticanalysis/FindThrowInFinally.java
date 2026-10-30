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

import lombok.EqualsAndHashCode;
import lombok.Value;
import org.openrewrite.Cursor;
import org.openrewrite.ExecutionContext;
import org.openrewrite.Preconditions;
import org.openrewrite.Recipe;
import org.openrewrite.TreeVisitor;
import org.openrewrite.java.JavaIsoVisitor;
import org.openrewrite.java.tree.J;
import org.openrewrite.marker.SearchResult;
import org.openrewrite.staticanalysis.java.JavaFileChecker;

import java.time.Duration;
import java.util.Iterator;
import java.util.Set;

import static java.util.Collections.singleton;

@Value
@EqualsAndHashCode(callSuper = false)
public class FindThrowInFinally extends Recipe {

    private static final String MESSAGE = "A `throw` in a `finally` block replaces any exception the `try` or " +
            "`catch` was propagating, losing its message and stack trace.";

    String displayName = "Find `throw` statements in `finally` blocks";

    String description = "Marks `throw` statements that run as part of a `finally` block. The thrown exception " +
            "replaces whatever the `try` or `catch` block was propagating, so the original failure is lost. A " +
            "`throw` inside the body of a lambda, a local class or an anonymous class declared in the `finally` is " +
            "not marked, because it does not leave the `finally` when the block runs.";

    Set<String> tags = singleton("RSPEC-S1163");

    Duration estimatedEffortPerOccurrence = Duration.ofMinutes(30);

    @Override
    public TreeVisitor<?, ExecutionContext> getVisitor() {
        return Preconditions.check(new JavaFileChecker<>(), new JavaIsoVisitor<ExecutionContext>() {
            @Override
            public J.Throw visitThrow(J.Throw thrown, ExecutionContext ctx) {
                J.Throw t = super.visitThrow(thrown, ctx);
                return runsInFinallyBlock(getCursor()) ? SearchResult.found(t, MESSAGE) : t;
            }
        });
    }

    private static boolean runsInFinallyBlock(Cursor cursor) {
        J child = null;
        for (Iterator<Cursor> path = cursor.getPathAsCursors(); path.hasNext(); ) {
            Object value = path.next().getValue();
            // An anonymous class's body does not run with the `finally`, but its constructor arguments do
            if (value instanceof J.MethodDeclaration || value instanceof J.Lambda ||
                    value instanceof J.ClassDeclaration ||
                    value instanceof J.NewClass && ((J.NewClass) value).getBody() == child) {
                return false;
            }
            if (value instanceof J.Try && ((J.Try) value).getFinally() == child) {
                return true;
            }
            if (value instanceof J) {
                child = (J) value;
            }
        }
        return false;
    }
}
