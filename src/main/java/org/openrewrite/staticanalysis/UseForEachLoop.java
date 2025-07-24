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

import org.jspecify.annotations.Nullable;
import org.openrewrite.ExecutionContext;
import org.openrewrite.Recipe;
import org.openrewrite.Tree;
import org.openrewrite.TreeVisitor;
import org.openrewrite.java.JavaTemplate;
import org.openrewrite.java.JavaVisitor;
import org.openrewrite.java.VariableNameUtils;
import org.openrewrite.java.search.SemanticallyEqual;
import org.openrewrite.java.tree.*;
import org.openrewrite.marker.Markers;

import java.time.Duration;
import java.util.Collections;

import static org.openrewrite.java.VariableNameUtils.GenerationStrategy.INCREMENT_NUMBER;

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

                J collection;
                if (condition.getRight() instanceof J.MethodInvocation) {
                    J.MethodInvocation sizeCall = (J.MethodInvocation) condition.getRight();
                    if (!"size".equals(sizeCall.getSimpleName()) || !((sizeCall.getArguments().isEmpty()) || (sizeCall.getArguments().size() == 1 && sizeCall.getArguments().get(0) instanceof J.Empty))) {
                        return super.visitForLoop(forLoop, ctx);
                    }
                    collection = sizeCall.getSelect();
                } else if (condition.getRight() instanceof J.FieldAccess) {
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

                if (!isValidForTransformation(forLoop.getBody(), indexVarName, collection)) {
                    return super.visitForLoop(forLoop, ctx);
                }

                if (!isIterableOrArray(collection)) {
                    return super.visitForLoop(forLoop, ctx);
                }

                String forEachVarName = determineForEachVariableName(forLoop.getBody(), indexVarName, collection);

                JavaTemplate template = JavaTemplate.builder("for (String " + forEachVarName + " : #{any()}) #{any()}")
                        .build();

                Statement transformedBody = (Statement) new BodyTransformer(indexVarName, collection, forEachVarName).visit(forLoop.getBody(), getCursor());

                J.ForEachLoop forEachLoop = template.apply(getCursor(), forLoop.getCoordinates().replace(), collection, transformedBody);

                J.ForEachLoop.Control foreachControl = forEachLoop.getControl();
                J iterable = foreachControl.getIterable();
                return forEachLoop.withControl(
                        foreachControl.withIterable(iterable.withPrefix(Space.format(" ")))
                );
            }

            private String determineForEachVariableName(Statement body, String indexVarName, J collection) {
                VariableNameDetector detector = new VariableNameDetector(indexVarName, collection);
                detector.visit(body, null);

                String detectedName = detector.getDetectedVariableName();
                if (detectedName != null) {
                    return detectedName;
                }

                String derivedName = deriveVariableNameFromCollection(collection);

                return VariableNameUtils.generateVariableName(derivedName, getCursor(), INCREMENT_NUMBER);
            }

            private String deriveVariableNameFromCollection(J collection) {
                String collectionName = getCollectionName(collection);
                if (collectionName == null) {
                    return "item";
                }

                if (collectionName.endsWith("s") && collectionName.length() > 1) {
                    return collectionName.substring(0, collectionName.length() - 1);
                }

                return collectionName + "Item";
            }

            private String getCollectionName(J collection) {
                if (collection instanceof J.Identifier) {
                    return ((J.Identifier) collection).getSimpleName();
                }
                if (collection instanceof J.FieldAccess) {
                    return ((J.FieldAccess) collection).getSimpleName();
                }
                return null;
            }

            private boolean isValidForTransformation(Statement body, String indexVarName, J collection) {
                ValidationVisitor validator = new ValidationVisitor(indexVarName, collection);
                validator.visit(body, null);
                return validator.isValid();
            }

            private boolean isIterableOrArray(J collection) {
                if (collection == null || !(collection instanceof TypedTree)) {
                    return false;
                }

                JavaType type = ((TypedTree) collection).getType();
                if (type == null) {
                    return false;
                }

                return type instanceof JavaType.Array ||
                       TypeUtils.isAssignableTo("java.lang.Iterable", type);
            }

            private boolean isCollectionOrArrayAccess(J initializer, String indexVarName, J collection) {
                if (initializer instanceof J.MethodInvocation) {
                    J.MethodInvocation method = (J.MethodInvocation) initializer;
                    return "get".equals(method.getSimpleName()) &&
                            method.getArguments().size() == 1 &&
                            method.getArguments().get(0) instanceof J.Identifier &&
                            indexVarName.equals(((J.Identifier) method.getArguments().get(0)).getSimpleName()) &&
                            SemanticallyEqual.areEqual(method.getSelect(), collection);
                } else if (initializer instanceof J.ArrayAccess) {
                    J.ArrayAccess arrayAccess = (J.ArrayAccess) initializer;
                    return arrayAccess.getDimension().getIndex() instanceof J.Identifier &&
                            indexVarName.equals(((J.Identifier) arrayAccess.getDimension().getIndex()).getSimpleName()) &&
                            SemanticallyEqual.areEqual(arrayAccess.getIndexed(), collection);
                }
                return false;
            }

            private class VariableNameDetector extends JavaVisitor<Object> {
                private final String indexVarName;
                private final J collection;
                private String detectedVariableName;

                public VariableNameDetector(String indexVarName, J collection) {
                    this.indexVarName = indexVarName;
                    this.collection = collection;
                }

                public String getDetectedVariableName() {
                    return detectedVariableName;
                }

                @Override
                public J visitVariableDeclarations(J.VariableDeclarations variableDeclarations, Object o) {
                    if (variableDeclarations.getVariables().size() == 1) {
                        J.VariableDeclarations.NamedVariable variable = variableDeclarations.getVariables().get(0);
                        if (variable.getInitializer() != null && isCollectionOrArrayAccess(variable.getInitializer(), indexVarName, collection)) {
                            detectedVariableName = variable.getSimpleName();
                        }
                    }
                    return super.visitVariableDeclarations(variableDeclarations, o);
                }
            }

            private class ValidationVisitor extends JavaVisitor<Object> {
                private final String indexVarName;
                private final J collection;
                private boolean valid = true;
                private boolean insideValidAccess;

                public ValidationVisitor(String indexVarName, J collection) {
                    this.indexVarName = indexVarName;
                    this.collection = collection;
                }

                public boolean isValid() {
                    return valid;
                }

                @Override
                public J visitIdentifier(J.Identifier identifier, Object o) {
                    if (indexVarName.equals(identifier.getSimpleName()) && !insideValidAccess) {
                        valid = false;
                    }
                    return super.visitIdentifier(identifier, o);
                }

                @Override
                public J visitMethodInvocation(J.MethodInvocation method, Object o) {
                    if ("get".equals(method.getSimpleName()) &&
                            method.getArguments().size() == 1 &&
                            method.getArguments().get(0) instanceof J.Identifier &&
                            indexVarName.equals(((J.Identifier) method.getArguments().get(0)).getSimpleName())) {

                        boolean wasInsideValidAccess = insideValidAccess;
                        if (SemanticallyEqual.areEqual(method.getSelect(), collection)) {
                            insideValidAccess = true;
                        } else {
                            valid = false;
                        }

                        J result = super.visitMethodInvocation(method, o);
                        insideValidAccess = wasInsideValidAccess;
                        return result;
                    }
                    return super.visitMethodInvocation(method, o);
                }

                @Override
                public J visitArrayAccess(J.ArrayAccess arrayAccess, Object o) {
                    if (arrayAccess.getDimension().getIndex() instanceof J.Identifier &&
                            indexVarName.equals(((J.Identifier) arrayAccess.getDimension().getIndex()).getSimpleName())) {

                        boolean wasInsideValidAccess = insideValidAccess;
                        if (SemanticallyEqual.areEqual(arrayAccess.getIndexed(), collection)) {
                            insideValidAccess = true;
                        } else {
                            valid = false;
                        }

                        J result = super.visitArrayAccess(arrayAccess, o);
                        insideValidAccess = wasInsideValidAccess;
                        return result;
                    }
                    return super.visitArrayAccess(arrayAccess, o);
                }

            }

            private class BodyTransformer extends JavaVisitor<Object> {
                private final String indexVarName;
                private final J collection;
                private final String newVariableName;
                private String variableToReplace;

                public BodyTransformer(String indexVarName, J collection, String newVariableName) {
                    this.indexVarName = indexVarName;
                    this.collection = collection;
                    this.newVariableName = newVariableName;
                }

                @Override
                public @Nullable J visitVariableDeclarations(J.VariableDeclarations variableDeclarations, Object o) {
                    if (variableDeclarations.getVariables().size() == 1) {
                        J.VariableDeclarations.NamedVariable variable = variableDeclarations.getVariables().get(0);
                        if (variable.getInitializer() != null && isCollectionOrArrayAccess(variable.getInitializer(), indexVarName, collection)) {
                            variableToReplace = variable.getSimpleName();
                            return null;
                        }
                    }
                    return super.visitVariableDeclarations(variableDeclarations, o);
                }

                @Override
                public J visitIdentifier(J.Identifier identifier, Object o) {
                    if (variableToReplace != null && variableToReplace.equals(identifier.getSimpleName())) {
                        return new J.Identifier(
                                Tree.randomId(),
                                identifier.getPrefix(),
                                Markers.EMPTY,
                                Collections.emptyList(),
                                newVariableName,
                                identifier.getType(),
                                null
                        );
                    }
                    return super.visitIdentifier(identifier, o);
                }

                @Override
                public J visitMethodInvocation(J.MethodInvocation method, Object o) {
                    if ("get".equals(method.getSimpleName()) &&
                            method.getArguments().size() == 1 &&
                            method.getArguments().get(0) instanceof J.Identifier &&
                            indexVarName.equals(((J.Identifier) method.getArguments().get(0)).getSimpleName()) &&
                            SemanticallyEqual.areEqual(method.getSelect(), collection)) {

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
                    if (arrayAccess.getDimension().getIndex() instanceof J.Identifier &&
                            indexVarName.equals(((J.Identifier) arrayAccess.getDimension().getIndex()).getSimpleName()) &&
                            SemanticallyEqual.areEqual(arrayAccess.getIndexed(), collection)) {

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

            }
        };
    }
}
