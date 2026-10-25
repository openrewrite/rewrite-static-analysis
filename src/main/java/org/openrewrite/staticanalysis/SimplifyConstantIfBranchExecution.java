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
import org.openrewrite.Cursor;
import org.openrewrite.ExecutionContext;
import org.openrewrite.Recipe;
import org.openrewrite.TreeVisitor;
import org.openrewrite.internal.ListUtils;
import org.openrewrite.java.JavaVisitor;
import org.openrewrite.java.cleanup.SimplifyBooleanExpressionVisitor;
import org.openrewrite.java.cleanup.UnnecessaryParenthesesVisitor;
import org.openrewrite.java.tree.Expression;
import org.openrewrite.java.tree.J;
import org.openrewrite.java.tree.Statement;

import java.util.HashSet;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static java.util.Collections.singleton;

public class SimplifyConstantIfBranchExecution extends Recipe {

    @Getter
    final String displayName = "Simplify constant `if` branch execution";

    @Getter
    final String description = "Checks for `if` conditions that are always `true` or `false` and simplifies them. " +
            "Branches that can never execute are dead code that misleads readers and may mask logic errors introduced during refactoring.";

    @Getter
    final Set<String> tags = singleton("RSPEC-S6646");

    @Override
    public TreeVisitor<?, ExecutionContext> getVisitor() {
        return new SimplifyConstantIfBranchExecutionVisitor();
    }

    private static class SimplifyConstantIfBranchExecutionVisitor extends JavaVisitor<ExecutionContext> {

        /**
         * Block cursor message holding the ids of the statements that replaced a simplified {@code if} in that block,
         * so that only those are inlined or removed rather than every other block around them.
         */
        private static final String SIMPLIFIED_STATEMENTS = "simplifiedStatements";

        @Override
        public J visitBlock(J.Block block, ExecutionContext ctx) {
            J.Block bl = (J.Block) super.visitBlock(block, ctx);
            Set<UUID> simplifiedIds = getCursor().pollMessage(SIMPLIFIED_STATEMENTS);
            if (simplifiedIds == null) {
                return bl;
            }
            for (Statement statement : bl.getStatements()) {
                if (statement instanceof J.Block && simplifiedIds.contains(statement.getId())) {
                    J.Block simplifiedBlock = (J.Block) statement;
                    bl = isEmpty(simplifiedBlock) ?
                            bl.withStatements(ListUtils.map(bl.getStatements(), s -> s.getId().equals(statement.getId()) ? null : s)) :
                            (J.Block) new RemoveUnneededBlock.RemoveUnneededBlockStatementVisitor(simplifiedBlock)
                                    .visitNonNull(bl, ctx, getCursor().getParentOrThrow());
                }
            }
            return bl;
        }

        private J simplified(Statement replacement) {
            Cursor parent = getCursor().getParentTreeCursor();
            if (parent.getValue() instanceof J.Block) {
                parent.<Set<UUID>>computeMessageIfAbsent(SIMPLIFIED_STATEMENTS, k -> new HashSet<>()).add(replacement.getId());
            }
            return replacement;
        }

        private static boolean isEmpty(Statement statement) {
            return statement instanceof J.Block &&
                   ((J.Block) statement).getStatements().isEmpty() &&
                   ((J.Block) statement).getEnd().getComments().isEmpty();
        }

        @SuppressWarnings("unchecked")
        private static <E extends Expression> E cleanupBooleanExpression(E expression, Cursor c, ExecutionContext ctx) {
            E ex1 = (E) new UnnecessaryParenthesesVisitor<>().visitNonNull(expression, ctx, c.getParentOrThrow());
            ex1 = (E) new SimplifyBooleanExpressionVisitor().visitNonNull(ex1, ctx, c.getParentTreeCursor());
            if (expression == ex1 ||
                J.Literal.isLiteralValue(ex1, Boolean.FALSE) ||
                J.Literal.isLiteralValue(ex1, Boolean.TRUE)) {
                return ex1;
            }
            // Run recursively until no further changes are needed
            return cleanupBooleanExpression(ex1, c, ctx);
        }

        @Override
        public J visitIf(J.If if_, ExecutionContext ctx) {
            J.If if__ = (J.If) super.visitIf(if_, ctx);
            if (if_.getElsePart() != null && if_.getElsePart().getBody() instanceof J.If &&
                if__.getElsePart() != null && isEmpty(if__.getElsePart().getBody())) {
                // An `else if` that was simplified away
                if__ = if__.withElsePart(null);
            }
            J.If ifBeforeCleanup = if__;

            J.ControlParentheses<Expression> cp = cleanupBooleanExpression(if__.getIfCondition(), getCursor(), ctx);
            if__ = if__.withIfCondition(cp);

            // The compile-time constant value of the if condition control parentheses.
            final Optional<Boolean> compileTimeConstantBoolean;
            if (J.Literal.isLiteralValue(cp.getTree(), Boolean.TRUE)) {
                compileTimeConstantBoolean = Optional.of(true);
            } else if (J.Literal.isLiteralValue(cp.getTree(), Boolean.FALSE)) {
                compileTimeConstantBoolean = Optional.of(false);
            } else {
                // The condition is not a literal, so we can't simplify it.
                compileTimeConstantBoolean = Optional.empty();
            }

            // The simplification process did not result in resolving to a single 'true' or 'false' value
            if (!compileTimeConstantBoolean.isPresent()) {
                return ifBeforeCleanup; // Return the visited `if`
            }
            if (compileTimeConstantBoolean.get()) {
                // True branch
                // Only keep the `then` branch, and remove the `else` branch.
                Statement s = if__.getThenPart().withPrefix(if__.getPrefix());
                doAfterVisit(new RemoveUnreachableCodeVisitor());
                return simplified(maybeAutoFormat(
                        if__,
                        s,
                        ctx
                ));
            }
            // False branch
            // Only keep the `else` branch, and remove the `then` branch.
            if (if__.getElsePart() != null) {
                // The `else` part needs to be kept
                Statement s = if__.getElsePart().getBody().withPrefix(if__.getPrefix());
                doAfterVisit(new RemoveUnreachableCodeVisitor());
                return simplified(maybeAutoFormat(
                        if__,
                        s,
                        ctx
                ));
            }
            /*
             * The `else` branch is not present, therefore, the `if` can be removed.
             * Temporarily return an empty block that will (most likely) later be removed.
             * We need to return an empty block, in the following cases:
             * ```
             * if (a) a();
             * else if (false) { }
             * ```
             * Failing to return an empty block here would result in the following code being emitted:
             * ```
             * if (a) a();
             * else
             * ```
             * The above is not valid java and will cause later processing errors.
             */
            return simplified(J.Block.createEmptyBlock());
        }

        @Override
        public J visitTernary(J.Ternary ternary, ExecutionContext ctx) {
            J.Ternary j = (J.Ternary) super.visitTernary(ternary, ctx);
            return cleanupBooleanExpression(j, getCursor(), ctx);
        }
    }
}
