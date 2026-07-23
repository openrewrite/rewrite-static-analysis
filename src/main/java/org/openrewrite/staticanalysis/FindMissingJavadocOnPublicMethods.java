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

import lombok.EqualsAndHashCode;
import lombok.Value;
import org.jspecify.annotations.Nullable;
import org.openrewrite.ExecutionContext;
import org.openrewrite.Preconditions;
import org.openrewrite.Recipe;
import org.openrewrite.TreeVisitor;
import org.openrewrite.java.JavaIsoVisitor;
import org.openrewrite.java.tree.*;
import org.openrewrite.marker.SearchResult;
import org.openrewrite.staticanalysis.groovy.GroovyFileChecker;
import org.openrewrite.staticanalysis.java.JavaFileChecker;
import org.openrewrite.staticanalysis.kotlin.KotlinFileChecker;
import org.openrewrite.staticanalysis.table.MissingJavadocOnPublicMethods;

import java.util.List;

import static java.util.stream.Collectors.joining;

@Value
@EqualsAndHashCode(callSuper = false)
public class FindMissingJavadocOnPublicMethods extends Recipe {

    transient MissingJavadocOnPublicMethods report = new MissingJavadocOnPublicMethods(this);

    @Override
    public String getDisplayName() {
        return "Find public methods missing Javadoc";
    }

    @Override
    public String getDescription() {
        return "Locates `public` method declarations that are not documented with a Javadoc comment, " +
               "marks them with a search result, and records them in a data table.";
    }

    @Override
    public TreeVisitor<?, ExecutionContext> getVisitor() {
        return Preconditions.check(Preconditions.or(
                new JavaFileChecker<>(),
                new GroovyFileChecker<>(),
                new KotlinFileChecker<>()
        ), new JavaIsoVisitor<ExecutionContext>() {
            @Override
            public J.MethodDeclaration visitMethodDeclaration(J.MethodDeclaration method, ExecutionContext ctx) {
                J.MethodDeclaration md = super.visitMethodDeclaration(method, ctx);

                J.ClassDeclaration enclosing = getCursor().firstEnclosing(J.ClassDeclaration.class);
                JavaSourceFile sourceFile = getCursor().firstEnclosing(JavaSourceFile.class);
                if (enclosing == null || !isPublic(md, enclosing, sourceFile) || hasJavadoc(md)) {
                    return md;
                }

                JavaType.Method methodType = md.getMethodType();
                String className = methodType != null ?
                        methodType.getDeclaringType().getFullyQualifiedName() :
                        enclosing.getType() != null ? enclosing.getType().getFullyQualifiedName() : "";

                String parameterTypes;
                if (methodType != null) {
                    parameterTypes = methodType.getParameterTypes().stream()
                            .map(String::valueOf)
                            .collect(joining(", "));
                } else {
                    parameterTypes = md.getParameters().stream()
                            .filter(J.VariableDeclarations.class::isInstance)
                            .map(J.VariableDeclarations.class::cast)
                            .map(v -> String.valueOf(v.getType()))
                            .collect(joining(", "));
                }

                report.insertRow(ctx, new MissingJavadocOnPublicMethods.Row(
                        sourceFile == null ? "" : sourceFile.getSourcePath().toString(),
                        className,
                        md.getSimpleName(),
                        parameterTypes
                ));

                return md.withName(SearchResult.found(md.getName()));
            }

            private boolean isPublic(J.MethodDeclaration md, J.ClassDeclaration enclosing, @Nullable JavaSourceFile sourceFile) {
                if (md.hasModifier(J.Modifier.Type.Public)) {
                    return true;
                }
                // Any explicit non-public visibility rules the method out in every language
                for (J.Modifier modifier : md.getModifiers()) {
                    if (modifier.getType() == J.Modifier.Type.Private || modifier.getType() == J.Modifier.Type.Protected ||
                        (modifier.getType() == J.Modifier.Type.LanguageExtension && "internal".equals(modifier.getKeyword()))) {
                        return false;
                    }
                }
                // With no explicit visibility, Groovy and Kotlin methods are public by default,
                // while in Java only interface methods are implicitly public.
                return defaultsToPublic(sourceFile) || enclosing.getKind() == J.ClassDeclaration.Kind.Type.Interface;
            }

            private boolean defaultsToPublic(@Nullable JavaSourceFile sourceFile) {
                if (sourceFile == null) {
                    return false;
                }
                // Detect Groovy/Kotlin by compilation unit type without a hard dependency on those modules
                String cuType = sourceFile.getClass().getName();
                return cuType.startsWith("org.openrewrite.groovy.") || cuType.startsWith("org.openrewrite.kotlin.");
            }

            private boolean hasJavadoc(J.MethodDeclaration md) {
                if (isJavadoc(md.getComments())) {
                    return true;
                }
                // A Javadoc comment preceding annotations may attach to the first leading annotation
                for (J.Annotation annotation : md.getLeadingAnnotations()) {
                    if (isJavadoc(annotation.getComments())) {
                        return true;
                    }
                }
                return false;
            }

            private boolean isJavadoc(List<Comment> comments) {
                return comments.stream().anyMatch(c -> c instanceof Javadoc.DocComment);
            }
        });
    }
}
