/*
 * Copyright 2022 the original author or authors.
 * <p>
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * <p>
 * https://www.apache.org/licenses/LICENSE-2.0
 * <p>
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.openrewrite.staticanalysis;

import org.openrewrite.ExecutionContext;
import org.openrewrite.Recipe;
import org.openrewrite.SourceFile;
import org.openrewrite.TreeVisitor;
import org.openrewrite.internal.lang.Nullable;
import org.openrewrite.java.JavaVisitor;
import org.openrewrite.java.cleanup.SimplifyBooleanExpressionVisitor;
import org.openrewrite.java.cleanup.UnnecessaryParenthesesVisitor;
import org.openrewrite.java.style.Checkstyle;
import org.openrewrite.java.style.EmptyBlockStyle;
import org.openrewrite.java.tree.Expression;
import org.openrewrite.java.tree.J;
import org.openrewrite.java.tree.JavaSourceFile;
import org.openrewrite.java.tree.Statement;

import java.util.Optional;

public class SimplifyConstantIfBranchExecution extends Recipe {

    @Override
    public String getDisplayName() {
        return "Simplify constant if branch execution";
    }

    @Override
    public String getDescription() {
        return "Checks for if expressions that are always `true` or `false` and simplifies them.";
    }

    @Override
    public TreeVisitor<?, ExecutionContext> getVisitor() {
        return new SimplifyConstantIfBranchExecutionVisitor();
    }

    private static class SimplifyConstantIfBranchExecutionVisitor extends JavaVisitor<ExecutionContext> {

        @Override
        public J visitBlock(J.Block block, ExecutionContext ctx) {
            J.Block bl = (J.Block) super.visitBlock(block, ctx);
            if (bl != block) {
                bl = (J.Block) new RemoveUnneededBlock.RemoveUnneededBlockStatementVisitor()
                        .visitNonNull(bl, ctx, getCursor().getParentOrThrow());
                EmptyBlockStyle style = getCursor().firstEnclosingOrThrow(JavaSourceFile.class)
                        .getStyle(EmptyBlockStyle.class);
                if (style == null) {
                    style = Checkstyle.emptyBlock();
                }
                bl = (J.Block) new EmptyBlockVisitor<>(style)
                        .visitNonNull(bl, ctx, getCursor().getParentOrThrow());
            }
            return bl;
        }

        @SuppressWarnings("unchecked")
        private <E extends Expression> E cleanupBooleanExpression(
                E expression, ExecutionContext ctx
        ) {
            E ex1 =
                    (E) new UnnecessaryParenthesesVisitor()
                            .visitNonNull(expression, ctx, getCursor().getParentOrThrow());
            ex1 = (E) new SimplifyBooleanExpressionVisitor()
                    .visitNonNull(ex1, ctx, getCursor().getParentTreeCursor());
            if (expression == ex1 || isLiteralFalse(ex1) || isLiteralTrue(ex1)) {
                return ex1;
            }
            // Run recursively until no further changes are needed
            return cleanupBooleanExpression(ex1, ctx);
        }

        @Override
        public J visitIf(J.If if_, ExecutionContext ctx) {
            J.If if__ = (J.If) super.visitIf(if_, ctx);
            J.If ifBeforeCleanup = if__;

            J.ControlParentheses<Expression> cp = cleanupBooleanExpression(if__.getIfCondition(), ctx);
            if__ = if__.withIfCondition(cp);

            // The compile-time constant value of the if condition control parentheses.
            final Optional<Boolean> compileTimeConstantBoolean;
            if (isLiteralTrue(cp.getTree())) {
                compileTimeConstantBoolean = Optional.of(true);
            } else if (isLiteralFalse(cp.getTree())) {
                compileTimeConstantBoolean = Optional.of(false);
            } else {
                // The condition is not a literal, so we can't simplify it.
                compileTimeConstantBoolean = Optional.empty();
            }

            // The simplification process did not result in resolving to a single 'true' or 'false' value
            if (!compileTimeConstantBoolean.isPresent()) {
                return ifBeforeCleanup; // Return the visited `if`
            } else if (compileTimeConstantBoolean.get()) {
                // True branch
                // Only keep the `then` branch, and remove the `else` branch.
                Statement s = if__.getThenPart().withPrefix(if__.getPrefix());
                doAfterVisit(new RemoveUnreachableCodeVisitor());
                return maybeAutoFormat(
                        if__,
                        s,
                        ctx
                );
            } else {
                // False branch
                // Only keep the `else` branch, and remove the `then` branch.
                if (if__.getElsePart() != null) {
                    // The `else` part needs to be kept
                    Statement s = if__.getElsePart().getBody().withPrefix(if__.getPrefix());
                    doAfterVisit(new RemoveUnreachableCodeVisitor());
                    return maybeAutoFormat(
                            if__,
                            s,
                            ctx
                    );
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
                return J.Block.createEmptyBlock();
            }
        }

        private static boolean isLiteralTrue(@Nullable Expression expression) {
            return J.Literal.isLiteralValue(expression, Boolean.TRUE);
        }

        private static boolean isLiteralFalse(@Nullable Expression expression) {
            return J.Literal.isLiteralValue(expression, Boolean.FALSE);
        }
    }

}
