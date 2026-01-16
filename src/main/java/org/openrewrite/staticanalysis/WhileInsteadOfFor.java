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
import org.openrewrite.java.JavaTemplate;
import org.openrewrite.java.JavaVisitor;
import org.openrewrite.java.tree.J;

import java.util.Set;

import static java.util.Collections.singleton;

public class WhileInsteadOfFor extends Recipe {

    @Getter
    final String displayName = "Prefer `while` over `for` loops";

    @Getter
    final String description = "When only the condition expression is defined in a for loop, and the initialization and increment expressions are missing, a while loop should be used instead to increase readability.";

    @Getter
    final Set<String> tags = singleton("RSPEC-S1264");

    @Override
    public TreeVisitor<?, ExecutionContext> getVisitor() {
        return new JavaVisitor<ExecutionContext>() {
            final JavaTemplate whileLoop = JavaTemplate.builder("while(#{any(boolean)}) {}")
                    .build();

            @Override
            public J visitForLoop(J.ForLoop forLoop, ExecutionContext ctx) {
                if (forLoop.getControl().getInit().get(0) instanceof J.Empty &&
                    forLoop.getControl().getUpdate().get(0) instanceof J.Empty &&
                    !(forLoop.getControl().getCondition() instanceof J.Empty)
                ) {
                    J.WhileLoop w = whileLoop.apply(getCursor(), forLoop.getCoordinates().replace(), forLoop.getControl().getCondition());
                    return w.withBody(forLoop.getBody());
                }
                return super.visitForLoop(forLoop, ctx);
            }
        };
    }
}
