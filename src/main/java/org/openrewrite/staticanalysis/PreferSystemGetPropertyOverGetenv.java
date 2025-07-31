/*
 * Copyright 2025 the original author or authors.
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
import org.openrewrite.Recipe;
import org.openrewrite.TreeVisitor;
import org.openrewrite.java.JavaIsoVisitor;
import org.openrewrite.java.JavaTemplate;
import org.openrewrite.java.MethodMatcher;
import org.openrewrite.java.tree.J;

public class PreferSystemGetPropertyOverGetenv extends Recipe {

    private static final MethodMatcher GETENV = new MethodMatcher("java.lang.System getenv(java.lang.String)");

    @Override
    public String getDisplayName() {
        return "Prefer System.getProperty(\"user.home\") over System.getenv(\"HOME\")";
    }

    @Override
    public String getDescription() {
        return "Replaces System.getenv(\"HOME\") with System.getProperty(\"user.home\") for better portability.";
    }

    @Override
    public TreeVisitor<?, ExecutionContext> getVisitor() {
        return new JavaIsoVisitor<ExecutionContext>() {
            @Override
            public J.MethodInvocation visitMethodInvocation(J.MethodInvocation method, ExecutionContext ctx) {
                if (GETENV.matches(method) &&
                        method.getArguments().size() == 1 &&
                        method.getArguments().get(0).printTrimmed().equals("\"HOME\"")) {

                    maybeAddImport("java.lang.System");

                    return JavaTemplate.builder("System.getProperty(\"user.home\")")
                            .imports("java.lang.System")
                            .build()
                            .apply(updateCursor(method), method.getCoordinates().replace());
                }
                return super.visitMethodInvocation(method, ctx);
            }
        };
    }
}
