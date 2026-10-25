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
import org.openrewrite.ExecutionContext;
import org.openrewrite.Preconditions;
import org.openrewrite.Recipe;
import org.openrewrite.TreeVisitor;
import org.openrewrite.internal.ListUtils;
import org.openrewrite.java.JavaIsoVisitor;
import org.openrewrite.java.JavaTemplate;
import org.openrewrite.java.MethodMatcher;
import org.openrewrite.java.search.DeclaresMethod;
import org.openrewrite.java.tree.J;
import org.openrewrite.java.tree.JavaType;
import org.openrewrite.java.tree.NameTree;
import org.openrewrite.java.tree.TypeUtils;
import org.openrewrite.staticanalysis.groovy.GroovyFileChecker;
import org.openrewrite.staticanalysis.java.JavaFileChecker;

import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;

import static java.util.Collections.singleton;
import static java.util.Objects.requireNonNull;

public class ObjectFinalizeCallsSuper extends Recipe {
    private static final MethodMatcher FINALIZE_METHOD_MATCHER = new MethodMatcher("java.lang.Object finalize()", true);

    @Getter
    final String displayName = "`finalize()` calls super";

    @Getter
    final String description = "Overrides of `Object#finalize()` should call super. " +
            "Skipping the super call can prevent parent classes from releasing critical system resources during garbage collection.";

    @Getter
    final Set<String> tags = singleton("RSPEC-S1114");

    @Override
    public TreeVisitor<?, ExecutionContext> getVisitor() {
        // Kotlin's `Any` has no `finalize()` to call on `super`, and C# forbids calling `base.Finalize()`
        return Preconditions.check(Preconditions.and(
                Preconditions.or(new JavaFileChecker<>(), new GroovyFileChecker<>()),
                new DeclaresMethod<>(FINALIZE_METHOD_MATCHER)), new JavaIsoVisitor<ExecutionContext>() {
            @Override
            public J.MethodDeclaration visitMethodDeclaration(J.MethodDeclaration method, ExecutionContext ctx) {
                J.MethodDeclaration md = super.visitMethodDeclaration(method, ctx);
                JavaType.Method methodType = md.getMethodType();
                J.Block body = md.getBody();
                if (methodType == null || body == null || !FINALIZE_METHOD_MATCHER.matches(methodType) || hasSuperFinalizeMethodInvocation(md)) {
                    return md;
                }
                JavaType.Method superFinalize = TypeUtils.findOverriddenMethod(methodType).orElse(null);
                if (superFinalize == null) {
                    return md;
                }

                // `Throwable` is checked in Java but not in Groovy
                List<NameTree> throwz = md.getThrows();
                if (getCursor().firstEnclosing(J.CompilationUnit.class) != null &&
                        (throwz == null || throwz.stream().noneMatch(t -> TypeUtils.isOfClassType(t.getType(), "java.lang.Throwable")))) {
                    List<JavaType> superThrown = superFinalize.getThrownExceptions();
                    // An override may only declare what it overrides declares, so a narrower throws clause has no
                    // legal way to take the added call
                    if (throwz != null) {
                        if (superThrown.stream().anyMatch(thrown ->
                                throwz.stream().noneMatch(t -> TypeUtils.isAssignableTo(t.getType(), thrown)))) {
                            return md;
                        }
                    } else if (superThrown.stream().anyMatch(t -> TypeUtils.isOfClassType(t, "java.lang.Throwable"))) {
                        md = JavaTemplate.builder("Throwable")
                                .build()
                                .apply(updateCursor(md), md.getCoordinates().replaceThrows());
                    } else if (!superThrown.isEmpty()) {
                        return md;
                    }
                }

                md = JavaTemplate.builder("super.finalize()")
                        .build()
                        .apply(updateCursor(md), body.getCoordinates().lastStatement());
                // The template can't attribute `super` against a superclass it only knows from the type table
                J.Block withCall = requireNonNull(md.getBody());
                return md.withBody(withCall.withStatements(ListUtils.mapLast(withCall.getStatements(), s -> {
                    J.MethodInvocation call = (J.MethodInvocation) s;
                    return call.withMethodType(superFinalize)
                            .withName(call.getName().withType(superFinalize))
                            .withSelect(((J.Identifier) requireNonNull(call.getSelect())).withType(methodType.getDeclaringType().getSupertype()));
                })));
            }

            private boolean hasSuperFinalizeMethodInvocation(J.MethodDeclaration md) {
                AtomicBoolean hasSuperFinalize = new AtomicBoolean(Boolean.FALSE);
                new JavaIsoVisitor<AtomicBoolean>() {
                    @Override
                    public J.MethodInvocation visitMethodInvocation(J.MethodInvocation method, AtomicBoolean exists) {
                        J.MethodInvocation mi = super.visitMethodInvocation(method, exists);
                        if (FINALIZE_METHOD_MATCHER.matches(mi)) {
                            exists.set(Boolean.TRUE);
                        }
                        return mi;
                    }
                }.visit(md, hasSuperFinalize);
                return hasSuperFinalize.get();
            }
        });
    }
}
