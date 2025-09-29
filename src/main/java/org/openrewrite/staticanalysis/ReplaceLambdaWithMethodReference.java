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

import org.jspecify.annotations.Nullable;
import org.openrewrite.*;
import org.openrewrite.java.JavaTemplate;
import org.openrewrite.java.JavaVisitor;
import org.openrewrite.java.service.ImportService;
import org.openrewrite.java.tree.*;
import org.openrewrite.kotlin.KotlinVisitor;
import org.openrewrite.kotlin.tree.K;
import org.openrewrite.marker.Markers;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import static java.util.Collections.emptyList;
import static java.util.Collections.singleton;
import static java.util.Collections.singletonList;
import static org.openrewrite.Tree.randomId;
import static org.openrewrite.staticanalysis.JavaElementFactory.*;

public class ReplaceLambdaWithMethodReference extends Recipe {

    @Override
    public String getDisplayName() {
        return "Use method references in lambda";
    }

    @Override
    public String getDescription() {
        return "Replaces the single statement lambdas `o -> o instanceOf X`, `o -> (A) o`, `o -> System.out.println(o)`, `o -> o != null`, `o -> o == null` with the equivalent method reference.";
    }

    @Override
    public Set<String> getTags() {
        return singleton("RSPEC-S1612");
    }

    @Override
    public Duration getEstimatedEffortPerOccurrence() {
        return Duration.ofMinutes(2);
    }

    @Override
    public TreeVisitor<?, ExecutionContext> getVisitor() {
        return new TreeVisitor<Tree, ExecutionContext>() {
            @Override
            public @Nullable Tree visit(@Nullable Tree tree, ExecutionContext ctx, Cursor parent) {
                if (tree instanceof J.CompilationUnit) {
                    return new ReplaceLambdaWithMethodReferenceJavaVisitor().visit(tree, ctx);
                }
                if (tree instanceof K.CompilationUnit) {
                    return new ReplaceLambdaWithMethodReferenceKotlinVisitor().visit(tree, ctx);
                }
                return tree;
            }
        };
    }

    private static class ReplaceLambdaWithMethodReferenceKotlinVisitor extends KotlinVisitor<ExecutionContext> {
        // Implement Me
    }

    private static class ReplaceLambdaWithMethodReferenceJavaVisitor extends JavaVisitor<ExecutionContext> {
        @Override
        public J visitLambda(J.Lambda lambda, ExecutionContext ctx) {
            J.Lambda l = (J.Lambda) super.visitLambda(lambda, ctx);
            updateCursor(l);

            if (insideAnonymousInnerClass()) {
                return l;
            }

            J body = l.getBody();
            if (body instanceof J.Block && ((J.Block) body).getStatements().size() == 1) {
                Statement statement = ((J.Block) body).getStatements().get(0);
                if (statement instanceof J.Return) {
                    body = ((J.Return) statement).getExpression();
                } else {
                    body = statement;
                }
            }

            if (body instanceof J.InstanceOf) {
                J.InstanceOf instanceOf = (J.InstanceOf) body;
                J j = instanceOf.getClazz();
                if ((j instanceof J.Identifier || j instanceof J.FieldAccess) &&
                        instanceOf.getExpression() instanceof J.Identifier) {
                    // Create the class literal directly from the original expression
                    JavaType.Class classType = getClassType(((TypeTree) j).getType());
                    if (classType != null) {
                        JavaType.Parameterized parameterized = new JavaType.Parameterized(null, classType, singletonList(((TypeTree) j).getType()));
                        J.FieldAccess classLiteral = new J.FieldAccess(
                                randomId(),
                                Space.EMPTY,
                                Markers.EMPTY,
                                j.withPrefix(Space.EMPTY), // Use the original expression directly
                                new JLeftPadded<>(
                                        Space.EMPTY,
                                        new J.Identifier(randomId(), Space.EMPTY, Markers.EMPTY, emptyList(), "class", parameterized, null),
                                        Markers.EMPTY
                                ),
                                parameterized
                        );
                        //noinspection DataFlowIssue
                        JavaType.FullyQualified rawClassType = ((JavaType.Parameterized) classLiteral.getType()).getType();
                        Optional<JavaType.Method> isInstanceMethod = rawClassType.getMethods().stream().filter(m -> "isInstance".equals(m.getName())).findFirst();
                        if (isInstanceMethod.isPresent()) {
                            J.MemberReference updated = newInstanceMethodReference(classLiteral, isInstanceMethod.get(), lambda.getType()).withPrefix(lambda.getPrefix());
                            doAfterVisit(service(ImportService.class).shortenFullyQualifiedTypeReferencesIn(updated));
                            return updated;
                        }
                    }
                }
                return l;
            }
            if (body instanceof J.TypeCast && l.getParameters().getParameters().size() == 1) {
                J.TypeCast cast = (J.TypeCast) body;
                J param = l.getParameters().getParameters().get(0);
                if (cast.getExpression() instanceof J.Identifier && param instanceof J.VariableDeclarations &&
                        ((J.Identifier) cast.getExpression()).getSimpleName().equals(((J.VariableDeclarations) param).getVariables().get(0).getSimpleName())) {
                    J.ControlParentheses<TypeTree> j = cast.getClazz();
                    J tree = j.getTree();
                    if ((tree instanceof J.Identifier || tree instanceof J.FieldAccess) &&
                            !(j.getType() instanceof JavaType.GenericTypeVariable)) {
                        // Create the class literal directly from the original expression
                        JavaType.Class classType = getClassType(((Expression) tree).getType());
                        if (classType != null) {
                            JavaType.Parameterized parameterized = new JavaType.Parameterized(null, classType, singletonList(((Expression) tree).getType()));
                            J.FieldAccess classLiteral = new J.FieldAccess(
                                    randomId(),
                                    Space.EMPTY,
                                    Markers.EMPTY,
                                    tree.withPrefix(Space.EMPTY), // Use the original expression directly
                                    new JLeftPadded<>(
                                            Space.EMPTY,
                                            new J.Identifier(randomId(), Space.EMPTY, Markers.EMPTY, emptyList(), "class", parameterized, null),
                                            Markers.EMPTY
                                    ),
                                    parameterized
                            );
                            //noinspection DataFlowIssue
                            JavaType.FullyQualified fullClassType = ((JavaType.Parameterized) classLiteral.getType()).getType();
                            Optional<JavaType.Method> castMethod = fullClassType.getMethods().stream().filter(m -> "cast".equals(m.getName())).findFirst();
                            if (castMethod.isPresent()) {
                                J.MemberReference updated = newInstanceMethodReference(classLiteral, castMethod.get(), lambda.getType()).withPrefix(lambda.getPrefix());
                                doAfterVisit(service(ImportService.class).shortenFullyQualifiedTypeReferencesIn(updated));
                                return updated;
                            }
                        }
                    }
                }
                return l;
            }

            String code = "";
            if (body instanceof J.Binary) {
                J.Binary binary = (J.Binary) body;
                if ((binary.getOperator() == J.Binary.Type.Equal || binary.getOperator() == J.Binary.Type.NotEqual) &&
                        isNullCheck(binary.getLeft(), binary.getRight()) ||
                        isNullCheck(binary.getRight(), binary.getLeft())) {
                    code = J.Binary.Type.Equal == binary.getOperator() ? "java.util.Objects::isNull" :
                            "java.util.Objects::nonNull";
                    J updated = JavaTemplate.builder(code)
                            .contextSensitive()
                            .build()
                            .apply(getCursor(), l.getCoordinates().replace());
                    doAfterVisit(service(ImportService.class).shortenFullyQualifiedTypeReferencesIn(updated));
                    return updated;
                }
            } else if (body instanceof MethodCall) {
                MethodCall method = (MethodCall) body;
                if (method instanceof J.NewClass) {
                    J.NewClass nc = (J.NewClass) method;
                    if (nc.getBody() != null) {
                        return l;
                    }
                    if (isAMethodInvocationArgument(l, getCursor()) && nc.getType() instanceof JavaType.Class) {
                        JavaType.Class clazz = (JavaType.Class) nc.getType();
                        boolean hasMultipleConstructors = clazz.getMethods().stream().filter(JavaType.Method::isConstructor).count() > 1;
                        if (hasMultipleConstructors) {
                            return l;
                        }
                    }
                } else if (method instanceof J.MemberReference) {
                    return l;
                }

                if (method.getMethodType() == null ||
                        hasSelectWithPotentialSideEffects(method) ||
                        hasSelectWhoseReferenceMightChange(method) ||
                        !methodArgumentsMatchLambdaParameters(method, lambda)) {
                    return l;
                }

                JavaType.Method methodType = method.getMethodType();
                if (methodType != null && !isMethodReferenceAmbiguous(methodType)) {
                    Expression select =
                            method instanceof J.MethodInvocation ? ((J.MethodInvocation) method).getSelect() : null;
                    if (methodType.hasFlags(Flag.Static) ||
                            methodSelectMatchesFirstLambdaParameter(method, lambda)) {
                        if (method.getType() instanceof JavaType.Parameterized &&
                                ((JavaType.Parameterized) method.getType()).getTypeParameters().stream()
                                        .anyMatch(JavaType.GenericTypeVariable.class::isInstance)) {
                            return l;
                        }
                        J.MemberReference updated = newStaticMethodReference(methodType, true, lambda.getType()).withPrefix(lambda.getPrefix());
                        doAfterVisit(service(ImportService.class).shortenFullyQualifiedTypeReferencesIn(updated));
                        return updated;
                    }
                    if (method instanceof J.NewClass) {
                        NameTree clazz = ((J.NewClass) method).getClazz();
                        clazz = clazz instanceof J.ParameterizedType ? ((J.ParameterizedType) clazz).getClazz() : clazz;
                        return newInstanceMethodReference(clazz.withPrefix(Space.EMPTY), "new", methodType, lambda.getType()).withPrefix(lambda.getPrefix());
                    }
                    if (select != null) {
                        return newInstanceMethodReference(select, methodType, lambda.getType()).withPrefix(lambda.getPrefix());
                    }

                    Cursor owner = getCursor().dropParentUntil(is -> is instanceof J.ClassDeclaration ||
                            (is instanceof J.NewClass && ((J.NewClass) is).getBody() != null) ||
                            is instanceof J.Lambda);
                    return JavaElementFactory.newInstanceMethodReference(
                            JavaElementFactory.newThis(owner.<TypedTree>getValue().getType()),
                            methodType,
                            lambda.getType()
                    ).withPrefix(lambda.getPrefix());
                }
            }

            return l;
        }

        private boolean insideAnonymousInnerClass() {
            // Check if we're inside an anonymous inner class
            Cursor current = getCursor();
            while (current != null) {
                if (current.getValue() instanceof J.ClassDeclaration) {
                    // We've reached a regular class declaration, stop looking
                    return false;
                }
                if (current.getValue() instanceof J.NewClass &&
                        current.<J.NewClass>getValue().getBody() != null) {
                    // Don't replace lambdas inside anonymous inner classes that call unqualified methods
                    // as the "this" reference semantics might change
                    return true;
                }
                current = current.getParent();
            }
            return false;
        }

        private boolean hasSelectWithPotentialSideEffects(MethodCall method) {
            return method instanceof J.MethodInvocation &&
                   ((J.MethodInvocation) method).getSelect() instanceof MethodCall;
        }

        private boolean hasSelectWhoseReferenceMightChange(MethodCall method) {
            if (method instanceof J.MethodInvocation) {
                Expression select = ((J.MethodInvocation) method).getSelect();
                if (select instanceof J.Identifier) {
                    JavaType.Variable fieldType = ((J.Identifier) select).getFieldType();
                    return fieldType != null && fieldType.getOwner() instanceof JavaType.Class && !fieldType.hasFlags(Flag.Final);
                }
                if (select instanceof J.FieldAccess) {
                    JavaType.Variable fieldType = ((J.FieldAccess) select).getName().getFieldType();
                    return fieldType != null && fieldType.getOwner() instanceof JavaType.Class && !fieldType.hasFlags(Flag.Final);
                }
                if (select instanceof J.NewClass || select instanceof J.Parentheses) {
                    return true;
                }
            }
            return false;
        }

        private boolean methodArgumentsMatchLambdaParameters(MethodCall method, J.Lambda lambda) {
            JavaType.Method methodType = method.getMethodType();
            if (methodType == null) {
                return false;
            }
            boolean static_ = methodType.hasFlags(Flag.Static);
            List<Expression> methodArgs = getMethodArguments(method);
            List<J.VariableDeclarations.NamedVariable> lambdaParameters = getLambdaParameters(lambda);
            if (methodArgs.isEmpty() && lambdaParameters.isEmpty()) {
                return true;
            }
            if (!static_ && methodSelectMatchesFirstLambdaParameter(method, lambda)) {
                methodArgs.add(0, ((J.MethodInvocation) method).getSelect());
            }
            if (methodArgs.size() != lambdaParameters.size()) {
                return false;
            }
            for (int i = 0; i < lambdaParameters.size(); i++) {
                JavaType lambdaParam = lambdaParameters.get(i).getVariableType();
                if (!(methodArgs.get(i) instanceof J.Identifier)) {
                    return false;
                }
                JavaType methodArgument = ((J.Identifier) methodArgs.get(i)).getFieldType();
                if (lambdaParam != methodArgument) {
                    return false;
                }
            }
            return true;
        }

        private static List<Expression> getMethodArguments(MethodCall method) {
            List<Expression> list = new ArrayList<>();
            if (method instanceof J.MethodInvocation) {
                // avoid additional `ArrayList` allocation by using `JContainer#getElements()`
                for (Expression a : ((J.MethodInvocation) method).getPadding().getArguments().getElements()) {
                    if (!(a instanceof J.Empty)) {
                        list.add(a);
                    }
                }
            } else if (method instanceof J.NewClass) {
                // avoid additional `ArrayList` allocation by using `JContainer#getElements()`
                for (Expression a : ((J.NewClass) method).getPadding().getArguments().getElements()) {
                    if (!(a instanceof J.Empty)) {
                        list.add(a);
                    }
                }
            } else {
                for (Expression a : method.getArguments()) {
                    if (!(a instanceof J.Empty)) {
                        list.add(a);
                    }
                }
            }
            return list;
        }

        private static List<J.VariableDeclarations.NamedVariable> getLambdaParameters(J.Lambda lambda) {
            List<J.VariableDeclarations.NamedVariable> list = new ArrayList<>();
            for (J j : lambda.getParameters().getParameters()) {
                if (j instanceof J.VariableDeclarations) {
                    J.VariableDeclarations.NamedVariable namedVariable = ((J.VariableDeclarations) j).getVariables().get(0);
                    list.add(namedVariable);
                }
            }
            return list;
        }

        private boolean methodSelectMatchesFirstLambdaParameter(MethodCall method, J.Lambda lambda) {
            if (!(method instanceof J.MethodInvocation) ||
                !(((J.MethodInvocation) method).getSelect() instanceof J.Identifier) ||
                lambda.getParameters().getParameters().isEmpty() ||
                !(lambda.getParameters().getParameters().get(0) instanceof J.VariableDeclarations)) {
                return false;
            }
            J.VariableDeclarations firstLambdaParameter = (J.VariableDeclarations) lambda.getParameters()
                    .getParameters().get(0);
            return ((J.Identifier) ((J.MethodInvocation) method).getSelect()).getFieldType() ==
                   firstLambdaParameter.getVariables().get(0).getVariableType();
        }

        private boolean isNullCheck(J j1, J j2) {
            return j1 instanceof J.Identifier && j2 instanceof J.Literal &&
                   "null".equals(((J.Literal) j2).getValueSource());
        }

        private boolean isMethodReferenceAmbiguous(JavaType.Method method) {
            int count = 0;
            for (JavaType.Method meth : method.getDeclaringType().getMethods()) {
                if (meth.getName().equals(method.getName()) && !"println".equals(meth.getName()) && !meth.isConstructor()) {
                    if (++count > 1) {
                        return true;
                    }
                }
            }
            return false;
        }

        private static JavaType.@Nullable Class getClassType(@Nullable JavaType type) {
            if (type instanceof JavaType.Class) {
                JavaType.Class classType = (JavaType.Class) type;
                if ("java.lang.Class".equals(classType.getFullyQualifiedName())) {
                    return classType;
                }
                if ("java.lang.Object".equals(classType.getFullyQualifiedName())) {
                    for (JavaType.Method method : classType.getMethods()) {
                        if ("getClass".equals(method.getName())) {
                            return getClassType(method.getReturnType());
                        }
                    }
                    return null;
                }
                return getClassType(classType.getSupertype());
            }
            if (type instanceof JavaType.Parameterized) {
                return getClassType(((JavaType.Parameterized) type).getType());
            }
            if (type instanceof JavaType.GenericTypeVariable) {
                return getClassType(((JavaType.GenericTypeVariable) type).getBounds().get(0));
            }
            if (type instanceof JavaType.Array) {
                return getClassType(((JavaType.Array) type).getElemType());
            }
            if (type instanceof JavaType.Variable) {
                return getClassType(((JavaType.Variable) type).getOwner());
            }
            if (type instanceof JavaType.Method) {
                return getClassType(((JavaType.Method) type).getDeclaringType());
            }
            return null;
        }
    }

    private static boolean isAMethodInvocationArgument(J.Lambda lambda, Cursor cursor) {
        Cursor parent = cursor.dropParentUntil(p -> p instanceof J.MethodInvocation || p instanceof J.CompilationUnit);
        if (parent.getValue() instanceof J.MethodInvocation) {
            J.MethodInvocation m = parent.getValue();
            return m.getArguments().stream().anyMatch(arg -> arg == lambda);
        }
        return false;
    }
}
