/*
 * Copyright 2021 the original author or authors.
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

import org.jspecify.annotations.Nullable;
import org.openrewrite.ExecutionContext;
import org.openrewrite.Recipe;
import org.openrewrite.Tree;
import org.openrewrite.TreeVisitor;
import org.openrewrite.internal.ListUtils;
import org.openrewrite.java.JavaIsoVisitor;
import org.openrewrite.java.JavaVisitor;
import org.openrewrite.java.JavadocVisitor;
import org.openrewrite.java.tree.*;

import java.time.Duration;
import java.util.*;

public class UnnecessaryThrows extends Recipe {

    @Override
    public String getDisplayName() {
        return "Unnecessary throws";
    }

    @Override
    public String getDescription() {
        return "Remove unnecessary `throws` declarations. This recipe will only remove unused, checked exceptions if:\n" +
                "\n" +
                "- The declaring class or the method declaration is `final`.\n" +
                "- The method declaration is `static` or `private`.\n" +
                "- The method overrides a method declaration in a super class and the super class does not throw the exception.\n" +
                "- The method is `public` or `protected` and the exception is not documented via a JavaDoc as a `@throws` tag.";
    }

    @Override
    public Set<String> getTags() {
        return Collections.singleton("RSPEC-S1130");
    }

    @Override
    public Duration getEstimatedEffortPerOccurrence() {
        return Duration.ofMinutes(5);
    }

    @Override
    public TreeVisitor<?, ExecutionContext> getVisitor() {
        return new JavaIsoVisitor<ExecutionContext>() {
            @Override
            public J.MethodDeclaration visitMethodDeclaration(J.MethodDeclaration method, ExecutionContext ctx) {
                J.MethodDeclaration m = super.visitMethodDeclaration(method, ctx);
                Set<JavaType.FullyQualified> unusedThrows = findExceptionCandidates(method);

                if (!unusedThrows.isEmpty()) {

                    new JavaIsoVisitor<ExecutionContext>() {

                        @Override
                        public @Nullable J visit(@Nullable Tree tree, ExecutionContext ctx) {
                            if (unusedThrows.isEmpty()) {
                                return (J) tree;
                            }
                            return super.visit(tree, ctx);
                        }

                        @Override
                        public J.Try.Resource visitTryResource(J.Try.Resource tryResource, ExecutionContext ctx) {
                            TypedTree resource = tryResource.getVariableDeclarations();

                            JavaType.FullyQualified resourceType = TypeUtils.asFullyQualified(resource.getType());
                            if (resourceType != null) {
                                if (TypeUtils.isAssignableTo(JavaType.ShallowClass.build("java.io.Closeable"), resourceType)) {
                                    unusedThrows.remove(JavaType.ShallowClass.build("java.io.IOException"));
                                } else if (TypeUtils.isAssignableTo(JavaType.ShallowClass.build("java.lang.AutoCloseable"), resourceType)) {
                                    unusedThrows.remove(JavaType.ShallowClass.build("java.lang.Exception"));
                                }
                            }

                            return super.visitTryResource(tryResource, ctx);
                        }

                        @Override
                        public J.Throw visitThrow(J.Throw thrown, ExecutionContext ctx) {
                            JavaType.FullyQualified type = TypeUtils.asFullyQualified(thrown.getException().getType());
                            if (type != null) {
                                unusedThrows.removeIf(t -> TypeUtils.isAssignableTo(t, type));
                            }
                            return thrown;
                        }

                        @Override
                        public J.MethodInvocation visitMethodInvocation(J.MethodInvocation method, ExecutionContext ctx) {
                            removeThrownTypes(method.getMethodType());
                            return super.visitMethodInvocation(method, ctx);
                        }

                        @Override
                        public J.NewClass visitNewClass(J.NewClass newClass, ExecutionContext ctx) {
                            removeThrownTypes(newClass.getConstructorType());
                            return super.visitNewClass(newClass, ctx);
                        }

                        private void removeThrownTypes(JavaType.@Nullable Method type) {
                            if (type != null) {
                                for (JavaType.FullyQualified thrownException : type.getThrownExceptions()) {
                                    unusedThrows.removeIf(t -> TypeUtils.isAssignableTo(t, thrownException));
                                }
                            }
                        }
                    }.visit(m, ctx);

                    if (!unusedThrows.isEmpty()) {
                        m = m.withThrows(ListUtils.map(m.getThrows(), t -> {
                            JavaType.FullyQualified type = TypeUtils.asFullyQualified(t.getType());
                            if (type != null && unusedThrows.contains(type)) {
                                maybeRemoveImport(type);
                                return null;
                            }
                            return t;
                        }));
                    }
                }

                return m;
            }
        };
    }


    private Set<JavaType.FullyQualified> findExceptionCandidates(J.@Nullable MethodDeclaration method) {

        if (method == null || method.getMethodType() == null || method.isAbstract()) {
            return Collections.emptySet();
        }

        //Collect all checked exceptions.
        Set<JavaType.FullyQualified> candidates = new TreeSet<>(Comparator.comparing(JavaType.FullyQualified::getFullyQualifiedName));

        if (method.getThrows() != null) {
            for (NameTree exception : method.getThrows()) {
                if (exception.getType() == null || exception.getType() instanceof JavaType.Unknown) {
                    return Collections.emptySet();
                }
                if (exception.getType() instanceof JavaType.FullyQualified && !TypeUtils.isAssignableTo(JavaType.ShallowClass.build("java.lang.RuntimeException"), exception.getType())) {
                    candidates.add(TypeUtils.asFullyQualified(exception.getType()));
                }
            }
        }

        if (candidates.isEmpty()) {
            return Collections.emptySet();
        }

        //noinspection ConstantConditions
        if ((method.getMethodType().getDeclaringType() != null && method.getMethodType().getDeclaringType().getFlags().contains(Flag.Final)) ||
                method.isAbstract() || method.hasModifier(J.Modifier.Type.Static) ||
                method.hasModifier(J.Modifier.Type.Private) ||
                method.hasModifier(J.Modifier.Type.Final)) {
            //Consider all checked exceptions as candidates if the type/method are final or the method is private or static.
            return candidates;
        }

        //Remove any candidates that are defined in an overridden method.
        Optional<JavaType.Method> superMethod = TypeUtils.findOverriddenMethod(method.getMethodType());
        if (superMethod.isPresent()) {
            JavaType.Method baseMethod = superMethod.get();
            baseMethod.getThrownExceptions();
            for (JavaType.FullyQualified baseException : baseMethod.getThrownExceptions()) {
                candidates.remove(baseException);
            }
        }
        if (!candidates.isEmpty()) {
            //Remove any candidates that are defined in Javadocs for the method.
            new JavaVisitor<Set<JavaType.FullyQualified>>() {
                @Override
                protected JavadocVisitor<Set<JavaType.FullyQualified>> getJavadocVisitor() {
                    return new JavadocVisitor<Set<JavaType.FullyQualified>>(this) {
                        @Override
                        public Javadoc visitThrows(Javadoc.Throws aThrows, Set<JavaType.FullyQualified> candidates) {
                            if (aThrows.getExceptionName() instanceof TypeTree) {
                                JavaType.FullyQualified exceptionType = TypeUtils.asFullyQualified(((TypeTree) aThrows.getExceptionName()).getType());
                                if (exceptionType != null) {
                                    candidates.remove(exceptionType);
                                }
                            }
                            return super.visitThrows(aThrows, candidates);
                        }
                    };
                }
            }.visit(method, candidates);
        }
        return candidates;
    }
}
