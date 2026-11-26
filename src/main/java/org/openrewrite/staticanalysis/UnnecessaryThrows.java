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
import org.openrewrite.Preconditions;
import org.openrewrite.ScanningRecipe;
import org.openrewrite.Tree;
import org.openrewrite.TreeVisitor;
import org.openrewrite.internal.ListUtils;
import org.openrewrite.internal.StringUtils;
import org.openrewrite.java.JavaIsoVisitor;
import org.openrewrite.java.JavaVisitor;
import org.openrewrite.java.JavadocVisitor;
import org.openrewrite.java.MethodMatcher;
import org.openrewrite.java.tree.*;
import org.openrewrite.staticanalysis.java.JavaFileChecker;

import java.nio.file.Path;
import java.util.*;

import static java.util.Collections.*;
import static java.util.Objects.requireNonNull;

import static org.openrewrite.java.tree.J.Modifier.Type.*;

public class UnnecessaryThrows extends ScanningRecipe<UnnecessaryThrows.Accumulator> {

    @Getter
    final String displayName = "Unnecessary throws";

    @Getter
    final String description = "Remove unnecessary `throws` declarations. This recipe " +
            "will only remove unused, checked exceptions from a method when no other source file in the " +
            "repository depends on them: no call inside a `try` that catches a related checked exception, no " +
            "method reference to the method and no override of it. Callers in the same source file that catch " +
            "the removed exception are cleaned up along with it. A method that another source file depends on " +
            "only loses an exception that a supertype in the same `throws` clause already covers, as " +
            "`FileNotFoundException` in `throws IOException, FileNotFoundException`, unless a JavaDoc `@throws` " +
            "tag names it. Code outside the repository is not seen, so on a published library a caller that " +
            "catches the exception or a subclass that overrides the method while declaring it no longer compiles.\n\n" +
            "The `throws` declaration is also retained:\n" +
            "\n" +
            " - On methods overridden within the same source file, so that an override which does throw " +
            "the exception keeps compiling.\n" +
            " - When an overridden method declares the same exception, or a non-`private`, non-`static`, " +
            "non-`final` method documents it via a JavaDoc `@throws` tag.\n" +
            " - When the method body contains a call whose type does not fully resolve, because such a " +
            "call may throw the exception.\n\n" +
            "When a `throws` declaration is removed, any `@throws` or `@exception` " +
            "JavaDoc tag documenting that exception is removed along with it, so that " +
            "the documentation does not describe an exception the method no longer declares.\n\n" +
            "Declaring exceptions that are never thrown misleads callers into " +
            "writing unnecessary error-handling code and obscures the method's " +
            "true behavior.";

    @Getter
    final Set<String> tags = singleton("RSPEC-S1130");

    public static class Accumulator {
        // Method key -> files with a dependency on the method's `throws`
        final Map<String, Set<Path>> dependentFiles = new HashMap<>();

        void add(String key, Path file) {
            dependentFiles.computeIfAbsent(key, k -> new HashSet<>()).add(file);
        }

        boolean isDependedOnOutside(JavaType.Method method, Path file) {
            return isDependedOnOutside(methodKey(method), file) ||
                    isDependedOnOutside(unresolvedKey(method.getName()), file);
        }

        private boolean isDependedOnOutside(String key, Path file) {
            Set<Path> files = dependentFiles.get(key);
            return files != null && files.stream().anyMatch(f -> !f.equals(file));
        }
    }

    // Name and arity rather than the full signature: overloads only make the scan more conservative, and
    // a signature built from a declaration loses parameters typed by a type variable.
    private static String methodKey(JavaType.Method method) {
        return methodKey(method.getDeclaringType(), method.getName(), method.getParameterTypes().size());
    }

    private static String methodKey(JavaType.FullyQualified declaringType, String name, int arity) {
        return declaringType.getFullyQualifiedName() + "#" + name + "/" + arity;
    }

    // A call or method reference that does not resolve could be of any method with that name
    private static String unresolvedKey(String name) {
        return "?#" + name;
    }

    @Override
    public Accumulator getInitialValue(ExecutionContext ctx) {
        return new Accumulator();
    }

    @Override
    public TreeVisitor<?, ExecutionContext> getScanner(Accumulator acc) {
        return new JavaIsoVisitor<ExecutionContext>() {
            @Override
            public J.MethodInvocation visitMethodInvocation(J.MethodInvocation method, ExecutionContext ctx) {
                JavaType.Method type = method.getMethodType();
                if (!TypeUtils.isWellFormedType(type)) {
                    if (isInTryCatching(null)) {
                        acc.add(unresolvedKey(method.getSimpleName()), sourcePath());
                    }
                } else if (!type.getThrownExceptions().isEmpty() && isInTryCatching(type.getThrownExceptions())) {
                    acc.add(methodKey(type), sourcePath());
                }
                return super.visitMethodInvocation(method, ctx);
            }

            @Override
            public J.MemberReference visitMemberReference(J.MemberReference memberRef, ExecutionContext ctx) {
                JavaType.Method type = memberRef.getMethodType();
                if (type == null) {
                    acc.add(unresolvedKey(memberRef.getReference().getSimpleName()), sourcePath());
                } else if (!type.getThrownExceptions().isEmpty()) {
                    acc.add(methodKey(type), sourcePath());
                }
                return super.visitMemberReference(memberRef, ctx);
            }

            @Override
            public J.MethodDeclaration visitMethodDeclaration(J.MethodDeclaration method, ExecutionContext ctx) {
                JavaType.Method type = method.getMethodType();
                // Only an override that declares exceptions depends on the overridden method's `throws`
                if (type != null && !type.getThrownExceptions().isEmpty() && !method.isConstructor() &&
                        !method.hasModifier(Private) && !method.hasModifier(Static)) {
                    // Every method of a supertype with this name and arity may be overridden here
                    addSupertypes(type.getDeclaringType(), type.getName(), type.getParameterTypes().size(), new HashSet<>());
                }
                return super.visitMethodDeclaration(method, ctx);
            }

            private void addSupertypes(JavaType.FullyQualified type, String name, int arity, Set<String> seen) {
                List<JavaType.FullyQualified> supertypes = new ArrayList<>(type.getInterfaces());
                if (type.getSupertype() != null) {
                    supertypes.add(type.getSupertype());
                }
                for (JavaType.FullyQualified supertype : supertypes) {
                    if (seen.add(supertype.getFullyQualifiedName())) {
                        acc.add(methodKey(supertype, name, arity), sourcePath());
                        addSupertypes(supertype, name, arity, seen);
                    }
                }
            }

            // Whether an enclosing `try` catches a checked type related to one of the thrown exceptions, or to
            // any exception when they are unknown. `catch (Exception e)` and `catch (Throwable t)` stay valid.
            private boolean isInTryCatching(@Nullable List<JavaType> thrown) {
                for (Cursor c = getCursor().getParentTreeCursor(); c.getValue() instanceof J; c = c.getParentTreeCursor()) {
                    if (c.getValue() instanceof J.Try) {
                        for (J.Try.Catch aCatch : ((J.Try) c.getValue()).getCatches()) {
                            JavaType caught = aCatch.getParameter().getType();
                            if (!TypeUtils.isOfClassType(caught, "java.lang.Exception") &&
                                    !TypeUtils.isOfClassType(caught, "java.lang.Throwable") &&
                                    (thrown == null || !TypeUtils.isWellFormedType(caught) ||
                                            thrown.stream().anyMatch(t -> TypeUtils.isAssignableTo(caught, t) || TypeUtils.isAssignableTo(t, caught)))) {
                                return true;
                            }
                        }
                    }
                }
                return false;
            }

            private Path sourcePath() {
                return getCursor().firstEnclosingOrThrow(JavaSourceFile.class).getSourcePath();
            }
        };
    }

    @Override
    public TreeVisitor<?, ExecutionContext> getVisitor(Accumulator acc) {
        // Sound only where the compiler enforces catch-or-declare: elsewhere a checked exception can be thrown
        // without any callee declaring it, so an undeclared exception is no evidence that the `throws` is unused.
        return Preconditions.check(new JavaFileChecker<>(), new JavaIsoVisitor<ExecutionContext>() {
            @Override
            public J.MethodDeclaration visitMethodDeclaration(J.MethodDeclaration method, ExecutionContext ctx) {
                J.MethodDeclaration m = super.visitMethodDeclaration(method, ctx);
                Set<JavaType.FullyQualified> unusedThrows = findExceptionCandidates(method);

                if (!unusedThrows.isEmpty()) {

                    new JavaIsoVisitor<ExecutionContext>() {

                        @Override
                        public @Nullable J visit(@Nullable Tree tree, ExecutionContext ctx) {
                            if (unusedThrows.isEmpty()) {
                                return (J) tree;
                            }
                            return super.visit(tree, ctx);
                        }

                        @Override
                        public J.VariableDeclarations visitVariableDeclarations(J.VariableDeclarations multiVariable, ExecutionContext ctx) {
                            // Lombok's `@Cleanup` closes the variable in a generated `finally`, a call the LST doesn't show
                            if (multiVariable.getLeadingAnnotations().stream().anyMatch(a -> "Cleanup".equals(a.getSimpleName()))) {
                                unusedThrows.clear();
                            }
                            return super.visitVariableDeclarations(multiVariable, ctx);
                        }

                        @Override
                        public J.Try.Resource visitTryResource(J.Try.Resource tryResource, ExecutionContext ctx) {
                            // A resource is closed implicitly at the end of the block, so its close() is a call
                            // site. It is commonly inherited, so it has to be looked up through the supertypes.
                            JavaType.FullyQualified resourceType = TypeUtils.asFullyQualified(tryResource.getVariableDeclarations().getType());
                            Optional<JavaType.Method> close = TypeUtils.findDeclaredMethod(resourceType, "close", emptyList());
                            if (close.isPresent()) {
                                removeThrownTypes(close.get());
                            } else {
                                unusedThrows.clear();
                            }

                            return super.visitTryResource(tryResource, ctx);
                        }

                        @Override
                        public J.Throw visitThrow(J.Throw thrown, ExecutionContext ctx) {
                            JavaType type = thrown.getException().getType();
                            if (!TypeUtils.isWellFormedType(type)) {
                                unusedThrows.clear();
                                return thrown;
                            }
                            unusedThrows.removeIf(t -> TypeUtils.isAssignableTo(t, type));
                            return super.visitThrow(thrown, ctx);
                        }

                        @Override
                        public J.MethodInvocation visitMethodInvocation(J.MethodInvocation method, ExecutionContext ctx) {
                            removeThrownTypes(method.getMethodType());
                            return super.visitMethodInvocation(method, ctx);
                        }

                        @Override
                        public J.NewClass visitNewClass(J.NewClass newClass, ExecutionContext ctx) {
                            removeThrownTypes(newClass.getConstructorType());
                            return super.visitNewClass(newClass, ctx);
                        }

                        // A call site that does not fully resolve is indistinguishable from one that throws
                        // nothing, so it counts as possibly throwing every candidate. `isWellFormedType` does
                        // not reach a method's thrown exceptions, which are checked here.
                        private void removeThrownTypes(JavaType.@Nullable Method type) {
                            if (!TypeUtils.isWellFormedType(type)) {
                                unusedThrows.clear();
                                return;
                            }
                            for (JavaType thrownException : type.getThrownExceptions()) {
                                if (!TypeUtils.isWellFormedType(thrownException)) {
                                    unusedThrows.clear();
                                    return;
                                }
                                unusedThrows.removeIf(t -> TypeUtils.isAssignableTo(t, thrownException));
                            }
                        }
                    }.visit(m, ctx, requireNonNull(getCursor().getParent()));

                    if (!unusedThrows.isEmpty()) {
                        MethodMatcher originalMethodMatcher = new MethodMatcher(m);
                        JavaType.Method originalMethodType = m.getMethodType();
                        // `MethodMatcher` misses calls of a method taking a `T`, so also match a unique name and arity
                        boolean uniqueNameAndArity = originalMethodType.getDeclaringType().getMethods().stream()
                                .filter(other -> hasSameNameAndArity(other, originalMethodType))
                                .count() == 1;

                        JavaType.Method replacementMethodType = originalMethodType.withThrownExceptions(ListUtils.map(originalMethodType.getThrownExceptions(), t -> {
                            JavaType.FullyQualified type = TypeUtils.asFullyQualified(t);
                            return type != null && unusedThrows.contains(type) ? null : t;
                        }));
                        m = m.withThrows(ListUtils.map(m.getThrows(), t -> {
                                    JavaType.FullyQualified type = TypeUtils.asFullyQualified(t.getType());
                                    if (type != null && unusedThrows.contains(type)) {
                                        maybeRemoveImport(type);
                                        return null;
                                    }
                                    return t;
                                }))
                                .withMethodType(replacementMethodType)
                                .withName(m.getName().withType(replacementMethodType));

                        // The `@throws` tags documenting the now-removed exceptions would otherwise
                        // be left behind describing exceptions the method no longer declares.
                        m = removeJavadocThrows(m, unusedThrows, ctx);

                        // Remove the thrown exceptions from the method type, such that UnnecessaryCatch can continue
                        doAfterVisit(new JavaIsoVisitor<ExecutionContext>() {
                            @Override
                            public J.MethodInvocation visitMethodInvocation(J.MethodInvocation invocation, ExecutionContext ctx) {
                                JavaType.Method invoked = invocation.getMethodType();
                                if (originalMethodMatcher.matches(invocation) || (uniqueNameAndArity && invoked != null &&
                                        hasSameNameAndArity(invoked, originalMethodType) &&
                                        TypeUtils.isOfClassType(invoked.getDeclaringType(), originalMethodType.getDeclaringType().getFullyQualifiedName()))) {
                                    // Keep the call's substituted parameter and return types
                                    JavaType.Method retyped = requireNonNull(invoked).withThrownExceptions(ListUtils.filter(invoked.getThrownExceptions(), t ->
                                            !(t instanceof JavaType.FullyQualified && unusedThrows.contains(t))));
                                    invocation = invocation.withMethodType(retyped).withName(invocation.getName().withType(retyped));
                                }
                                return super.visitMethodInvocation(invocation, ctx);
                            }
                        });
                        // Only catches that no longer compile are removed; `catch (Exception e)` and
                        // `catch (Throwable t)` remain valid, and removing those would alter behavior.
                        doAfterVisit(new UnnecessaryCatch(false, false).getVisitor());
                    }
                }

                return m;
            }

            /**
             * Drop the `@throws`/`@exception` tags documenting exceptions that were just removed from
             * the `throws` clause.
             */
            private J.MethodDeclaration removeJavadocThrows(J.MethodDeclaration m, Set<JavaType.FullyQualified> removed, ExecutionContext ctx) {
                if (m.getComments().stream().noneMatch(Javadoc.DocComment.class::isInstance)) {
                    return m;
                }
                Cursor parent = getCursor().getParentTreeCursor();
                return m.withComments(ListUtils.map(m.getComments(), c -> {
                    if (c instanceof Javadoc.DocComment) {
                        return (Comment) new RemoveThrowsTagVisitor(removed).visit((Javadoc.DocComment) c, ctx, parent);
                    }
                    return c;
                }));
            }

            private Set<JavaType.FullyQualified> findExceptionCandidates(J.@Nullable MethodDeclaration method) {

                if (method == null || method.getMethodType() == null || method.isAbstract() || method.isConstructor()) {
                    return emptySet();
                }

                Set<JavaType.FullyQualified> candidates = new TreeSet<>(Comparator.comparing(JavaType.FullyQualified::getFullyQualifiedName));

                if (method.getThrows() != null) {
                    for (NameTree exception : method.getThrows()) {
                        if (exception.getType() == null || exception.getType() instanceof JavaType.Unknown) {
                            return emptySet();
                        }
                        if (exception.getType() instanceof JavaType.FullyQualified && !TypeUtils.isAssignableTo("java.lang.RuntimeException", exception.getType())) {
                            candidates.add(TypeUtils.asFullyQualified(exception.getType()));
                        }
                    }
                }

                if (candidates.isEmpty()) {
                    return emptySet();
                }

                // Callers and overrides in other files can't be updated, so methods they depend on only
                // lose an exception that a supertype declared alongside it already covers.
                if (acc.isDependedOnOutside(method.getMethodType(), getCursor().firstEnclosingOrThrow(JavaSourceFile.class).getSourcePath())) {
                    List<NameTree> declared = requireNonNull(method.getThrows());
                    candidates.removeIf(c -> declared.stream().noneMatch(d ->
                            !TypeUtils.isOfType(d.getType(), c) && TypeUtils.isAssignableTo(d.getType(), c)));
                    if (candidates.isEmpty()) {
                        return emptySet();
                    }
                } else if ((method.getMethodType().getDeclaringType() != null && method.getMethodType().getDeclaringType().getFlags().contains(Flag.Final)) ||
                        method.hasModifier(Static) || method.hasModifier(Private) || method.hasModifier(Final)) {
                    return candidates;
                }

                Optional<JavaType.Method> superMethod = TypeUtils.findOverriddenMethod(method.getMethodType());
                if (superMethod.isPresent()) {
                    JavaType.Method baseMethod = superMethod.get();
                    for (JavaType baseException : baseMethod.getThrownExceptions()) {
                        if (baseException instanceof JavaType.FullyQualified) {
                            candidates.remove(baseException);
                        }
                    }
                }

                // Retain exceptions still declared by an override in the same compilation unit.
                J.CompilationUnit cu = getCursor().firstEnclosing(J.CompilationUnit.class);
                JavaType.Method methodType = method.getMethodType();
                if (cu != null && !candidates.isEmpty()) {
                    new JavaIsoVisitor<Set<JavaType.FullyQualified>>() {
                        @Override
                        public J.MethodDeclaration visitMethodDeclaration(J.MethodDeclaration md, Set<JavaType.FullyQualified> cs) {
                            JavaType.Method omt = md.getMethodType();
                            if (omt != null && omt != methodType &&
                                    TypeUtils.findOverriddenMethod(omt).map(sm -> TypeUtils.isOfType(sm, methodType)).orElse(false)) {
                                for (JavaType thrown : omt.getThrownExceptions()) {
                                    cs.removeIf(t -> TypeUtils.isAssignableTo(t, thrown));
                                }
                            }
                            return super.visitMethodDeclaration(md, cs);
                        }
                    }.visit(cu, candidates);
                }

                if (!candidates.isEmpty()) {
                    new JavaVisitor<Set<JavaType.FullyQualified>>() {
                        @Override
                        protected JavadocVisitor<Set<JavaType.FullyQualified>> getJavadocVisitor() {
                            return new JavadocVisitor<Set<JavaType.FullyQualified>>(this) {
                                @Override
                                public Javadoc visitThrows(Javadoc.Throws aThrows, Set<JavaType.FullyQualified> candidates) {
                                    if (aThrows.getExceptionName() instanceof TypeTree) {
                                        JavaType.FullyQualified exceptionType = TypeUtils.asFullyQualified(((TypeTree) aThrows.getExceptionName()).getType());
                                        if (exceptionType != null) {
                                            candidates.remove(exceptionType);
                                        }
                                    }
                                    return super.visitThrows(aThrows, candidates);
                                }
                            };
                        }
                    }.visit(method, candidates);
                }
                return candidates;
            }
        });
    }

    private static boolean hasSameNameAndArity(JavaType.Method a, JavaType.Method b) {
        return a.getName().equals(b.getName()) && a.getParameterTypes().size() == b.getParameterTypes().size();
    }

    private static class RemoveThrowsTagVisitor extends JavadocVisitor<ExecutionContext> {
        private final Set<JavaType.FullyQualified> removed;

        RemoveThrowsTagVisitor(Set<JavaType.FullyQualified> removed) {
            super(new JavaIsoVisitor<>());
            this.removed = removed;
        }

        @Override
        public @Nullable Javadoc visitDocComment(Javadoc.DocComment javadoc, ExecutionContext ctx) {
            List<Javadoc> body = javadoc.getBody();
            List<Javadoc> newBody = new ArrayList<>(body.size());
            boolean changed = false;

            // Walk backwards so that the line break and margin preceding a removed tag, along with
            // any continuation lines belonging to its description, are dropped with it.
            boolean dropUntilLineBreak = false;
            for (int i = body.size() - 1; i >= 0; i--) {
                Javadoc doc = body.get(i);
                if (dropUntilLineBreak) {
                    if (doc instanceof Javadoc.LineBreak) {
                        dropUntilLineBreak = false;
                    }
                } else if (doc instanceof Javadoc.Throws && isRemoved((Javadoc.Throws) doc)) {
                    changed = true;
                    dropUntilLineBreak = true;
                } else {
                    newBody.add(doc);
                }
            }

            if (!changed) {
                return javadoc;
            }
            reverse(newBody);
            // Removing the last tag can strand the blank line that separated the description from
            // the tag block, so collapse any trailing empty lines back onto the closing delimiter.
            trimTrailingBlankLines(newBody);
            if (newBody.isEmpty() || RemoveJavaDocAuthorTag.isBlank(getCursor(), newBody)) {
                return null;
            }
            return javadoc.withBody(newBody);
        }

        private void trimTrailingBlankLines(List<Javadoc> body) {
            Javadoc.LineBreak lastLineBreak = null;
            while (!body.isEmpty()) {
                Javadoc last = body.get(body.size() - 1);
                if (last instanceof Javadoc.LineBreak) {
                    if (lastLineBreak == null) {
                        lastLineBreak = (Javadoc.LineBreak) last;
                    }
                } else if (!(last instanceof Javadoc.Text) || !StringUtils.isBlank(((Javadoc.Text) last).getText())) {
                    break;
                }
                body.remove(body.size() - 1);
            }
            if (!body.isEmpty() && lastLineBreak != null) {
                // The margin carries the leading `*` of the line that followed; the closing `*/`
                // supplies its own, so drop it to avoid printing a stray asterisk.
                String margin = lastLineBreak.getMargin();
                body.add(margin.endsWith("*") ?
                        lastLineBreak.withMargin(margin.substring(0, margin.length() - 1)) :
                        lastLineBreak);
            }
        }

        private boolean isRemoved(Javadoc.Throws aThrows) {
            if (aThrows.getExceptionName() instanceof TypeTree) {
                TypeTree exceptionName = (TypeTree) aThrows.getExceptionName();
                JavaType.FullyQualified type = TypeUtils.asFullyQualified(exceptionName.getType());
                if (type != null) {
                    return removed.contains(type);
                }
                // The tag may name an exception the compiler could not attribute; fall back to the
                // simple name so an unattributed tag is not left behind.
                if (exceptionName instanceof J.Identifier) {
                    String simpleName = ((J.Identifier) exceptionName).getSimpleName();
                    for (JavaType.FullyQualified fq : removed) {
                        if (fq.getClassName().equals(simpleName)) {
                            return true;
                        }
                    }
                }
            }
            return false;
        }
    }
}
