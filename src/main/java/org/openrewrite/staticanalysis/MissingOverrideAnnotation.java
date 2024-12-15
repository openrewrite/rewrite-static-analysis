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

import lombok.EqualsAndHashCode;
import lombok.Value;
import org.jspecify.annotations.Nullable;
import org.openrewrite.*;
import org.openrewrite.java.AnnotationMatcher;
import org.openrewrite.java.JavaIsoVisitor;
import org.openrewrite.java.JavaTemplate;
import org.openrewrite.java.service.AnnotationService;
import org.openrewrite.java.tree.J;
import org.openrewrite.java.tree.TypeUtils;
import org.openrewrite.kotlin.tree.K;

import java.time.Duration;
import java.util.Collections;
import java.util.Comparator;
import java.util.Set;

@Value
@EqualsAndHashCode(callSuper = false)
public class MissingOverrideAnnotation extends Recipe {
    @Option(displayName = "Ignore methods in anonymous classes",
            description = "When enabled, ignore missing annotations on methods which override methods when the class definition is within an anonymous class.",
            required = false)
    @Nullable
    Boolean ignoreAnonymousClassMethods;

    @Override
    public String getDisplayName() {
        return "Add missing `@Override` to overriding and implementing methods";
    }

    @Override
    public String getDescription() {
        return "Adds `@Override` to methods overriding superclass methods or implementing interface methods. " +
                "Annotating methods improves readability by showing the author's intent to override. " +
                "Additionally, when annotated, the compiler will emit an error when a signature of the overridden method does not match the superclass method.";
    }

    @Override
    public Set<String> getTags() {
        return Collections.singleton("RSPEC-S1161");
    }

    @Override
    public Duration getEstimatedEffortPerOccurrence() {
        return Duration.ofMinutes(5);
    }

    @Override
    public TreeVisitor<?, ExecutionContext> getVisitor() {
        return new MissingOverrideAnnotationVisitor();
    }

    private class MissingOverrideAnnotationVisitor extends JavaIsoVisitor<ExecutionContext> {
        private final AnnotationMatcher OVERRIDE_ANNOTATION = new AnnotationMatcher("@java.lang.Override");

        @Override
        public boolean isAcceptable(SourceFile sourceFile, ExecutionContext ctx) {
            // Kotlin has a dedicated `override` keyword which is enforced by the compiler
            return super.isAcceptable(sourceFile, ctx) && !(sourceFile instanceof K.CompilationUnit);
        }

        private Cursor getCursorToParentScope(Cursor cursor) {
            return cursor.dropParentUntil(is -> is instanceof J.NewClass || is instanceof J.ClassDeclaration);
        }

        @Override
        public J.MethodDeclaration visitMethodDeclaration(J.MethodDeclaration method, ExecutionContext ctx) {
            if (!method.hasModifier(J.Modifier.Type.Static) &&
                    !method.isConstructor() &&
                    !service(AnnotationService.class).matches(getCursor(), OVERRIDE_ANNOTATION) &&
                    TypeUtils.isOverride(method.getMethodType()) &&
                    !(Boolean.TRUE.equals(ignoreAnonymousClassMethods) &&
                    getCursorToParentScope(getCursor()).getValue() instanceof J.NewClass)) {

                method = JavaTemplate.apply("@Override", getCursor(), method.getCoordinates().addAnnotation(Comparator.comparing(J.Annotation::getSimpleName)));
            }
            return super.visitMethodDeclaration(method, ctx);
        }
    }
}
