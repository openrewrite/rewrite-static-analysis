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

import lombok.RequiredArgsConstructor;
import org.jspecify.annotations.Nullable;
import org.openrewrite.Cursor;
import org.openrewrite.SourceFile;
import org.openrewrite.Tree;
import org.openrewrite.java.*;
import org.openrewrite.java.service.AnnotationService;
import org.openrewrite.java.style.Checkstyle;
import org.openrewrite.java.style.HideUtilityClassConstructorStyle;
import org.openrewrite.java.tree.J;
import org.openrewrite.java.tree.JavaType;
import org.openrewrite.java.tree.Statement;
import org.openrewrite.style.Style;

import java.util.ArrayList;
import java.util.Collection;
import java.util.EnumSet;

public class HideUtilityClassConstructorVisitor<P> extends JavaIsoVisitor<P> {

    private static final EnumSet<J.ClassDeclaration.Kind.Type> EXCLUDE_CLASS_TYPES =
            EnumSet.of(J.ClassDeclaration.Kind.Type.Interface, J.ClassDeclaration.Kind.Type.Record);

    private UtilityClassMatcher utilityClassMatcher;
    private HideUtilityClassConstructorStyle style;

    @Override
    public @Nullable J visit(@Nullable Tree tree, P p) {
        if (style == null && tree instanceof SourceFile) {
            style = Style.from(HideUtilityClassConstructorStyle.class, (SourceFile)tree, Checkstyle::hideUtilityClassConstructorStyle);
            utilityClassMatcher = new UtilityClassMatcher(style.getIgnoreIfAnnotatedBy());
        }
        return super.visit(tree, p);
    }

    @Override
    public J.ClassDeclaration visitClassDeclaration(J.ClassDeclaration classDecl, P p) {
        J.ClassDeclaration c = super.visitClassDeclaration(classDecl, p);
        if (!EXCLUDE_CLASS_TYPES.contains(c.getKind()) && !c.hasModifier(J.Modifier.Type.Abstract) && utilityClassMatcher.isRefactorableUtilityClass(getCursor())) {
            c = (J.ClassDeclaration) new UtilityClassWithImplicitDefaultConstructorVisitor().visit(c, p, getCursor().getParentOrThrow());
            c = (J.ClassDeclaration) new UtilityClassWithExposedConstructorInspectionVisitor<>(c).visit(c, p, getCursor().getParentOrThrow());
        }
        return c;
    }

    /**
     * Adds an empty private constructor if the class has zero explicit constructors. This hides the default implicit constructor.
     */
    private class UtilityClassWithImplicitDefaultConstructorVisitor extends JavaIsoVisitor<P> {

        @Override
        public J.ClassDeclaration visitClassDeclaration(J.ClassDeclaration classDecl, P p) {
            if (utilityClassMatcher.hasImplicitDefaultConstructor(classDecl) &&
                    J.ClassDeclaration.Kind.Type.Enum != classDecl.getKind()) {
                classDecl = JavaTemplate.builder("private #{}() {}")
                        .contextSensitive()
                        .build()
                        .apply(getCursor(), classDecl.getBody().getCoordinates().lastStatement(), classDecl.getSimpleName());
            }
            return classDecl;
        }
    }

    /**
     * We consider a Utility Class to have an "exposed" constructor if the constructor is Public or Package-Private.
     * The constructor may be "Protected" in cases where it's desirable to subclass the Utility Class.
     */
    @RequiredArgsConstructor
    private static class UtilityClassWithExposedConstructorInspectionVisitor<P> extends JavaIsoVisitor<P> {
        private final J.ClassDeclaration utilityClass;

        @Override
        public J.ClassDeclaration visitClassDeclaration(J.ClassDeclaration classDecl, P p) {
            return classDecl == utilityClass ? super.visitClassDeclaration(classDecl, p) : classDecl;
        }

        @Override
        public J.MethodDeclaration visitMethodDeclaration(J.MethodDeclaration method, P p) {
            J.MethodDeclaration md = super.visitMethodDeclaration(method, p);
            if (md.getMethodType() == null || !md.isConstructor() ||
                    (md.hasModifier(J.Modifier.Type.Private) || md.hasModifier(J.Modifier.Type.Protected) || md.getMethodType().getDeclaringType().getKind() == JavaType.Class.Kind.Enum)) {
                return md;
            }

            ChangeMethodAccessLevelVisitor<P> changeMethodAccessLevelVisitor = new ChangeMethodAccessLevelVisitor<>(
                    new MethodMatcher(method),
                    J.Modifier.Type.Private
            );
            md = (J.MethodDeclaration) changeMethodAccessLevelVisitor.visit(md, p, getCursor().getParentOrThrow());
            assert md != null;
            return md;
        }
    }

    private final class UtilityClassMatcher {
        private final AnnotationMatcher configurationAnnotation = new AnnotationMatcher("@org.springframework.context.annotation.Configuration", true);
        private final Collection<AnnotationMatcher> ignorableAnnotations;

        private UtilityClassMatcher(Collection<String> ignorableAnnotations) {
            this.ignorableAnnotations = new ArrayList<>(ignorableAnnotations.size());
            for (String ignorableAnnotation : ignorableAnnotations) {
                this.ignorableAnnotations.add(new AnnotationMatcher(ignorableAnnotation));
            }
        }

        boolean hasIgnorableAnnotation(Cursor cursor) {
            AnnotationService service = service(AnnotationService.class);
            for (AnnotationMatcher ignorableAnn : ignorableAnnotations) {
                if (service.matches(cursor, ignorableAnn)) {
                    return true;
                }
            }
            return false;
        }

        boolean hasMainMethod(J.ClassDeclaration c) {
            if (c.getType() == null) {
                return false;
            }
            for (Statement statement : c.getBody().getStatements()) {
                if (statement instanceof J.MethodDeclaration) {
                    J.MethodDeclaration md = (J.MethodDeclaration) statement;
                    if (!md.isConstructor() &&
                            md.hasModifier(J.Modifier.Type.Public) &&
                            md.hasModifier(J.Modifier.Type.Static) &&
                            md.getReturnTypeExpression() != null &&
                            JavaType.Primitive.Void == md.getReturnTypeExpression().getType() &&

                            // note that the matcher for "main(String)" will match on "main(String[]) as expected.
                            new MethodMatcher(c.getType().getFullyQualifiedName() + " main(String[])")
                                    .matches(md, c)) {
                        return true;
                    }
                }
            }
            return false;
        }

        /**
         * If a Class Declaration has zero constructors, it uses the Implicit Default Constructor.
         * <p>
         * Keep in mind, a Class Declaration can have Explicit Default Constructors if the Constructor is declared
         * without any Access Modifier such as Public, Private, or Protected.
         *
         * @return true if there are zero explicit constructors, meaning the Class has an implicit default constructor.
         */
        boolean hasImplicitDefaultConstructor(J.ClassDeclaration c) {
            for (Statement statement : c.getBody().getStatements()) {
                if (statement instanceof J.MethodDeclaration) {
                    J.MethodDeclaration methodDeclaration = (J.MethodDeclaration) statement;
                    if (methodDeclaration.isConstructor()) {
                        return false;
                    }
                }
            }
            return true;
        }

        boolean isRefactorableUtilityClass(Cursor cursor) {
            J.ClassDeclaration c = cursor.getValue();
            return isUtilityClass(c) &&
                    !hasIgnorableAnnotation(cursor) &&
                    !hasConfigurationAnnotation(c) &&
                    !hasMainMethod(c);
        }

        private boolean hasConfigurationAnnotation(J.ClassDeclaration c) {
            return c.getLeadingAnnotations().stream().anyMatch(configurationAnnotation::matches);
        }

        boolean isUtilityClass(J.ClassDeclaration c) {
            if (c.getImplements() != null || c.getExtends() != null) {
                return false;
            }

            int staticMethodCount = countStaticMethods(c);
            if (staticMethodCount < 0) {
                return false;
            }

            int staticFieldCount = countStaticFields(c);
            if (staticFieldCount < 0) {
                return false;
            }

            return staticMethodCount != 0 || staticFieldCount != 0;
        }

        /**
         * @return -1 if a non-static field is found, else the count of non-private static fields.
         */
        private int countStaticFields(J.ClassDeclaration classDeclaration) {
            int count = 0;

            for (Statement statement : classDeclaration.getBody().getStatements()) {
                if (!(statement instanceof J.VariableDeclarations)) {
                    continue;
                }
                J.VariableDeclarations field = (J.VariableDeclarations) statement;
                if (!field.hasModifier(J.Modifier.Type.Static)) {
                    return -1;
                }
                if (field.hasModifier(J.Modifier.Type.Private)) {
                    continue;
                }
                count++;
            }

            return count;
        }

        /**
         * @return -1 if a non-static method is found, else the count of non-private static methods.
         */
        private int countStaticMethods(J.ClassDeclaration classDeclaration) {
            int count = 0;
            for (Statement statement : classDeclaration.getBody().getStatements()) {
                if (!(statement instanceof J.MethodDeclaration)) {
                    continue;
                }

                J.MethodDeclaration method = (J.MethodDeclaration) statement;
                if (method.isConstructor()) {
                    continue;
                }
                if (!method.hasModifier(J.Modifier.Type.Static)) {
                    return -1;
                }
                if (method.hasModifier(J.Modifier.Type.Private)) {
                    continue;
                }
                count++;
            }

            return count;
        }
    }
}
