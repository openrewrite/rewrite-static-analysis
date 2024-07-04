/*
 * Copyright 2020 the original author or authors.
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

import org.openrewrite.*;
import org.openrewrite.internal.ListUtils;
import org.openrewrite.internal.lang.Nullable;
import org.openrewrite.java.JavaIsoVisitor;
import org.openrewrite.java.search.UsesJavaVersion;
import org.openrewrite.java.tree.*;
import org.openrewrite.marker.Markers;
import org.openrewrite.staticanalysis.java.JavaFileChecker;

import java.time.Duration;
import java.util.*;

import static java.util.Collections.singletonList;
import static org.openrewrite.Tree.randomId;
import static org.openrewrite.java.tree.TypeUtils.findDeclaredMethod;

public class UseDiamondOperator extends Recipe {

    @Override
    public String getDisplayName() {
        return "Use diamond operator";
    }

    @Override
    public String getDescription() {
        return "The diamond operator (`<>`) should be used. Java 7 introduced the diamond operator (<>) to reduce the verbosity of generics code. For instance, instead of having to declare a List's type in both its declaration and its constructor, you can now simplify the constructor declaration with `<>`, and the compiler will infer the type.";
    }

    @Override
    public Set<String> getTags() {
        return Collections.singleton("RSPEC-S2293");
    }

    @Override
    public Duration getEstimatedEffortPerOccurrence() {
        return Duration.ofMinutes(1);
    }

    @Override
    public TreeVisitor<?, ExecutionContext> getVisitor() {
        // don't try to do this for Groovy or Kotlin sources
        return Preconditions.check(new JavaFileChecker<>(), new UseDiamondOperatorVisitor());
    }

    private static class UseDiamondOperatorVisitor extends JavaIsoVisitor<ExecutionContext> {
        private boolean java9;

        @Override
        public J.CompilationUnit visitCompilationUnit(J.CompilationUnit cu, ExecutionContext ctx) {
            java9 = new UsesJavaVersion<Integer>(9).visit(cu, 0) != cu;
            return super.visitCompilationUnit(cu, ctx);
        }

        @Override
        public J.VariableDeclarations visitVariableDeclarations(J.VariableDeclarations multiVariable, ExecutionContext ctx) {
            J.VariableDeclarations varDecls = super.visitVariableDeclarations(multiVariable, ctx);
            final TypedTree varDeclsTypeExpression = varDecls.getTypeExpression();
            if (varDeclsTypeExpression != null &&
                varDecls.getVariables().size() == 1 &&
                varDecls.getVariables().get(0).getInitializer() != null &&
                varDecls.getTypeExpression() instanceof J.ParameterizedType) {
                varDecls = varDecls.withVariables(ListUtils.map(varDecls.getVariables(), nv -> {
                    if (nv.getInitializer() instanceof J.NewClass) {
                        nv = nv.withInitializer(maybeRemoveParams(parameterizedTypes((J.ParameterizedType) varDeclsTypeExpression), (J.NewClass) nv.getInitializer()));
                    }
                    return nv;
                }));
            }
            return varDecls;
        }

        @Override
        public J.Assignment visitAssignment(J.Assignment assignment, ExecutionContext ctx) {
            J.Assignment asgn = super.visitAssignment(assignment, ctx);
            if (asgn.getAssignment() instanceof J.NewClass) {
                JavaType.Parameterized assignmentType = TypeUtils.asParameterized(asgn.getType());
                J.NewClass nc = (J.NewClass) asgn.getAssignment();
                if (assignmentType != null && nc.getClazz() instanceof J.ParameterizedType) {
                    asgn = asgn.withAssignment(maybeRemoveParams(assignmentType.getTypeParameters(), nc));
                }
            }
            return asgn;
        }

        @Override
        public J.MethodInvocation visitMethodInvocation(J.MethodInvocation method, ExecutionContext ctx) {
            if (isAParameter()) {
                return method;
            }

            J.MethodInvocation mi = super.visitMethodInvocation(method, ctx);
            JavaType.Method methodType = mi.getMethodType();

            if (methodType != null &&
                    !mi.getArguments().isEmpty() &&
                    methodType.getParameterTypes().size() <= mi.getArguments().size()) {


                Optional<JavaType.Method> declaredMethodType = findDeclaredMethod(methodType.getDeclaringType(), methodType.getName(), methodType.getParameterTypes());
                if (!declaredMethodType.isPresent()) {
                    // If we cannot find the method in the declaringType is because its parameter types doesn't match
                    // due to generic type parameters being inferred on the invocation. We cannot safely apply the
                    // diamond operator on an argument of this method, because we cannot guarantee type inference.
                    return method;
                }

                mi = mi.withArguments(ListUtils.map(mi.getArguments(), (i, arg) -> {
                    if (arg instanceof J.NewClass) {
                        boolean isGenericType = false;
                        boolean isVarArg = methodType.getParameterTypes().size() == 1 &&
                                methodType.getParameterTypes().get(0) instanceof JavaType.Array;

                        if (isVarArg) {
                            isGenericType = isGenericType(((JavaType.Array) methodType.getParameterTypes().get(0)).getElemType());
                        } else if (i < methodType.getParameterTypes().size()) {
                            JavaType parameterType = methodType.getParameterTypes().get(i);
                            isGenericType = isGenericType(parameterType);
                        }

                        if (isGenericType) {
                            return arg;
                        }

                        J.NewClass nc = (J.NewClass) arg;
                        if ((java9 || nc.getBody() == null) && !methodType.getParameterTypes().isEmpty()) {
                            JavaType.Parameterized paramType = TypeUtils.asParameterized(getMethodParamType(methodType, i));
                            if (paramType != null && nc.getClazz() instanceof J.ParameterizedType) {
                                return maybeRemoveParams(paramType.getTypeParameters(), nc);
                            }
                        }
                    }
                    return arg;
                }));
            }
            return mi;
        }

        private boolean isGenericType(@Nullable JavaType type) {
            if (type == null) {
                return false;
            }

            boolean isGeneric = false;
            JavaType.Parameterized parameterized = TypeUtils.asParameterized(type);
            if (parameterized != null) {
                List<JavaType> types = parameterized.getTypeParameters();

                for (JavaType tp : types) {
                    if (tp instanceof JavaType.GenericTypeVariable) {
                        isGeneric = true;
                        break;
                    }
                }
            }

            return isGeneric;
        }

        private JavaType getMethodParamType(JavaType.Method methodType, int paramIndex) {
            if (methodType.hasFlags(Flag.Varargs) && paramIndex >= methodType.getParameterTypes().size() - 1) {
                return ((JavaType.Array) methodType.getParameterTypes().get(methodType.getParameterTypes().size() - 1)).getElemType();
            } else {
                return methodType.getParameterTypes().get(paramIndex);
            }
        }

        @Override
        public J.Return visitReturn(J.Return _return, ExecutionContext ctx) {
            J.Return return_ = super.visitReturn(_return, ctx);
            J.NewClass nc = return_.getExpression() instanceof J.NewClass ? (J.NewClass) return_.getExpression() : null;
            if (nc != null && (java9 || nc.getBody() == null) && nc.getClazz() instanceof J.ParameterizedType) {
                J parentBlock = getCursor().dropParentUntil(v -> v instanceof J.MethodDeclaration || v instanceof J.Lambda).getValue();
                if (parentBlock instanceof J.MethodDeclaration) {
                    J.MethodDeclaration md = (J.MethodDeclaration) parentBlock;
                    if (md.getReturnTypeExpression() instanceof J.ParameterizedType) {
                        return_ = return_.withExpression(
                                maybeRemoveParams(parameterizedTypes((J.ParameterizedType) md.getReturnTypeExpression()), nc));
                    }
                }
            }
            return return_;
        }

        @Nullable
        private List<JavaType> parameterizedTypes(J.ParameterizedType parameterizedType) {
            if (parameterizedType.getTypeParameters() == null) {
                return null;
            }
            List<JavaType> types = new ArrayList<>(parameterizedType.getTypeParameters().size());
            for (Expression typeParameter : parameterizedType.getTypeParameters()) {
                types.add(typeParameter.getType());
            }
            return types;
        }


        private J.NewClass maybeRemoveParams(@Nullable List<JavaType> paramTypes, J.NewClass newClass) {
            if (paramTypes != null && (java9 || newClass.getBody() == null) && newClass.getClazz() instanceof J.ParameterizedType) {
                J.ParameterizedType newClassType = (J.ParameterizedType) newClass.getClazz();
                if (newClassType.getTypeParameters() != null) {
                    if (paramTypes.size() != newClassType.getTypeParameters().size() || hasAnnotations(newClassType)) {
                        return newClass;
                    } else {
                        for (int i = 0; i < paramTypes.size(); i++) {
                            if (!TypeUtils.isAssignableTo(paramTypes.get(i), newClassType.getTypeParameters().get(i).getType())) {
                                return newClass;
                            }
                        }
                    }
                    newClassType.getTypeParameters().stream()
                            .map(e -> TypeUtils.asFullyQualified(e.getType()))
                            .forEach(this::maybeRemoveImport);
                    newClass = newClass.withClazz(newClassType.withTypeParameters(singletonList(new J.Empty(randomId(), Space.EMPTY, Markers.EMPTY))));
                }
            }
            return newClass;
        }

        private static boolean hasAnnotations(J type) {
            if (type instanceof J.ParameterizedType) {
                J.ParameterizedType parameterizedType = (J.ParameterizedType) type;
                if (hasAnnotations(parameterizedType.getClazz())) {
                    return true;
                } else if (parameterizedType.getTypeParameters() != null) {
                    for (Expression typeParameter : parameterizedType.getTypeParameters()) {
                        if (hasAnnotations(typeParameter)) {
                            return true;
                        }
                    }
                }
            } else {
                return type instanceof J.AnnotatedType;
            }
            return false;
        }

        private boolean isAParameter() {
            return getCursor().dropParentUntil(p -> p instanceof J.MethodInvocation ||
                                                    p instanceof J.ClassDeclaration ||
                                                    p instanceof J.CompilationUnit ||
                                                    p instanceof J.Block ||
                                                    p == Cursor.ROOT_VALUE).getValue() instanceof J.MethodInvocation;
        }
    }
}
