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

import org.openrewrite.Cursor;
import org.openrewrite.ExecutionContext;
import org.openrewrite.Recipe;
import org.openrewrite.TreeVisitor;
import org.openrewrite.java.JavaTemplate;
import org.openrewrite.java.JavaVisitor;
import org.openrewrite.java.ShortenFullyQualifiedTypeReferences;
import org.openrewrite.java.tree.*;

import java.time.Duration;
import java.util.*;
import java.util.stream.Collectors;

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
        return Collections.singleton("RSPEC-1612");
    }

    @Override
    public Duration getEstimatedEffortPerOccurrence() {
        return Duration.ofMinutes(2);
    }

    @Override
    public TreeVisitor<?, ExecutionContext> getVisitor() {
        return new JavaVisitor<ExecutionContext>() {
            @Override
            public J visitLambda(J.Lambda lambda, ExecutionContext executionContext) {
                J.Lambda l = (J.Lambda) super.visitLambda(lambda, executionContext);
                updateCursor(l);

                if (TypeUtils.isOfClassType(lambda.getType(), "groovy.lang.Closure")) {
                    return l;
                }

                String code = "";
                J body = l.getBody();
                if (body instanceof J.Block && ((J.Block) body).getStatements().size() == 1) {
                    Statement statement = ((J.Block) body).getStatements().get(0);
                    if (statement instanceof J.MethodInvocation) {
                        body = statement;
                    } else if (statement instanceof J.Return &&
                               (((J.Return) statement).getExpression()) instanceof MethodCall) {
                        body = ((J.Return) statement).getExpression();
                    }
                } else if (body instanceof J.InstanceOf) {
                    J.InstanceOf instanceOf = (J.InstanceOf) body;
                    J j = instanceOf.getClazz();
                    if ((j instanceof J.Identifier || j instanceof J.FieldAccess) &&
                        instanceOf.getExpression() instanceof J.Identifier) {
                        J.FieldAccess classLiteral = newClassLiteral(((TypeTree) j).getType(), j instanceof J.FieldAccess);
                        if (classLiteral != null) {
                            //noinspection DataFlowIssue
                            JavaType.FullyQualified rawClassType = ((JavaType.Parameterized) classLiteral.getType()).getType();
                            Optional<JavaType.Method> isInstanceMethod = rawClassType.getMethods().stream().filter(m -> m.getName().equals("isInstance")).findFirst();
                            if (isInstanceMethod.isPresent()) {
                                return newInstanceMethodReference(isInstanceMethod.get(), classLiteral, lambda.getType()).withPrefix(lambda.getPrefix());
                            }
                        }
                    }
                } else if (body instanceof J.TypeCast) {
                    if (!(((J.TypeCast) body).getExpression() instanceof J.MethodInvocation)) {
                        J.ControlParentheses<TypeTree> j = ((J.TypeCast) body).getClazz();
                        J tree = j.getTree();
                        if ((tree instanceof J.Identifier || tree instanceof J.FieldAccess) &&
                            !(j.getType() instanceof JavaType.GenericTypeVariable)) {
                            J.FieldAccess classLiteral = newClassLiteral(((Expression) tree).getType(), tree instanceof J.FieldAccess);
                            if (classLiteral != null) {
                                //noinspection DataFlowIssue
                                JavaType.FullyQualified classType = ((JavaType.Parameterized) classLiteral.getType()).getType();
                                Optional<JavaType.Method> castMethod = classType.getMethods().stream().filter(m -> m.getName().equals("cast")).findFirst();
                                if (castMethod.isPresent()) {
                                    return newInstanceMethodReference(castMethod.get(), classLiteral, lambda.getType()).withPrefix(lambda.getPrefix());
                                }
                            }
                        }
                    }
                }

                if (body instanceof J.Binary) {
                    J.Binary binary = (J.Binary) body;
                    if (isNullCheck(binary.getLeft(), binary.getRight()) ||
                        isNullCheck(binary.getRight(), binary.getLeft())) {
                        doAfterVisit(new ShortenFullyQualifiedTypeReferences().getVisitor());
                        code = J.Binary.Type.Equal.equals(binary.getOperator()) ? "java.util.Objects::isNull" :
                                "java.util.Objects::nonNull";
                        return JavaTemplate.builder(code)
                                .contextSensitive()
                                .build()
                                .apply(getCursor(), l.getCoordinates().replace());
                    }
                } else if (body instanceof MethodCall) {
                    MethodCall method = (MethodCall) body;
                    if (method instanceof J.NewClass) {
                        J.NewClass nc = (J.NewClass) method;
                        if (nc.getBody() != null) {
                            return l;
                        } else {
                            if (isAMethodInvocationArgument(l, getCursor()) && nc.getType() instanceof JavaType.Class) {
                                JavaType.Class clazz = (JavaType.Class) nc.getType();
                                boolean hasMultipleConstructors = clazz.getMethods().stream().filter(JavaType.Method::isConstructor).count() > 1;
                                if (hasMultipleConstructors) {
                                    return l;
                                }
                            }
                        }
                    }

                    if (multipleMethodInvocations(method) ||
                        !methodArgumentsMatchLambdaParameters(method, lambda) ||
                        method instanceof J.MemberReference) {
                        return l;
                    }

                    Expression select =
                            method instanceof J.MethodInvocation ? ((J.MethodInvocation) method).getSelect() : null;
                    JavaType.Method methodType = method.getMethodType();
                    if (methodType != null) {
                        if (methodType.hasFlags(Flag.Static) ||
                            methodSelectMatchesFirstLambdaParameter(method, lambda)) {
                            doAfterVisit(new ShortenFullyQualifiedTypeReferences().getVisitor());

                            return newStaticMethodReference(methodType, true, lambda.getType()).withPrefix(lambda.getPrefix());
                        } else if (method instanceof J.NewClass) {
                            return JavaTemplate.builder("#{}::new")
                                    .contextSensitive()
                                    .build()
                                    .apply(getCursor(), l.getCoordinates().replace(), className((J.NewClass) method));
                        } else if (select != null) {
                            return newInstanceMethodReference(methodType, select, lambda.getType()).withPrefix(lambda.getPrefix());
                        } else {
                            String templ = "#{}::#{}";
                            return JavaTemplate.builder(templ)
                                    .contextSensitive()
                                    .build()
                                    .apply(getCursor(), l.getCoordinates().replace(), "this",
                                            method.getMethodType().getName());
                        }
                    }
                }

                return l;
            }

            // returns the class name as given in the source code (qualified or unqualified)
            private String className(J.NewClass method) {
                TypeTree clazz = method.getClazz();
                return clazz instanceof J.ParameterizedType ? ((J.ParameterizedType) clazz).getClazz().toString() :
                        Objects.toString(clazz);
            }

            private boolean multipleMethodInvocations(MethodCall method) {
                return method instanceof J.MethodInvocation &&
                       ((J.MethodInvocation) method).getSelect() instanceof J.MethodInvocation;
            }

            private boolean methodArgumentsMatchLambdaParameters(MethodCall method, J.Lambda lambda) {
                JavaType.Method methodType = method.getMethodType();
                if (methodType == null) {
                    return false;
                }
                boolean static_ = methodType.hasFlags(Flag.Static);
                List<Expression> methodArgs = method.getArguments().stream().filter(a -> !(a instanceof J.Empty))
                        .collect(Collectors.toList());
                List<J.VariableDeclarations.NamedVariable> lambdaParameters = lambda.getParameters().getParameters()
                        .stream().filter(J.VariableDeclarations.class::isInstance)
                        .map(J.VariableDeclarations.class::cast).map(v -> v.getVariables().get(0))
                        .collect(Collectors.toList());
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
        };

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
