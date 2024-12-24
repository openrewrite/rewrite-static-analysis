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
import org.openrewrite.Cursor;
import org.openrewrite.internal.ListUtils;
import org.openrewrite.java.AnnotationMatcher;
import org.openrewrite.java.JavaIsoVisitor;
import org.openrewrite.java.service.AnnotationService;
import org.openrewrite.java.style.ExplicitInitializationStyle;
import org.openrewrite.java.tree.J;
import org.openrewrite.java.tree.JavaType;
import org.openrewrite.java.tree.Space;
import org.openrewrite.java.tree.TypeUtils;

import java.util.Iterator;

@Value
@EqualsAndHashCode(callSuper = false)
public class ExplicitInitializationVisitor<P> extends JavaIsoVisitor<P> {
    private static final AnnotationMatcher LOMBOK_VALUE = new AnnotationMatcher("@lombok.Value");
    private static final AnnotationMatcher LOMBOK_BUILDER_DEFAULT = new AnnotationMatcher("@lombok.Builder.Default");

    ExplicitInitializationStyle style;

    @Override
    public J.VariableDeclarations.NamedVariable visitVariable(J.VariableDeclarations.NamedVariable variable, P p) {
        J.VariableDeclarations.NamedVariable v = super.visitVariable(variable, p);
        Cursor variableDeclsCursor = getCursor().getParentTreeCursor();
        Cursor maybeBlockOrGType = variableDeclsCursor.getParentTreeCursor();
        if (maybeBlockOrGType.getParent() == null || maybeBlockOrGType.getParent().getParent() == null) {
            // Groovy type.
            return v;
        } else {
            J maybeClassDecl = maybeBlockOrGType
                    .getParentTreeCursor() // maybe J.ClassDecl
                    .getValue();
            if (!(maybeClassDecl instanceof J.ClassDeclaration) ||
                J.ClassDeclaration.Kind.Type.Class != ((J.ClassDeclaration) maybeClassDecl).getKind() ||
                !(variableDeclsCursor.getValue() instanceof J.VariableDeclarations)) {
                return v;
            }
        }
        Iterator<Cursor> clz = getCursor().getPathAsCursors(c -> c.getValue() instanceof J.ClassDeclaration);
        if (clz.hasNext() && service(AnnotationService.class).matches(clz.next(), LOMBOK_VALUE)) {
            return v;
        }
        JavaType.Primitive primitive = TypeUtils.asPrimitive(variable.getType());
        JavaType.Array array = TypeUtils.asArray(variable.getType());

        J.VariableDeclarations variableDecls = variableDeclsCursor.getValue();
        if (service(AnnotationService.class).matches(variableDeclsCursor, LOMBOK_BUILDER_DEFAULT)) {
            return v;
        }
        if (variable.getInitializer() instanceof J.Literal && !variableDecls.hasModifier(J.Modifier.Type.Final)) {
            J.Literal literalInit = (J.Literal) variable.getInitializer();
            if (TypeUtils.asFullyQualified(variable.getType()) != null && JavaType.Primitive.Null == literalInit.getType()) {
                v = v.withInitializer(null);
            } else if (primitive != null && !Boolean.TRUE.equals(style.getOnlyObjectReferences())) {
                switch (primitive) {
                    case Boolean:
                        if (literalInit.getValue() == Boolean.valueOf(false)) {
                            v = v.withInitializer(null);
                        }
                        break;
                    case Char:
                        if (literalInit.getValue() instanceof Character && (Character) literalInit.getValue() == 0) {
                            v = v.withInitializer(null);
                        }
                        break;
                    case Int:
                    case Long:
                    case Short:
                        if (literalInit.getValue() instanceof Number && ((Number) literalInit.getValue()).intValue() == 0) {
                            v = v.withInitializer(null);
                        }
                        break;
                }
            } else if (array != null && JavaType.Primitive.Null == literalInit.getType()) {
                v = v.withInitializer(null)
                        .withDimensionsAfterName(ListUtils.map(v.getDimensionsAfterName(), (i, dim) ->
                                i == 0 ? dim.withBefore(Space.EMPTY) : dim));
            }
        }
        return v;
    }
}
