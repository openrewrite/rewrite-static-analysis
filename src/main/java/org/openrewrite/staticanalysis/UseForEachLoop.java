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
                J.ForLoop.Control control = forLoop.getControl();

                if (control.getInit().size() != 1 || control.getCondition() == null || control.getUpdate().size() != 1) {
                    return super.visitForLoop(forLoop, ctx);
                }

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

                if (!(control.getCondition() instanceof J.Binary)) {
                    return super.visitForLoop(forLoop, ctx);
                }

                J.Binary condition = (J.Binary) control.getCondition();
                if (condition.getOperator() != J.Binary.Type.LessThan) {
                    return super.visitForLoop(forLoop, ctx);
                }

                if (!(condition.getLeft() instanceof J.Identifier) ||
                        !((J.Identifier) condition.getLeft()).getSimpleName().equals(indexVarName)) {
                    return super.visitForLoop(forLoop, ctx);
                }

                if (!(condition.getRight() instanceof J.MethodInvocation)) {
                    return super.visitForLoop(forLoop, ctx);
                }

                J.MethodInvocation sizeCall = (J.MethodInvocation) condition.getRight();
                if (!"size".equals(sizeCall.getSimpleName()) || !((sizeCall.getArguments().isEmpty()) || (sizeCall.getArguments().size() == 1 && sizeCall.getArguments().get(0) instanceof J.Empty))) {
                    return super.visitForLoop(forLoop, ctx);
                }

                Statement update = control.getUpdate().get(0);
                if (!(update instanceof J.Unary)) {
                    return super.visitForLoop(forLoop, ctx);
                }

                J.Unary unaryUpdate = (J.Unary) update;
                if (unaryUpdate.getOperator() != J.Unary.Type.PostIncrement && unaryUpdate.getOperator() != J.Unary.Type.PreIncrement) {
                    return super.visitForLoop(forLoop, ctx);
                }

                if (!(unaryUpdate.getExpression() instanceof J.Identifier) ||
                        !((J.Identifier) unaryUpdate.getExpression()).getSimpleName().equals(indexVarName)) {
                    return super.visitForLoop(forLoop, ctx);
                }

                J collection = sizeCall.getSelect();

                JavaTemplate template = JavaTemplate.builder("for (String name: #{any()}) #{any()}")
                        .build();

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
                    if (expr1 == expr2) return true;

                    if (expr1.getClass() != expr2.getClass()) return false;

                    if (expr1 instanceof J.Identifier) {
                        J.Identifier id1 = (J.Identifier) expr1;
                        J.Identifier id2 = (J.Identifier) expr2;
                        return id1.getSimpleName().equals(id2.getSimpleName());
                    }

                    if (expr1 instanceof J.FieldAccess) {
                        J.FieldAccess field1 = (J.FieldAccess) expr1;
                        J.FieldAccess field2 = (J.FieldAccess) expr2;
                        return field1.getSimpleName().equals(field2.getSimpleName()) &&
                                isSameExpression(field1.getTarget(), field2.getTarget());
                    }

                    if (expr1 instanceof J.MethodInvocation) {
                        J.MethodInvocation method1 = (J.MethodInvocation) expr1;
                        J.MethodInvocation method2 = (J.MethodInvocation) expr2;
                        if (!method1.getSimpleName().equals(method2.getSimpleName())) return false;
                        if (!isSameExpression(method1.getSelect(), method2.getSelect())) return false;

                        if (method1.getArguments().size() != method2.getArguments().size()) return false;
                        for (int i = 0; i < method1.getArguments().size(); i++) {
                            if (!isSameExpression(method1.getArguments().get(i), method2.getArguments().get(i))) {
                                return false;
                            }
                        }
                        return true;
                    }

                    if (expr1 instanceof J.ArrayAccess) {
                        J.ArrayAccess arr1 = (J.ArrayAccess) expr1;
                        J.ArrayAccess arr2 = (J.ArrayAccess) expr2;
                        return isSameExpression(arr1.getIndexed(), arr2.getIndexed()) &&
                                isSameExpression(arr1.getDimension().getIndex(), arr2.getDimension().getIndex());
                    }

                    if (expr1 instanceof J.Literal) {
                        J.Literal lit1 = (J.Literal) expr1;
                        J.Literal lit2 = (J.Literal) expr2;
                        return java.util.Objects.equals(lit1.getValue(), lit2.getValue());
                    }

                    if (expr1 instanceof J.Parentheses) {
                        J.Parentheses par1 = (J.Parentheses) expr1;
                        J.Parentheses par2 = (J.Parentheses) expr2;
                        return isSameExpression(par1.getTree(), par2.getTree());
                    }

                    // For other types, fall back to reference equality
                    return expr1 == expr2;
                }
            }
        };
    }
}
