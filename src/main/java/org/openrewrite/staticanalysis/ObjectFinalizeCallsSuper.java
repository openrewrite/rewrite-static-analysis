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

import org.openrewrite.ExecutionContext;
import org.openrewrite.Preconditions;
import org.openrewrite.Recipe;
import org.openrewrite.TreeVisitor;
import org.openrewrite.java.JavaIsoVisitor;
import org.openrewrite.java.JavaTemplate;
import org.openrewrite.java.MethodMatcher;
import org.openrewrite.java.search.DeclaresMethod;
import org.openrewrite.java.tree.J;

import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;

import static java.util.Collections.singleton;

public class ObjectFinalizeCallsSuper extends Recipe {
    private static final MethodMatcher FINALIZE_METHOD_MATCHER = new MethodMatcher("java.lang.Object finalize()", true);

    @Override
    public String getDisplayName() {
        return "`finalize()` calls super";
    }

    @Override
    public String getDescription() {
        return "Overrides of `Object#finalize()` should call super.";
    }

    @Override
    public Set<String> getTags() {
        return singleton("RSPEC-S1114");
    }

    @Override
    public TreeVisitor<?, ExecutionContext> getVisitor() {
        return Preconditions.check(new DeclaresMethod<>(FINALIZE_METHOD_MATCHER), new JavaIsoVisitor<ExecutionContext>() {
            @Override
            public J.MethodDeclaration visitMethodDeclaration(J.MethodDeclaration method, ExecutionContext ctx) {
                J.MethodDeclaration md = super.visitMethodDeclaration(method, ctx);
                if (FINALIZE_METHOD_MATCHER.matches(md.getMethodType()) && !hasSuperFinalizeMethodInvocation(md)) {
                    //noinspection ConstantConditions
                    md = JavaTemplate.builder("super.finalize()")
                            .contextSensitive()
                            .build()
                            .apply(updateCursor(md),
                                    md.getBody().getCoordinates().lastStatement());
                }
                return md;
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
