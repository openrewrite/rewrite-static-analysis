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

import lombok.Getter;
import org.jspecify.annotations.Nullable;
import org.openrewrite.ExecutionContext;
import org.openrewrite.Recipe;
import org.openrewrite.TreeVisitor;
import org.openrewrite.internal.ListUtils;
import org.openrewrite.internal.ReflectionUtils;
import org.openrewrite.java.JavaVisitor;
import org.openrewrite.java.tree.J;
import org.openrewrite.kotlin.tree.K;

import java.time.Duration;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;

import static java.util.Collections.singleton;

@Getter
public class RemoveUnusedLabels extends Recipe {

    private static final boolean IS_KOTLIN_AVAILABLE = ReflectionUtils.isClassAvailable("org.openrewrite.kotlin.tree.K");

    final String displayName = "Remove unused labels";

    final String description = "Remove labels that are not referenced by any `break` or `continue` statement " +
            "or by a Kotlin labeled `return` or `this` expression.";

    final Set<String> tags = singleton("RSPEC-S1065");

    final Duration estimatedEffortPerOccurrence = Duration.ofMinutes(1);

    @Override
    public TreeVisitor<?, ExecutionContext> getVisitor() {
        return new JavaVisitor<ExecutionContext>() {
            @Override
            public J visitLabel(J.Label label, ExecutionContext ctx) {
                J.Label l = (J.Label) super.visitLabel(label, ctx);
                String labelName = l.getLabel().getSimpleName();

                boolean used = new JavaVisitor<AtomicBoolean>() {
                    @Override
                    public @Nullable J preVisit(J tree, AtomicBoolean u) {
                        // Kotlin also references labels through `return@label` and `this@label`
                        if (IS_KOTLIN_AVAILABLE) {
                            J.Identifier kotlinLabel = null;
                            if (tree instanceof K.Return) {
                                kotlinLabel = ((K.Return) tree).getLabel();
                            } else if (tree instanceof K.This) {
                                kotlinLabel = ((K.This) tree).getLabel();
                            }
                            if (kotlinLabel != null && labelName.equals(kotlinLabel.getSimpleName())) {
                                u.set(true);
                            }
                        }
                        return tree;
                    }

                    @Override
                    public J visitBreak(J.Break breakStatement, AtomicBoolean u) {
                        if (breakStatement.getLabel() != null &&
                            labelName.equals(breakStatement.getLabel().getSimpleName())) {
                            u.set(true);
                        }
                        return super.visitBreak(breakStatement, u);
                    }

                    @Override
                    public J visitContinue(J.Continue continueStatement, AtomicBoolean u) {
                        if (continueStatement.getLabel() != null &&
                            labelName.equals(continueStatement.getLabel().getSimpleName())) {
                            u.set(true);
                        }
                        return super.visitContinue(continueStatement, u);
                    }
                }.reduce(l.getStatement(), new AtomicBoolean(false)).get();

                if (used) {
                    return l;
                }
                // The label is removed, so any comments attached to it move onto the statement it labeled
                return l.getStatement().withPrefix(l.getPrefix().withComments(ListUtils.concatAll(l.getPrefix().getComments(),
                        ListUtils.concatAll(l.getPadding().getLabel().getAfter().getComments(), l.getStatement().getComments()))));
            }
        };
    }
}
