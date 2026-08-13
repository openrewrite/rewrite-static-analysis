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
import org.openrewrite.Recipe;
import org.openrewrite.Tree;
import org.openrewrite.TreeVisitor;
import org.openrewrite.internal.ListUtils;
import org.openrewrite.internal.StringUtils;
import org.openrewrite.java.JavaIsoVisitor;
import org.openrewrite.java.JavaVisitor;
import org.openrewrite.java.JavadocVisitor;
import org.openrewrite.java.MethodMatcher;
import org.openrewrite.java.tree.*;

import java.util.*;

import static java.util.Collections.*;
import static java.util.Objects.requireNonNull;
import static org.openrewrite.java.tree.J.Modifier.Type.*;

public class UnnecessaryThrows extends Recipe {

    @Getter
    final String displayName = "Unnecessary throws";

    @Getter
    final String description = "Remove unnecessary `throws` declarations. This recipe " +
            "will only remove unused, checked exceptions if:\n" +
            "\n" +
            " - The declaring class or the method declaration is `final`.\n" +
            " - The method declaration is `static` or `private`.\n" +
            " - The method overrides a method declaration in a super class " +
            "and the super class does not throw the exception.\n" +
            " - The method is `public` and the exception is not " +
            "documented via a JavaDoc as a `@throws` tag.\n\n" +
            "The `throws` declaration is retained on overridable methods " +
            "(package-private and `protected` methods on non-`final` classes), and on `public` " +
            "methods overridden within the same source file, so that a subclass override which " +
            "does throw the exception keeps compiling. Overrides in other source files cannot be " +
            "detected without a scanning recipe and are therefore not accounted for.\n\n" +
            "When a `throws` declaration is removed, any `@throws` or `@exception` " +
            "JavaDoc tag documenting that exception is removed along with it, so that " +
            "the documentation does not describe an exception the method no longer declares.\n\n" +
            "Declaring exceptions that are never thrown misleads callers into " +
            "writing unnecessary error-handling code and obscures the method's " +
            "true behavior.";

    @Getter
    final Set<String> tags = singleton("RSPEC-S1130");

    @Override
    public TreeVisitor<?, ExecutionContext> getVisitor() {
        return new JavaIsoVisitor<ExecutionContext>() {
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
                        public J.Try.Resource visitTryResource(J.Try.Resource tryResource, ExecutionContext ctx) {
                            TypedTree resource = tryResource.getVariableDeclarations();

                            JavaType.FullyQualified resourceType = TypeUtils.asFullyQualified(resource.getType());
                            if (resourceType != null) {
                                // Find the close() method on the resource type to get its actual thrown exceptions
                                for (JavaType.Method method : resourceType.getMethods()) {
                                    if ("close".equals(method.getName()) && method.getParameterTypes().isEmpty()) {
                                        removeThrownTypes(method);
                                        break;
                                    }
                                }
                            }

                            return super.visitTryResource(tryResource, ctx);
                        }

                        @Override
                        public J.Throw visitThrow(J.Throw thrown, ExecutionContext ctx) {
                            JavaType.FullyQualified type = TypeUtils.asFullyQualified(thrown.getException().getType());
                            if (type != null) {
                                unusedThrows.removeIf(t -> TypeUtils.isAssignableTo(t, type));
                            }
                            return thrown;
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

                        private void removeThrownTypes(JavaType.@Nullable Method type) {
                            if (type != null) {
                                for (JavaType thrownException : type.getThrownExceptions()) {
                                    unusedThrows.removeIf(t -> TypeUtils.isAssignableTo(t, thrownException));
                                }
                            }
                        }
                    }.visit(m, ctx, requireNonNull(getCursor().getParent()));

                    if (!unusedThrows.isEmpty()) {
                        MethodMatcher originalMethodMatcher = new MethodMatcher(m);

                        JavaType.Method replacementMethodType = m.getMethodType().withThrownExceptions(ListUtils.map(m.getMethodType().getThrownExceptions(), t -> {
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
                                if (originalMethodMatcher.matches(invocation)) {
                                    invocation =  invocation.withMethodType(replacementMethodType)
                                            .withName(invocation.getName().withType(replacementMethodType));
                                }
                                return super.visitMethodInvocation(invocation, ctx);
                            }
                        });
                        doAfterVisit(new UnnecessaryCatch(true, false).getVisitor());
                    }
                }

                return m;
            }

            /**
             * Drop the `@throws`/`@exception` tags documenting exceptions that were just removed from
             * the `throws` clause. Only reachable for methods whose javadoc does not guard removal in
             * the first place, i.e. `private`, `static` and `final` methods.
             */
            private J.MethodDeclaration removeJavadocThrows(J.MethodDeclaration m, Set<JavaType.FullyQualified> removed, ExecutionContext ctx) {
                if (m.getComments().stream().noneMatch(Javadoc.DocComment.class::isInstance)) {
                    return m;
                }
                Cursor parent = getCursor().getParentTreeCursor();
                return m.withComments(ListUtils.map(m.getComments(), c -> {
                    if (c instanceof Javadoc.DocComment) {
                        return (Comment) new RemoveThrowsTagVisitor(removed).visitNonNull((Javadoc.DocComment) c, ctx, parent);
                    }
                    return c;
                }));
            }

            private Set<JavaType.FullyQualified> findExceptionCandidates(J.@Nullable MethodDeclaration method) {

                if (method == null || method.getMethodType() == null || method.isAbstract() || method.isConstructor()) {
                    return emptySet();
                }

                // Do not change the API of methods that may be overridden by a subclass
                // (package-private and protected, non-final, on a non-final class)
                if (!method.hasModifier(Private) && !method.hasModifier(Public) &&
                        !method.hasModifier(Static) && !method.hasModifier(Final)) {
                    J.ClassDeclaration cd = getCursor().firstEnclosing(J.ClassDeclaration.class);
                    if (cd != null && !cd.hasModifier(Final)) {
                        return emptySet();
                    }
                }

                //Collect all checked exceptions.
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

                //noinspection ConstantConditions
                if ((method.getMethodType().getDeclaringType() != null && method.getMethodType().getDeclaringType().getFlags().contains(Flag.Final)) ||
                        method.isAbstract() || method.hasModifier(Static) ||
                        method.hasModifier(Private) ||
                        method.hasModifier(Final)) {
                    //Consider all checked exceptions as candidates if the type/method are final or the method is private or static.
                    return candidates;
                }

                //Remove any candidates that are defined in an overridden method.
                Optional<JavaType.Method> superMethod = TypeUtils.findOverriddenMethod(method.getMethodType());
                if (superMethod.isPresent()) {
                    JavaType.Method baseMethod = superMethod.get();
                    baseMethod.getThrownExceptions();
                    for (JavaType baseException : baseMethod.getThrownExceptions()) {
                        if (baseException instanceof JavaType.FullyQualified) {
                            candidates.remove(baseException);
                        }
                    }
                }

                // Retain exceptions still declared by an override in the same compilation unit.
                // Cross-file overrides can't be detected without a scanning recipe (see description).
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
                    //Remove any candidates that are defined in Javadocs for the method.
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
        };
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
                //noinspection DataFlowIssue
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
