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

import org.openrewrite.Cursor;
import org.openrewrite.ExecutionContext;
import org.openrewrite.Recipe;
import org.openrewrite.TreeVisitor;
import org.openrewrite.java.JavaTemplate;
import org.openrewrite.java.JavaVisitor;
import org.openrewrite.java.tree.J;
import org.openrewrite.java.tree.JavaType;

import java.time.Duration;
import java.util.ArrayList;
import static java.util.Collections.singleton;

import java.util.List;
import java.util.Set;

/**
 * This recipe replaces magic number literals in method bodies with named constants following the Sonar java:S109 rule.
 * All detected magic numbers (excluding those explicitly assigned to variables or fields, and -1, 0, 1) will be extracted as
 * private static final constants at the top of the class.
 * The original numeric usages are replaced with the new constant name to improve code readability and maintainability.
 */
public class ReplaceMagicNumbersWithConstants extends Recipe {
    private static final String CUSTOM_MODIFIERS = "private static final";

    @Override
    public String getDisplayName() {
    public String getDescription() {
        return "Replaces magic number literals in method bodies with named constants to improve code readability and maintainability. " +
                "Magic numbers are replaced by private static final constants declared at the top of the class, following Sonar's java:S109 rule. " +
                "The recipe does not create constants for literals that are already assigned to fields or variables, nor for typical non-magic numbers (such as 0, 1, or -1). " +
                "Currently, only numeric primitive literals are handled; string and character literals are unaffected. " +
                "If a constant for a value already exists, or the constant name would conflict with an existing symbol, the recipe will skip that value.";
                + "Magic numbers are replaced by private static final constants declared at the top of the class, following Sonar's java:S109 rule. "
                + "The recipe does not create constants for literals that are already assigned to fields or variables, nor for typical non-magic numbers (such as 0, 1, or -1). "
                + "Currently, only numeric primitive literals are handled; string and character literals are unaffected. "
                + "If a constant for a value already exists, or the constant name would conflict with an existing symbol, the recipe will skip that value.";
    }

    @Override
    public TreeVisitor<?, ExecutionContext> getVisitor() {
        return new JavaVisitor<ExecutionContext>() {
            @Override
            public J visitClassDeclaration(J.ClassDeclaration classDecl, ExecutionContext ctx) {
                J.ClassDeclaration cd = (J.ClassDeclaration) super.visitClassDeclaration(classDecl, ctx);

                List<J.Literal> literals = new ArrayList<>();
                // COLLECT magic numbers not in variable initializers and not -1/0/1
                new JavaVisitor<ExecutionContext>() {
                    @Override
                    public J visitLiteral(J.Literal literal, ExecutionContext ctx2) {
                        Cursor cursor = getCursor();
                        if (!(cursor.getParent().getParent().getValue() instanceof J.VariableDeclarations.NamedVariable) &&
                                !isIgnoredMagicNumber(literal)) {
                            literals.add(literal);
                        }
                        return literal;
                    }
                }.visit(classDecl, ctx);

                List<String> newFieldSources = new ArrayList<>();
                for (J.Literal literal : literals) {
                    String constantName = getStrValFromLiteral(literal);
                    boolean alreadyExists = cd.getBody().getStatements().stream()
                            .filter(J.VariableDeclarations.class::isInstance)
                            .map(J.VariableDeclarations.class::cast)
                            .flatMap(vars -> vars.getVariables().stream())
                            .anyMatch(var -> var.getSimpleName().equals(constantName));
                    if (!alreadyExists) {
                        String modifiers = CUSTOM_MODIFIERS;
                        String typeName = getTypeName(literal);
                        String fieldSource = modifiers + " " + typeName + " " + constantName + " = " + literal.getValueSource() + ";";
                        newFieldSources.add(fieldSource);
                    }
                }
                if (newFieldSources.isEmpty()) {
                    return cd;
                }

                String templateStr = String.join("\n", newFieldSources);
                JavaTemplate template = JavaTemplate.builder(templateStr)
                        .contextSensitive()
                        .build();
                Cursor bodyCursor = new Cursor(getCursor(), cd.getBody());
                J.Block updatedBody = template.apply(bodyCursor, cd.getBody().getCoordinates().firstStatement());

                return cd.withBody(updatedBody);
            }

            @Override
            public J visitLiteral(J.Literal literal, ExecutionContext ctx) {
                Cursor cursor = getCursor();
                // Do NOT replace in variable/field initializers or for ignored numbers
                if (cursor.getParent().getParent().getValue() instanceof J.VariableDeclarations.NamedVariable ||
                        isIgnoredMagicNumber(literal)) {
                    return super.visitLiteral(literal, ctx);
                }
                String constantName = getStrValFromLiteral(literal);
                if (constantName != null) {
                    JavaTemplate template = JavaTemplate.builder(constantName).build();
                    return template.apply(getCursor(), literal.getCoordinates().replace());
                }
                return super.visitLiteral(literal, ctx);
            }
        };
    }

    @Override
    public Set<String> getTags() {
        return singleton("RSPEC-109");
    }

    @Override
    public Duration getEstimatedEffortPerOccurrence() {
        return Duration.ofSeconds(10);
    }

    private String getStrValFromLiteral(J.Literal literal) {
        String type = getTypeName(literal).toUpperCase();
        String valueSource = literal.getValueSource();
        if (valueSource == null) {
            return null;
        }
        if (valueSource.startsWith("-")) {
            valueSource = "NEGATIVE_" + valueSource.substring(1);
        }
        return type + "_" + valueSource.replace(".", "_");
    }

    private String getTypeName(J.Literal literal) {
        if (literal.getType() == null) {
            return "Object";
        }
        JavaType type = literal.getType();
        if (type instanceof JavaType.Primitive) {
            return ((JavaType.Primitive) type).getKeyword();
        }
        return type.toString(); // fallback
    }

    private boolean isIgnoredMagicNumber(J.Literal literal) {
        Object value = literal.getValue();
        if (value instanceof Number) {
            double d = ((Number) value).doubleValue();
            // Only ignore -1, 0, 1 for all numeric types.
            return d == -1.0 || d == 0.0 || d == 1.0;
        }
        return false;
    }
}
