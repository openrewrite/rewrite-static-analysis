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
import org.openrewrite.java.AnnotationMatcher;
import org.openrewrite.java.JavaIsoVisitor;
import org.openrewrite.java.JavaTemplate;
import org.openrewrite.java.MethodMatcher;
import org.openrewrite.java.service.AnnotationService;
import org.openrewrite.java.tree.J;
import org.openrewrite.java.tree.JavaType;

import java.time.Duration;
import java.util.Collections;
import java.util.Comparator;
import java.util.Set;
import java.util.stream.Stream;

@Incubating(since = "7.0.0")
public class CovariantEquals extends Recipe {

    @Override
    public String getDisplayName() {
        return "Covariant equals";
    }

    @Override
    public String getDescription() {
        return "Checks that classes and records which define a covariant `equals()` method also override method `equals(Object)`. " +
               "Covariant `equals()` means a method that is similar to `equals(Object)`, but with a covariant parameter type (any subtype of `Object`).";
    }

    @Override
    public Set<String> getTags() {
        return Collections.singleton("RSPEC-S2162");
    }

    @Override
    public Duration getEstimatedEffortPerOccurrence() {
        return Duration.ofMinutes(5);
    }

    @Override
    public TreeVisitor<?, ExecutionContext> getVisitor() {
        MethodMatcher objectEquals = new MethodMatcher("* equals(java.lang.Object)");
        return new JavaIsoVisitor<ExecutionContext>() {

            @Override
            public J.ClassDeclaration visitClassDeclaration(J.ClassDeclaration classDecl, ExecutionContext ctx) {
                J.ClassDeclaration cd = super.visitClassDeclaration(classDecl, ctx);
                Stream<J.MethodDeclaration> mds = cd.getBody().getStatements().stream()
                        .filter(J.MethodDeclaration.class::isInstance)
                        .map(J.MethodDeclaration.class::cast);
                if (cd.getKind() != J.ClassDeclaration.Kind.Type.Interface && mds.noneMatch(m -> objectEquals.matches(m, classDecl))) {
                    cd = (J.ClassDeclaration) new ChangeCovariantEqualsMethodVisitor(cd).visit(cd, ctx, getCursor().getParentOrThrow());
                    assert cd != null;
                }
                return cd;
            }

            class ChangeCovariantEqualsMethodVisitor extends JavaIsoVisitor<ExecutionContext> {
                private final AnnotationMatcher OVERRIDE_ANNOTATION = new AnnotationMatcher("@java.lang.Override");

                private final J.ClassDeclaration enclosingClass;

                public ChangeCovariantEqualsMethodVisitor(J.ClassDeclaration enclosingClass) {
                    this.enclosingClass = enclosingClass;
                }

                @Override
                public J.MethodDeclaration visitMethodDeclaration(J.MethodDeclaration method, ExecutionContext ctx) {
                    J.MethodDeclaration m = super.visitMethodDeclaration(method, ctx);
                    updateCursor(m);

                    /*
                     * Looking for "public boolean equals(EnclosingClassType)" as the method signature match.
                     * We'll replace it with "public boolean equals(Object)"
                     */
                    JavaType.FullyQualified type = enclosingClass.getType();
                    if (type == null || type instanceof JavaType.Unknown) {
                        return m;
                    }

                    String ecfqn = type.getFullyQualifiedName();
                    if (m.hasModifier(J.Modifier.Type.Public) &&
                        m.getReturnTypeExpression() != null &&
                        JavaType.Primitive.Boolean.equals(m.getReturnTypeExpression().getType()) &&
                        new MethodMatcher(ecfqn + " equals(" + ecfqn + ")").matches(m, enclosingClass)) {

                        if (!service(AnnotationService.class).matches(getCursor(), OVERRIDE_ANNOTATION)) {
                            m = JavaTemplate.builder("@Override").build()
                                    .apply(updateCursor(m),
                                            m.getCoordinates().addAnnotation(Comparator.comparing(J.Annotation::getSimpleName)));
                        }

                        /*
                         * Change parameter type to Object, and maybe change input parameter name representing the other object.
                         * This is because we prepend these type-checking replacement statements to the existing "equals(..)" body.
                         * Therefore we don't want to collide with any existing variable names.
                         */
                        J.VariableDeclarations.NamedVariable oldParamName = ((J.VariableDeclarations) m.getParameters().get(0)).getVariables().get(0);
                        String paramName = "obj".equals(oldParamName.getSimpleName()) ? "other" : "obj";
                        m = JavaTemplate.builder("Object #{}").build()
                                .apply(updateCursor(m),
                                        m.getCoordinates().replaceParameters(),
                                        paramName);

                        /*
                         * We'll prepend this type-check and type-cast to the beginning of the existing
                         * equals(..) method body statements, and let the existing equals(..) method definition continue
                         * with the logic doing what it was doing.
                         */
                        String equalsBodyPrefixTemplate = "if (#{} == this) return true;\n" +
                                                          "if (#{} == null || getClass() != #{}.getClass()) return false;\n" +
                                                          "#{} #{} = (#{}) #{};\n";
                        JavaTemplate equalsBodySnippet = JavaTemplate.builder(equalsBodyPrefixTemplate).contextSensitive().build();

                        assert m.getBody() != null;
                        Object[] params = new Object[]{
                                paramName,
                                paramName,
                                paramName,
                                enclosingClass.getSimpleName(),
                                oldParamName.getSimpleName(),
                                enclosingClass.getSimpleName(),
                                paramName
                        };

                        m = equalsBodySnippet.apply(new Cursor(getCursor().getParent(), m),
                                m.getBody().getStatements().get(0).getCoordinates().before(),
                                params);
                    }

                    return m;
                }
            }
        };
    }
}
