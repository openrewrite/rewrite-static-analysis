/*
 * Copyright 2024 the original author or authors.
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
package org.openrewrite.staticanalysis.java;

import lombok.EqualsAndHashCode;
import lombok.Value;
import org.jspecify.annotations.Nullable;
import org.openrewrite.*;
import org.openrewrite.internal.ListUtils;
import org.openrewrite.internal.StringUtils;
import org.openrewrite.java.JavaIsoVisitor;
import org.openrewrite.java.search.UsesType;
import org.openrewrite.java.tree.*;

import java.util.ArrayList;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;
import java.util.regex.Pattern;

@Value
@EqualsAndHashCode(callSuper = false)
public class MoveFieldAnnotationToType extends Recipe {

    @Option(displayName = "Annotation type",
            description = "The type of annotation to move.",
            example = "org.openrewrite..*",
            required = false)
    @Nullable
    String annotationType;

    @Override
    public String getDisplayName() {
        return "Move annotation to type instead of field";
    }

    @Override
    public String getDescription() {
        return "Annotations that could be applied to either a field or a " +
               "type are better applied to the type, because similar annotations " +
               "may be more restrictive, leading to compile errors like " +
               "'scoping construct cannot be annotated with type-use annotation' " +
               "when migrating later.";
    }

    @Override
    public TreeVisitor<?, ExecutionContext> getVisitor() {
        String annotationTypeInput = annotationType == null ? "org.openrewrite..*" : annotationType;

        return Preconditions.check(new UsesType<>(annotationTypeInput, null), new JavaIsoVisitor<ExecutionContext>() {
            final Pattern typePattern = Pattern.compile(StringUtils.aspectjNameToPattern(annotationTypeInput));

            @Override
            public J.AnnotatedType visitAnnotatedType(J.AnnotatedType annotatedType, ExecutionContext ctx) {
                J.AnnotatedType at = super.visitAnnotatedType(annotatedType, ctx);

                if (isQualifiedClass(at.getTypeExpression())) {
                    AtomicReference<J.Annotation> matchingAnnotation = new AtomicReference<>();
                    at = at.withAnnotations(ListUtils.map(at.getAnnotations(), a -> {
                        if (matchesType(a)) {
                            matchingAnnotation.set(a);
                            return null;
                        }
                        return a;
                    }));
                    if (at.getTypeExpression() != null && matchingAnnotation.get() != null) {
                        TypeTree te = annotateInnerClass(at.getTypeExpression(), matchingAnnotation.get());
                        at = at.withTypeExpression(te);
                        // auto format should handle this, but evidently doesn't
                        if (at.getAnnotations().isEmpty() && !(getCursor().getParentTreeCursor().getValue() instanceof J.MethodDeclaration)) {
                            at = at.withTypeExpression(te.withPrefix(te.getPrefix().withWhitespace("")));
                        }
                        at = autoFormat(at, at.getTypeExpression(), ctx, getCursor().getParentOrThrow());
                    }
                }
                return at;
            }

            @Override
            public J.VariableDeclarations visitVariableDeclarations(J.VariableDeclarations multiVariable, ExecutionContext ctx) {
                J.VariableDeclarations mv = super.visitVariableDeclarations(multiVariable, ctx);

                if (isQualifiedClass(mv.getTypeExpression())) {
                    AtomicReference<J.Annotation> matchingAnnotation = new AtomicReference<>();
                    mv = mv.withLeadingAnnotations(ListUtils.map(mv.getLeadingAnnotations(), a -> {
                        if (matchesType(a)) {
                            matchingAnnotation.set(a);
                            return null;
                        }
                        return a;
                    }));
                    if (mv.getTypeExpression() != null && matchingAnnotation.get() != null) {
                        TypeTree te = annotateInnerClass(mv.getTypeExpression(), matchingAnnotation.get());
                        mv = mv.withTypeExpression(te);
                        // auto format should handle this, but evidently doesn't
                        if (mv.getLeadingAnnotations().isEmpty()) {
                            mv = mv.withTypeExpression(te.withPrefix(te.getPrefix().withWhitespace("")));
                        }
                        mv = autoFormat(mv, mv.getTypeExpression(), ctx, getCursor().getParentOrThrow());
                    }
                }
                return mv;
            }

            @Override
            public J.MethodDeclaration visitMethodDeclaration(J.MethodDeclaration method, ExecutionContext ctx) {
                J.MethodDeclaration md = super.visitMethodDeclaration(method, ctx);

                if (isQualifiedClass(md.getReturnTypeExpression())) {
                    AtomicReference<J.Annotation> matchingAnnotation = new AtomicReference<>();
                    md = md.withLeadingAnnotations(ListUtils.map(md.getLeadingAnnotations(), a -> {
                        if (matchesType(a)) {
                            matchingAnnotation.set(a);
                            return null;
                        }
                        return a;
                    }));
                    if (md.getReturnTypeExpression() != null && matchingAnnotation.get() != null) {
                        TypeTree te = annotateInnerClass(md.getReturnTypeExpression(), matchingAnnotation.get());
                        md = md.withReturnTypeExpression(te);
                        // auto format should handle this, but evidently doesn't
                        if (md.getLeadingAnnotations().isEmpty()) {
                            md = md.withReturnTypeExpression(te.withPrefix(te.getPrefix().withWhitespace("")));
                        }
                        md = autoFormat(md, md.getReturnTypeExpression(), ctx, getCursor().getParentOrThrow());
                    }
                }
                return md;
            }

            private boolean matchesType(J.Annotation ann) {
                JavaType.FullyQualified fq = TypeUtils.asFullyQualified(ann.getType());
                return fq != null && typePattern.matcher(fq.getFullyQualifiedName()).matches();
            }

            private boolean isQualifiedClass(@Nullable TypeTree tree) {
                return tree instanceof J.FieldAccess ||
                       (tree instanceof J.ParameterizedType && ((J.ParameterizedType) tree).getClazz() instanceof J.FieldAccess) ||
                       tree instanceof J.ArrayType;
            }

            private TypeTree annotateInnerClass(TypeTree qualifiedClassRef, J.Annotation annotation) {
                J.Annotation usedAnnotation = annotation;
                if (annotation.getAnnotationType() instanceof J.FieldAccess) {
                    usedAnnotation = usedAnnotation.withAnnotationType(
                            new J.Identifier(
                                    UUID.randomUUID(),
                                    annotation.getAnnotationType().getPrefix(),
                                    annotation.getAnnotationType().getMarkers(),
                                    new ArrayList<>(),
                                    annotation.getSimpleName(),
                                    annotation.getType(),
                                    null
                            ));
                }
                if (qualifiedClassRef instanceof J.FieldAccess) {
                    J.FieldAccess q = (J.FieldAccess) qualifiedClassRef;
                    q = q.withName(q.getName().withAnnotations(
                            ListUtils.concat(usedAnnotation.withPrefix(Space.EMPTY), q.getName().getAnnotations())));
                    if (q.getName().getPrefix().getWhitespace().isEmpty()) {
                        q = q.withName(q.getName().withPrefix(q.getName().getPrefix().withWhitespace(" ")));
                    }
                    return q;
                } else if (qualifiedClassRef instanceof J.ParameterizedType &&
                           ((J.ParameterizedType) qualifiedClassRef).getClazz() instanceof TypeTree) {
                    J.ParameterizedType pt = (J.ParameterizedType) qualifiedClassRef;
                    return pt.withClazz(annotateInnerClass((TypeTree) pt.getClazz(), usedAnnotation));
                } else if (qualifiedClassRef instanceof J.ArrayType) {
                    J.ArrayType at = (J.ArrayType) qualifiedClassRef;
                    at = at.withAnnotations(ListUtils.concat(annotation.withPrefix(Space.SINGLE_SPACE), at.getAnnotations()));
                    return at;
                }
                return qualifiedClassRef;
            }
        });
    }
}
