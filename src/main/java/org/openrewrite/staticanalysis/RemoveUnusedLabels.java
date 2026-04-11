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
import org.openrewrite.java.JavaVisitor;
import org.openrewrite.java.tree.J;

import java.time.Duration;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;

import static java.util.Collections.singleton;

@Getter
public class RemoveUnusedLabels extends Recipe {

    final String displayName = "Remove unused labels";

    final String description = "Remove labels that are not referenced by any `break` or `continue` statement.";

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
                return l.getStatement().withPrefix(l.getPrefix());
            }
        };
    }
}
