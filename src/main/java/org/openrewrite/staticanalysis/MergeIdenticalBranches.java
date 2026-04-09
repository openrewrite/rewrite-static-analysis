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
import org.openrewrite.java.search.SemanticallyEqual;
import org.openrewrite.java.tree.Expression;
import org.openrewrite.java.tree.J;
import org.openrewrite.java.tree.Space;
import org.openrewrite.java.tree.Statement;

import java.time.Duration;
import java.util.Set;

import static java.util.Collections.singleton;

public class MergeIdenticalBranches extends Recipe {

    @Getter
    final String displayName = "Branches with identical implementations should be merged";

    @Getter
    final String description = "When two consecutive branches of an `if`/`else if` chain execute the same code, " +
            "they can be merged by combining their conditions with `||`. " +
            "This removes duplication and makes the intent clearer.";

    @Getter
    final Set<String> tags = singleton("RSPEC-S1871");

    @Getter
    final Duration estimatedEffortPerOccurrence = Duration.ofMinutes(10);

    @Override
    public TreeVisitor<?, ExecutionContext> getVisitor() {
        return new JavaVisitor<ExecutionContext>() {

            @Override
            public J visitIf(J.If if_, ExecutionContext ctx) {
                J.If if__ = (J.If) super.visitIf(if_, ctx);

                // Only process the outermost if
                if (getCursor().getParentTreeCursor().getValue() instanceof J.If.Else) {
                    return if__;
                }

                J.If merged = mergeConsecutiveIdentical(if__);
                return merged != if__ ? merged : if__;
            }

            private J.If mergeConsecutiveIdentical(J.If outerIf) {
                if (outerIf.getElsePart() == null) {
                    return outerIf;
                }

                Statement elseBody = outerIf.getElsePart().getBody();

                if (elseBody instanceof J.If) {
                    J.If elseIf = (J.If) elseBody;

                    if (SemanticallyEqual.areEqual(outerIf.getThenPart(), elseIf.getThenPart())) {
                        // Merge: combine conditions with ||, skip the else-if
                        Expression combined = JavaElementFactory.newLogicalExpression(
                                J.Binary.Type.Or,
                                outerIf.getIfCondition().getTree(),
                                elseIf.getIfCondition().getTree().withPrefix(Space.SINGLE_SPACE)
                        );

                        J.If merged = outerIf
                                .withIfCondition(outerIf.getIfCondition().withTree(combined))
                                .withElsePart(elseIf.getElsePart());

                        // Continue merging in case the next branch also matches
                        return mergeConsecutiveIdentical(merged);
                    }

                    // Recurse into the rest of the chain
                    J.If rebuiltElseIf = mergeConsecutiveIdentical(elseIf);
                    if (rebuiltElseIf != elseIf) {
                        return outerIf.withElsePart(outerIf.getElsePart().withBody(rebuiltElseIf));
                    }
                }

                return outerIf;
            }
        };
    }
}
