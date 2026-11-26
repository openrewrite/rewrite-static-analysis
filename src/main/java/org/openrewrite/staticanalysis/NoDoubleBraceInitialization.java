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
import lombok.RequiredArgsConstructor;
import org.jspecify.annotations.Nullable;
import org.openrewrite.*;
import org.openrewrite.internal.ListUtils;
import org.openrewrite.java.JavaIsoVisitor;
import org.openrewrite.java.JavaVisitor;
import org.openrewrite.java.MethodMatcher;
import org.openrewrite.java.search.UsesType;
import org.openrewrite.java.trait.Scope;
import org.openrewrite.java.tree.*;
import org.openrewrite.marker.Markers;
import org.openrewrite.marker.SearchResult;

import java.time.Duration;
import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Predicate;

import static java.util.Collections.singletonList;
import static java.util.stream.Collectors.toList;

public class NoDoubleBraceInitialization extends Recipe {
    private static final JavaType MAP_TYPE = JavaType.buildType("java.util.Map");
    private static final JavaType LIST_TYPE = JavaType.buildType("java.util.List");
    private static final JavaType SET_TYPE = JavaType.buildType("java.util.Set");
    private static final MethodMatcher GET_CLASS = new MethodMatcher("java.lang.Object getClass()");

    @Getter
    final String displayName = "No double brace initialization";

    @Getter
    final String description = "Replace `List`, `Map`, and `Set` double brace initialization " +
            "with an initialization block. Double brace initialization creates " +
            "an anonymous inner class that holds a hidden reference to the " +
            "enclosing instance, which can cause memory leaks and " +
            "serialization issues.";

    @Getter
    final Set<String> tags = new LinkedHashSet<>(Arrays.asList("RSPEC-S1171", "RSPEC-S3599"));

    @Getter
    final Duration estimatedEffortPerOccurrence = Duration.ofMinutes(30);

    @Override
    public TreeVisitor<?, ExecutionContext> getVisitor() {
        return Preconditions.check(
                Preconditions.or(
                        new UsesType<>("java.util.Map", false),
                        new UsesType<>("java.util.List", false),
                        new UsesType<>("java.util.Set", false)
                ),
                new NoDoubleBraceInitializationVisitor()
        );
    }

    private static class NoDoubleBraceInitializationVisitor extends JavaIsoVisitor<ExecutionContext> {

        private boolean isSupportedDoubleBraceInitialization(J.NewClass nc) {
            if (getCursor().getParent() == null ||
                    getCursor().getParent().firstEnclosing(J.class) instanceof J.MethodInvocation ||
                    getCursor().getParent().firstEnclosing(J.class) instanceof J.NewClass) {
                return false;
            }
            J.ClassDeclaration cd = getCursor().firstEnclosing(J.ClassDeclaration.class);
            if (cd != null && (cd.getKind() == J.ClassDeclaration.Kind.Type.Interface ||
                               cd.getKind() == J.ClassDeclaration.Kind.Type.Annotation)) {
                return false;
            }
            if (nc.getBody() != null && !nc.getBody().getStatements().isEmpty() &&
                    nc.getBody().getStatements().size() == 1 &&
                    nc.getBody().getStatements().get(0) instanceof J.Block &&
                    getCursor().getParent(3) != null) {
                return TypeUtils.isAssignableTo(MAP_TYPE, nc.getType()) ||
                        TypeUtils.isAssignableTo(LIST_TYPE, nc.getType()) ||
                        TypeUtils.isAssignableTo(SET_TYPE, nc.getType());
            }
            return false;
        }

        @Override
        public J.NewClass visitNewClass(J.NewClass newClass, ExecutionContext ctx) {
            J.NewClass nc = super.visitNewClass(newClass, ctx);
            updateCursor(nc);
            if (isSupportedDoubleBraceInitialization(newClass)) {
                Cursor parentBlockCursor = getCursor().dropParentUntil(J.Block.class::isInstance);
                J.VariableDeclarations.NamedVariable var = getCursor().firstEnclosing(J.VariableDeclarations.NamedVariable.class);
                J.Block secondBlock = (J.Block) nc.getBody().getStatements().get(0);
                List<Statement> initStatements = secondBlock.getStatements();

                boolean maybeMistakenlyMissedAddingElement = !initStatements.isEmpty() &&
                        initStatements.stream().allMatch(J.NewClass.class::isInstance);

                if (maybeMistakenlyMissedAddingElement) {
                    JavaType newClassType = nc.getType();
                    String addToCollectionMethod = TypeUtils.isAssignableTo(MAP_TYPE, newClassType) ? "put()" : "add()";
                    return nc.withBody(AddWarningMessage.addWarningComment(nc.getBody(), addToCollectionMethod));
                }

                // If not any method invocation (like add(), push(), etc) happened in the double brace to initialize
                // the content of the collection, it means the intention of the code in the double brace is uncertain
                // or maybe a custom code bug (like issue: https://github.com/openrewrite/rewrite/issues/2674),
                // we don't want to rewrite code for this case to avoid introducing other warnings.
                boolean hasMethodInvocationInDoubleBrace = FindMethodInvocationInDoubleBrace.find(secondBlock);

                if (hasMethodInvocationInDoubleBrace && var != null && parentBlockCursor.getParent() != null &&
                    isWholeInitializerOfOnlyVariable(var, newClass) && canInstantiateDirectly(newClass)) {
                    Cursor namedVarCursor = getCursor().dropParentUntil(J.VariableDeclarations.NamedVariable.class::isInstance);
                    Cursor varDeclsCursor = namedVarCursor.dropParentUntil(J.VariableDeclarations.class::isInstance);
                    J.VariableDeclarations varDecls = varDeclsCursor.getValue();
                    if (parentBlockCursor.getParent().getValue() instanceof J.ClassDeclaration) {
                        JavaType.FullyQualified fq = TypeUtils.asFullyQualified(nc.getType());
                        if (fq != null && fq.getSupertype() != null && !generatesCodeFromFields(varDecls)) {
                            // A deferred lambda reads the field when it runs, which only a final field makes safe
                            List<Statement> qualified = qualifyMembersOfAnonymousClass(nc, secondBlock, var, varDecls,
                                    parentBlockCursor.getValue(), varDecls.hasModifier(J.Modifier.Type.Final));
                            if (qualified == null) {
                                return nc;
                            }
                            namedVarCursor.putMessage("DROP_INITIALIZER", Boolean.TRUE);

                            nc = nc.withBody(null);
                            initStatements = new ArrayList<>(qualified);
                            initStatements.add(0, new J.Assignment(Tree.randomId(), Space.EMPTY, Markers.EMPTY, var.getName().withId(UUID.randomUUID()), JLeftPadded.build(nc), fq));
                            parentBlockCursor.computeMessageIfAbsent("INIT_STATEMENTS", v -> new HashMap<Statement, List<Statement>>()).put(varDecls, initStatements);
                        }
                    } else if (parentBlockCursor.getParent().getValue() instanceof J.MethodDeclaration) {
                        // A local read from a lambda or a class body must be effectively final
                        boolean effectivelyFinal = Scope.bindingOf(namedVarCursor, var).map(Scope.Binding::isEffectivelyFinal).orElse(false);
                        initStatements = qualifyMembersOfAnonymousClass(nc, secondBlock, var, varDecls, null, effectivelyFinal);
                        if (initStatements == null) {
                            return nc;
                        }
                        Cursor initCursor = new Cursor(new Cursor(getCursor(), nc.getBody()), secondBlock.withStatements(initStatements));
                        if (!Scope.at(initCursor).canMoveAfter(varDeclsCursor)) {
                            return nc;
                        }
                        parentBlockCursor.computeMessageIfAbsent("METHOD_DECL_STATEMENTS", v -> new HashMap<Statement, List<Statement>>()).put(varDecls, initStatements);
                        nc = nc.withBody(null);
                    }
                }

            }
            return nc;
        }

        // Lombok treats a final field without initializer differently; an unresolved annotation may be Lombok
        private boolean generatesCodeFromFields(J.VariableDeclarations field) {
            if (field.hasModifier(J.Modifier.Type.Static)) {
                return false;
            }
            List<J.Annotation> annotations = new ArrayList<>(field.getAllAnnotations());
            J.ClassDeclaration owner = getCursor().firstEnclosing(J.ClassDeclaration.class);
            if (owner != null) {
                annotations.addAll(owner.getAllAnnotations());
            }
            for (J.Annotation annotation : annotations) {
                JavaType.FullyQualified type = TypeUtils.asFullyQualified(annotation.getType());
                if (type == null || type instanceof JavaType.Unknown || type.getFullyQualifiedName().startsWith("lombok.")) {
                    return true;
                }
            }
            return false;
        }

        // Another variable of the same declaration could read the variable before the moved statements run
        private boolean isWholeInitializerOfOnlyVariable(J.VariableDeclarations.NamedVariable var, J.NewClass newClass) {
            J.VariableDeclarations declarations = getCursor().firstEnclosing(J.VariableDeclarations.class);
            Expression initializer = Expression.unwrap(var.getInitializer());
            return declarations != null && declarations.getVariables().size() == 1 &&
                   initializer != null && initializer.getId().equals(newClass.getId());
        }

        // A protected constructor is accessible to the anonymous subclass, not to a plain `new` in another package
        private boolean canInstantiateDirectly(J.NewClass newClass) {
            JavaType.FullyQualified type = newClass.getClazz() == null ? null :
                    TypeUtils.asFullyQualified(newClass.getClazz().getType());
            if (type instanceof JavaType.Parameterized) {
                type = ((JavaType.Parameterized) type).getType();
            }
            if (type == null || type.getKind() != JavaType.FullyQualified.Kind.Class || type.hasFlags(Flag.Abstract)) {
                return false;
            }
            boolean samePackage = packageName(getCursor()).equals(type.getPackageName());
            int arguments = newClass.getArguments().size() == 1 && newClass.getArguments().get(0) instanceof J.Empty ?
                    0 : newClass.getArguments().size();
            for (JavaType.Method constructor : type.getMethods()) {
                int parameters = constructor.getParameterTypes().size();
                boolean couldBeCalled = parameters == arguments ||
                                        constructor.hasFlags(Flag.Varargs) && arguments >= parameters - 1;
                if (constructor.isConstructor() && couldBeCalled &&
                    !constructor.hasFlags(Flag.Public) && (constructor.hasFlags(Flag.Private) || !samePackage)) {
                    return false;
                }
            }
            return true;
        }

        private static String packageName(Cursor cursor) {
            JavaSourceFile sourceFile = cursor.firstEnclosing(JavaSourceFile.class);
            return sourceFile == null || sourceFile.getPackageDeclaration() == null ? "" :
                    sourceFile.getPackageDeclaration().getPackageName();
        }

        /**
         * @param classBody the class body of the field, or null for a local variable
         * @return the statements with the anonymous class's calls made on the variable, or null if they cannot move
         */
        private @Nullable List<Statement> qualifyMembersOfAnonymousClass(J.NewClass anonymous, J.Block initializer, J.VariableDeclarations.NamedVariable var,
                                                                         J.VariableDeclarations declarations, J.@Nullable Block classBody,
                                                                         boolean capturable) {
            Cursor bodyCursor = new Cursor(getCursor(), anonymous.getBody());
            JavaType.FullyQualified variableType = TypeUtils.asFullyQualified(var.getType());
            JavaType created = anonymous.getClazz() == null ? null : anonymous.getClazz().getType();
            boolean rawAssignedToGeneric = variableType instanceof JavaType.Parameterized && !(created instanceof JavaType.Parameterized);
            if (variableType == null || hasWildcard(variableType) || rawAssignedToGeneric) {
                // Through a wildcard add(..) takes no argument; a raw anonymous class takes more than the variable
                return null;
            }
            String variableName = var.getSimpleName();
            // For a local, Scope#canMoveAfter refuses a local of the initializer that shadows it
            if (classBody != null && Scope.declaredWithin(new Cursor(bodyCursor, initializer)).anyMatch(b -> b.getName().equals(variableName))) {
                return null;
            }
            QualifyMembers qualify = new QualifyMembers(anonymous, var.getName(), variableType, capturable,
                    classBody, declarations);
            J.Block qualified = (J.Block) qualify.visitNonNull(initializer, 0, bodyCursor);
            return qualify.unsafe ? null : qualified.getStatements();
        }

        private static boolean hasWildcard(JavaType type) {
            if (type instanceof JavaType.Parameterized) {
                for (JavaType parameter : ((JavaType.Parameterized) type).getTypeParameters()) {
                    // A nested wildcard, as in Map<String, Class<?>>, is fine
                    if (parameter instanceof JavaType.GenericTypeVariable &&
                        "?".equals(((JavaType.GenericTypeVariable) parameter).getName())) {
                        return true;
                    }
                }
            }
            return false;
        }

        @RequiredArgsConstructor
        private static class QualifyMembers extends JavaVisitor<Integer> {
            private final J.NewClass anonymous;
            private final J.Identifier variable;
            private final JavaType.FullyQualified variableType;
            private final boolean capturable;
            private final J.@Nullable Block classBody;
            private final J.VariableDeclarations declarations;
            boolean unsafe;

            @Override
            public @Nullable J visit(@Nullable Tree tree, Integer p) {
                return unsafe ? (J) tree : super.visit(tree, p);
            }

            @Override
            public J visitMethodInvocation(J.MethodInvocation method, Integer p) {
                Expression select = method.getSelect();
                if (select == null) {
                    Scope.Binding binding = Scope.at(getCursor()).lookup(method.getSimpleName(), Scope.Namespace.METHOD).orElse(null);
                    if (binding == null) {
                        unsafe = true;
                        return method;
                    }
                    if (isMemberOfAnonymous(binding)) {
                        // Scope binds a name, not an overload, so the call's own method type is checked
                        return qualify((J.MethodInvocation) super.visitMethodInvocation(method, p));
                    }
                } else if (select instanceof J.Identifier && "this".equals(((J.Identifier) select).getSimpleName()) &&
                           !inNestedClassBody()) {
                    return qualify(method.withArguments(ListUtils.map(method.getArguments(), a -> (Expression) visit(a, p))));
                }
                return super.visitMethodInvocation(method, p);
            }

            private J.MethodInvocation qualify(J.MethodInvocation mi) {
                if (!canQualify(mi)) {
                    unsafe = true;
                    return mi;
                }
                return mi.withSelect(variable.withId(Tree.randomId()).withPrefix(Space.EMPTY));
            }

            private boolean canQualify(J.MethodInvocation mi) {
                JavaType.Method method = mi.getMethodType();
                // A static interface method such as List.of cannot be called on an instance
                if (method == null || method.hasFlags(Flag.Static) || GET_CLASS.matches(mi)) {
                    return false;
                }
                return !inNestedClassBody() && (capturable || !anyEnclosingInAnonymous(c -> c.getValue() instanceof J.Lambda)) &&
                       accessibleFromOutside(method) && callableOnVariable(method);
            }

            @Override
            public J visitFieldAccess(J.FieldAccess fieldAccess, Integer p) {
                // The field read as T.L is null in the anonymous class but set once the code moves
                JavaType.Variable read = fieldAccess.getName().getFieldType();
                if (classBody != null && read != null && variable.getFieldType() != null &&
                    read.getName().equals(variable.getFieldType().getName()) &&
                    TypeUtils.isOfType(read.getOwner(), variable.getFieldType().getOwner())) {
                    unsafe = true;
                    return fieldAccess;
                }
                return super.visitFieldAccess(fieldAccess, p);
            }

            @Override
            public J visitIdentifier(J.Identifier identifier, Integer p) {
                Object parent = getCursor().getParentTreeCursor().getValue();
                if (parent instanceof J.FieldAccess && ((J.FieldAccess) parent).getName() == identifier ||
                        parent instanceof J.MethodInvocation && ((J.MethodInvocation) parent).getName() == identifier ||
                        parent instanceof J.MemberReference && ((J.MemberReference) parent).getReference() == identifier) {
                    return identifier;
                }
                String name = identifier.getSimpleName();
                if ("this".equals(name) || "super".equals(name)) {
                    if (!inNestedClassBody()) {
                        unsafe = true;
                    }
                } else {
                    Scope.Namespace namespace = Scope.service(getCursor()).namespaceOf(getCursor());
                    if (namespace != null) {
                        Scope.at(getCursor()).lookup(name, namespace).ifPresent(binding -> {
                            if (isMemberOfAnonymous(binding) ||
                                namespace == Scope.Namespace.VARIABLE && isFieldDeclaredFromHereOn(binding)) {
                                unsafe = true;
                            }
                        });
                    }
                }
                return identifier;
            }

            // The field itself would be set instead of null, and a later field would be a forward reference
            private boolean isFieldDeclaredFromHereOn(Scope.Binding binding) {
                Tree scope = binding.getScope().getTree();
                if (classBody == null || !scope.getId().equals(classBody.getId()) &&
                                         !(scope instanceof J.ClassDeclaration &&
                                           ((J.ClassDeclaration) scope).getBody().getId().equals(classBody.getId()))) {
                    return false;
                }
                boolean staticBinding = binding.getType() instanceof JavaType.Variable &&
                                        ((JavaType.Variable) binding.getType()).hasFlags(Flag.Static) ||
                                        binding.getDeclaration().map(d -> d.firstEnclosing(J.EnumValue.class) != null).orElse(false);
                if (staticBinding && !declarations.hasModifier(J.Modifier.Type.Static)) {
                    // An enum's instance initializer cannot read its non-constant static fields
                    J.ClassDeclaration owner = getCursor().firstEnclosing(J.ClassDeclaration.class);
                    return owner != null && owner.getKind() == J.ClassDeclaration.Kind.Type.Enum;
                }
                Cursor declaration = binding.getDeclaration().orElse(null);
                if (declaration == null) {
                    // Inherited, so already initialized
                    return false;
                }
                int fieldIndex = classBody.getStatements().indexOf(declarations);
                for (Cursor c = declaration; c != null; c = c.getParent()) {
                    if (c.getValue() instanceof Statement) {
                        int index = classBody.getStatements().indexOf(c.getValue());
                        if (index >= 0) {
                            // A nested class body reads a later field when it runs, not during initialization
                            return index > fieldIndex ? !inNestedClassBody() : index == fieldIndex;
                        }
                    }
                }
                return true;
            }

            // The declared type may lack the method or pick another overload, as Collection#remove(Object)
            private boolean callableOnVariable(JavaType.Method method) {
                JavaType.FullyQualified declaring = method.getDeclaringType();
                JavaType.FullyQualified raw = variableType instanceof JavaType.Parameterized ?
                        ((JavaType.Parameterized) variableType).getType() : variableType;
                if (TypeUtils.isObject(declaring) || TypeUtils.isAssignableTo(declaring.getFullyQualifiedName(), raw)) {
                    return true;
                }
                // Compare the method as declared, before its type variables are substituted
                JavaType.Method declared = null;
                for (JavaType.Method candidate : declaring.getMethods()) {
                    if (TypeUtils.isOfTypeIgnoringGenerics(candidate, method)) {
                        if (declared != null) {
                            return false;
                        }
                        declared = candidate;
                    }
                }
                for (JavaType.Method m = declared; m != null; m = TypeUtils.findOverriddenMethod(m).orElse(null)) {
                    if (TypeUtils.isAssignableTo(m.getDeclaringType().getFullyQualifiedName(), raw)) {
                        return true;
                    }
                }
                return false;
            }

            private boolean isMemberOfAnonymous(Scope.Binding binding) {
                return binding.getScope().getTree().getId().equals(anonymous.getId());
            }

            private boolean accessibleFromOutside(JavaType.Method method) {
                return method.hasFlags(Flag.Public) ||
                       !method.hasFlags(Flag.Private) && packageName(getCursor()).equals(method.getDeclaringType().getPackageName());
            }

            private boolean inNestedClassBody() {
                return anyEnclosingInAnonymous(c -> c.getValue() instanceof J.Block &&
                                                    (c.getParentTreeCursor().getValue() instanceof J.ClassDeclaration ||
                                                     c.getParentTreeCursor().getValue() instanceof J.NewClass));
            }

            private boolean anyEnclosingInAnonymous(Predicate<Cursor> matches) {
                for (Cursor c = getCursor(); c != null && c.getValue() != anonymous.getBody(); c = c.getParent()) {
                    if (matches.test(c)) {
                        return true;
                    }
                }
                return false;
            }
        }

        @Override
        public J.VariableDeclarations.NamedVariable visitVariable(J.VariableDeclarations.NamedVariable variable, ExecutionContext ctx) {
            J.VariableDeclarations.NamedVariable var = super.visitVariable(variable, ctx);
            if (getCursor().pollMessage("DROP_INITIALIZER") != null) {
                var = var.withInitializer(null);
            }
            return var;
        }

        @Override
        public J.Block visitBlock(J.Block block, ExecutionContext ctx) {
            J.Block bl = super.visitBlock(block, ctx);
            Map<Statement, List<Statement>> initStatements = getCursor().pollMessage("INIT_STATEMENTS");
            Map<Statement, List<Statement>> methodInitStatements = getCursor().pollMessage("METHOD_DECL_STATEMENTS");

            if (initStatements != null) {
                for (Map.Entry<Statement, List<Statement>> objectListEntry : initStatements.entrySet()) {
                    Object statement = objectListEntry.getKey();
                    int statementIndex = bl.getStatements().indexOf(statement);
                    if (statementIndex > -1) {
                        JRightPadded<Boolean> isStatic;
                        if (objectListEntry.getKey() instanceof J.VariableDeclarations && J.Modifier.hasModifier(((J.VariableDeclarations) statement).getModifiers(), J.Modifier.Type.Static)) {
                            isStatic = JRightPadded.build(true).withAfter(Space.format(" "));
                        } else {
                            isStatic = JRightPadded.build(false);
                        }
                        J.Block initBlock = new J.Block(
                                Tree.randomId(),
                                Space.EMPTY,
                                Markers.EMPTY,
                                isStatic,
                                objectListEntry.getValue().stream().map(JRightPadded::build).collect(toList()),
                                Space.EMPTY
                        );
                        bl = maybeAutoFormat(bl, bl.withStatements(ListUtils.insertAll(bl.getStatements(), statementIndex + 1, singletonList(initBlock))),
                                initBlock, ctx, getCursor().getParent(2));
                    }
                }
            } else if (methodInitStatements != null) {
                for (Map.Entry<Statement, List<Statement>> objectListEntry : methodInitStatements.entrySet()) {
                    int statementIndex = bl.getStatements().indexOf(objectListEntry.getKey());
                    if (statementIndex > -1) {
                        bl = maybeAutoFormat(bl, bl.withStatements(ListUtils.insertAll(bl.getStatements(), statementIndex + 1, objectListEntry.getValue())),
                                objectListEntry.getValue().get(objectListEntry.getValue().size() - 1), ctx, getCursor().getParent());
                    }
                }
            }
            return bl;
        }
    }

    private static class AddWarningMessage extends JavaIsoVisitor<String> {
        static <T extends J> T addWarningComment(T nc, String methodName) {
            return (T) new AddWarningMessage().visitNonNull(nc, methodName);
        }

        @Override
        public J.NewClass visitNewClass(J.NewClass newClass, String methodName) {
            String comment = "Did you mean to invoke " + methodName + " method to the collection?";
            return SearchResult.found(newClass, comment);
        }
    }

    private static class FindMethodInvocationInDoubleBrace extends JavaIsoVisitor<AtomicBoolean> {
        /**
         * Find whether any collection content initialization method(e.g. add() or put()) is invoked in the double brace.
         *
         * @param j The subtree to search, supposed to be the 2nd brace (J.Block)
         * @return true if any method invocation found in the double brace, otherwise false.
         */
        static boolean find(J j) {
            return new FindMethodInvocationInDoubleBrace()
                    .reduce(j, new AtomicBoolean()).get();
        }

        @Override
        public J.MethodInvocation visitMethodInvocation(J.MethodInvocation method, AtomicBoolean atomicBoolean) {
            if (atomicBoolean.get() || method.getMethodType() == null) {
                return method;
            }
            JavaType.FullyQualified declaring = method.getMethodType().getDeclaringType();
            if (TypeUtils.isAssignableTo(MAP_TYPE, declaring) || TypeUtils.isAssignableTo(LIST_TYPE, declaring) || TypeUtils.isAssignableTo(SET_TYPE, declaring)) {
                atomicBoolean.set(true);
                return method;
            }

            return super.visitMethodInvocation(method, atomicBoolean);
        }
    }
}
