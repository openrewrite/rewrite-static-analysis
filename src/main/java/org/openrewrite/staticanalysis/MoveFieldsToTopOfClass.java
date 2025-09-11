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
import org.openrewrite.java.JavaIsoVisitor;
import org.openrewrite.java.tree.J;
import org.openrewrite.java.tree.Space;
import org.openrewrite.java.tree.Statement;
import org.openrewrite.style.GeneralFormatStyle;
import org.openrewrite.style.Style;

import java.time.Duration;
import java.util.Comparator;
import java.util.List;
import java.util.stream.IntStream;

import static org.openrewrite.java.format.AutodetectGeneralFormatStyle.autodetectGeneralFormatStyle;

@EqualsAndHashCode(callSuper = false)
@Value
public class MoveFieldsToTopOfClass extends Recipe {

    @Override
    public String getDisplayName() {
        return "Move fields to the top of class declaration";
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

                // The spacing of the original first statement is preserved so that
                // it can be applied to the new first statement.
                Statement originalFirstStatement = statements.get(0);

                // Sort statements: fields first (public static, protected, private), then non-fields
                statements.sort(createStatementComparator());

                // Adjust prefix of the first statement if it's not the original first statement
                if (statements.get(0) != originalFirstStatement) {
                    Statement firstStatement = adjustPrefix(statements.get(0), originalFirstStatement.getPrefix());
                    statements.set(0, firstStatement);
                }

                // ensure proper spacing before the first non-field statement
                IntStream.range(1, statements.size())
                    .filter(i -> !(statements.get(i) instanceof J.VariableDeclarations))
                    .findFirst()
                    .ifPresent(i -> statements.set(i, ensurePrecedingNewline(statements.get(i))));

                return cd.withBody(cd.getBody().withStatements(statements));
            }

            private Statement adjustPrefix(Statement field, Space originalPrefix) {
                Space fieldPrefix = field.getPrefix();
                Space newPrefix = originalPrefix.withComments(fieldPrefix.getComments());
                return field.withPrefix(newPrefix);
            }

            private Statement ensurePrecedingNewline(Statement firstNonField) {
                J.CompilationUnit cu = getCursor().firstEnclosing(J.CompilationUnit.class);
                if (cu == null) {
                    return firstNonField;
                }
                GeneralFormatStyle generalFormatStyle = Style.from(GeneralFormatStyle.class, cu, () -> autodetectGeneralFormatStyle(cu));
                String newLine = generalFormatStyle.newLine();

                Space prefix = firstNonField.getPrefix();
                // if there is not an empty line before the first non-field, add one,
                // preserving any comments and ensuring proper indentation
                if (!prefix.getLastWhitespace().endsWith(String.format("%s\\s*%s", newLine, newLine))) {
                    String indent = prefix.getIndent();
                    Space newPrefix = Space.format(newLine + newLine + indent).withComments(prefix.getComments());
                    return firstNonField.withPrefix(newPrefix);
                }
                return firstNonField;
            }


            private Comparator<Statement> createStatementComparator() {
                return (s1, s2) -> {
                    boolean s1IsField = s1 instanceof J.VariableDeclarations;
                    boolean s2IsField = s2 instanceof J.VariableDeclarations;

                    // Fields come before non-fields
                    if (s1IsField && !s2IsField) return -1;
                    if (!s1IsField && s2IsField) return 1;
                    if (!s1IsField) return 0; // Both are non-fields, preserve order

                    // Both are fields - sort by visibility: public static first, then protected, then private
                    J.VariableDeclarations field1 = (J.VariableDeclarations) s1;
                    J.VariableDeclarations field2 = (J.VariableDeclarations) s2;

                    int priority1 = getFieldPriority(field1);
                    int priority2 = getFieldPriority(field2);

                    return Integer.compare(priority1, priority2);
                };
            }

            private int getFieldPriority(J.VariableDeclarations field) {
                // Use bitmask where lower values have higher priority
                // Bit 3-2: visibility (00=public, 01=protected, 10=private)
                // Bit 1: static/final (0=static final, 1=final or instance)
                // Bit 0: final (0=final, 1=instance)

                int priority = 0b000; // Start from 0

                // Visibility bits (bits 3-2)
                if (field.hasModifier(J.Modifier.Type.Private)) {
                    priority |= 0b1000; // private
                } else if (field.hasModifier(J.Modifier.Type.Protected)) {
                    priority |= 0b0100; // protected
                }
                // public gets 0b0000

                // Static/final precedence (bit 1)
                boolean isStatic = field.hasModifier(J.Modifier.Type.Static);
                boolean isFinal = field.hasModifier(J.Modifier.Type.Final);

                if (!isStatic || !isFinal) {
                    priority |= 0b010; // not (static final)
                }

                // Final bit (bit 0)
                if (!isFinal) {
                    priority |= 0b001; // not final
                }

                return priority;
            }

        };
    }
}
