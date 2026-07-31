/*
 * Copyright 2026 the original author or authors.
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
import org.openrewrite.Preconditions;
import org.openrewrite.Recipe;
import org.openrewrite.TreeVisitor;
import org.openrewrite.analysis.dataflow.DataFlowNode;
import org.openrewrite.analysis.dataflow.DataFlowSpec;
import org.openrewrite.analysis.dataflow.FindLocalFlowPaths;
import org.openrewrite.internal.ListUtils;
import org.openrewrite.java.ChangeType;
import org.openrewrite.java.JavaIsoVisitor;
import org.openrewrite.java.search.UsesType;
import org.openrewrite.java.tree.*;
import org.openrewrite.staticanalysis.table.LegacySynchronizedTypesNotMigrated;

import java.util.IdentityHashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;

import static java.util.Collections.emptySet;

/**
 * Base for recipes that replace a legacy synchronized type (`Hashtable`, `Vector`, `StringBuffer`)
 * with its unsynchronized counterpart when data flow analysis proves the instance is a local variable
 * that never escapes its method. The whole variable — its declaration, initializer, and every use
 * (including chained calls) — is re-typed so that no reference to the legacy type is left behind and
 * its import can be removed.
 */
abstract class ReplaceLegacyCollection extends Recipe {

    abstract String getLegacyType();

    abstract String getReplacementType();

    java.util.Set<String> getIncompatibleMethods() {
        return emptySet();
    }

    boolean isIncompatibleConstructor(J.NewClass newClass) {
        return false;
    }

    /**
     * Supertypes of the legacy type that the replacement does <em>not</em> also implement, so that the
     * value cannot flow into a variable declared with one of them (e.g. `Dictionary` for `Hashtable`).
     * The legacy type itself is always treated as incompatible.
     */
    java.util.Set<String> getIncompatibleSupertypes() {
        return emptySet();
    }

    transient LegacySynchronizedTypesNotMigrated notMigrated = new LegacySynchronizedTypesNotMigrated(this);

    @Override
    public TreeVisitor<?, ExecutionContext> getVisitor() {
        String legacyType = getLegacyType();
        String replacementType = getReplacementType();
        String replacementSimpleName = replacementType.substring(replacementType.lastIndexOf('.') + 1);
        java.util.Set<String> incompatibleMethods = getIncompatibleMethods();
        java.util.Set<String> incompatibleTargets = new java.util.HashSet<>(getIncompatibleSupertypes());
        incompatibleTargets.add(legacyType);
        return Preconditions.check(new UsesType<>(legacyType, false), new JavaIsoVisitor<ExecutionContext>() {
            @Override
            public J.VariableDeclarations.NamedVariable visitVariable(J.VariableDeclarations.NamedVariable variable, ExecutionContext ctx) {
                J.VariableDeclarations.NamedVariable v = super.visitVariable(variable, ctx);

                Expression initializer = v.getInitializer();
                JavaType.Variable variableType = v.getVariableType();
                boolean isField = variableType != null && !(variableType.getOwner() instanceof JavaType.Method);
                boolean newLegacyInstance = initializer instanceof J.NewClass && TypeUtils.isOfClassType(initializer.getType(), legacyType);
                boolean legacyField = isField && variableType != null && TypeUtils.isOfClassType(variableType.getType(), legacyType);
                if (!newLegacyInstance && !legacyField) {
                    return v;
                }

                String reason;
                if (isField) {
                    // Fields are shared state by nature; never migrated.
                    reason = "declared as a field";
                } else {
                    // A shared type expression across multiple variables cannot be changed for just one of them.
                    J.VariableDeclarations declaration = getCursor().firstEnclosing(J.VariableDeclarations.class);
                    J.MethodDeclaration method = getCursor().firstEnclosing(J.MethodDeclaration.class);
                    if (declaration != null && declaration.getVariables().size() > 1) {
                        reason = "declared alongside other variables";
                    } else if (isIncompatibleConstructor((J.NewClass) initializer)) {
                        reason = "uses a constructor with no " + replacementSimpleName + " equivalent";
                    } else if (method != null && usesIncompatibleMethod(method, variable.getSimpleName(), legacyType, incompatibleMethods)) {
                        reason = "calls a method with no " + replacementSimpleName + " equivalent";
                    } else {
                        DataFlowSpec escapes = new DataFlowSpec() {
                            @Override
                            public boolean isSource(DataFlowNode srcNode) {
                                return initializer == srcNode.getCursor().getValue();
                            }

                            @Override
                            public boolean isSink(DataFlowNode sinkNode) {
                                return escapesMethod(sinkNode.getCursor(), incompatibleTargets);
                            }
                        };
                        if (FindLocalFlowPaths.noneMatch(getCursor(), escapes)) {
                            Expression newInitializer = (Expression) new ChangeType(legacyType, replacementType, false)
                                    .getVisitor().visitNonNull(initializer, ctx, getCursor().getParentOrThrow());
                            JavaType newType = newInitializer.getType();
                            JavaType.Variable newVariableType = variableType.withType(newType);
                            v = v.withInitializer(newInitializer)
                                    .withVariableType(newVariableType)
                                    .withName(v.getName().withType(newType).withFieldType(newVariableType));
                            // Register the variable so its uses (which reference the old JavaType.Variable) are re-typed.
                            Map<JavaType.Variable, JavaType.Variable> retype = getCursor().getNearestMessage("retype");
                            if (retype == null) {
                                retype = new IdentityHashMap<>();
                                getCursor().putMessageOnFirstEnclosing(J.CompilationUnit.class, "retype", retype);
                            }
                            retype.put(variableType, newVariableType);
                            getCursor().putMessageOnFirstEnclosing(J.VariableDeclarations.class, "replace", true);
                            return v;
                        }
                        reason = "escapes the method";
                    }
                }

                JavaSourceFile cu = getCursor().firstEnclosing(JavaSourceFile.class);
                J.ClassDeclaration clazz = getCursor().firstEnclosing(J.ClassDeclaration.class);
                notMigrated.insertRow(ctx, new LegacySynchronizedTypesNotMigrated.Row(
                        cu == null ? "" : cu.getSourcePath().toString(),
                        clazz != null && clazz.getType() != null ? clazz.getType().getFullyQualifiedName() : "",
                        legacyType,
                        reason));
                return v;
            }

            @Override
            public J.VariableDeclarations visitVariableDeclarations(J.VariableDeclarations multiVariable, ExecutionContext ctx) {
                J.VariableDeclarations mv = super.visitVariableDeclarations(multiVariable, ctx);
                if (getCursor().getMessage("replace", false)) {
                    mv = mv.withTypeExpression((TypeTree) new ChangeType(legacyType, replacementType, false)
                            .getVisitor().visit(mv.getTypeExpression(), ctx, getCursor().getParentOrThrow()));
                    maybeAddImport(replacementType);
                    maybeRemoveImport(legacyType);
                }
                return mv;
            }

            @Override
            public J.Identifier visitIdentifier(J.Identifier identifier, ExecutionContext ctx) {
                Map<JavaType.Variable, JavaType.Variable> retype = getCursor().getNearestMessage("retype");
                if (retype != null && identifier.getFieldType() != null && retype.containsKey(identifier.getFieldType())) {
                    JavaType.Variable newVariableType = retype.get(identifier.getFieldType());
                    return identifier.withType(newVariableType.getType()).withFieldType(newVariableType);
                }
                return identifier;
            }

            @Override
            public J.MethodInvocation visitMethodInvocation(J.MethodInvocation method, ExecutionContext ctx) {
                J.MethodInvocation mi = super.visitMethodInvocation(method, ctx);
                JavaType.Method methodType = mi.getMethodType();
                // Once the receiver has been re-typed to the replacement, its still-legacy method type
                // (and, for chained calls, its return type) must follow so no legacy reference remains.
                if (methodType != null && mi.getSelect() != null &&
                        TypeUtils.isOfClassType(mi.getSelect().getType(), replacementType) &&
                        TypeUtils.isOfClassType(methodType.getDeclaringType(), legacyType)) {
                    JavaType.Method newMethodType = remapMethodType(methodType, legacyType, replacementType);
                    mi = mi.withMethodType(newMethodType).withName(mi.getName().withType(newMethodType));
                }
                return mi;
            }
        });
    }

    private static boolean escapesMethod(Cursor cursor, java.util.Set<String> incompatibleTargets) {
        Object value = cursor.getValue();
        Object parent = cursor.getParentTreeCursor().getValue();
        // Contexts where the value leaves the method, or where its static type is relied upon in a way
        // the replacement type may not satisfy (cast, array element, either branch of a ternary).
        if (parent instanceof J.Return || parent instanceof J.TypeCast ||
                parent instanceof J.NewArray || parent instanceof J.Ternary) {
            return true;
        }
        if (parent instanceof J.MethodInvocation) {
            return ((J.MethodInvocation) parent).getArguments().contains(value);
        }
        if (parent instanceof J.NewClass) {
            return ((J.NewClass) parent).getArguments().contains(value);
        }
        if (parent instanceof J.Assignment) {
            J.Assignment assignment = (J.Assignment) parent;
            Expression target = assignment.getVariable();
            return assignment.getAssignment() == value &&
                    (target instanceof J.FieldAccess ||
                            (target instanceof J.Identifier && ((J.Identifier) target).getFieldType() != null));
        }
        if (parent instanceof J.VariableDeclarations.NamedVariable) {
            // Aliased into another local: unsafe only if the replacement type cannot satisfy that
            // variable's declared type (e.g. `Vector<X> v2 = v;` or `Dictionary<K,V> d = table;`).
            J.VariableDeclarations.NamedVariable target = (J.VariableDeclarations.NamedVariable) parent;
            // Only an aliasing reference (`v2 = v`), never this variable's own `new` initializer (the source).
            if (value instanceof J.Identifier && target.getInitializer() == value) {
                for (String incompatible : incompatibleTargets) {
                    if (TypeUtils.isOfClassType(target.getType(), incompatible)) {
                        return true;
                    }
                }
            }
        }
        return false;
    }

    private static boolean usesIncompatibleMethod(J.MethodDeclaration enclosing, String variableName,
                                                   String legacyType, java.util.Set<String> incompatibleMethods) {
        if (incompatibleMethods.isEmpty()) {
            return false;
        }
        return new JavaIsoVisitor<AtomicBoolean>() {
            @Override
            public J.MethodInvocation visitMethodInvocation(J.MethodInvocation method, AtomicBoolean found) {
                if (found.get()) {
                    return method;
                }
                if (method.getSelect() instanceof J.Identifier &&
                        variableName.equals(((J.Identifier) method.getSelect()).getSimpleName()) &&
                        TypeUtils.isOfClassType(method.getSelect().getType(), legacyType) &&
                        incompatibleMethods.contains(method.getSimpleName())) {
                    found.set(true);
                    return method;
                }
                return super.visitMethodInvocation(method, found);
            }
        }.reduce(enclosing, new AtomicBoolean(false)).get();
    }

    private static JavaType.Method remapMethodType(JavaType.Method methodType, String legacyType, String replacementType) {
        return methodType
                .withDeclaringType((JavaType.FullyQualified) remapType(methodType.getDeclaringType(), legacyType, replacementType))
                .withReturnType(remapType(methodType.getReturnType(), legacyType, replacementType))
                .withParameterTypes(ListUtils.map(methodType.getParameterTypes(), p -> remapType(p, legacyType, replacementType)));
    }

    private static JavaType remapType(JavaType type, String legacyType, String replacementType) {
        if (!TypeUtils.isOfClassType(type, legacyType)) {
            return type;
        }
        JavaType.FullyQualified replacement = TypeUtils.asFullyQualified(JavaType.buildType(replacementType));
        if (replacement != null && type instanceof JavaType.Parameterized) {
            return new JavaType.Parameterized(null, replacement, ((JavaType.Parameterized) type).getTypeParameters());
        }
        return replacement == null ? type : replacement;
    }
}
