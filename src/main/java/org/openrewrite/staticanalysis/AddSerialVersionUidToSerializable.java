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
import org.openrewrite.internal.ListUtils;
import org.openrewrite.java.JavaIsoVisitor;
import org.openrewrite.java.JavaTemplate;
import org.openrewrite.java.tree.J;
import org.openrewrite.java.tree.JavaType;
import org.openrewrite.java.tree.Space;
import org.openrewrite.java.tree.TypeUtils;
import org.openrewrite.marker.Markers;

import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;

import static java.util.Collections.emptyList;
import static java.util.Collections.singleton;

@EqualsAndHashCode(callSuper = false)
@Value
public class AddSerialVersionUidToSerializable extends Recipe {

    @Option(displayName = "New serial version UID",
            description = "Value of the added serial version UID.",
            example = "42L",
            required = false)
    @Nullable
    String uid;

    @Override
    public String getDisplayName() {
        return "Add `serialVersionUID` to a `Serializable` class when missing";
    }

    @Override
    public String getDescription() {
        return "A `serialVersionUID` field is strongly recommended in all `Serializable` classes. If this is not " +
                "defined on a `Serializable` class, the compiler will generate this value. If a change is later made " +
                "to the class, the generated value will change and attempts to deserialize the class will fail.";
    }

    @Override
    public Set<String> getTags() {
        return singleton("RSPEC-S2057");
    }

    @Override
    public TreeVisitor<?, ExecutionContext> getVisitor() {
        return new JavaIsoVisitor<ExecutionContext>() {
            final JavaTemplate template = JavaTemplate.builder(String.format("private static final long serialVersionUID = %s;", uid != null ? uid : "1")).build();

            @Override
            public J.MethodDeclaration visitMethodDeclaration(J.MethodDeclaration method, ExecutionContext ctx) {
                // Anonymous classes are not of interest
                return method;
            }

            @Override
            public J.VariableDeclarations visitVariableDeclarations(J.VariableDeclarations multiVariable, ExecutionContext ctx) {
                // Anonymous classes are not of interest
                return multiVariable;
            }

            @Override
            public J.ClassDeclaration visitClassDeclaration(J.ClassDeclaration classDecl, ExecutionContext ctx) {
                J.ClassDeclaration c = super.visitClassDeclaration(classDecl, ctx);
                if (c.getKind() != J.ClassDeclaration.Kind.Type.Class || !requiresSerialVersionField(classDecl.getType())) {
                    return c;
                }
                AtomicBoolean needsSerialVersionId = new AtomicBoolean(true);
                J.Block body = c.getBody();
                c = c.withBody(c.getBody().withStatements(ListUtils.map(c.getBody().getStatements(), s -> {
                    if (!(s instanceof J.VariableDeclarations)) {
                        return s;
                    }
                    J.VariableDeclarations varDecls = (J.VariableDeclarations) s;
                    for (J.VariableDeclarations.NamedVariable v : varDecls.getVariables()) {
                        if ("serialVersionUID".equals(v.getSimpleName())) {
                            needsSerialVersionId.set(false);
                            return maybeAutoFormat(varDecls, maybeFixVariableDeclarations(varDecls), ctx, new Cursor(getCursor(), body));
                        }
                    }
                    return s;
                })));
                if (needsSerialVersionId.get()) {
                    c = template.apply(updateCursor(c), c.getBody().getCoordinates().firstStatement());
                }
                return c;
            }

            private J.VariableDeclarations maybeFixVariableDeclarations(J.VariableDeclarations varDecls) {
                List<J.Modifier> modifiers = varDecls.getModifiers();
                if (!J.Modifier.hasModifier(modifiers, J.Modifier.Type.Private) ||
                        !J.Modifier.hasModifier(modifiers, J.Modifier.Type.Static) ||
                        !J.Modifier.hasModifier(modifiers, J.Modifier.Type.Final)) {
                    varDecls = varDecls.withModifiers(Arrays.asList(
                            new J.Modifier(Tree.randomId(), Space.EMPTY, Markers.EMPTY, null, J.Modifier.Type.Private, emptyList()),
                            new J.Modifier(Tree.randomId(), Space.SINGLE_SPACE, Markers.EMPTY, null, J.Modifier.Type.Static, emptyList()),
                            new J.Modifier(Tree.randomId(), Space.SINGLE_SPACE, Markers.EMPTY, null, J.Modifier.Type.Final, emptyList())
                    ));
                }
                if (TypeUtils.asPrimitive(varDecls.getType()) != JavaType.Primitive.Long) {
                    varDecls = varDecls.withTypeExpression(new J.Primitive(Tree.randomId(), Space.EMPTY, Markers.EMPTY, JavaType.Primitive.Long));
                }
                return varDecls;
            }

            private boolean requiresSerialVersionField(@Nullable JavaType type) {
                if (type == null) {
                    return false;
                }
                if (type instanceof JavaType.Primitive) {
                    return true;
                }
                if (type instanceof JavaType.Array) {
                    return requiresSerialVersionField(((JavaType.Array) type).getElemType());
                }
                if (type instanceof JavaType.Parameterized) {
                    JavaType.Parameterized parameterized = (JavaType.Parameterized) type;
                    if (parameterized.isAssignableTo("java.util.Collection") || parameterized.isAssignableTo("java.util.Map")) {
                        //If the type is either a collection or a map, make sure the type parameters are serializable. We
                        //force all type parameters to be checked to correctly scoop up all non-serializable candidates.
                        boolean typeParametersSerializable = true;
                        for (JavaType typeParameter : parameterized.getTypeParameters()) {
                            typeParametersSerializable = typeParametersSerializable && requiresSerialVersionField(typeParameter);
                        }
                        return typeParametersSerializable;
                    }
                    //All other parameterized types fall through
                } else if (type instanceof JavaType.FullyQualified) {
                    JavaType.FullyQualified fq = (JavaType.FullyQualified) type;
                    if (fq.getKind() == JavaType.Class.Kind.Enum) {
                        return false;
                    }

                    if (fq.getKind() != JavaType.Class.Kind.Interface &&
                            !fq.isAssignableTo("java.lang.Throwable")) {
                        return fq.isAssignableTo("java.io.Serializable");
                    }
                }
                return false;
            }
        };
    }
}
