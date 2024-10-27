/*
 * Copyright 2022 the original author or authors.
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

import lombok.EqualsAndHashCode;
import lombok.Value;
import org.jspecify.annotations.Nullable;
import org.openrewrite.*;
import org.openrewrite.csharp.tree.Cs;
import org.openrewrite.internal.ListUtils;
import org.openrewrite.java.JavaIsoVisitor;
import org.openrewrite.java.service.AnnotationService;
import org.openrewrite.java.tree.*;
import org.openrewrite.marker.Markers;

import java.time.Duration;
import java.util.*;
import java.util.function.Predicate;
import java.util.stream.Collectors;

import static java.util.Collections.emptyList;

public class FinalizePrivateFields extends Recipe {
    @Override
    public String getDisplayName() {
        return "Finalize private fields";
    }

    @Override
    public String getDescription() {
        return "Adds the `final` modifier keyword to private instance variables which are not reassigned.";
    }

    @Override
    public @Nullable Duration getEstimatedEffortPerOccurrence() {
        return Duration.ofMinutes(2);
    }

    @Override
    public TreeVisitor<?, ExecutionContext> getVisitor() {
        return new JavaIsoVisitor<ExecutionContext>() {
            private Set<JavaType.Variable> privateFieldsToBeFinalized = new HashSet<>();

            @Nullable
            private SourceFile topLevel;

            @Override
            public @Nullable J visit(@Nullable Tree tree, ExecutionContext ctx) {
                if (topLevel == null && tree instanceof SourceFile) {
                    topLevel = (SourceFile) tree;
                }
                return super.visit(tree, ctx);
            }

            @Override
            public J.ClassDeclaration visitClassDeclaration(J.ClassDeclaration classDecl, ExecutionContext ctx) {
                if (!service(AnnotationService.class).getAllAnnotations(getCursor()).isEmpty()) {
                    // skip if a class has any annotation, since the annotation could generate some code to assign
                    // fields like Lombok @Setter.
                    return classDecl;
                }

                // skip if a class has multi constructor methods
                if (getConstructorCount(classDecl) > 1 || isInnerClass(classDecl)) {
                    return classDecl;
                }

                List<J.VariableDeclarations.NamedVariable> privateFields = collectPrivateFields(getCursor());
                Map<JavaType.Variable, Integer> privateFieldAssignCountMap = privateFields.stream()
                        .filter(v -> v.getVariableType() != null)
                        .collect(Collectors.toMap(J.VariableDeclarations.NamedVariable::getVariableType,
                                v -> v.getInitializer() != null ? 1 : 0));

                CollectPrivateFieldsAssignmentCounts.collect(classDecl, privateFieldAssignCountMap);

                privateFieldsToBeFinalized = privateFieldAssignCountMap.entrySet()
                        .stream()
                        .filter(entry -> entry.getValue() == 1)
                        .map(Map.Entry::getKey)
                        .collect(Collectors.toSet());

                return super.visitClassDeclaration(classDecl, ctx);
            }

            @Override
            public J.VariableDeclarations visitVariableDeclarations(J.VariableDeclarations multiVariable,
                                                                    ExecutionContext ctx) {
                J.VariableDeclarations mv = super.visitVariableDeclarations(multiVariable, ctx);

                boolean canAllVariablesBeFinalized = mv.getVariables()
                        .stream()
                        .map(J.VariableDeclarations.NamedVariable::getVariableType)
                        .allMatch(privateFieldsToBeFinalized::contains);

                if (canAllVariablesBeFinalized) {
                    mv = autoFormat(mv.withVariables(ListUtils.map(mv.getVariables(), v -> {
                        JavaType.Variable type = v.getVariableType();
                        return type != null ? v.withVariableType(type.withFlags(
                                Flag.bitMapToFlags(type.getFlagsBitMap() | Flag.Final.getBitMask()))) : null;
                    })).withModifiers(ListUtils.concat(mv.getModifiers(),
                            new J.Modifier(Tree.randomId(), Space.EMPTY, Markers.EMPTY, topLevel instanceof Cs ? "readonly" : "final",
                                    topLevel instanceof Cs ? J.Modifier.Type.LanguageExtension : J.Modifier.Type.Final, emptyList()))), ctx);
                }

                return mv;
            }

            private boolean anyAnnotationApplied(Cursor variableCursor) {
                return !service(AnnotationService.class).getAllAnnotations(variableCursor).isEmpty() ||
                       variableCursor.<J.VariableDeclarations>getValue().getTypeExpression() instanceof J.AnnotatedType;
            }

            /**
             * Collect private and non-final fields from a class
             */
            private List<J.VariableDeclarations.NamedVariable> collectPrivateFields(Cursor classCursor) {
                J.ClassDeclaration classDecl = classCursor.getValue();
                Cursor bodyCursor = new Cursor(classCursor, classDecl.getBody());
                return classDecl.getBody()
                        .getStatements()
                        .stream()
                        .filter(J.VariableDeclarations.class::isInstance)
                        .map(J.VariableDeclarations.class::cast)
                        .filter(mv -> mv.hasModifier(J.Modifier.Type.Private) &&
                                      !mv.hasModifier(J.Modifier.Type.Final) &&
                                      (!(topLevel instanceof Cs) || mv.getModifiers().stream().noneMatch(m -> "readonly".equals(m.getKeyword()) || "const".equals(m.getKeyword()))) &&
                                      !mv.hasModifier(J.Modifier.Type.Volatile))
                        .filter(mv -> !anyAnnotationApplied(new Cursor(bodyCursor, mv)))
                        .map(J.VariableDeclarations::getVariables)
                        .flatMap(Collection::stream)
                        .collect(Collectors.toList());
            }
        };
    }

    private static int getConstructorCount(J.ClassDeclaration classDecl) {
        return (int) classDecl.getBody()
                .getStatements()
                .stream()
                .filter(J.MethodDeclaration.class::isInstance)
                .map(J.MethodDeclaration.class::cast)
                .filter(J.MethodDeclaration::isConstructor)
                .count();
    }

    private static boolean isInnerClass(J.ClassDeclaration classDecl) {
        return classDecl.getType() != null && classDecl.getType().getOwningClass() != null;
    }

    private static class CollectPrivateFieldsAssignmentCounts extends JavaIsoVisitor<Map<JavaType.Variable, Integer>> {

        /**
         * Collects private fields assignment counts, count rules are:
         * (1) any assignment in class constructor, instance variable initializer or initializer block count for 1.
         * (2) any assignment in other place count for 2.
         * (3) any assignment in a loop or lambda in anywhere count for 2.
         * By giving 3 rules above, if a private field has an assigned count equal to 1, it should be finalized.
         *
         * @param j The subtree to search, supposed to be at Class declaration level.
         */
        static void collect(J j, Map<JavaType.Variable, Integer> assignedCountMap) {
            new CollectPrivateFieldsAssignmentCounts().visit(j, assignedCountMap);
        }

        @Override
        public J.Assignment visitAssignment(J.Assignment assignment, Map<JavaType.Variable, Integer> assignedCountMap) {
            J.Assignment a = super.visitAssignment(assignment, assignedCountMap);
            updateAssignmentCount(getCursor(), a.getVariable(), assignedCountMap);
            return a;
        }

        @Override
        public J.AssignmentOperation visitAssignmentOperation(J.AssignmentOperation assignOp,
                                                              Map<JavaType.Variable, Integer> assignedCountMap) {
            J.AssignmentOperation a = super.visitAssignmentOperation(assignOp, assignedCountMap);
            updateAssignmentCount(getCursor(), a.getVariable(), assignedCountMap);
            return a;
        }

        @Override
        public J.Unary visitUnary(J.Unary unary, Map<JavaType.Variable, Integer> assignedCountMap) {
            J.Unary u = super.visitUnary(unary, assignedCountMap);
            if (u.getOperator().isModifying()) {
                updateAssignmentCount(getCursor(), u.getExpression(), assignedCountMap);
            }
            return u;
        }

        private static void updateAssignmentCount(Cursor cursor,
                                                  Expression expression,
                                                  Map<JavaType.Variable, Integer> assignedCountMap
        ) {
            JavaType.Variable privateField = null;

            if (expression instanceof J.FieldAccess) {
                // to support case of field accessed via 'this' like 'A.this.member'.
                J.Identifier lastId = FindLastIdentifier.getLastIdentifier(expression);
                if (lastId != null && assignedCountMap.containsKey(lastId.getFieldType())) {
                    privateField = lastId.getFieldType();
                }
            } else if (expression instanceof J.Identifier) {
                J.Identifier i = (J.Identifier) expression;
                if (assignedCountMap.containsKey(i.getFieldType())) {
                    privateField = i.getFieldType();
                }
            }

            if (privateField != null) {
                // filtered so the variable is a private field here
                int assignedCount = assignedCountMap.get(privateField);

                // increment count rules are following
                // (1) any assignment in class constructor, instance variable initializer or initializer block count for 1.
                // (2) any assignment in other place count for 2.
                // (3) any assignment in a loop or lambda in anywhere count for 2.
                int increment;
                if (isInLoop(cursor) || isInLambda(cursor)) {
                    increment = 2;
                } else if (isInitializedByClass(cursor, privateField.hasFlags(Flag.Static))) {
                    increment = 1;
                } else {
                    increment = 2;
                }

                assignedCountMap.put(privateField, assignedCount + increment);
            }
        }

        private static boolean isInLoop(Cursor cursor) {
            return isInForLoop(cursor) || isInDoWhileLoopLoop(cursor) || isInWhileLoop(cursor);
        }

        /**
         * @param cursor               current assignment position
         * @param privateFieldIsStatic true if the private field is static
         * @return true if the cursor is in a constructor or an initializer block (both static or non-static)
         */
        private static boolean isInitializedByClass(Cursor cursor, boolean privateFieldIsStatic) {
            Object parent = cursor.dropParentWhile(p -> (p instanceof J.Block && !((J.Block) p).isStatic()) ||
                                                        p instanceof JRightPadded ||
                                                        p instanceof JLeftPadded)
                    .getValue();
            if (parent instanceof J.Block) {
                return privateFieldIsStatic;
            }
            if (privateFieldIsStatic) {
                return false;
            }
            if (parent instanceof J.MethodDeclaration) {
                return ((J.MethodDeclaration) parent).isConstructor();
            }
            return parent instanceof J.ClassDeclaration;
        }

        /**
         * When searching for assignment belonging, drop cursor stop at class declaration or method declaration.
         */
        private static boolean dropCursorEndCondition(Object parent) {
            return parent instanceof J.ClassDeclaration || parent instanceof J.MethodDeclaration;
        }

        /**
         * Drop until meet endCondition or condition
         *
         * @return true if meet the condition, or false if not meet the condition until the end.
         */
        private static boolean dropUntilMeetCondition(Cursor cursor,
                                                      Predicate<Object> endCondition,
                                                      Predicate<Object> condition) {
            return condition.test(
                    cursor.dropParentUntil(parent -> endCondition.test(parent) || condition.test(parent)).getValue());
        }

        private static boolean isInForLoop(Cursor cursor) {
            return dropUntilMeetCondition(cursor,
                    CollectPrivateFieldsAssignmentCounts::dropCursorEndCondition,
                    J.ForLoop.class::isInstance);
        }

        private static boolean isInDoWhileLoopLoop(Cursor cursor) {
            return dropUntilMeetCondition(cursor,
                    CollectPrivateFieldsAssignmentCounts::dropCursorEndCondition,
                    J.DoWhileLoop.class::isInstance);
        }

        private static boolean isInWhileLoop(Cursor cursor) {
            return dropUntilMeetCondition(cursor,
                    CollectPrivateFieldsAssignmentCounts::dropCursorEndCondition,
                    J.WhileLoop.class::isInstance);
        }

        private static boolean isInLambda(Cursor cursor) {
            return dropUntilMeetCondition(cursor,
                    CollectPrivateFieldsAssignmentCounts::dropCursorEndCondition,
                    J.Lambda.class::isInstance);
        }
    }

    @Value
    @EqualsAndHashCode(callSuper = false)
    private static class FindLastIdentifier extends JavaIsoVisitor<List<J.Identifier>> {

        /**
         * Find the last identifier in a J.FieldAccess. The purpose is to check whether it's a private field.
         *
         * @param j the subtree to search, supposed to be a J.FieldAccess
         * @return the last Identifier if found, otherwise null.
         */
        static J.@Nullable Identifier getLastIdentifier(J j) {
            List<J.Identifier> ids = new FindLastIdentifier().reduce(j, new ArrayList<>());
            return !ids.isEmpty() ? ids.get(ids.size() - 1) : null;
        }

        @Override
        public J.Identifier visitIdentifier(J.Identifier identifier, List<J.Identifier> ids) {
            ids.add(identifier);
            return super.visitIdentifier(identifier, ids);
        }
    }
}
