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
import org.openrewrite.java.tree.*;
import org.openrewrite.marker.Markers;

import java.util.Scanner;

import static java.util.Collections.emptyList;
import static java.util.Collections.singletonList;
import static org.openrewrite.Tree.randomId;

final class JavaElementFactory {

    static J.Binary newLogicalExpression(J.Binary.Type operator, Expression left, Expression right) {
        return new J.Binary(
                randomId(),
                Space.EMPTY,
                Markers.EMPTY,
                left,
                new JLeftPadded<>(Space.SINGLE_SPACE, operator, Markers.EMPTY),
                right,
                JavaType.Primitive.Boolean
        );
    }

    static J.MemberReference newStaticMethodReference(JavaType.Method method, boolean qualified, @Nullable JavaType type) {
        JavaType.FullyQualified declaringType = method.getDeclaringType();
        Expression containing = className(declaringType, qualified);
        return newInstanceMethodReference(containing, method, type);
    }

    static Expression className(JavaType type, boolean qualified) {
        Expression name = null;
        String qualifiedName;
        if (type instanceof JavaType.Parameterized) {
            type = ((JavaType.Parameterized) type).getType();
        }
        if (qualified && type instanceof JavaType.FullyQualified && ((JavaType.FullyQualified) type).getOwningClass() != null) {
            Expression expression = className(((JavaType.FullyQualified) type).getOwningClass(), true);
            String simpleName = ((JavaType.FullyQualified) type).getClassName();
            return new J.FieldAccess(
                    randomId(),
                    Space.EMPTY,
                    Markers.EMPTY,
                    expression,
                    new JLeftPadded<>(
                            Space.EMPTY,
                            new J.Identifier(
                                    randomId(),
                                    Space.EMPTY,
                                    Markers.EMPTY,
                                    emptyList(),
                                    simpleName.substring(simpleName.lastIndexOf('.') + 1),
                                    type,
                                    null
                            ),
                            Markers.EMPTY),
                    type
            );
        }
        if (type instanceof JavaType.FullyQualified) {
            qualifiedName = qualified ? ((JavaType.FullyQualified) type).getFullyQualifiedName() : ((JavaType.FullyQualified) type).getClassName();
        } else {
            qualifiedName = type.toString();
        }

        Scanner scanner = new Scanner(qualifiedName.replace('$', '.')).useDelimiter("\\.");
        for (int i = 0; scanner.hasNext(); i++) {
            String part = scanner.next();
            JavaType typeOfContaining = scanner.hasNext() ? JavaType.Unknown.getInstance() : type;
            if (i > 0) {
                name = new J.FieldAccess(
                        randomId(),
                        Space.EMPTY,
                        Markers.EMPTY,
                        name,
                        new JLeftPadded<>(Space.EMPTY, new J.Identifier(randomId(), Space.EMPTY, Markers.EMPTY,
                                emptyList(), part, typeOfContaining, null), Markers.EMPTY),
                        typeOfContaining
                );
            } else {
                name = new J.Identifier(randomId(), Space.EMPTY, Markers.EMPTY, emptyList(), part, type, null);
            }
        }
        assert name != null;
        return name;
    }

    static J.MemberReference newInstanceMethodReference(Expression containing, JavaType.Method method, @Nullable JavaType type) {
        return newInstanceMethodReference(containing, method.getName(), method, type);
    }

    static J.MemberReference newInstanceMethodReference(Expression containing, String methodName, JavaType.Method methodType, @Nullable JavaType type) {
        return new J.MemberReference(
                randomId(),
                Space.EMPTY,
                Markers.EMPTY,
                new JRightPadded<>(containing, Space.EMPTY, Markers.EMPTY),
                null,
                new JLeftPadded<>(Space.EMPTY, new J.Identifier(randomId(), Space.EMPTY, Markers.EMPTY, emptyList(), methodName, null, null), Markers.EMPTY),
                type,
                methodType,
                null
        );
    }

    static J.@Nullable FieldAccess newClassLiteral(@Nullable JavaType type, boolean qualified) {
        JavaType.Class classType = getClassType(type);
        if (classType == null) {
            return null;
        }

        JavaType.Parameterized parameterized = new JavaType.Parameterized(null, classType, singletonList(type));
        return new J.FieldAccess(
                randomId(),
                Space.EMPTY,
                Markers.EMPTY,
                className(type, qualified),
                new JLeftPadded<>(
                        Space.EMPTY,
                        new J.Identifier(randomId(), Space.EMPTY, Markers.EMPTY, emptyList(), "class", parameterized, null),
                        Markers.EMPTY
                ),
                parameterized
        );
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

    public static J.Identifier newThis(JavaType type) {
        return new J.Identifier(randomId(), Space.EMPTY, Markers.EMPTY, emptyList(), "this", type, null);
    }
}
