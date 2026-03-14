/*
 * Copyright 2025 the original author or authors.
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
import org.openrewrite.java.JavaVisitor;
import org.openrewrite.staticanalysis.kotlin.KotlinFileChecker;
import org.openrewrite.java.tree.*;
import org.openrewrite.java.tree.J.FieldAccess;
import org.openrewrite.java.tree.J.Identifier;
import org.openrewrite.java.tree.JavaType.Method;
import org.openrewrite.marker.Markers;

import java.time.Duration;

import static java.util.Collections.emptyList;

@Value
@EqualsAndHashCode(callSuper = false)
public class ExplicitThis extends Recipe {

    @Override
    public String getDisplayName() {
        return "`field` → `this.field`";
    }

    @Override
    public String getDescription() {
        return "Add explicit 'this.' prefix to field and method access.";
    }

    @Override
    public Duration getEstimatedEffortPerOccurrence() {
        return Duration.ofSeconds(5);
    }

    @Override
    public TreeVisitor<?, ExecutionContext> getVisitor() {
        return Preconditions.check(Preconditions.not(new KotlinFileChecker<>()), new ExplicitThisVisitor());
    }

    private static final class ExplicitThisVisitor extends JavaVisitor<ExecutionContext> {

        private boolean isStatic;
        private boolean isInsideFieldAccess;

        private static class ClassContext {
            final JavaType.FullyQualified type;
            final boolean isAnonymous;

            ClassContext(JavaType.FullyQualified type, boolean isAnonymous) {
                this.type = type;
                this.isAnonymous = isAnonymous;
            }
        }

        @Override
        public J visitFieldAccess(FieldAccess fieldAccess, ExecutionContext ctx) {
            boolean previousIsInsideFieldAccess = this.isInsideFieldAccess;
            this.isInsideFieldAccess = true;

            J result = super.visitFieldAccess(fieldAccess, ctx);

            this.isInsideFieldAccess = previousIsInsideFieldAccess;
            return result;
        }

        @Override
        public J visitIdentifier(J.Identifier identifier, ExecutionContext ctx) {
            J.Identifier id = (J.Identifier) super.visitIdentifier(identifier, ctx);

            if (this.isStatic) {
                return id;
            }

            if (this.isInsideFieldAccess) {
                return id;
            }

            JavaType.Variable fieldType = id.getFieldType();
            if (fieldType == null) {
                return id;
            }

            if (!(fieldType.getOwner() instanceof JavaType.FullyQualified)) {
                return id;
            }

            if (fieldType.hasFlags(Flag.Static)) {
                return id;
            }

            String name = id.getSimpleName();
            if ("this".equals(name) || "super".equals(name)) {
                return id;
            }

            if (this.isPartOfDeclaration()) {
                return id;
            }

            J.FieldAccess fieldAccess = this.createFieldAccess(id, (JavaType.FullyQualified) fieldType.getOwner());
            return fieldAccess != null ? fieldAccess : id;
        }

        @Override
        public J visitBlock(J.Block block, ExecutionContext ctx) {
            if (!block.isStatic()) {
                return super.visitBlock(block, ctx);
            }

            boolean previousStatic = this.isStatic;
            this.isStatic = true;

            J.Block result = (J.Block) super.visitBlock(block, ctx);

            this.isStatic = previousStatic;
            return result;
        }

        @Override
        public J visitMethodDeclaration(J.MethodDeclaration method, ExecutionContext ctx) {
            boolean previousStatic = this.isStatic;

            if (method.hasModifier(J.Modifier.Type.Static)) {
                this.isStatic = true;
            }

            J.MethodDeclaration result = (J.MethodDeclaration) super.visitMethodDeclaration(method, ctx);

            this.isStatic = previousStatic;

            return result;
        }

        @Override
        public J visitMethodInvocation(J.MethodInvocation method, ExecutionContext ctx) {
            J.MethodInvocation m = (J.MethodInvocation) super.visitMethodInvocation(method, ctx);

            if (this.isStatic) {
                return m;
            }

            if (m.getName().getSimpleName().equals("super") || m.getName().getSimpleName().equals("this")) {
                return m;
            }

            Method methodType = m.getMethodType();
            if (
                    m.getSelect() != null ||
                            methodType == null ||
                            methodType.hasFlags(Flag.Static)
            ) {
                return m;
            }

            ClassContext currentContext = this.getCurrentClassContext();
            if (currentContext == null) {
                return m;
            }

            JavaType.FullyQualified methodOwnerType = methodType.getDeclaringType();
            Expression thisExpression = this.createQualifiedThisExpression(currentContext, methodOwnerType);
            if (thisExpression == null) {
                return m;
            }

            return m.withSelect(thisExpression);
        }

        private boolean isPartOfDeclaration() {
            Cursor parent = this.getCursor().getParent();
            if (parent == null || !(parent.getValue() instanceof J.VariableDeclarations.NamedVariable)) {
                return false;
            }
            J.VariableDeclarations.NamedVariable namedVar = parent.getValue();
            return namedVar.getName() == this.getCursor().getValue();
        }

        @Nullable
        private ClassContext getCurrentClassContext() {
            Cursor currentCursor = this.getCursor().dropParentUntil(p ->
                p instanceof J.ClassDeclaration ||
                (p instanceof J.NewClass && ((J.NewClass) p).getBody() != null) ||
                p == Cursor.ROOT_VALUE
            );

            if (currentCursor.getValue() instanceof J.ClassDeclaration) {
                J.ClassDeclaration currentClass = currentCursor.getValue();
                JavaType.FullyQualified currentClassType = currentClass.getType();
                if (currentClassType == null) {
                    return null;
                }
                String currentClassName = this.getSimpleClassName(currentClassType.getFullyQualifiedName());
                boolean currentIsAnonymous = this.isAnonymousClassName(currentClassName);
                return new ClassContext(currentClassType, currentIsAnonymous);
            } else if (currentCursor.getValue() instanceof J.NewClass) {
                J.NewClass newClass = currentCursor.getValue();
                JavaType type = newClass.getType();
                if (!(type instanceof JavaType.FullyQualified)) {
                    return null;
                }
                return new ClassContext((JavaType.FullyQualified) type, true);
            }
            return null;
        }

        @Nullable
        private Expression createQualifiedThisExpression(ClassContext currentContext, JavaType.FullyQualified targetType) {
            if (TypeUtils.isOfType(currentContext.type, targetType) ||
                    TypeUtils.isAssignableTo(targetType.getFullyQualifiedName(), currentContext.type)) {
                return JavaElementFactory.newThis(currentContext.type);
            }

            if (currentContext.isAnonymous) {
                String ownerClassName = this.getSimpleClassName(targetType.getFullyQualifiedName());
                if (this.isAnonymousClassName(ownerClassName)) {
                    return null;
                }
                return this.createOuterThisReference(targetType, ownerClassName);
            }

            String simpleClassName = this.getSimpleClassName(targetType.getFullyQualifiedName());
            return this.createOuterThisReference(targetType, simpleClassName);
        }

        private J.FieldAccess createOuterThisReference(JavaType.FullyQualified ownerType, String simpleClassName) {
            J.Identifier outerClassIdentifier = new J.Identifier(
                    Tree.randomId(), Space.EMPTY, Markers.EMPTY,
                    emptyList(), simpleClassName, ownerType, null);
            J.Identifier thisIdentifier = JavaElementFactory.newThis(ownerType);

            return new J.FieldAccess(
                    Tree.randomId(),
                    Space.EMPTY,
                    Markers.EMPTY,
                    outerClassIdentifier,
                    JLeftPadded.build(thisIdentifier),
                    null
            );
        }

        private J.@Nullable FieldAccess createFieldAccess(J.Identifier identifier, JavaType.FullyQualified fieldOwnerType) {
            ClassContext currentContext = this.getCurrentClassContext();
            if (currentContext == null) {
                return null;
            }

            Expression thisExpression = this.createQualifiedThisExpression(currentContext, fieldOwnerType);
            if (thisExpression == null) {
                return null;
            }

            return new J.FieldAccess(
                    Tree.randomId(),
                    identifier.getPrefix(),
                    Markers.EMPTY,
                    thisExpression,
                    JLeftPadded.build(identifier.withPrefix(Space.EMPTY)),
                    identifier.getFieldType()
            );
        }

        /**
         * Extracts the simple class name from a fully qualified class name.
         * Handles both package-separated names (dots) and inner class separators (dollar signs).
         * Examples: "com.example.Outer$Inner" -> "Inner", "com.example.Outer" -> "Outer"
         */
        private String getSimpleClassName(String fullyQualifiedName) {
            int lastDot = fullyQualifiedName.lastIndexOf('.');
            int lastDollar = fullyQualifiedName.lastIndexOf('$');
            int lastSeparator = Math.max(lastDot, lastDollar);
            return lastSeparator >= 0 ? fullyQualifiedName.substring(lastSeparator + 1) : fullyQualifiedName;
        }

        /**
         * Detects if a class name represents an anonymous class.
         * Anonymous classes are identified by numeric names (generated by the compiler as 1, 2, 3, etc.).
         */
        private boolean isAnonymousClassName(String simpleName) {
            if (simpleName.isEmpty()) {
                return false;
            }
            return Character.isDigit(simpleName.charAt(0));
        }
    }
}
