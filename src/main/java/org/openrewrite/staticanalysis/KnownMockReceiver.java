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

import org.openrewrite.Cursor;
import org.openrewrite.SourceFile;
import org.openrewrite.java.JavaIsoVisitor;
import org.openrewrite.java.MethodMatcher;
import org.openrewrite.java.tree.Expression;
import org.openrewrite.java.tree.J;
import org.openrewrite.java.tree.TypeUtils;
import org.openrewrite.java.tree.JavaType;
import org.openrewrite.java.tree.JavaSourceFile;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.HashMap;
import java.util.UUID;
import java.util.Set;

/**
 * Mock methods dispatch independently: a default size() of zero does not imply isEmpty().
 * Track attributed variable symbols, including fields assigned in setup methods. A symbol
 * assigned a mock anywhere is conservatively retained even when another assignment is real;
 * deciding which assignment reaches a call across lifecycle methods requires interprocedural flow.
 */
final class KnownMockReceiver {
    private static final MethodMatcher[] FACTORIES = {
            new MethodMatcher("spock.mock.MockingApi Mock(..)", true),
            new MethodMatcher("spock.mock.MockingApi Stub(..)", true),
            new MethodMatcher("spock.mock.MockingApi Spy(..)", true),
            new MethodMatcher("spock.mock.MockingApi GroovyMock(..)", true),
            new MethodMatcher("spock.mock.MockingApi GroovyStub(..)", true),
            new MethodMatcher("spock.mock.MockingApi GroovySpy(..)", true),
            new MethodMatcher("spock.mock.DetachedMockFactory Mock(..)", true),
            new MethodMatcher("spock.mock.DetachedMockFactory Stub(..)", true),
            new MethodMatcher("spock.mock.DetachedMockFactory Spy(..)", true),
            new MethodMatcher("org.spockframework.mock.MockUtil createDetachedMock(..)", true),
            new MethodMatcher("org.mockito.Mockito mock(..)", true),
            new MethodMatcher("org.mockito.Mockito spy(..)", true),
            new MethodMatcher("org.mockito.BDDMockito mock(..)", true),
            new MethodMatcher("org.mockito.BDDMockito spy(..)", true)
    };

    private SourceFile source;
    private final Map<UUID, Object> references = new HashMap<>();
    private final Set<UUID> factories = new HashSet<>();
    private final Set<Object> mocks = new HashSet<>();

    boolean isMock(Expression receiver, Cursor cursor) {
        SourceFile enclosing = cursor.firstEnclosing(SourceFile.class);
        if (enclosing != source) {
            source = enclosing;
            mocks.clear();
            references.clear();
            factories.clear();
            List<Object> targets = new ArrayList<>();
            List<Expression> values = new ArrayList<>();
            new JavaIsoVisitor<Integer>() {
                @Override
                public J.MethodInvocation visitMethodInvocation(J.MethodInvocation method, Integer p) {
                    if (isFactory(method, getCursor())) {
                        factories.add(method.getId());
                    }
                    return super.visitMethodInvocation(method, p);
                }

                @Override
                public J.Identifier visitIdentifier(J.Identifier identifier, Integer p) {
                    Object symbol = resolve(identifier, getCursor());
                    if (symbol != null) {
                        references.put(identifier.getId(), symbol);
                    }
                    return identifier;
                }

                @Override
                public J.FieldAccess visitFieldAccess(J.FieldAccess field, Integer p) {
                    J.FieldAccess f = super.visitFieldAccess(field, p);
                    Object symbol = resolve(field, getCursor());
                    if (symbol != null) {
                        references.put(field.getId(), symbol);
                    }
                    return f;
                }

                @Override
                public J.VariableDeclarations visitVariableDeclarations(J.VariableDeclarations declarations, Integer p) {
                    for (J.VariableDeclarations.NamedVariable variable : declarations.getVariables()) {
                        Object symbol = variable.getId();
                        if (symbol != null) {
                            for (J.Annotation annotation : declarations.getLeadingAnnotations()) {
                                if (TypeUtils.isOfClassType(annotation.getType(), "org.mockito.Mock") ||
                                    TypeUtils.isOfClassType(annotation.getType(), "org.mockito.Spy")) {
                                    mocks.add(symbol);
                                }
                            }
                            if (variable.getInitializer() != null) {
                                targets.add(symbol);
                                values.add(variable.getInitializer());
                            }
                        }
                    }
                    return super.visitVariableDeclarations(declarations, p);
                }

                @Override
                public J.Assignment visitAssignment(J.Assignment assignment, Integer p) {
                    Object symbol = resolve(assignment.getVariable(), getCursor());
                    if (symbol != null) {
                        targets.add(symbol);
                        values.add(assignment.getAssignment());
                    }
                    return super.visitAssignment(assignment, p);
                }
            }.visit(enclosing, 0);
            // Resolve aliases irrespective of declaration or setup-method order.
            boolean changed;
            do {
                changed = false;
                for (int i = 0; i < targets.size(); i++) {
                    if (known(values.get(i))) {
                        changed |= mocks.add(targets.get(i));
                    }
                }
            } while (changed);
        }
        return receiver != null && known(receiver);
    }

    private boolean known(Expression expression) {
        Expression unwrapped = expression.unwrap();
        if (unwrapped instanceof J.TypeCast) {
            return known(((J.TypeCast) unwrapped).getExpression());
        }
        if (unwrapped instanceof J.Ternary) {
            return known(((J.Ternary) unwrapped).getTruePart()) || known(((J.Ternary) unwrapped).getFalsePart());
        }
        if (factories.contains(unwrapped.getId())) {
            return true;
        }
        Object symbol = references.get(unwrapped.getId());
        if (symbol == null && unwrapped instanceof J.FieldAccess) {
            symbol = references.get(((J.FieldAccess) unwrapped).getName().getId());
        }
        return symbol != null && mocks.contains(symbol);
    }

    private boolean isFactory(J.MethodInvocation method, Cursor cursor) {
        for (MethodMatcher factory : FACTORIES) {
            if (factory.matches(method)) {
                return true;
            }
        }
        if (method.getMethodType() != null) {
            return false;
        }
        // Old Groovy models can retain the receiver/extends type but omit the
        // factory's method type. Restrict that fallback to known framework types.
        String name = method.getSimpleName();
        if (method.getSelect() != null) {
            String owner = qualifiedType(method.getSelect().getType());
            if ("org.spockframework.mock.MockUtil".equals(owner)) {
                return "createDetachedMock".equals(name);
            }
            if ("spock.mock.DetachedMockFactory".equals(owner)) {
                return "Mock".equals(name) || "Stub".equals(name) || "Spy".equals(name);
            }
            return false;
        }
        if (!"Mock".equals(name) && !"Stub".equals(name) && !"Spy".equals(name) &&
            !"GroovyMock".equals(name) && !"GroovyStub".equals(name) && !"GroovySpy".equals(name)) {
            return false;
        }
        J.ClassDeclaration enclosing = cursor.firstEnclosing(J.ClassDeclaration.class);
        if (enclosing == null || enclosing.getExtends() == null) {
            return false;
        }
        // A local declaration with the same name is evidence against the inherited API.
        for (org.openrewrite.java.tree.Statement statement : enclosing.getBody().getStatements()) {
            if (statement instanceof J.MethodDeclaration && ((J.MethodDeclaration) statement).getSimpleName().equals(name)) {
                return false;
            }
        }
        String parent = qualifiedType(enclosing.getExtends().getType());
        return "spock.lang.Specification".equals(parent) || "spock.mock.MockingApi".equals(parent) ||
               TypeUtils.isAssignableTo("spock.mock.MockingApi", enclosing.getExtends().getType());
    }

    private String qualifiedType(@org.jspecify.annotations.Nullable JavaType type) {
        JavaType.FullyQualified fq = TypeUtils.asFullyQualified(type);
        if (fq == null) {
            return "";
        }
        String name = fq.getFullyQualifiedName();
        if (name.contains(".") || !(source instanceof JavaSourceFile)) {
            return name;
        }
        JavaSourceFile javaSource = (JavaSourceFile) source;
        for (J.Import anImport : javaSource.getImports()) {
            if (!anImport.isStatic() && anImport.getTypeName().endsWith("." + name)) {
                return anImport.getTypeName();
            }
        }
        J.Package pkg = javaSource.getPackageDeclaration();
        return pkg == null ? name : pkg.getExpression().printTrimmed(new Cursor(new Cursor(null, source), pkg)) + "." + name;
    }

    private static Object resolve(Expression expression, Cursor cursor) {
        J.Identifier identifier;
        boolean fieldOnly = false;
        if (expression instanceof J.Identifier) {
            identifier = (J.Identifier) expression;
        } else if (expression instanceof J.FieldAccess &&
                   ((J.FieldAccess) expression).getTarget() instanceof J.Identifier &&
                   "this".equals(((J.Identifier) ((J.FieldAccess) expression).getTarget()).getSimpleName())) {
            identifier = ((J.FieldAccess) expression).getName();
            fieldOnly = true;
        } else {
            return null;
        }
        // Older Groovy models omit Identifier.fieldType even when declaration and
        // factory types are present. Resolve the nearest lexical declaration, never a
        // file-wide simple name, so another method's local cannot acquire its provenance.
        for (Cursor scope = cursor; scope != null; scope = scope.getParent()) {
            Object value = scope.getValue();
            List<? extends J> statements = null;
            if (value instanceof J.Block && (!fieldOnly || scope.getParentTreeCursor().getValue() instanceof J.ClassDeclaration)) {
                statements = ((J.Block) value).getStatements();
            } else if (!fieldOnly && value instanceof J.MethodDeclaration) {
                statements = ((J.MethodDeclaration) value).getParameters();
            } else if (!fieldOnly && value instanceof J.Lambda) {
                statements = ((J.Lambda) value).getParameters().getParameters();
            }
            if (statements != null) {
                for (J statement : statements) {
                    while (statement instanceof J.Label) {
                        statement = ((J.Label) statement).getStatement();
                    }
                    if (statement instanceof J.VariableDeclarations) {
                        for (J.VariableDeclarations.NamedVariable variable : ((J.VariableDeclarations) statement).getVariables()) {
                            if (variable.getSimpleName().equals(identifier.getSimpleName())) {
                                return variable.getId();
                            }
                        }
                    }
                }
            }
        }
        return identifier.getFieldType();
    }
}
