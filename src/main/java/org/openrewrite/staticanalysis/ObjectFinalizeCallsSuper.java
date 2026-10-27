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
import org.openrewrite.ExecutionContext;
import org.openrewrite.Preconditions;
import org.openrewrite.ScanningRecipe;
import org.openrewrite.TreeVisitor;
import org.openrewrite.internal.ListUtils;
import org.openrewrite.java.JavaIsoVisitor;
import org.openrewrite.java.JavaTemplate;
import org.openrewrite.java.MethodMatcher;
import org.openrewrite.java.search.DeclaresMethod;
import org.openrewrite.java.tree.J;
import org.openrewrite.java.tree.JContainer;
import org.openrewrite.java.tree.JRightPadded;
import org.openrewrite.java.tree.JavaType;
import org.openrewrite.java.tree.NameTree;
import org.openrewrite.java.tree.Space;
import org.openrewrite.java.tree.TypeUtils;
import org.openrewrite.marker.Markers;
import org.openrewrite.staticanalysis.groovy.GroovyFileChecker;
import org.openrewrite.staticanalysis.java.JavaFileChecker;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;

import static java.util.Collections.emptyList;
import static java.util.Collections.singleton;
import static java.util.Objects.requireNonNull;
import static org.openrewrite.Tree.randomId;

public class ObjectFinalizeCallsSuper extends ScanningRecipe<Map<String, JavaType.Method>> {
    private static final MethodMatcher FINALIZE_METHOD_MATCHER = new MethodMatcher("java.lang.Object finalize()", true);

    @Getter
    final String displayName = "`finalize()` calls super";

    @Getter
    final String description = "Overrides of `Object#finalize()` should call super. " +
            "Skipping the super call can prevent parent classes from releasing critical system resources during garbage collection.";

    @Getter
    final Set<String> tags = singleton("RSPEC-S1114");

    @Override
    public Map<String, JavaType.Method> getInitialValue(ExecutionContext ctx) {
        return new HashMap<>();
    }

    // A subclass sees the parse-time `throws` of its parent, so record which overrides this run widens before editing
    @Override
    public TreeVisitor<?, ExecutionContext> getScanner(Map<String, JavaType.Method> widened) {
        return Preconditions.check(Preconditions.and(new JavaFileChecker<>(), new DeclaresMethod<>(FINALIZE_METHOD_MATCHER)), new JavaIsoVisitor<ExecutionContext>() {
            @Override
            public J.MethodDeclaration visitMethodDeclaration(J.MethodDeclaration method, ExecutionContext ctx) {
                J.MethodDeclaration md = super.visitMethodDeclaration(method, ctx);
                JavaType.Method superFinalize = superFinalizeToCall(md);
                if (superFinalize != null && md.getThrows() == null) {
                    widened.put(requireNonNull(md.getMethodType()).getDeclaringType().getFullyQualifiedName(), superFinalize);
                }
                return md;
            }
        });
    }

    @Override
    public TreeVisitor<?, ExecutionContext> getVisitor(Map<String, JavaType.Method> widened) {
        // Kotlin's `Any` has no `finalize()` to call on `super`, and C# forbids calling `base.Finalize()`
        return Preconditions.check(Preconditions.and(
                Preconditions.or(new JavaFileChecker<>(), new GroovyFileChecker<>()),
                new DeclaresMethod<>(FINALIZE_METHOD_MATCHER)), new JavaIsoVisitor<ExecutionContext>() {
            @Override
            public J.MethodDeclaration visitMethodDeclaration(J.MethodDeclaration method, ExecutionContext ctx) {
                J.MethodDeclaration md = super.visitMethodDeclaration(method, ctx);
                JavaType.Method superFinalize = superFinalizeToCall(md);
                if (superFinalize == null) {
                    return md;
                }
                JavaType.Method methodType = requireNonNull(md.getMethodType());
                J.Block body = requireNonNull(md.getBody());
                List<JavaType> superThrown = thrownAfterRun(superFinalize, widened);

                // `Throwable` is checked in Java but not in Groovy
                if (getCursor().firstEnclosing(J.CompilationUnit.class) != null) {
                    List<NameTree> throwz = md.getThrows();
                    if (throwz == null) {
                        md = declareThrows(md, methodType, superThrown);
                    } else if (superThrown.stream().anyMatch(thrown -> throwz.stream().noneMatch(t -> TypeUtils.isAssignableTo(t.getType(), thrown)))) {
                        // An override may only declare what it overrides declares, so a narrower throws clause has no
                        // legal way to take the added call
                        return md;
                    }
                }

                md = JavaTemplate.builder("super.finalize()")
                        .build()
                        .apply(updateCursor(md), body.getCoordinates().lastStatement());
                // The template can't attribute `super` against a superclass it only knows from the type table
                J.Block withCall = requireNonNull(md.getBody());
                JavaType.Method calledFinalize = superFinalize.withThrownExceptions(superThrown);
                return md.withBody(withCall.withStatements(ListUtils.mapLast(withCall.getStatements(), s -> {
                    J.MethodInvocation call = (J.MethodInvocation) s;
                    return call.withMethodType(calledFinalize)
                            .withName(call.getName().withType(calledFinalize))
                            .withSelect(((J.Identifier) requireNonNull(call.getSelect())).withType(methodType.getDeclaringType().getSupertype()));
                })));
            }

            // Built by hand, as a template could not attribute exception types declared in the project
            private J.MethodDeclaration declareThrows(J.MethodDeclaration md, JavaType.Method methodType, List<JavaType> exceptions) {
                if (exceptions.isEmpty()) {
                    return md;
                }
                List<JRightPadded<NameTree>> declared = new ArrayList<>(exceptions.size());
                for (JavaType thrown : exceptions) {
                    JavaType.FullyQualified exception = (JavaType.FullyQualified) thrown;
                    String className = exception.getClassName();
                    declared.add(JRightPadded.build(new J.Identifier(randomId(), Space.SINGLE_SPACE, Markers.EMPTY,
                            emptyList(), className.substring(className.lastIndexOf('.') + 1), exception, null)));
                    maybeAddImport(exception);
                }
                return md.getPadding().withThrows(JContainer.build(Space.SINGLE_SPACE, declared, Markers.EMPTY))
                        .withMethodType(methodType.withThrownExceptions(exceptions));
            }
        });
    }

    // The overridden `finalize()` this method will call, or null when the method is left alone
    private static JavaType.@Nullable Method superFinalizeToCall(J.MethodDeclaration md) {
        JavaType.Method methodType = md.getMethodType();
        if (methodType == null || md.getBody() == null || !FINALIZE_METHOD_MATCHER.matches(methodType) || hasSuperFinalizeMethodInvocation(md)) {
            return null;
        }
        JavaType.Method superFinalize = TypeUtils.findOverriddenMethod(methodType).orElse(null);
        if (superFinalize == null || superFinalize.getThrownExceptions().stream().anyMatch(t -> TypeUtils.asFullyQualified(t) == null)) {
            return null;
        }
        return superFinalize;
    }

    // An override widened in this run ends up declaring what its own super declares, so follow the chain up to the first clause that stays
    private static List<JavaType> thrownAfterRun(JavaType.Method superFinalize, Map<String, JavaType.Method> widened) {
        JavaType.Method method = superFinalize;
        Set<String> visited = new HashSet<>();
        for (JavaType.Method above = widened.get(method.getDeclaringType().getFullyQualifiedName());
             above != null && visited.add(method.getDeclaringType().getFullyQualifiedName());
             above = widened.get(method.getDeclaringType().getFullyQualifiedName())) {
            method = above;
        }
        return method.getThrownExceptions();
    }

    private static boolean hasSuperFinalizeMethodInvocation(J.MethodDeclaration md) {
        return new JavaIsoVisitor<AtomicBoolean>() {
            @Override
            public J.MethodInvocation visitMethodInvocation(J.MethodInvocation method, AtomicBoolean exists) {
                J.MethodInvocation mi = super.visitMethodInvocation(method, exists);
                if (FINALIZE_METHOD_MATCHER.matches(mi)) {
                    exists.set(true);
                }
                return mi;
            }
        }.reduce(md, new AtomicBoolean()).get();
    }
}
