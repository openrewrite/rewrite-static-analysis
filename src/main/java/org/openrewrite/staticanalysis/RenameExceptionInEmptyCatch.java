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

import lombok.EqualsAndHashCode;
import lombok.Value;
import org.openrewrite.ExecutionContext;
import org.openrewrite.Recipe;
import org.openrewrite.TreeVisitor;
import org.openrewrite.java.JavaIsoVisitor;
import org.openrewrite.java.RenameVariable;
import org.openrewrite.java.trait.Scope;
import org.openrewrite.java.tree.J;

import java.time.Duration;

@EqualsAndHashCode(callSuper = false)
@Value
public class RenameExceptionInEmptyCatch extends Recipe {

    String displayName = "Rename caught exceptions in empty catch blocks to `ignored`";

    String description = "Renames caught exceptions in empty catch blocks to `ignored`. A numeric suffix is appended to `ignored` if that name is already taken.";

    Duration estimatedEffortPerOccurrence = Duration.ofMinutes(2);

    @Override
    public TreeVisitor<?, ExecutionContext> getVisitor() {
        return new JavaIsoVisitor<ExecutionContext>() {
            @Override
            public J.Try.Catch visitCatch(J.Try.Catch aCatch, ExecutionContext ctx) {
                // Only visit empty catch blocks.
                if (aCatch.getBody().getStatements().isEmpty() && aCatch.getBody().getEnd().getComments().isEmpty()) {
                    return super.visitCatch(aCatch, ctx);
                }
                return aCatch;
            }

            @Override
            public J.VariableDeclarations visitVariableDeclarations(J.VariableDeclarations multiVariable, ExecutionContext ctx) {
                if (getCursor().getParentTreeCursor().getParentTreeCursor().getValue() instanceof J.Try.Catch) {
                    for (J.VariableDeclarations.NamedVariable variable : multiVariable.getVariables()) {
                        if (!variable.getSimpleName().contains("ignored")) {
                            // The catch block is empty, so the name only has to stay clear of what is visible here
                            doAfterVisit(new RenameVariable<>(variable, Scope.at(getCursor()).freeName("ignored", n -> false)));
                        }
                    }
                }
                return super.visitVariableDeclarations(multiVariable, ctx);
            }
        };
    }
}
