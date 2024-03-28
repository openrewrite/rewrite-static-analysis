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
import org.openrewrite.TreeVisitor;
import org.openrewrite.internal.ListUtils;
import org.openrewrite.internal.RecipeRunException;
import org.openrewrite.java.JavaTemplate;
import org.openrewrite.java.JavaVisitor;
import org.openrewrite.java.service.ImportService;
import org.openrewrite.java.tree.*;
import org.openrewrite.marker.Marker;
import org.openrewrite.marker.Markers;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import static java.util.Collections.singleton;
import static java.util.Collections.singletonList;
import static org.openrewrite.Tree.randomId;

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
    public TreeVisitor<?, ExecutionContext> getVisitor() {
        return new JavaVisitor<ExecutionContext>() {
            final JavaTemplate ifElseIfPrimitive = JavaTemplate.builder("" +
                                                                        "if(#{any()} == #{any()}) {\n" +
                                                                        "} else if(#{any()} == #{any()}) {\n" +
                                                                        "}").build();

            final JavaTemplate ifElseIfString = JavaTemplate.builder("" +
                                                                     "if(#{any(java.lang.String)}.equals(#{any(java.lang.String)})) {\n" +
                                                                     "} else if(#{any(java.lang.String)}.equals(#{any(java.lang.String)})) {\n" +
                                                                     "}").build();

            final JavaTemplate ifElsePrimitive = JavaTemplate.builder("" +
                                                                      "if(#{any()} == #{any()}) {\n" +
                                                                      "} else {\n" +
                                                                      "}").build();

            final JavaTemplate ifElseString = JavaTemplate.builder("" +
                                                                   "if(#{any(java.lang.String)}.equals(#{any(java.lang.String)})) {\n" +
                                                                   "} else {\n" +
                                                                   "}").build();

            final JavaTemplate ifPrimitive = JavaTemplate.builder("" +
                                                                  "if(#{any()} == #{any()}) {\n" +
                                                                  "}").build();

            final JavaTemplate ifString = JavaTemplate.builder("" +
                                                               "if(#{any(java.lang.String)}.equals(#{any(java.lang.String)})) {\n" +
                                                               "}").build();

            @Override
            public J visitBlock(J.Block block, ExecutionContext ctx) {
                // Handle the edge case of the extra-pointless switch statement which contains _only_ the default case
                return block.withStatements(ListUtils.flatMap(block.getStatements(), statement -> {
                    Statement visited = (Statement) visit(statement, ctx, getCursor());
                    if (!(visited instanceof J.Switch) || !visited.getMarkers().findFirst(DefaultOnly.class).isPresent()) {
                        return visited;
                    }
                    // Unwrap the contents of the default block, discarding the break statement if one exists
                    J.Case defaultCase = (J.Case) ((J.Switch) visited).getCases().getStatements().get(0);

                    return ListUtils.map(defaultCase.getStatements(), caseStatement -> {
                        if (caseStatement instanceof J.Break) {
                            return null;
                        }
                        return autoFormat(caseStatement, ctx, getCursor());
                    });
                }));
            }

            @Override
            public J visitSwitch(J.Switch switch_, ExecutionContext ctx) {
                if (doRewrite(switch_)) {
                    J.Switch sortedSwitch = (J.Switch) new DefaultComesLast().getVisitor().visit(switch_, ctx);
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
                                    return super.visitSwitch(switch_, ctx);
                                }
                            } else {
                                Statement lastStatement = aCase.getStatements().isEmpty() ? null : aCase.getStatements().get(aCase.getStatements().size() - 1);
                                if (j != statements.size() - 1 && !(lastStatement instanceof J.Break || lastStatement instanceof J.Return)) {
                                    return super.visitSwitch(switch_, ctx);
                                }
                            }
                            cases[i++] = aCase;
                        }
                    }

                    if (i == 0) {
                        return super.visitSwitch(switch_, ctx);
                    }

                    try {
                        Expression tree = sortedSwitch.getSelector().getTree();
                        J.If generatedIf;
                        if (TypeUtils.isString(tree.getType())) {
                            if (cases[1] == null) {
                                if (isDefault(cases[0])) {
                                    return switch_.withMarkers(switch_.getMarkers().add(new DefaultOnly()));
                                } else {
                                    generatedIf = ifString.apply(getCursor(), switch_.getCoordinates().replace(), cases[0].getPattern(), tree);
                                }
                            } else if (isDefault(cases[1])) {
                                generatedIf = ifElseString.apply(getCursor(), switch_.getCoordinates().replace(), cases[0].getPattern(), tree);
                            } else {
                                generatedIf = ifElseIfString.apply(getCursor(), switch_.getCoordinates().replace(), cases[0].getPattern(), tree, cases[1].getPattern(), tree);
                            }
                        } else if (switchesOnEnum(switch_)) {
                            if (cases[1] == null && isDefault(cases[0])) {
                                return switch_.withMarkers(switch_.getMarkers().add(new DefaultOnly()));
                            }

                            generatedIf = createIfForEnum(tree, cases[0].getPattern()).withPrefix(switch_.getPrefix());
                            if (cases[1] != null) {
                                Statement elseBody = J.Block.createEmptyBlock();
                                if (!isDefault(cases[1])) {
                                    elseBody = createIfForEnum(tree, cases[1].getPattern());
                                }
                                generatedIf = generatedIf
                                        .withElsePart(new J.If.Else(
                                                        randomId(),
                                                        Space.EMPTY,
                                                        Markers.EMPTY,
                                                        JRightPadded.build(elseBody)
                                                )
                                        );
                            }
                            doAfterVisit(service(ImportService.class).shortenFullyQualifiedTypeReferencesIn(generatedIf));
                        } else {
                            if (cases[1] == null) {
                                if (isDefault(cases[0])) {
                                    return switch_.withMarkers(switch_.getMarkers().add(new DefaultOnly()));
                                } else {
                                    generatedIf = ifPrimitive.apply(getCursor(), switch_.getCoordinates().replace(), tree, cases[0].getPattern());
                                }
                            } else if (isDefault(cases[1])) {
                                generatedIf = ifElsePrimitive.apply(getCursor(), switch_.getCoordinates().replace(), tree, cases[0].getPattern());
                            } else {
                                generatedIf = ifElseIfPrimitive.apply(getCursor(), switch_.getCoordinates().replace(), tree, cases[0].getPattern(), tree, cases[1].getPattern());
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
                        return switch_;
                    }
                }

                return super.visitSwitch(switch_, ctx);
            }

            private boolean doRewrite(J.Switch switch_) {
                if (switch_.getCases().getStatements().size() > 2) {
                    return false;
                }
                return switch_.getCases().getStatements().stream()
                        .reduce(0, (a, b) -> a + ((J.Case) b).getExpressions().size(), Integer::sum) < 3;
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

            private boolean isDefault(J.Case case_) {
                return case_.getPattern() instanceof J.Identifier && "default".equals(((J.Identifier) case_.getPattern()).getSimpleName());
            }

            private boolean switchesOnEnum(J.Switch switch_) {
                JavaType selectorType = switch_.getSelector().getTree().getType();
                return selectorType instanceof JavaType.Class
                       && ((JavaType.Class) selectorType).getKind() == JavaType.Class.Kind.Enum;
            }

        };
    }

    private static J.If createIfForEnum(Expression expression, Expression enumTree) {
        J.If generatedIf;
        if (enumTree instanceof J.Identifier) {
            enumTree = new J.FieldAccess(
                    randomId(),
                    enumTree.getPrefix(),
                    Markers.EMPTY,
                    JavaElementFactory.className(enumTree.getType(), true),
                    JLeftPadded.build(enumTree.withPrefix(Space.EMPTY)),
                    enumTree.getType()
            );
        }
        J.Binary ifCond = JavaElementFactory.newLogicalExpression(J.Binary.Type.Equal, expression, enumTree);
        generatedIf = new J.If(
                randomId(),
                Space.EMPTY,
                Markers.EMPTY,
                new J.ControlParentheses<>(randomId(), Space.EMPTY, Markers.EMPTY, JRightPadded.build(ifCond)),
                JRightPadded.build(J.Block.createEmptyBlock()),
                null
        );
        return generatedIf;
    }

    @Value
    @With
    @AllArgsConstructor
    private static class DefaultOnly implements Marker {
        UUID id;

        public DefaultOnly() {
            id = randomId();
        }
    }
}
