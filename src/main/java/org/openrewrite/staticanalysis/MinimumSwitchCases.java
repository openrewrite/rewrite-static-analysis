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

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.Value;
import lombok.With;
import org.jspecify.annotations.Nullable;
import org.openrewrite.*;
import org.openrewrite.internal.ListUtils;
import org.openrewrite.internal.RecipeRunException;
import org.openrewrite.java.JavaIsoVisitor;
import org.openrewrite.java.JavaTemplate;
import org.openrewrite.java.JavaVisitor;
import org.openrewrite.java.service.ImportService;
import org.openrewrite.java.tree.*;
import org.openrewrite.marker.Marker;
import org.openrewrite.marker.Markers;
import org.openrewrite.staticanalysis.csharp.CSharpFileChecker;

import java.util.*;

import static java.util.Collections.singleton;
import static java.util.Collections.singletonList;
import static org.openrewrite.Tree.randomId;

public class MinimumSwitchCases extends Recipe {
    @Getter
    final String displayName = "`switch` statements should have at least 3 `case` clauses";

    @Getter
    final String description = "`switch` statements are useful when many code paths branch depending on the value of a single expression. " +
            "For just one or two code paths, the code will be more readable with `if` statements. " +
            "Using `switch` for trivial branching adds unnecessary syntactic overhead and obscures the simplicity of the logic.";

    @Getter
    final Set<String> tags = singleton("RSPEC-S1301");

    @Override
    public TreeVisitor<?, ExecutionContext> getVisitor() {
        return Preconditions.check(Preconditions.not(new CSharpFileChecker<>()), new JavaVisitor<ExecutionContext>() {
            @Override
            public J visitBlock(J.Block block, ExecutionContext ctx) {
                // Handle the edge case of the extra-pointless switch statement which contains _only_ the default case
                return block.withStatements(ListUtils.flatMap(block.getStatements(), (statement) -> {
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
                    J.Switch sortedSwitch = (J.Switch) new DefaultComesLast().getVisitor().visit(switch_, ctx, getCursor().getParentTreeCursor());
                    assert sortedSwitch != null;

                    J.Case[] cases = new J.Case[2];
                    int i = 0;
                    List<Statement> statements = sortedSwitch.getCases().getStatements();
                    for (int j = 0; j < statements.size(); j++) {
                        Statement statement = statements.get(j);
                        if (statement instanceof J.Case) {
                            J.Case aCase = (J.Case) statement;
                            if (aCase.getType() == J.Case.Type.Rule) {
                                if (aCase.getCaseLabels().size() > 1 || !(aCase.getBody() instanceof Statement)) {
                                    return super.visitSwitch(switch_, ctx);
                                }
                            } else {
                                List<Statement> breaks = new ArrayList<>();
                                new BreakFinderVisitor().visit(aCase, breaks);
                                Statement lastStatement = aCase.getStatements().isEmpty() ? null : aCase.getStatements().get(aCase.getStatements().size() - 1);
                                if (breaks.size() > 1) {
                                    return super.visitSwitch(switch_, ctx);
                                }
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
                                }
                                generatedIf = JavaTemplate.apply("if(#{any(java.lang.String)}.equals(#{any(java.lang.String)})) {\n}", getCursor(), switch_.getCoordinates().replace(), cases[0].getPattern(), tree);
                            } else if (isDefault(cases[1])) {
                                generatedIf = JavaTemplate.apply("if(#{any(java.lang.String)}.equals(#{any(java.lang.String)})) {\n} else {\n}", getCursor(), switch_.getCoordinates().replace(), cases[0].getPattern(), tree);
                            } else {
                                generatedIf = JavaTemplate.apply("if(#{any(java.lang.String)}.equals(#{any(java.lang.String)})) {\n} else if(#{any(java.lang.String)}.equals(#{any(java.lang.String)})) {\n}", getCursor(), switch_.getCoordinates().replace(), cases[0].getPattern(), tree, cases[1].getPattern(), tree);
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
                                }
                                generatedIf = JavaTemplate.apply("if(#{any()} == #{any()}) {\n}", getCursor(), switch_.getCoordinates().replace(), tree, cases[0].getPattern());
                            } else if (isDefault(cases[1])) {
                                generatedIf = JavaTemplate.apply("if(#{any()} == #{any()}) {\n} else {\n}", getCursor(), switch_.getCoordinates().replace(), tree, cases[0].getPattern());
                            } else {
                                generatedIf = JavaTemplate.apply("if(#{any()} == #{any()}) {\n} else if(#{any()} == #{any()}) {\n}", getCursor(), switch_.getCoordinates().replace(), tree, cases[0].getPattern(), tree, cases[1].getPattern());
                            }
                        }

                        // move first case to "if"
                        List<Statement> thenStatements = getStatements(cases[0]);
                        List<Statement> filteredThen = removeBreaksPreservingComments(thenStatements);

                        generatedIf = generatedIf.withThenPart(((J.Block) generatedIf.getThenPart()).withStatements(filteredThen));

                        // Collect variable declarations from first case for redeclaration in else block
                        Map<String, J.VariableDeclarations> declaredVars = collectDeclaredVariables(filteredThen);

                        // move second case to "else"
                        if (cases[1] != null) {
                            assert generatedIf.getElsePart() != null;
                            List<Statement> elseStatements = removeBreaksPreservingComments(getStatements(cases[1]));
                            // Transfer comments from the switch block's end space to the else block
                            Space switchEnd = sortedSwitch.getCases().getEnd();
                            if (!switchEnd.getComments().isEmpty()) {
                                if (elseStatements.isEmpty()) {
                                    // Create a placeholder empty statement to carry the comments
                                    J.Block elseBlock = (J.Block) (isDefault(cases[1]) ?
                                            generatedIf.getElsePart().getBody() :
                                            ((J.If) generatedIf.getElsePart().getBody()).getThenPart());
                                    elseBlock = elseBlock.withEnd(elseBlock.getEnd().withComments(
                                            ListUtils.concatAll(switchEnd.getComments(), elseBlock.getEnd().getComments())));
                                    if (isDefault(cases[1])) {
                                        generatedIf = generatedIf.withElsePart(generatedIf.getElsePart().withBody(elseBlock));
                                    } else {
                                        generatedIf = generatedIf.withElsePart(generatedIf.getElsePart().withBody(
                                                ((J.If) generatedIf.getElsePart().getBody()).withThenPart(elseBlock)));
                                    }
                                } else {
                                    Statement first = elseStatements.get(0);
                                    elseStatements.set(0, first.withPrefix(first.getPrefix().withComments(
                                            ListUtils.concatAll(switchEnd.getComments(), first.getPrefix().getComments()))));
                                }
                            }
                            elseStatements = redeclareAssignments(elseStatements, declaredVars);
                            if (isDefault(cases[1])) {
                                generatedIf = generatedIf.withElsePart(generatedIf.getElsePart().withBody(((J.Block) generatedIf.getElsePart().getBody()).withStatements(elseStatements)));
                            } else {
                                J.If elseIf = (J.If) generatedIf.getElsePart().getBody();
                                generatedIf = generatedIf.withElsePart(generatedIf.getElsePart().withBody(elseIf.withThenPart(((J.Block) elseIf.getThenPart()).withStatements(elseStatements))));
                            }
                        }

                        return autoFormat(super.visit(generatedIf, ctx), ctx);
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
                // Don't transform if any case has an identifier pattern without type info
                // (we can't properly qualify it in the if statement)
                if (hasUnresolvableIdentifierCasePattern(switch_)) {
                    return false;
                }
                return switch_.getCases().getStatements().stream()
                               .reduce(0, (a, b) -> a + ((J.Case) b).getCaseLabels().size(), Integer::sum) < 3;
            }

            private boolean hasUnresolvableIdentifierCasePattern(J.Switch switch_) {
                for (Statement statement : switch_.getCases().getStatements()) {
                    if (statement instanceof J.Case) {
                        J.Case aCase = (J.Case) statement;
                        if (!isDefault(aCase)) {
                            Expression pattern = aCase.getPattern();
                            // Identifiers (like enum constants or static fields) need type info
                            // to be properly qualified in an if statement
                            if (pattern instanceof J.Identifier) {
                                JavaType patternType = pattern.getType();
                                if (patternType == null || patternType instanceof JavaType.Unknown) {
                                    return true;
                                }
                            }
                        }
                    }
                }
                return false;
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

            private List<Statement> removeBreaksPreservingComments(List<Statement> statements) {
                return ListUtils.map(statements, (i, s) -> {
                    if (s instanceof J.Break) {
                        return null;
                    }
                    // Collect comments from any following break statement
                    if (i + 1 < statements.size() && statements.get(i + 1) instanceof J.Break) {
                        List<Comment> breakComments = statements.get(i + 1).getComments();
                        if (!breakComments.isEmpty()) {
                            return s.withPrefix(s.getPrefix().withComments(
                                    ListUtils.concatAll(s.getPrefix().getComments(), breakComments)));
                        }
                    }
                    return s;
                });
            }

            private Map<String, J.VariableDeclarations> collectDeclaredVariables(List<Statement> statements) {
                Map<String, J.VariableDeclarations> vars = new LinkedHashMap<>();
                for (Statement s : statements) {
                    if (s instanceof J.VariableDeclarations) {
                        J.VariableDeclarations vd = (J.VariableDeclarations) s;
                        for (J.VariableDeclarations.NamedVariable v : vd.getVariables()) {
                            vars.put(v.getSimpleName(), vd);
                        }
                    }
                }
                return vars;
            }

            private List<Statement> redeclareAssignments(List<Statement> statements, Map<String, J.VariableDeclarations> declaredVars) {
                if (declaredVars.isEmpty()) {
                    return statements;
                }
                List<Statement> result = new ArrayList<>();
                for (Statement s : statements) {
                    if (s instanceof J.Assignment) {
                        J.Assignment assignment = (J.Assignment) s;
                        if (assignment.getVariable() instanceof J.Identifier) {
                            String name = ((J.Identifier) assignment.getVariable()).getSimpleName();
                            J.VariableDeclarations originalDecl = declaredVars.get(name);
                            if (originalDecl != null) {
                                J.VariableDeclarations.NamedVariable newVar = originalDecl.getVariables().get(0)
                                        .withName(((J.Identifier) assignment.getVariable()).withPrefix(Space.EMPTY))
                                        .withInitializer(assignment.getAssignment())
                                        .withId(randomId());
                                J.VariableDeclarations newDecl = originalDecl
                                        .withVariables(singletonList(newVar))
                                        .withPrefix(s.getPrefix())
                                        .withId(randomId());
                                result.add(newDecl);
                                continue;
                            }
                        }
                    }
                    result.add(s);
                }
                return result;
            }

            private boolean isDefault(J.Case case_) {
                return case_.getPattern() instanceof J.Identifier && "default".equals(((J.Identifier) case_.getPattern()).getSimpleName());
            }

            private boolean switchesOnEnum(J.Switch switch_) {
                JavaType selectorType = switch_.getSelector().getTree().getType();
                if (selectorType instanceof JavaType.Class &&
                       ((JavaType.Class) selectorType).getKind() == JavaType.Class.Kind.Enum) {
                    return true;
                }

                // Also check case pattern types - handles cases where selector type is unknown
                // but the case patterns have type information
                for (Statement statement : switch_.getCases().getStatements()) {
                    if (statement instanceof J.Case) {
                        J.Case aCase = (J.Case) statement;
                        Expression pattern = aCase.getPattern();
                        if (pattern instanceof J.Identifier && !isDefault(aCase)) {
                            JavaType patternType = pattern.getType();
                            if (patternType instanceof JavaType.Class &&
                                ((JavaType.Class) patternType).getKind() == JavaType.Class.Kind.Enum) {
                                return true;
                            }
                        }
                    }
                }
                return false;
            }

        });
    }

    private static J.If createIfForEnum(Expression expression, Expression enumTree) {
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
        return new J.If(
                randomId(),
                Space.EMPTY,
                Markers.EMPTY,
                new J.ControlParentheses<>(randomId(), Space.EMPTY, Markers.EMPTY, JRightPadded.build(ifCond)),
                JRightPadded.build(J.Block.createEmptyBlock()),
                null
        );
    }

    @AllArgsConstructor
    @Value
    @With
    private static class DefaultOnly implements Marker {
        UUID id;

        public DefaultOnly() {
            id = randomId();
        }
    }

    private static class BreakFinderVisitor extends JavaIsoVisitor<List<Statement>> {

        @Override
        public @Nullable J visit(@Nullable Tree tree, List<Statement> statements) {
            if (tree instanceof J.Break) {
                statements.add((J.Break) tree);
            } else if (tree instanceof J.Switch) {
                return (J) tree;
            }
            return super.visit(tree, statements);
        }
    }
}
