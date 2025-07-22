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

import org.openrewrite.ExecutionContext;
import org.openrewrite.Recipe;
import org.openrewrite.TreeVisitor;
import org.openrewrite.java.JavaTemplate;
import org.openrewrite.java.JavaVisitor;
import org.openrewrite.java.tree.J;
import org.openrewrite.java.tree.Statement;
import org.openrewrite.Tree;
import org.openrewrite.marker.Markers;

import java.util.Collections;
import java.time.Duration;

public class UseForEachLoop extends Recipe {

    @Override
    public String getDisplayName() {
        return "Use for-each loops instead of manual indexing";
    }

    @Override
    public String getDescription() {
        return "Replace traditional for loops that iterate over collections or arrays with enhanced for-each loops for improved readability.";
    }

    @Override
    public Duration getEstimatedEffortPerOccurrence() {
        return Duration.ofMinutes(5);
    }

    @Override
    public TreeVisitor<?, ExecutionContext> getVisitor() {
        return new JavaVisitor<ExecutionContext>() {
            @Override
            public J visitForLoop(J.ForLoop forLoop, ExecutionContext ctx) {
                System.err.println("VISITING FOR LOOP: " + forLoop.printTrimmed(getCursor()));

                // Basic pattern check for List iteration: for (int i = 0; i < names.size(); i++)
                J.ForLoop.Control control = forLoop.getControl();

                // Must have exactly one init, one condition, and one update
                if (control.getInit().size() != 1 ||
                        control.getCondition() == null ||
                        control.getUpdate().size() != 1) {
                    return super.visitForLoop(forLoop, ctx);
                }

                // Check init: int i = 0
                Statement init = control.getInit().get(0);
                if (!(init instanceof J.VariableDeclarations)) {
                    return super.visitForLoop(forLoop, ctx);
                }

                J.VariableDeclarations initVars = (J.VariableDeclarations) init;
                if (initVars.getVariables().size() != 1) {
                    return super.visitForLoop(forLoop, ctx);
                }

                J.VariableDeclarations.NamedVariable indexVar = initVars.getVariables().get(0);
                if (indexVar.getInitializer() == null || !(indexVar.getInitializer() instanceof J.Literal)) {
                    return super.visitForLoop(forLoop, ctx);
                }

                J.Literal initValue = (J.Literal) indexVar.getInitializer();
                if (!Integer.valueOf(0).equals(initValue.getValue())) {
                    return super.visitForLoop(forLoop, ctx);
                }

                String indexVarName = indexVar.getSimpleName();

                // Check condition: i < collection.size()
                if (!(control.getCondition() instanceof J.Binary)) {
                    return super.visitForLoop(forLoop, ctx);
                }

                J.Binary condition = (J.Binary) control.getCondition();
                if (condition.getOperator() != J.Binary.Type.LessThan) {
                    return super.visitForLoop(forLoop, ctx);
                }

                // Left side should be the index variable
                if (!(condition.getLeft() instanceof J.Identifier) ||
                        !((J.Identifier) condition.getLeft()).getSimpleName().equals(indexVarName)) {
                    return super.visitForLoop(forLoop, ctx);
                }

                // Right side should be collection.size() for now - simplify to just check method name
                if (!(condition.getRight() instanceof J.MethodInvocation)) {
                    return super.visitForLoop(forLoop, ctx);
                }

                J.MethodInvocation sizeCall = (J.MethodInvocation) condition.getRight();
                if (!"size".equals(sizeCall.getSimpleName()) || !sizeCall.getArguments().isEmpty()) {
                    return super.visitForLoop(forLoop, ctx);
                }

                // Check update: i++ or ++i
                Statement update = control.getUpdate().get(0);
                if (!(update instanceof J.Unary)) {
                    return super.visitForLoop(forLoop, ctx);
                }

                J.Unary unaryUpdate = (J.Unary) update;
                if (unaryUpdate.getOperator() != J.Unary.Type.PostIncrement &&
                        unaryUpdate.getOperator() != J.Unary.Type.PreIncrement) {
                    return super.visitForLoop(forLoop, ctx);
                }

                if (!(unaryUpdate.getExpression() instanceof J.Identifier) ||
                        !((J.Identifier) unaryUpdate.getExpression()).getSimpleName().equals(indexVarName)) {
                    return super.visitForLoop(forLoop, ctx);
                }

                // Simple transformation for now
                J collection = sizeCall.getSelect();

                JavaTemplate template = JavaTemplate.builder("for (String name : #{any()}) #{any()}")
                        .build();

                // Transform body by replacing collection.get(i) with name
                Statement transformedBody = (Statement) new SimpleBodyTransformer(indexVarName, collection, "name").visit(forLoop.getBody(), getCursor());

                J.ForEachLoop forEachLoop = template.apply(getCursor(), forLoop.getCoordinates().replace(),
                        collection, transformedBody);

                return forEachLoop;
            }

            private class SimpleBodyTransformer extends JavaVisitor<Object> {
                private final String indexVarName;
                private final J collection;
                private final String newVariableName;

                public SimpleBodyTransformer(String indexVarName, J collection, String newVariableName) {
                    this.indexVarName = indexVarName;
                    this.collection = collection;
                    this.newVariableName = newVariableName;
                }

                @Override
                public J visitMethodInvocation(J.MethodInvocation method, Object o) {
                    // Replace collection.get(i) with the new variable
                    if ("get".equals(method.getSimpleName()) &&
                            method.getArguments().size() == 1 &&
                            method.getArguments().get(0) instanceof J.Identifier &&
                            indexVarName.equals(((J.Identifier) method.getArguments().get(0)).getSimpleName()) &&
                            isSameExpression(method.getSelect(), collection)) {

                        return new J.Identifier(
                                Tree.randomId(),
                                method.getPrefix(),
                                Markers.EMPTY,
                                Collections.emptyList(),
                                newVariableName,
                                method.getType(),
                                null
                        );
                    }
                    return super.visitMethodInvocation(method, o);
                }

                private boolean isSameExpression(J expr1, J expr2) {
                    if (expr1 == null || expr2 == null) return false;
                    return expr1.printTrimmed(getCursor()).equals(expr2.printTrimmed(getCursor()));
                }
            }
        };
    }
}
