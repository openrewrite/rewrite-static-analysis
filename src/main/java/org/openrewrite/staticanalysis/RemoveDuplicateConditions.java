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
import org.openrewrite.java.tree.Statement;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import static java.util.Collections.singleton;

@Getter
public class RemoveDuplicateConditions extends Recipe {

    final String displayName = "Related \"if/else if\" conditions should not be the same";

    final String description = "When an `if`/`else if` chain contains the same condition more than once, " +
            "the second branch can never execute because the first matching branch always wins. " +
            "The duplicate branch is dead code and should be removed.";

    final Set<String> tags = singleton("RSPEC-S1862");

    final Duration estimatedEffortPerOccurrence = Duration.ofMinutes(10);

    @Override
    public TreeVisitor<?, ExecutionContext> getVisitor() {
        return new JavaVisitor<ExecutionContext>() {

            @Override
            public J visitIf(J.If if_, ExecutionContext ctx) {
                J.If if__ = (J.If) super.visitIf(if_, ctx);

                if (if__.getElsePart() == null) {
                    return if__;
                }

                // Only process the outermost if in a chain
                if (getCursor().getParentTreeCursor().getValue() instanceof J.If.Else) {
                    return if__;
                }

                // Collect all conditions in the chain
                List<Expression> conditions = new ArrayList<>();
                List<J.If> ifNodes = new ArrayList<>();
                J.If current = if__;
                while (current != null) {
                    conditions.add(current.getIfCondition().getTree());
                    ifNodes.add(current);
                    if (current.getElsePart() == null) {
                        break;
                    }
                    Statement elseBody = current.getElsePart().getBody();
                    current = elseBody instanceof J.If ? (J.If) elseBody : null;
                }

                // Find and remove branches with duplicate conditions
                J.If result = if__;
                boolean changed = false;
                for (int i = conditions.size() - 1; i >= 1; i--) {
                    for (int j = 0; j < i; j++) {
                        if (SemanticallyEqual.areEqual(conditions.get(i), conditions.get(j))) {
                            result = removeBranch(result, ifNodes.get(i));
                            changed = true;
                            break;
                        }
                    }
                }

                return changed ? result : if__;
            }

            private J.If removeBranch(J.If root, J.If toRemove) {
                // Walk the chain and skip the duplicate branch
                return rebuildChain(root, toRemove);
            }

            private J.If rebuildChain(J.If current, J.If toRemove) {
                if (current == toRemove) {
                    // Skip this branch: connect its else to the previous branch
                    // This case is handled by the caller
                    return current;
                }

                if (current.getElsePart() == null) {
                    return current;
                }

                Statement elseBody = current.getElsePart().getBody();
                if (elseBody instanceof J.If) {
                    J.If elseIf = (J.If) elseBody;
                    if (elseIf == toRemove) {
                        // Remove this else-if by connecting current to toRemove's else
                        return current.withElsePart(toRemove.getElsePart());
                    }
                    J.If rebuilt = rebuildChain(elseIf, toRemove);
                    if (rebuilt != elseIf) {
                        return current.withElsePart(current.getElsePart().withBody(rebuilt));
                    }
                }
                return current;
            }
        };
    }
}
