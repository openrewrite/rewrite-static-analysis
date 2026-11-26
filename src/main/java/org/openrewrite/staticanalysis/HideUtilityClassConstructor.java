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

import lombok.Getter;
import org.jspecify.annotations.Nullable;
import org.openrewrite.Cursor;
import org.openrewrite.ExecutionContext;
import org.openrewrite.ScanningRecipe;
import org.openrewrite.TreeVisitor;
import org.openrewrite.java.JavaIsoVisitor;
import org.openrewrite.java.tree.J;
import org.openrewrite.java.tree.JavaType;
import org.openrewrite.java.tree.TypeTree;
import org.openrewrite.java.tree.TypeUtils;
import org.openrewrite.kotlin.tree.K;

import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

import static java.util.Collections.singleton;

public class HideUtilityClassConstructor extends ScanningRecipe<HideUtilityClassConstructor.ConstructorUses> {

    @Getter
    final String displayName = "Hide utility class constructor";

    @Getter
    final String description = "Ensures utility classes (classes containing only static methods " +
            "or fields in their API) do not have a public constructor. " +
            "Instantiating a utility class is almost certainly a mistake, and a " +
            "private constructor makes that intent clear while preventing misuse. " +
            "A class is left alone when code outside its top-level class extends or instantiates it, holds instances of it, " +
            "or names it in a class literal or string that reflection could construct it from. " +
            "Code outside the repository is not seen.";

    @Getter
    final Set<String> tags = singleton("RSPEC-S1118");

    // Compared by name so that rewrite-kotlin, a provided dependency, is loaded only where Kotlin sources exist
    private static final String KOTLIN_CONSTRUCTOR_INVOCATION = "org.openrewrite.kotlin.tree.K$ConstructorInvocation";

    // As passed to Class.forName(..)
    private static final Pattern CLASS_NAME = Pattern.compile("(\\p{javaJavaIdentifierStart}\\p{javaJavaIdentifierPart}*\\.)+" +
            "\\p{javaUpperCase}[\\p{javaJavaIdentifierPart}$]*");

    // Class literals passed to, or used as the target of, these cannot construct the class
    private static final Set<String> NON_CONSTRUCTING_CLASS_METHODS = new HashSet<>(Arrays.asList(
            "getName", "getSimpleName", "getCanonicalName", "getTypeName", "getPackage", "getPackageName",
            "getResource", "getResourceAsStream", "getClassLoader", "getProtectionDomain", "isInstance", "cast",
            "isAssignableFrom", "getMethod", "getMethods", "getDeclaredMethod", "getDeclaredMethods", "getField",
            "getFields", "getDeclaredField", "getDeclaredFields", "getAnnotation", "getAnnotations",
            "isAnnotationPresent", "getModifiers", "desiredAssertionStatus"));
    private static final Set<String> NON_CONSTRUCTING_CLASS_ARGUMENT_METHODS = new HashSet<>(Arrays.asList(
            "getLogger", "getLog", "getFormatterLogger", "getXLogger", "mockStatic",
            "isInstanceOf", "isExactlyInstanceOf", "assertInstanceOf"));
    private static final Set<String> CLASS_LOADING_METHODS = new HashSet<>(Arrays.asList("forName", "loadClass"));

    // A private constructor is accessible within its whole top-level class (JLS 6.6.1), so only uses outside it count
    public static class ConstructorUses {
        final Set<String> usedOutside = new HashSet<>();

        // Simple name to the top-level classes using it, where the type is unresolved or only guessed (as by Groovy);
        // null stands for a use outside any class
        final Map<String, Set<@Nullable String>> unresolvedUsedFrom = new HashMap<>();

        void add(@Nullable JavaType type, @Nullable J nameTree, @Nullable String user) {
            JavaType.FullyQualified fq = TypeUtils.asFullyQualified(type);
            if (fq != null && !(fq instanceof JavaType.Unknown)) {
                String fqn = fq.getFullyQualifiedName();
                if (isGuessedName(fqn)) {
                    addUnresolved(fqn.substring(Math.max(fqn.lastIndexOf('.'), fqn.lastIndexOf('$')) + 1), user);
                }
                int nested = fqn.indexOf('$');
                String targetTopLevel = nested < 0 ? fqn : fqn.substring(0, nested);
                if (!targetTopLevel.equals(user)) {
                    usedOutside.add(fqn);
                }
            } else if (nameTree != null) {
                String simpleName = simpleName(nameTree);
                if (simpleName != null) {
                    addUnresolved(simpleName, user);
                }
            }
        }

        // Groovy names a type it cannot find as written, as `A` or `Outer.Util`, where a resolved name has a package and `$`
        private static boolean isGuessedName(String fqn) {
            int last = fqn.lastIndexOf('.');
            if (last < 0) {
                return true;
            }
            int previous = fqn.lastIndexOf('.', last - 1);
            return Character.isUpperCase(fqn.charAt(previous + 1));
        }

        void addUnresolved(String simpleName, @Nullable String user) {
            unresolvedUsedFrom.computeIfAbsent(simpleName, k -> new HashSet<>()).add(user);
        }

        // An unresolved call or reference like Kotlin's `A()` or `Outer::Util` may name a constructor
        void addIfConstructorName(String name, @Nullable String user) {
            if (!name.isEmpty() && Character.isUpperCase(name.charAt(0))) {
                addUnresolved(name, user);
            }
        }

        boolean isUsedOutsideTopLevelClass(Cursor classCursor) {
            J.ClassDeclaration c = classCursor.getValue();
            if (c.getType() == null || usedOutside.contains(c.getType().getFullyQualifiedName())) {
                return true;
            }
            Set<@Nullable String> users = unresolvedUsedFrom.get(c.getSimpleName());
            if (users != null) {
                String topLevel = topLevelClassName(classCursor);
                for (String user : users) {
                    if (user == null || !user.equals(topLevel)) {
                        return true;
                    }
                }
            }
            return false;
        }

        private static @Nullable String topLevelClassName(Cursor cursor) {
            J.ClassDeclaration outermost = null;
            for (Iterator<Object> it = cursor.getPath(); it.hasNext(); ) {
                Object o = it.next();
                if (o instanceof J.ClassDeclaration) {
                    outermost = (J.ClassDeclaration) o;
                }
            }
            return outermost == null || outermost.getType() == null ? null : outermost.getType().getFullyQualifiedName();
        }

        private static @Nullable String simpleName(J nameTree) {
            if (nameTree instanceof J.ParameterizedType) {
                return simpleName(((J.ParameterizedType) nameTree).getClazz());
            }
            if (nameTree instanceof J.FieldAccess) {
                return ((J.FieldAccess) nameTree).getSimpleName();
            }
            if (nameTree instanceof J.Identifier) {
                return ((J.Identifier) nameTree).getSimpleName();
            }
            return null;
        }
    }

    @Override
    public ConstructorUses getInitialValue(ExecutionContext ctx) {
        return new ConstructorUses();
    }

    @Override
    public TreeVisitor<?, ExecutionContext> getScanner(ConstructorUses uses) {
        return new JavaIsoVisitor<ExecutionContext>() {
            private int classDepth;
            private @Nullable String topLevel;

            @Override
            public J.ClassDeclaration visitClassDeclaration(J.ClassDeclaration classDecl, ExecutionContext ctx) {
                if (classDepth++ == 0) {
                    topLevel = classDecl.getType() == null ? null : classDecl.getType().getFullyQualifiedName();
                }
                // Every constructor of a subclass calls a constructor of its superclass, implicitly or not
                if (classDecl.getExtends() != null) {
                    uses.add(classDecl.getExtends().getType(), classDecl.getExtends(), topLevel);
                }
                if (classDecl.getImplements() != null) {
                    // Languages like Kotlin can list the superclass among the supertypes
                    for (TypeTree impl : classDecl.getImplements()) {
                        TypeTree supertype = KOTLIN_CONSTRUCTOR_INVOCATION.equals(impl.getClass().getName()) ?
                                ((K.ConstructorInvocation) impl).getTypeTree() : impl;
                        uses.add(supertype.getType(), supertype, topLevel);
                    }
                }
                J.ClassDeclaration c = super.visitClassDeclaration(classDecl, ctx);
                if (--classDepth == 0) {
                    topLevel = null;
                }
                return c;
            }

            @Override
            public J.NewClass visitNewClass(J.NewClass newClass, ExecutionContext ctx) {
                if (newClass.getClazz() != null) {
                    uses.add(newClass.getClazz().getType(), newClass.getClazz(), topLevel);
                }
                return super.visitNewClass(newClass, ctx);
            }

            @Override
            public J.MethodInvocation visitMethodInvocation(J.MethodInvocation method, ExecutionContext ctx) {
                // `super(..)` and `this(..)` are covered by the subclass's supertypes
                if (method.getMethodType() == null) {
                    uses.addIfConstructorName(method.getSimpleName(), topLevel);
                }
                return super.visitMethodInvocation(method, ctx);
            }

            @Override
            public J.VariableDeclarations visitVariableDeclarations(J.VariableDeclarations multiVariable, ExecutionContext ctx) {
                // Something constructs the instance held, as for `@Inject Util util;`
                if (multiVariable.getTypeExpression() != null) {
                    addDeclaredType(multiVariable.getTypeExpression().getType(), multiVariable.getTypeExpression());
                }
                return super.visitVariableDeclarations(multiVariable, ctx);
            }

            @Override
            public J.MethodDeclaration visitMethodDeclaration(J.MethodDeclaration method, ExecutionContext ctx) {
                // Something constructs the instance returned, as for a factory or `@Bean` method
                if (method.getReturnTypeExpression() != null) {
                    addDeclaredType(method.getReturnTypeExpression().getType(), method.getReturnTypeExpression());
                }
                return super.visitMethodDeclaration(method, ctx);
            }

            private void addDeclaredType(@Nullable JavaType type, J typeExpression) {
                if (type instanceof JavaType.Parameterized) {
                    for (JavaType typeParameter : ((JavaType.Parameterized) type).getTypeParameters()) {
                        addDeclaredType(typeParameter, typeExpression);
                    }
                }
                if (type instanceof JavaType.Array) {
                    addDeclaredType(((JavaType.Array) type).getElemType(), typeExpression);
                    return;
                }
                uses.add(type, typeExpression instanceof J.Identifier || typeExpression instanceof J.FieldAccess ? typeExpression : null, topLevel);
            }

            @Override
            public J.Literal visitLiteral(J.Literal literal, ExecutionContext ctx) {
                Object value = literal.getValue();
                if (value instanceof String && CLASS_NAME.matcher((String) value).matches()) {
                    uses.usedOutside.add((String) value);
                }
                return super.visitLiteral(literal, ctx);
            }

            @Override
            public J.FieldAccess visitFieldAccess(J.FieldAccess fieldAccess, ExecutionContext ctx) {
                if ("class".equals(fieldAccess.getSimpleName()) && mayConstruct(getCursor())) {
                    // Reflection through a class literal never runs as the class itself, wherever the literal is
                    uses.add(fieldAccess.getTarget().getType(), fieldAccess.getTarget(), null);
                }
                return super.visitFieldAccess(fieldAccess, ctx);
            }

            @Override
            public J.MemberReference visitMemberReference(J.MemberReference memberRef, ExecutionContext ctx) {
                JavaType.Method type = memberRef.getMethodType();
                String name = memberRef.getReference().getSimpleName();
                if ("new".equals(name) || "class".equals(name)) {
                    // `A::new`, or Kotlin's class literal `A::class`
                    uses.add(memberRef.getContaining().getType(), memberRef.getContaining(), topLevel);
                } else if (type == null) {
                    uses.addIfConstructorName(name, topLevel);
                } else if (type.isConstructor()) {
                    // Kotlin's `::A`
                    uses.add(type.getDeclaringType(), null, topLevel);
                }
                return super.visitMemberReference(memberRef, ctx);
            }
        };
    }

    // False for uses like `A.class.getName()`, `LoggerFactory.getLogger(A.class)` or `synchronized (A.class)`
    private static boolean mayConstruct(Cursor classLiteral) {
        Cursor parentCursor = classLiteral.getParentTreeCursor();
        Object parent = parentCursor.getValue();
        if (parent instanceof J.ControlParentheses) {
            return !(parentCursor.getParentTreeCursor().getValue() instanceof J.Synchronized);
        }
        if (!(parent instanceof J.MethodInvocation)) {
            return true;
        }
        J.MethodInvocation method = (J.MethodInvocation) parent;
        if (method.getSelect() == classLiteral.getValue()) {
            if (!NON_CONSTRUCTING_CLASS_METHODS.contains(method.getSimpleName())) {
                return true;
            }
            // As in `Class.forName(A.class.getName())`
            Object grandparent = parentCursor.getParentTreeCursor().getValue();
            return grandparent instanceof J.MethodInvocation &&
                    ((J.MethodInvocation) grandparent).getSelect() != method &&
                    CLASS_LOADING_METHODS.contains(((J.MethodInvocation) grandparent).getSimpleName());
        }
        return !NON_CONSTRUCTING_CLASS_ARGUMENT_METHODS.contains(method.getSimpleName());
    }

    @Override
    public TreeVisitor<?, ExecutionContext> getVisitor(ConstructorUses uses) {
        return new HideUtilityClassConstructorVisitor<>(classCursor -> !uses.isUsedOutsideTopLevelClass(classCursor));
    }
}
