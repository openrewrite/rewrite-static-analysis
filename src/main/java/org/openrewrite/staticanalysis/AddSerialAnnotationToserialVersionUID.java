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
package org.openrewrite.staticanalysis;

import org.jetbrains.annotations.NotNull;
import org.openrewrite.ExecutionContext;
import org.openrewrite.Recipe;
import org.openrewrite.TreeVisitor;
import org.openrewrite.internal.ListUtils;
import org.openrewrite.java.JavaIsoVisitor;
import org.openrewrite.java.JavaTemplate;
import org.openrewrite.java.TreeVisitingPrinter;
import org.openrewrite.java.tree.J;
import org.openrewrite.java.tree.JavaType;
import org.openrewrite.java.tree.Statement;
import org.openrewrite.java.tree.TypeUtils;

import java.time.Duration;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.UnaryOperator;

public class AddSerialAnnotationToserialVersionUID extends Recipe {



    @Override
    @NotNull
    public String getDisplayName() {
        return "Add @Serial annotation to serialVersionUID";
    }

    @Override
    @NotNull
    public String getDescription() {
        return "Add a @Serial annotation above a serialVersionUID attribute identifier line.";
    }

    @Override
    @NotNull
    public Duration getEstimatedEffortPerOccurrence() {
        return Duration.ofMinutes(1);
    }

    @Override
    @NotNull
    public TreeVisitor<?, ExecutionContext> getVisitor() {
        return new JavaIsoVisitor<ExecutionContext>() {

            @Override
            @NotNull
            public J.MethodDeclaration visitMethodDeclaration(J.@NotNull MethodDeclaration method, @NotNull ExecutionContext ctx) {
                // Anonymous classes are not of interest
                System.out.println("Entering visitMethodDeclaration Anonymous classes are not of interest");
                return method;
            }

            @Override
            @NotNull
            public J.VariableDeclarations visitVariableDeclarations(J.@NotNull VariableDeclarations multiVariable, @NotNull ExecutionContext ctx) {
                // Anonymous classes are not of interest
                System.out.println("Entering visitVariableDeclarations (plural with an s) Anonymous classes are not of interest");
                return multiVariable;
            }

            @Override
            @NotNull
            public J.ClassDeclaration visitClassDeclaration(J.@NotNull ClassDeclaration classDecl, @NotNull ExecutionContext ctx) {
                System.out.println("\n\nEntering visitClassDeclaration");
                System.out.println(TreeVisitingPrinter.printTree(getCursor()));

                System.out.println("BEFORE calling 'super.visitClassDeclaration'");
                J.ClassDeclaration c = super.visitClassDeclaration(classDecl, ctx);
                System.out.println("AFTER calling 'super.visitClassDeclaration'");
                if (c.getKind() != J.ClassDeclaration.Kind.Type.Class) {
                    return c;
                }

                AtomicBoolean needsSerialAnnotation = new AtomicBoolean(false);
                c = c.withBody(c.getBody().withStatements(ListUtils.map(c.getBody().getStatements(), new UnaryOperator<Statement>() {
                    @Override
                    public Statement apply(Statement s) {
                        if (!(s instanceof J.VariableDeclarations)) {
                            return s;
                        }
                        J.VariableDeclarations varDecls = (J.VariableDeclarations) s;
                        // Yes I know deprecated: varDecls.getAllAnnotations()
                        List<J.Annotation> allAnnotations = varDecls.getAllAnnotations();
                        long count = allAnnotations.stream().count();
                        System.out.println("Nr of annotations: " + count);

                        AtomicBoolean hasSerialAnnotation = new AtomicBoolean(false);
                        for (J.Annotation annotation : allAnnotations) {
                            String simpleName = annotation.getSimpleName();
                            System.out.println("Annotation name: " + simpleName);
                            if (simpleName.equals("Serial")) {
                                hasSerialAnnotation.set(true);
                            }
                        }


                        for (J.VariableDeclarations.NamedVariable v : varDecls.getVariables()) {
                            System.out.println("Variable: " + v.getSimpleName());
                            if ("serialVersionUID".equals(v.getSimpleName())) {

                                JavaType type = v.getType();

                                if (type instanceof JavaType.Primitive) {
                                    if (TypeUtils.asPrimitive(v.getType()) == JavaType.Primitive.Long) {
                                        if (hasSerialAnnotation.get()) {
                                            System.out.println("Found serialVersionUID WITH @Serial annotation");
                                            needsSerialAnnotation.set(false);
                                        } else {
                                            System.out.println("Found serialVersionUID");
                                            needsSerialAnnotation.set(true);
                                        }
                                        return s;
                                    }
                                }
                            }
                        }
                        return s;
                    }
                })));
                if (needsSerialAnnotation.get()) {
                    System.out.println("needsSerialAnnotation TRUE: " + needsSerialAnnotation.get());
                    c = JavaTemplate.apply("@Serial", getCursor(), c.getCoordinates().addAnnotation(Comparator.comparing(J.Annotation::getSimpleName)));
                    // It HAS to be added. This method seems to be the easiest way to do it. Does NOT work
                    maybeAddImport("java.io.Serial");
                } else {
                    System.out.println("needsSerialAnnotation FALSE: " + needsSerialAnnotation.get());
                }
                System.out.println("I believe this is the very end of all processing\n\n");
                return c;
            }
        };
    }
}
