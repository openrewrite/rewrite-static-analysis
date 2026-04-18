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
import org.openrewrite.java.tree.J;
import org.openrewrite.java.tree.Statement;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import static java.util.Collections.singleton;

@Getter
public class AllBranchesIdentical extends Recipe {

    final String displayName = "All branches in a conditional should not have the same implementation";

    final String description = "When every branch of an `if`/`else` chain executes the same code, " +
            "the condition serves no purpose and the code block can be used directly.";

    final Set<String> tags = singleton("RSPEC-S3923");

    final Duration estimatedEffortPerOccurrence = Duration.ofMinutes(15);

    @Override
    public TreeVisitor<?, ExecutionContext> getVisitor() {
        return new JavaVisitor<ExecutionContext>() {

            @Override
            public J visitIf(J.If if_, ExecutionContext ctx) {
                J.If if__ = (J.If) super.visitIf(if_, ctx);

                if (if__.getElsePart() == null) {
                    return if__;
                }

                List<Statement> bodies = new ArrayList<>();
                J.If current = if__;

                while (current != null) {
                    bodies.add(current.getThenPart());
                    if (current.getElsePart() == null) {
                        return if__;
                    }
                    Statement elseBody = current.getElsePart().getBody();
                    if (elseBody instanceof J.If) {
                        current = (J.If) elseBody;
                    } else {
                        bodies.add(elseBody);
                        current = null;
                    }
                }

                Statement first = bodies.get(0);
                for (int i = 1; i < bodies.size(); i++) {
                    if (!SemanticallyEqual.areEqual(first, bodies.get(i))) {
                        return if__;
                    }
                }

                doAfterVisit(new RemoveUnneededBlock().getVisitor());
                return first.withPrefix(if__.getPrefix());
            }
        };
    }
}
