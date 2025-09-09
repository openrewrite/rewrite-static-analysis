/*
 * Copyright 2025 the original author or authors.
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
import org.openrewrite.internal.ListUtils;
import org.openrewrite.java.JavaIsoVisitor;
import org.openrewrite.java.tree.J;
import org.openrewrite.java.tree.Space;
import org.openrewrite.java.tree.Statement;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

@EqualsAndHashCode(callSuper = false)
@Value
public class MoveFieldsToTopOfClass extends Recipe {

    @Override
    public String getDisplayName() {
        return "Move fields to the top of class definition";
    }

    @Override
    public String getDescription() {
        return "Reorders class members so that all field declarations appear before any method declarations, " +
               "constructors, or other class members. This improves code organization and readability by " +
               "grouping field declarations together at the top of the class. Comments associated with fields " +
               "are preserved during the reordering.";
    }

    @Override
    public Duration getEstimatedEffortPerOccurrence() {
        return Duration.ofMinutes(2);
    }

    @Override
    public TreeVisitor<?, ExecutionContext> getVisitor() {
        return new JavaIsoVisitor<ExecutionContext>() {
            @Override
            public J.ClassDeclaration visitClassDeclaration(J.ClassDeclaration classDecl, ExecutionContext ctx) {
                J.ClassDeclaration cd = super.visitClassDeclaration(classDecl, ctx);

                List<Statement> statements = cd.getBody().getStatements();
                if (statements.isEmpty()) {
                    return cd;
                }

                List<Statement> fieldDeclarations = new ArrayList<>();
                List<Statement> otherStatements = new ArrayList<>();

                // Separate field declarations from other statements
                for (Statement statement : statements) {
                    if (statement instanceof J.VariableDeclarations) {
                        fieldDeclarations.add(statement);
                    } else {
                        otherStatements.add(statement);
                    }
                }

                // If all fields are already at the top, no changes needed
                if (fieldDeclarations.isEmpty() || isAlreadyOrdered(statements, fieldDeclarations)) {
                    return cd;
                }

                // Reorder: fields first, then other statements
                List<Statement> reorderedStatements = new ArrayList<>();

                // Add field declarations with preserved comments and proper spacing
                for (int i = 0; i < fieldDeclarations.size(); i++) {
                    Statement field = fieldDeclarations.get(i);
                    if (i == 0) {
                        // First field should have the same prefix as the first statement originally had,
                        // but preserve its original comments
                        Space originalPrefix = field.getPrefix();
                        Space firstStatementPrefix = statements.get(0).getPrefix();
                        
                        // Combine: use first statement's whitespace but preserve field's comments
                        Space newPrefix = firstStatementPrefix.withComments(originalPrefix.getComments());
                        field = field.withPrefix(newPrefix);
                    }
                    reorderedStatements.add(field);
                }

                // Add other statements
                reorderedStatements.addAll(otherStatements);

                return autoFormat(cd.withBody(cd.getBody().withStatements(reorderedStatements)), ctx);
            }

            private boolean isAlreadyOrdered(List<Statement> allStatements, List<Statement> fieldDeclarations) {
                if (fieldDeclarations.isEmpty()) {
                    return true;
                }

                int firstNonFieldIndex = -1;
                for (int i = 0; i < allStatements.size(); i++) {
                    if (!(allStatements.get(i) instanceof J.VariableDeclarations)) {
                        firstNonFieldIndex = i;
                        break;
                    }
                }

                // If there are no non-field statements, or all fields come before first non-field
                if (firstNonFieldIndex == -1) {
                    return true;
                }

                // Check if any field declarations come after the first non-field statement
                for (int i = firstNonFieldIndex; i < allStatements.size(); i++) {
                    if (allStatements.get(i) instanceof J.VariableDeclarations) {
                        return false;
                    }
                }

                return true;
            }
        };
    }
}
