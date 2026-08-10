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
import org.openrewrite.Recipe;
import org.openrewrite.TreeVisitor;
import org.openrewrite.java.JavaIsoVisitor;
import org.openrewrite.java.JavaTemplate;
import org.openrewrite.java.MethodMatcher;
import org.openrewrite.java.search.DeclaresMethod;
import org.openrewrite.java.tree.J;
import org.openrewrite.java.tree.JavaType;
import org.openrewrite.java.tree.NameTree;
import org.openrewrite.java.tree.TypeUtils;

import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;

import static java.util.Collections.singleton;
import static java.util.Collections.singletonList;

public class ObjectFinalizeCallsSuper extends Recipe {
    private static final MethodMatcher FINALIZE_METHOD_MATCHER = new MethodMatcher("java.lang.Object finalize()", true);
    private static final JavaType.FullyQualified THROWABLE = JavaType.ShallowClass.build("java.lang.Throwable");

    @Getter
    final String displayName = "`finalize()` calls super";

    @Getter
    final String description = "Overrides of `Object#finalize()` should call super. " +
            "Skipping the super call can prevent parent classes from releasing critical system resources during garbage collection.";

    @Getter
    final Set<String> tags = singleton("RSPEC-S1114");

    @Override
    public TreeVisitor<?, ExecutionContext> getVisitor() {
        return Preconditions.check(new DeclaresMethod<>(FINALIZE_METHOD_MATCHER), new JavaIsoVisitor<ExecutionContext>() {
            @Override
            public J.MethodDeclaration visitMethodDeclaration(J.MethodDeclaration method, ExecutionContext ctx) {
                J.MethodDeclaration md = super.visitMethodDeclaration(method, ctx);
                JavaType.Method methodType = md.getMethodType();
                J.Block body = md.getBody();
                if (methodType == null || body == null || !FINALIZE_METHOD_MATCHER.matches(methodType) || hasSuperFinalizeMethodInvocation(md)) {
                    return md;
                }

                // `Object#finalize()` is declared to throw `Throwable`. That is a checked exception in Java, but
                // not in the other JVM languages this recipe runs on, so only a Java override has to declare it
                // for the added call to compile.
                List<NameTree> throwz = md.getThrows();
                boolean declaresThrowable = throwz != null &&
                        throwz.stream().anyMatch(t -> TypeUtils.isOfClassType(t.getType(), "java.lang.Throwable"));
                if (!declaresThrowable && getCursor().firstEnclosing(J.CompilationUnit.class) != null) {
                    // An override may only declare what the method it overrides declares. When the overridden
                    // `finalize()` declares `Throwable`, add it; when it declares nothing, the call needs no
                    // throws clause; anything else has no legal way to add the call without inventing
                    // exception handling, so leave those methods alone.
                    List<JavaType> superThrown = superFinalizeThrownExceptions(methodType);
                    if (superThrown == null) {
                        // Missing or unreliable type attribution.
                        return md;
                    }
                    if (!superThrown.isEmpty()) {
                        if (throwz != null || superThrown.stream().noneMatch(t -> TypeUtils.isOfClassType(t, "java.lang.Throwable"))) {
                            return md;
                        }
                        md = JavaTemplate.builder("Throwable")
                                .build()
                                .apply(updateCursor(md), md.getCoordinates().replaceThrows());
                        JavaType.Method declaresThrowableType = methodType.withThrownExceptions(singletonList(THROWABLE));
                        md = md.withMethodType(declaresThrowableType)
                                .withName(md.getName().withType(declaresThrowableType));
                    }
                }

                return JavaTemplate.builder("super.finalize()")
                        .contextSensitive()
                        .build()
                        .apply(updateCursor(md), body.getCoordinates().lastStatement());
            }

            private @Nullable List<JavaType> superFinalizeThrownExceptions(JavaType.Method finalizeMethod) {
                for (JavaType.FullyQualified type = finalizeMethod.getDeclaringType().getSupertype(); type != null; type = type.getSupertype()) {
                    if ("java.lang.Object".equals(type.getFullyQualifiedName())) {
                        return singletonList(THROWABLE);
                    }
                    for (JavaType.Method superMethod : type.getMethods()) {
                        if ("finalize".equals(superMethod.getName()) && superMethod.getParameterTypes().isEmpty()) {
                            return superMethod.getThrownExceptions();
                        }
                    }
                }
                // Missing or unreliable type attribution.
                return null;
            }

            private boolean hasSuperFinalizeMethodInvocation(J.MethodDeclaration md) {
                AtomicBoolean hasSuperFinalize = new AtomicBoolean(Boolean.FALSE);
                new JavaIsoVisitor<AtomicBoolean>() {
                    @Override
                    public J.MethodInvocation visitMethodInvocation(J.MethodInvocation method, AtomicBoolean exists) {
                        J.MethodInvocation mi = super.visitMethodInvocation(method, exists);
                        // A `super.finalize()` added by an earlier cycle is not always type attributed, so also
                        // match it syntactically to keep the recipe from adding the call a second time.
                        if (FINALIZE_METHOD_MATCHER.matches(mi) ||
                                mi.getSelect() instanceof J.Identifier &&
                                        "super".equals(((J.Identifier) mi.getSelect()).getSimpleName()) &&
                                        "finalize".equals(mi.getSimpleName()) &&
                                        mi.getArguments().size() == 1 && mi.getArguments().get(0) instanceof J.Empty) {
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
