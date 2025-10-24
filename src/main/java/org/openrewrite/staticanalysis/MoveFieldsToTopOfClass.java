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
import org.openrewrite.java.tree.Statement;

import java.time.Duration;
import java.util.Comparator;
import java.util.List;

import static java.util.stream.Collectors.toList;

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

                List<Statement> statements =
                        // getStatements() returns a new List each time, so no need to defensively copy
                        cd.getBody().getStatements()
                                .stream()
                                .sorted(statementComparator)
                                .collect(toList());

                return cd.withBody(cd.getBody().withStatements(statements));
            }

            private final Comparator<Statement> statementComparator =
                    (s1, s2) -> {
                        boolean s1IsField = s1 instanceof J.VariableDeclarations;
                        boolean s2IsField = s2 instanceof J.VariableDeclarations;

                        // Fields come before non-fields
                        if (s1IsField && !s2IsField) {
                            return -1;
                        }
                        if (!s1IsField && s2IsField) {
                            return 1;
                        }
                        if (!s1IsField) {
                            return 0; // Both are non-fields, preserve order

                            // Both are fields - sort by visibility and modifiers
                        }
                        J.VariableDeclarations field2 = (J.VariableDeclarations) s2;

                        int priority1 = getFieldSortOrder(field1);
                        int priority2 = getFieldSortOrder(field2);

                        return Integer.compare(priority1, priority2);
                    };


            // bitmasks for field sorting
            // order: public static final < static final < protected static final < private static final <
            //        public static < static < protected static < private static <
            //        public final < final < protected final < private final <
            //        public < package-private < protected < private
            private static final int PACKAGE_PRIVATE = 1;
            private static final int PROTECTED = PACKAGE_PRIVATE << 1;
            private static final int PRIVATE = PROTECTED << 1;
            private static final int NON_FINAL = PRIVATE << 1;
            private static final int NON_STATIC = NON_FINAL << 1;

            private int getFieldSortOrder(J.VariableDeclarations field) {
                int order = 0;
                if (field.hasModifier(J.Modifier.Type.Protected)) {
                    order |= PROTECTED;
                } else if (field.hasModifier(J.Modifier.Type.Private)) {
                    order |= PRIVATE;
                } else {
                    order |= PACKAGE_PRIVATE;
                }

                if (!field.hasModifier(J.Modifier.Type.Static)) {
                    order |= NON_STATIC;
                }

                if (!field.hasModifier(J.Modifier.Type.Final)) {
                    order |= NON_FINAL;
                }
                return order;
            }

        };
    }
}
