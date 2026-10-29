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
import org.jspecify.annotations.Nullable;
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
public class FindJumpOutOfFinally extends Recipe {

    String displayName = "Find jump statements that leave a `finally` block";

    String description = "Marks a `return`, `break` or `continue` in a `finally` block whose target lies outside " +
            "that block. Leaving a `finally` this way discards any exception the `try` or `catch` was propagating, " +
            "so the caller sees a normal completion instead of the failure. A `break` or `continue` that stays " +
            "inside a loop, a `switch` or a label declared in the `finally` is not marked, and neither is a jump " +
            "inside a lambda, a local class or an anonymous class the `finally` only declares. A `throw` in a " +
            "`finally` is left to `FindThrowInFinally`.";

    Set<String> tags = singleton("RSPEC-S1143");

    Duration estimatedEffortPerOccurrence = Duration.ofMinutes(30);

    @Override
    public TreeVisitor<?, ExecutionContext> getVisitor() {
        return Preconditions.check(new JavaFileChecker<>(), new JavaIsoVisitor<ExecutionContext>() {
            @Override
            public J.Return visitReturn(J.Return return_, ExecutionContext ctx) {
                J.Return r = super.visitReturn(return_, ctx);
                return leavesFinallyBlock(getCursor(), null, Jump.RETURN) ?
                        SearchResult.found(r, message("return")) : r;
            }

            @Override
            public J.Break visitBreak(J.Break break_, ExecutionContext ctx) {
                J.Break b = super.visitBreak(break_, ctx);
                return leavesFinallyBlock(getCursor(), labelName(b.getLabel()), Jump.BREAK) ?
                        SearchResult.found(b, message("break")) : b;
            }

            @Override
            public J.Continue visitContinue(J.Continue continue_, ExecutionContext ctx) {
                J.Continue c = super.visitContinue(continue_, ctx);
                return leavesFinallyBlock(getCursor(), labelName(c.getLabel()), Jump.CONTINUE) ?
                        SearchResult.found(c, message("continue")) : c;
            }
        });
    }

    private static @Nullable String labelName(J.@Nullable Identifier label) {
        return label == null ? null : label.getSimpleName();
    }

    private static String message(String keyword) {
        return "This `" + keyword + "` leaves the enclosing `finally` block, discarding any exception the `try` or " +
                "`catch` was propagating.";
    }

    /**
     * Walks out of the jump statement looking for the `finally` block it leaves. An enclosing loop, `switch` or
     * label that the jump targets, or a lambda or class body that only the `finally` declares, stops the walk.
     */
    private static boolean leavesFinallyBlock(Cursor cursor, @Nullable String label, Jump jump) {
        J child = null;
        for (Iterator<Cursor> path = cursor.getPathAsCursors(); path.hasNext(); ) {
            Object value = path.next().getValue();
            // An anonymous class body is an execution boundary too, and it has no `J.ClassDeclaration`,
            // so an initializer inside one would otherwise be read as part of the `finally`
            if (value instanceof J.MethodDeclaration || value instanceof J.Lambda ||
                    value instanceof J.ClassDeclaration ||
                    value instanceof J.NewClass && ((J.NewClass) value).getBody() == child) {
                return false;
            }
            if (label == null ? isJumpTarget(value, jump) :
                    value instanceof J.Label && label.equals(((J.Label) value).getLabel().getSimpleName())) {
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

    /** A `return` leaves every enclosing statement, so only a `break` or `continue` is stopped by its target. */
    private static boolean isJumpTarget(Object value, Jump jump) {
        if (jump == Jump.RETURN) {
            return false;
        }
        // A `break` cannot leave a switch expression, so only a `J.Switch` statement is one of its targets
        return value instanceof J.ForLoop || value instanceof J.ForEachLoop || value instanceof J.WhileLoop ||
                value instanceof J.DoWhileLoop || jump == Jump.BREAK && value instanceof J.Switch;
    }

    private enum Jump {
        RETURN, BREAK, CONTINUE
    }
}
