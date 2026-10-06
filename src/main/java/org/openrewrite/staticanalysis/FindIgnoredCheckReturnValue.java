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

import lombok.EqualsAndHashCode;
import lombok.Value;
import org.jspecify.annotations.Nullable;
import org.openrewrite.*;
import org.openrewrite.java.AnnotationMatcher;
import org.openrewrite.java.JavaIsoVisitor;
import org.openrewrite.java.MethodMatcher;
import org.openrewrite.java.service.AnnotationService;
import org.openrewrite.java.tree.*;
import org.openrewrite.marker.SearchResult;
import org.openrewrite.staticanalysis.java.JavaFileChecker;

import java.util.*;
import java.util.regex.Pattern;

import static java.util.Arrays.asList;

@Value
@EqualsAndHashCode(callSuper = false)
public class FindIgnoredCheckReturnValue extends ScanningRecipe<Set<String>> {

    private static final MethodMatcher MOCKITO_STUBBING_OR_VERIFICATION = new MethodMatcher("org.mockito..* *(..)");
    private static final MethodMatcher ASSERTJ_ASSERT_CONSTRUCTOR = new MethodMatcher("org.assertj.core.api.AbstractAssert <constructor>(..)", true);
    private static final Pattern LOCAL_OR_ANONYMOUS_CLASS = Pattern.compile(".*\\$\\d.*");
    private static final AnnotationMatcher SUPPRESS_CHECK_RETURN_VALUE = new AnnotationMatcher("@java.lang.SuppressWarnings(\"CheckReturnValue\")");
    private static final MethodMatcher FAIL = new MethodMatcher("*..* fail(..)");
    private static final List<String> EXPECTED_EXCEPTION_FUNCTIONAL_INTERFACES = asList(
            "org.junit.jupiter.api.function.Executable",
            "org.junit.function.ThrowingRunnable",
            "org.assertj.core.api.ThrowableAssert$ThrowingCallable");

    @Option(displayName = "Packages treated as `@CheckReturnValue`",
            description = "Packages whose methods should be treated as annotated with `@CheckReturnValue`. " +
                    "Package annotations of dependencies are not part of type attribution, so list packages here " +
                    "that declare `@CheckReturnValue` in a `package-info.java` outside of the repository. " +
                    "Subpackages are not included.",
            example = "org.openrewrite.java.tree",
            required = false)
    @Nullable
    List<String> checkReturnValuePackages;

    String displayName = "Find ignored results of `@CheckReturnValue` methods";

    String description = "Marks invocations whose result is discarded even though the method is annotated with " +
            "`@CheckReturnValue`, either directly or through its enclosing class or package. Any annotation " +
            "named `CheckReturnValue` is recognized, and `@CanIgnoreReturnValue` opts a method or class back out. " +
            "Ignoring such a result is usually a bug, such as calling a method on an immutable object without using " +
            "the returned copy. Calls expected to throw, Mockito stubbing and verification, calls on the current " +
            "instance in custom AssertJ assertion constructors, and code under " +
            "`@SuppressWarnings(\"CheckReturnValue\")` are not marked.";

    @Override
    public Set<String> getInitialValue(ExecutionContext ctx) {
        return checkReturnValuePackages == null ? new HashSet<>() : new HashSet<>(checkReturnValuePackages);
    }

    @Override
    public TreeVisitor<?, ExecutionContext> getScanner(Set<String> acc) {
        return Preconditions.check(new JavaFileChecker<>(), new JavaIsoVisitor<ExecutionContext>() {
            @Override
            public J.CompilationUnit visitCompilationUnit(J.CompilationUnit cu, ExecutionContext ctx) {
                J.Package pkg = cu.getPackageDeclaration();
                if (pkg != null && cu.getSourcePath().endsWith("package-info.java") &&
                        pkg.getAnnotations().stream().anyMatch(a -> "CheckReturnValue".equals(a.getSimpleName()))) {
                    acc.add(pkg.getPackageName());
                }
                return cu;
            }
        });
    }

    @Override
    public TreeVisitor<?, ExecutionContext> getVisitor(Set<String> acc) {
        return Preconditions.check(new JavaFileChecker<>(), new JavaIsoVisitor<ExecutionContext>() {
            @Override
            public J.ClassDeclaration visitClassDeclaration(J.ClassDeclaration classDecl, ExecutionContext ctx) {
                return isSuppressed() ? classDecl : super.visitClassDeclaration(classDecl, ctx);
            }

            @Override
            public J.MethodDeclaration visitMethodDeclaration(J.MethodDeclaration method, ExecutionContext ctx) {
                return isSuppressed() ? method : super.visitMethodDeclaration(method, ctx);
            }

            @Override
            public J.VariableDeclarations visitVariableDeclarations(J.VariableDeclarations multiVariable, ExecutionContext ctx) {
                return isSuppressed() ? multiVariable : super.visitVariableDeclarations(multiVariable, ctx);
            }

            @Override
            public J.MethodInvocation visitMethodInvocation(J.MethodInvocation method, ExecutionContext ctx) {
                J.MethodInvocation m = super.visitMethodInvocation(method, ctx);
                JavaType.Method type = m.getMethodType();
                String message = type == null || type.isConstructor() ? null : ignoredResultMessage(type);
                if (message != null && isResultIgnored(getCursor()) && !MOCKITO_STUBBING_OR_VERIFICATION.matches(m.getSelect()) &&
                        !isSelfCallInAssertJConstructor(m)) {
                    return SearchResult.found(m, message);
                }
                return m;
            }

            @Override
            public J.NewClass visitNewClass(J.NewClass newClass, ExecutionContext ctx) {
                J.NewClass n = super.visitNewClass(newClass, ctx);
                String message = ignoredResultMessage(n.getConstructorType());
                if (message != null && isResultIgnored(getCursor())) {
                    return SearchResult.found(n, message);
                }
                return n;
            }

            @Override
            public J.MemberReference visitMemberReference(J.MemberReference memberRef, ExecutionContext ctx) {
                J.MemberReference mr = super.visitMemberReference(memberRef, ctx);
                String message = ignoredResultMessage(mr.getMethodType());
                if (message != null && discardsResult(mr.getType())) {
                    return SearchResult.found(mr, message);
                }
                return mr;
            }

            private @Nullable String ignoredResultMessage(JavaType.@Nullable Method method) {
                if (method == null || method.getReturnType() == JavaType.Primitive.Void ||
                        TypeUtils.isOfClassType(method.getReturnType(), "java.lang.Void")) {
                    return null;
                }
                String scope = null;
                Boolean policy = annotationPolicy(method.getAnnotations());
                if (policy != null) {
                    scope = "the method";
                }
                for (JavaType.FullyQualified type = method.getDeclaringType(); policy == null && type != null; type = enclosingType(type)) {
                    policy = annotationPolicy(type.getAnnotations());
                    scope = "class `" + type.getClassName() + "`";
                }
                if (policy == null && acc.contains(method.getDeclaringType().getPackageName())) {
                    policy = true;
                    scope = "package `" + method.getDeclaringType().getPackageName() + "`";
                }
                if (!Boolean.TRUE.equals(policy)) {
                    return null;
                }
                String name = method.getName();
                if (method.isConstructor()) {
                    JavaType.FullyQualified created = method.getDeclaringType();
                    JavaType.FullyQualified anonymousSupertype = !created.getFullyQualifiedName().matches(".*\\$\\d+$") ? null :
                            created.getInterfaces().isEmpty() ? created.getSupertype() : created.getInterfaces().get(0);
                    name = anonymousSupertype == null ? "new " + created.getClassName() : "new " + anonymousSupertype.getClassName() + "() {...}";
                }
                return "Result of `" + name + "` is ignored, but `@CheckReturnValue` on " + scope + " requires using it. " +
                        "Use the returned value, remove the call, or annotate the method with `@CanIgnoreReturnValue` " +
                        "if ignoring the result is intended.";
            }

            private JavaType.@Nullable FullyQualified enclosingType(JavaType.FullyQualified type) {
                if (type.getOwningClass() != null || !isLocalOrAnonymous(type)) {
                    return type.getOwningClass();
                }
                return getCursor().getPathAsStream()
                        .filter(J.ClassDeclaration.class::isInstance)
                        .map(c -> ((J.ClassDeclaration) c).getType())
                        .filter(t -> t != null && !isLocalOrAnonymous(t))
                        .findFirst()
                        .orElse(null);
            }

            private boolean isLocalOrAnonymous(JavaType.FullyQualified type) {
                return LOCAL_OR_ANONYMOUS_CLASS.matcher(type.getFullyQualifiedName()).matches();
            }

            private @Nullable Boolean annotationPolicy(List<JavaType.FullyQualified> annotations) {
                for (JavaType.FullyQualified annotation : annotations) {
                    if ("CheckReturnValue".equals(annotation.getClassName())) {
                        return true;
                    }
                    if ("CanIgnoreReturnValue".equals(annotation.getClassName())) {
                        return false;
                    }
                }
                return null;
            }

            private boolean isSuppressed() {
                return service(AnnotationService.class).matches(getCursor(), SUPPRESS_CHECK_RETURN_VALUE);
            }

            private boolean isResultIgnored(Cursor cursor) {
                Object expression = cursor.getValue();
                Cursor parentCursor = cursor.getParentTreeCursor();
                Object parent = parentCursor.getValue();
                if (parent instanceof J.Block) {
                    return !isExpectedToThrow((J.Block) parent, parentCursor);
                }
                if (parent instanceof J.Label || parent instanceof J.If || parent instanceof J.If.Else || parent instanceof Loop) {
                    return true;
                }
                if (parent instanceof J.ForLoop.Control) {
                    return ((J.ForLoop.Control) parent).getCondition() != expression;
                }
                if (parent instanceof J.Case) {
                    J.Case aCase = (J.Case) parent;
                    return aCase.getStatements().contains(expression) ||
                            aCase.getBody() == expression &&
                                    parentCursor.dropParentUntil(p -> p instanceof J.Switch || p instanceof J.SwitchExpression).getValue() instanceof J.Switch;
                }
                if (parent instanceof J.Lambda) {
                    return discardsResult(((J.Lambda) parent).getType());
                }
                return false;
            }

            private boolean isSelfCallInAssertJConstructor(J.MethodInvocation method) {
                Expression select = method.getSelect();
                if (select != null && !(select instanceof J.Identifier &&
                        asList("this", "super").contains(((J.Identifier) select).getSimpleName()))) {
                    return false;
                }
                J.MethodDeclaration enclosing = getCursor().firstEnclosing(J.MethodDeclaration.class);
                return enclosing != null && ASSERTJ_ASSERT_CONSTRUCTOR.matches(enclosing.getMethodType());
            }

            private boolean isExpectedToThrow(J.Block block, Cursor blockCursor) {
                Object parent = blockCursor.getParentTreeCursor().getValue();
                if (!(parent instanceof J.Try) || ((J.Try) parent).getBody() != block || ((J.Try) parent).getCatches().isEmpty()) {
                    return false;
                }
                Statement last = block.getStatements().get(block.getStatements().size() - 1);
                return last instanceof J.MethodInvocation && FAIL.matches((J.MethodInvocation) last);
            }

            private boolean discardsResult(@Nullable JavaType functionalInterface) {
                JavaType.FullyQualified type = TypeUtils.asFullyQualified(functionalInterface);
                if (type == null || EXPECTED_EXCEPTION_FUNCTIONAL_INTERFACES.stream().anyMatch(fqn -> TypeUtils.isOfClassType(type, fqn))) {
                    return false;
                }
                JavaType.Method sam = singleAbstractMethod(type);
                return sam != null && sam.getReturnType() == JavaType.Primitive.Void;
            }

            private JavaType.@Nullable Method singleAbstractMethod(JavaType.FullyQualified type) {
                for (JavaType.Method method : type.getMethods()) {
                    if (method.hasFlags(Flag.Abstract) && !method.hasFlags(Flag.Default) && !method.hasFlags(Flag.Static) &&
                            !asList("equals", "hashCode", "toString").contains(method.getName())) {
                        return method;
                    }
                }
                for (JavaType.FullyQualified superInterface : type.getInterfaces()) {
                    JavaType.Method sam = singleAbstractMethod(superInterface);
                    if (sam != null) {
                        return sam;
                    }
                }
                return null;
            }
        });
    }
}
