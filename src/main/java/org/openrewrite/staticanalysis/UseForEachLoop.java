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
import org.openrewrite.java.tree.Space;
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

                // Handle both collection.size() and array.length patterns
                J collection;
                if (condition.getRight() instanceof J.MethodInvocation) {
                    // Handle collection.size() pattern
                    J.MethodInvocation sizeCall = (J.MethodInvocation) condition.getRight();
                    if (!"size".equals(sizeCall.getSimpleName()) || !((sizeCall.getArguments().isEmpty()) || (sizeCall.getArguments().size() == 1 && sizeCall.getArguments().get(0) instanceof J.Empty))) {
                        return super.visitForLoop(forLoop, ctx);
                    }
                    collection = sizeCall.getSelect();
                } else if (condition.getRight() instanceof J.FieldAccess) {
                    // Handle array.length pattern
                    J.FieldAccess lengthAccess = (J.FieldAccess) condition.getRight();
                    if (!"length".equals(lengthAccess.getSimpleName())) {
                        return super.visitForLoop(forLoop, ctx);
                    }
                    collection = lengthAccess.getTarget();
                } else {
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

                // Check if the loop body only accesses the same collection being iterated
                if (!isValidForTransformation(forLoop.getBody(), indexVarName, collection)) {
                    return super.visitForLoop(forLoop, ctx);
                }

                JavaTemplate template = JavaTemplate.builder("for (String name : #{any()}) #{any()}")
                        .build();

                Statement transformedBody = (Statement) new SimpleBodyTransformer(indexVarName, collection, "name").visit(forLoop.getBody(), getCursor());

                J.ForEachLoop forEachLoop = template.apply(getCursor(), forLoop.getCoordinates().replace(),
                        collection, transformedBody);

                J.ForEachLoop.Control foreachControl = forEachLoop.getControl();
                J iterable = foreachControl.getIterable();
                J.ForEachLoop fixedForEachLoop = forEachLoop.withControl(
                        foreachControl.withIterable(iterable.withPrefix(Space.format(" ")))
                );

                return fixedForEachLoop;
            }

            private boolean isValidForTransformation(Statement body, String indexVarName, J collection) {
                ValidationVisitor validator = new ValidationVisitor(indexVarName, collection);
                validator.visit(body, null);
                return validator.isValid();
            }

            private class ValidationVisitor extends JavaVisitor<Object> {
                private final String indexVarName;
                private final J collection;
                private boolean valid = true;

                public ValidationVisitor(String indexVarName, J collection) {
                    this.indexVarName = indexVarName;
                    this.collection = collection;
                }

                public boolean isValid() {
                    return valid;
                }

                @Override
                public J visitIdentifier(J.Identifier identifier, Object o) {
                    // If index variable is used for anything other than collection access, it's invalid
                    if (indexVarName.equals(identifier.getSimpleName())) {
                        // Check if this identifier is part of a valid collection access pattern
                        if (!isPartOfValidAccess(identifier)) {
                            valid = false;
                        }
                    }
                    return super.visitIdentifier(identifier, o);
                }

                @Override
                public J visitMethodInvocation(J.MethodInvocation method, Object o) {
                    // Check method calls like collection.get(i) or otherCollection.get(i)
                    if ("get".equals(method.getSimpleName()) &&
                            method.getArguments().size() == 1 &&
                            method.getArguments().get(0) instanceof J.Identifier &&
                            indexVarName.equals(((J.Identifier) method.getArguments().get(0)).getSimpleName())) {

                        // Only valid if accessing the same collection we're iterating over
                        if (!isSameExpression(method.getSelect(), collection)) {
                            valid = false;
                        }
                    }
                    return super.visitMethodInvocation(method, o);
                }

                @Override
                public J visitArrayAccess(J.ArrayAccess arrayAccess, Object o) {
                    // Check array access like array[i] or otherArray[i]
                    if (arrayAccess.getDimension().getIndex() instanceof J.Identifier &&
                            indexVarName.equals(((J.Identifier) arrayAccess.getDimension().getIndex()).getSimpleName())) {

                        // Only valid if accessing the same array we're iterating over
                        if (!isSameExpression(arrayAccess.getIndexed(), collection)) {
                            valid = false;
                        }
                    }
                    return super.visitArrayAccess(arrayAccess, o);
                }

                private boolean isPartOfValidAccess(J.Identifier identifier) {
                    // Check if the identifier is part of a method call argument or array index
                    // This is a simplified check - we'll rely on the specific visitMethodInvocation and visitArrayAccess
                    return true; // Let the specific visit methods handle validation
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

                    return false; // For simplicity, only handle basic cases
                }
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

                @Override
                public J visitArrayAccess(J.ArrayAccess arrayAccess, Object o) {
                    // Replace array[i] with the new variable
                    if (arrayAccess.getDimension().getIndex() instanceof J.Identifier &&
                            indexVarName.equals(((J.Identifier) arrayAccess.getDimension().getIndex()).getSimpleName()) &&
                            isSameExpression(arrayAccess.getIndexed(), collection)) {

                        return new J.Identifier(
                                Tree.randomId(),
                                arrayAccess.getPrefix(),
                                Markers.EMPTY,
                                Collections.emptyList(),
                                newVariableName,
                                arrayAccess.getType(),
                                null
                        );
                    }
                    return super.visitArrayAccess(arrayAccess, o);
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
