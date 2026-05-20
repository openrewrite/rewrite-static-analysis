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

import lombok.Getter;
import org.jspecify.annotations.Nullable;
import org.openrewrite.*;
import org.openrewrite.java.JavaIsoVisitor;
import org.openrewrite.java.NoMissingTypes;
import org.openrewrite.java.RemoveUnusedImports;
import org.openrewrite.java.search.FindAnnotations;
import org.openrewrite.java.service.AnnotationService;
import org.openrewrite.java.tree.*;

import java.util.List;
import java.util.Set;

import static java.util.Collections.singleton;

public class RemoveUnusedPrivateMethods extends Recipe {

    @Getter
    final String displayName = "Remove unused private methods";

    @Getter
    final String description = "`private` methods that are never executed are dead code and should be removed. " +
            "Keeping unreachable methods around adds maintenance burden and can give a false impression of the class's capabilities.";

    @Getter
    final Set<String> tags = singleton("RSPEC-S1144");

    @Override
    public TreeVisitor<?, ExecutionContext> getVisitor() {
        JavaIsoVisitor<ExecutionContext> visitor = new JavaIsoVisitor<ExecutionContext>() {
            @Override
            public J.ClassDeclaration visitClassDeclaration(J.ClassDeclaration classDeclaration, ExecutionContext ctx) {
                if (unusedWarningsSuppressed(classDeclaration)) {
                    return classDeclaration;
                }
                return super.visitClassDeclaration(classDeclaration, ctx);
            }

            private boolean unusedWarningsSuppressed(J classDeclaration) {
                for (J.Annotation annotation : FindAnnotations.find(classDeclaration, "java.lang.SuppressWarnings")) {
                    List<Expression> arguments = annotation.getArguments();
                    if (arguments != null) {
                        for (Expression argument : arguments) {
                            if (J.Literal.isLiteralValue(argument, "all") ||
                                    J.Literal.isLiteralValue(argument, "unused")) {
                                return true;
                            }
                        }
                    }
                }
                return false;
            }

            @Override
            public J.@Nullable MethodDeclaration visitMethodDeclaration(J.MethodDeclaration method, ExecutionContext ctx) {
                J.MethodDeclaration m = super.visitMethodDeclaration(method, ctx);
                JavaType.Method methodType = method.getMethodType();
                if (methodType != null && methodType.hasFlags(Flag.Private) &&
                        !method.isConstructor() &&
                        service(AnnotationService.class).getAllAnnotations(getCursor()).isEmpty()) {

                    J.ClassDeclaration classDeclaration = getCursor().firstEnclosing(J.ClassDeclaration.class);
                    if (classDeclaration == null) {
                        return m;
                    }
                    switch (m.getSimpleName()) {
                        case "readObject":
                        case "readObjectNoData":
                        case "readResolve":
                        case "writeObject":
                        case "writeReplace":
                            return m;
                    }

                    JavaSourceFile cu = getCursor().firstEnclosingOrThrow(JavaSourceFile.class);
                    for (JavaType.Method usedMethodType : cu.getTypesInUse().getUsedMethods()) {
                        if (referencesMethod(methodType, usedMethodType)) {
                            return m;
                        }
                    }

                    for (JavaType javaType : cu.getTypesInUse().getTypesInUse()) {
                        if (TypeUtils.isOfClassType(javaType, "org.junit.jupiter.params.provider.MethodSource")) {
                            return m;
                        }
                    }

                    doAfterVisit(new RemoveUnusedImports().getVisitor());
                    //noinspection ConstantConditions
                    return null;
                }

                return m;
            }
        };
        return Preconditions.check(new NoMissingTypes(), Repeat.repeatUntilStable(visitor));
    }

    private static boolean referencesMethod(JavaType.Method declaration, JavaType.Method used) {
        if (!declaration.getName().equals(used.getName())) {
            return false;
        }
        if (declaration.equals(used)) {
            return true;
        }
        JavaType.FullyQualified declarationType = TypeUtils.asFullyQualified(declaration.getDeclaringType());
        JavaType.FullyQualified usedDeclaringType = TypeUtils.asFullyQualified(used.getDeclaringType());
        if (declarationType == null || usedDeclaringType == null ||
                !TypeUtils.fullyQualifiedNamesAreEqual(declarationType.getFullyQualifiedName(), usedDeclaringType.getFullyQualifiedName())) {
            return false;
        }
        List<JavaType> declarationParams = declaration.getParameterTypes();
        List<JavaType> usedParams = used.getParameterTypes();
        if (declarationParams.size() != usedParams.size()) {
            return false;
        }
        for (int i = 0; i < declarationParams.size(); i++) {
            if (!TypeUtils.isOfType(declarationParams.get(i), usedParams.get(i)) &&
                    !TypeUtils.isOfType(usedParams.get(i), declarationParams.get(i))) {
                return false;
            }
        }
        return true;
    }
}
