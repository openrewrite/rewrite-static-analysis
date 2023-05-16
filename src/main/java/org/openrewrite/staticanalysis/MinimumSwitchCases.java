/*
 * Copyright 2021 the original author or authors.
 * <p>
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * <p>
 * https://www.apache.org/licenses/LICENSE-2.0
 * <p>
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.openrewrite.staticanalysis;

import lombok.AllArgsConstructor;
import lombok.Value;
import lombok.With;
import org.openrewrite.ExecutionContext;
import org.openrewrite.Recipe;
import org.openrewrite.Tree;
import org.openrewrite.internal.ListUtils;
import org.openrewrite.internal.RecipeRunException;
import org.openrewrite.java.JavaTemplate;
import org.openrewrite.java.JavaVisitor;
import org.openrewrite.java.tree.*;
import org.openrewrite.marker.Marker;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import static java.util.Collections.singleton;
import static java.util.Collections.singletonList;
import static java.util.Objects.requireNonNull;

public class MinimumSwitchCases extends Recipe {
    @Override
    public String getDisplayName() {
        return "`switch` statements should have at least 3 `case` clauses";
    }

    @Override
    public String getDescription() {
        return "`switch` statements are useful when many code paths branch depending on the value of a single expression. " +
                "For just one or two code paths, the code will be more readable with `if` statements.";
    }

    @Override
    public Set<String> getTags() {
        return singleton("RSPEC-1301");
    }

    @Override
    public Duration getEstimatedEffortPerOccurrence() {
        return Duration.ofMinutes(5);
    }

    @Override
    public JavaVisitor<ExecutionContext> getVisitor() {
        return new JavaVisitor<ExecutionContext>() {
            final JavaTemplate ifElseIfPrimitive = JavaTemplate.builder("" +
                    "if(#{any()} == #{any()}) {\n" +
                    "} else if(#{any()} == #{any()}) {\n" +
                    "}").context(this::getCursor).build();

            final JavaTemplate ifElseIfString = JavaTemplate.builder("" +
                    "if(#{any(java.lang.String)}.equals(#{any(java.lang.String)})) {\n" +
                    "} else if(#{any(java.lang.String)}.equals(#{any(java.lang.String)})) {\n" +
                    "}").context(this::getCursor).build();

            final JavaTemplate ifElseIfEnum = JavaTemplate.builder("" +
                    "if(#{any()} == #{}) {\n" +
                    "} else if(#{any()} == #{}) {\n" +
                    "}").context(this::getCursor).build();

            final JavaTemplate ifElsePrimitive = JavaTemplate.builder("" +
                    "if(#{any()} == #{any()}) {\n" +
                    "} else {\n" +
                    "}").context(this::getCursor).build();

            final JavaTemplate ifElseString = JavaTemplate.builder("" +
                    "if(#{any(java.lang.String)}.equals(#{any(java.lang.String)})) {\n" +
                    "} else {\n" +
                    "}").context(this::getCursor).build();

            final JavaTemplate ifElseEnum = JavaTemplate.builder("" +
                    "if(#{any()} == #{}) {\n" +
                    "} else {\n" +
                    "}").context(this::getCursor).build();

            final JavaTemplate ifPrimitive = JavaTemplate.builder("" +
                    "if(#{any()} == #{any()}) {\n" +
                    "}").context(this::getCursor).build();

            final JavaTemplate ifString = JavaTemplate.builder("" +
                    "if(#{any(java.lang.String)}.equals(#{any(java.lang.String)})) {\n" +
                    "}").context(this::getCursor).build();

            final JavaTemplate ifEnum = JavaTemplate.builder("" +
                    "if(#{any()} == #{}) {\n" +
                    "}").context(this::getCursor).build();

            @Override
            public J visitBlock(J.Block block, ExecutionContext executionContext) {
                // Handle the edge case of the extra-pointless switch statement which contains _only_ the default case
                return block.withStatements(ListUtils.flatMap(block.getStatements(), (statement) -> {
                    Statement visited = (Statement) visit(statement, executionContext, getCursor());
                    if (!(visited instanceof J.Switch) || !visited.getMarkers().findFirst(DefaultOnly.class).isPresent()) {
                        return visited;
                    }
                    // Unwrap the contents of the default block, discarding the break statement if one exists
                    J.Case defaultCase = (J.Case) ((J.Switch) visited).getCases().getStatements().get(0);

                    return ListUtils.map(defaultCase.getStatements(), caseStatement -> {
                        if (caseStatement instanceof J.Break) {
                            return null;
                        }
                        return autoFormat(caseStatement, executionContext, getCursor());
                    });
                }));
            }

            @Override
            public J visitSwitch(J.Switch switzh, ExecutionContext ctx) {
                if (switzh.getCases().getStatements().size() < 3) {
                    J.Switch sortedSwitch = (J.Switch) new DefaultComesLast().getVisitor().visit(switzh, ctx);
                    assert sortedSwitch != null;

                    J.Case[] cases = new J.Case[2];
                    int i = 0;
                    List<Statement> statements = sortedSwitch.getCases().getStatements();
                    for (int j = 0; j < statements.size(); j++) {
                        Statement statement = statements.get(j);
                        if (statement instanceof J.Case) {
                            J.Case aCase = (J.Case) statement;
                            if (aCase.getType() == J.Case.Type.Rule) {
                                if (aCase.getExpressions().size() > 1 || !(aCase.getBody() instanceof Statement)) {
                                    return super.visitSwitch(switzh, ctx);
                                }
                            } else {
                                Statement lastStatement = aCase.getStatements().isEmpty() ? null : aCase.getStatements().get(aCase.getStatements().size() - 1);
                                if (j != statements.size() - 1 && !(lastStatement instanceof J.Break || lastStatement instanceof J.Return)) {
                                    return super.visitSwitch(switzh, ctx);
                                }
                            }
                            cases[i++] = aCase;
                        }
                    }

                    if (i == 0) {
                        return super.visitSwitch(switzh, ctx);
                    }

                    try {
                        Expression tree = sortedSwitch.getSelector().getTree();
                        J.If generatedIf;
                        if (TypeUtils.isString(tree.getType())) {
                            if (cases[1] == null) {
                                if (isDefault(cases[0])) {
                                    return switzh.withMarkers(switzh.getMarkers().add(new DefaultOnly()));
                                } else {
                                    generatedIf = switzh.withTemplate(ifString, getCursor(), switzh.getCoordinates().replace(),
                                            cases[0].getPattern(), tree);
                                }
                            } else if (isDefault(cases[1])) {
                                generatedIf = switzh.withTemplate(ifElseString, getCursor(), switzh.getCoordinates().replace(),
                                        cases[0].getPattern(), tree);
                            } else {
                                generatedIf = switzh.withTemplate(ifElseIfString, getCursor(), switzh.getCoordinates().replace(),
                                        cases[0].getPattern(), tree, cases[1].getPattern(), tree);
                            }
                        } else if (switchesOnEnum(switzh)) {
                            if (cases[1] == null) {
                                if (isDefault(cases[0])) {
                                    return switzh.withMarkers(switzh.getMarkers().add(new DefaultOnly()));
                                } else {
                                    generatedIf = switzh.withTemplate(ifEnum, getCursor(), switzh.getCoordinates().replace(),
                                            tree, enumIdentToFieldAccessString(cases[0].getPattern()));
                                }
                            } else if (isDefault(cases[1])) {
                                generatedIf = switzh.withTemplate(ifElseEnum, getCursor(), switzh.getCoordinates().replace(),
                                        tree, enumIdentToFieldAccessString(cases[0].getPattern()));
                            } else {
                                generatedIf = switzh.withTemplate(ifElseIfEnum, getCursor(), switzh.getCoordinates().replace(),
                                        tree, enumIdentToFieldAccessString(cases[0].getPattern()), tree, enumIdentToFieldAccessString(cases[1].getPattern()));
                            }
                        } else {
                            if (cases[1] == null) {
                                if (isDefault(cases[0])) {
                                    return switzh.withMarkers(switzh.getMarkers().add(new DefaultOnly()));
                                } else {
                                    generatedIf = switzh.withTemplate(ifPrimitive, getCursor(), switzh.getCoordinates().replace(),
                                            tree, cases[0].getPattern());
                                }
                            } else if (isDefault(cases[1])) {
                                generatedIf = switzh.withTemplate(ifElsePrimitive, getCursor(), switzh.getCoordinates().replace(),
                                        tree, cases[0].getPattern());
                            } else {
                                generatedIf = switzh.withTemplate(ifElseIfPrimitive, getCursor(), switzh.getCoordinates().replace(),
                                        tree, cases[0].getPattern(), tree, cases[1].getPattern());
                            }
                        }

                        // move first case to "if"
                        List<Statement> thenStatements = getStatements(cases[0]);

                        generatedIf = generatedIf.withThenPart(((J.Block) generatedIf.getThenPart()).withStatements(ListUtils.map(thenStatements,
                                s -> s instanceof J.Break ? null : s)));

                        // move second case to "else"
                        if (cases[1] != null) {
                            assert generatedIf.getElsePart() != null;
                            if (isDefault(cases[1])) {
                                generatedIf = generatedIf.withElsePart(generatedIf.getElsePart().withBody(((J.Block) generatedIf.getElsePart().getBody()).withStatements(ListUtils.map(getStatements(cases[1]),
                                        s -> s instanceof J.Break ? null : s))));
                            } else {
                                J.If elseIf = (J.If) generatedIf.getElsePart().getBody();
                                generatedIf = generatedIf.withElsePart(generatedIf.getElsePart().withBody(elseIf.withThenPart(((J.Block) elseIf.getThenPart()).withStatements(ListUtils.map(getStatements(cases[1]),
                                        s -> s instanceof J.Break ? null : s)))));
                            }
                        }

                        return autoFormat(generatedIf, ctx);
                    } catch (RecipeRunException e) {
                        // JavaTemplate has problems on some Groovy files, don't currently have a way to adapt it appropriately
                        return switzh;
                    }
                }

                return super.visitSwitch(switzh, ctx);
            }

            private List<Statement> getStatements(J.Case aCase) {
                List<Statement> statements = new ArrayList<>();
                for (Statement statement : aCase.getType() == J.Case.Type.Rule ? singletonList((Statement) aCase.getBody()) : aCase.getStatements()) {
                    if (statement instanceof J.Block) {
                        statements.addAll(((J.Block) statement).getStatements());
                    } else {
                        statements.add(statement);
                    }
                }
                return statements;
            }

            private boolean isDefault(J.Case caze) {
                return caze.getPattern() instanceof J.Identifier && ((J.Identifier) caze.getPattern()).getSimpleName().equals("default");
            }

            private boolean switchesOnEnum(J.Switch switzh) {
                JavaType selectorType = switzh.getSelector().getTree().getType();
                return selectorType instanceof JavaType.Class
                        && ((JavaType.Class) selectorType).getKind() == JavaType.Class.Kind.Enum;
            }

            private String enumIdentToFieldAccessString(Expression casePattern) {
                String caseType = requireNonNull(TypeUtils.asFullyQualified(casePattern.getType())).getClassName();
                if (casePattern instanceof J.FieldAccess) {
                    // may be a field access in Groovy
                    return caseType + "." + ((J.FieldAccess) casePattern).getSimpleName();
                }
                // must be an identifier in Java
                return caseType + "." + ((J.Identifier) casePattern).getSimpleName();
            }

        };
    }

    @Value
    @With
    @AllArgsConstructor
    private static class DefaultOnly implements Marker {
        UUID id;

        public DefaultOnly() {
            id = Tree.randomId();
        }
    }
}
