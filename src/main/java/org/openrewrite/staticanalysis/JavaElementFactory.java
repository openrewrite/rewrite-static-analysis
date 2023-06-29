/*
 * Copyright 2023 the original author or authors.
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

import org.openrewrite.internal.lang.Nullable;
import org.openrewrite.java.tree.*;
import org.openrewrite.marker.Markers;

import java.util.Collections;
import java.util.Scanner;

import static org.openrewrite.Tree.randomId;

final class JavaElementFactory {

    static J.MemberReference newStaticMethodReference(JavaType.Method method, boolean qualified, @Nullable JavaType type) {
        JavaType.FullyQualified declaringType = method.getDeclaringType();
        Expression containing = null;
        String qualifiedName = qualified ? declaringType.getFullyQualifiedName() : declaringType.getClassName();
        Scanner scanner = new Scanner(qualifiedName.replace('$', '.')).useDelimiter("\\.");
        for (int i = 0; scanner.hasNext(); i++) {
            String part = scanner.next();
            JavaType typeOfContaining = scanner.hasNext() ? null : declaringType;
            if (i > 0) {
                containing = new J.FieldAccess(
                        randomId(),
                        Space.EMPTY,
                        Markers.EMPTY,
                        containing,
                        new JLeftPadded<>(Space.EMPTY, new J.Identifier(randomId(), Space.EMPTY, Markers.EMPTY, part, typeOfContaining, null), Markers.EMPTY),
                        typeOfContaining
                );
            } else {
                containing = new J.Identifier(randomId(), Space.EMPTY, Markers.EMPTY, part, declaringType, null);
            }
        }

        assert containing != null;
        return newInstanceMethodReference(method, containing, type);
    }

    static J.MemberReference newInstanceMethodReference(JavaType.Method method, Expression containing, @Nullable JavaType type) {
        return new J.MemberReference(
                randomId(),
                Space.EMPTY,
                Markers.EMPTY,
                new JRightPadded<>(containing, Space.EMPTY, Markers.EMPTY),
                null,
                new JLeftPadded<>(Space.EMPTY, new J.Identifier(randomId(), Space.EMPTY, Markers.EMPTY, method.getName(), null, null), Markers.EMPTY),
                type,
                method,
                null
        );
    }

    @Nullable
    static J.FieldAccess newClassLiteral(Expression typeReference) {
        JavaType.Class classType = getClassType(typeReference.getType());
        if (classType == null) {
            return null;
        }

        JavaType.Parameterized parameterized = new JavaType.Parameterized(null, classType, Collections.singletonList(typeReference.getType()));
        return new J.FieldAccess(
                randomId(),
                Space.EMPTY,
                Markers.EMPTY,
                typeReference,
                new JLeftPadded<>(
                        Space.EMPTY,
                        new J.Identifier(randomId(), Space.EMPTY, Markers.EMPTY, "class", parameterized, null),
                        Markers.EMPTY
                ),
                parameterized
        );
    }

    @Nullable
    private static JavaType.Class getClassType(@Nullable JavaType type) {
        if (type instanceof JavaType.Class) {
            JavaType.Class classType = (JavaType.Class) type;
            if (classType.getFullyQualifiedName().equals("java.lang.Class")) {
                return classType;
            } else if (classType.getFullyQualifiedName().equals("java.lang.Object")) {
                for (JavaType.Method method : classType.getMethods()) {
                    if (method.getName().equals("getClass")) {
                        return getClassType(method.getReturnType());
                    }
                }
                return null;
            } else {
                return getClassType(classType.getSupertype());
            }
        } else if (type instanceof JavaType.Parameterized) {
            return getClassType(((JavaType.Parameterized) type).getType());
        } else if (type instanceof JavaType.GenericTypeVariable) {
            return getClassType(((JavaType.GenericTypeVariable) type).getBounds().get(0));
        } else if (type instanceof JavaType.Array) {
            return getClassType(((JavaType.Array) type).getElemType());
        } else if (type instanceof JavaType.Variable) {
            return getClassType(((JavaType.Variable) type).getOwner());
        } else if (type instanceof JavaType.Method) {
            return getClassType(((JavaType.Method) type).getDeclaringType());
        }
        return null;
    }
}
