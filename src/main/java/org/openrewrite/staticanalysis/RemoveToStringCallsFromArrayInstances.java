/*
 * Copyright 2023 the original author or authors.
 * <p>
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * <p>
 * https://www.apache.org/licenses/LICENSE-2.0
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
import org.openrewrite.java.tree.Expression;
import org.openrewrite.java.tree.J;
import org.openrewrite.java.tree.JavaType;

import java.util.List;

public class RemoveToStringCallsFromArrayInstances extends Recipe {
    private static final MethodMatcher TO_STRING_MATCHER = new MethodMatcher("java.lang.Object toString(..)");
    private static final MethodMatcher PRINTLN_MATCHER = new MethodMatcher("java.io.PrintStream println(..)");
    private static final MethodMatcher STR_FORMAT_MATCHER = new MethodMatcher("java.lang.String format(..)");

    @Override
    public String getDisplayName() {
        return "toString should not be called on array instances";
    }

    @Override
    public String getDescription() {
        return "toString should not be called on array instances.";
    }

    public TreeVisitor<?, ExecutionContext> getVisitor() {
        return new RemoveToStringFromArraysVisitor();
    }

    private static class RemoveToStringFromArraysVisitor extends JavaIsoVisitor<ExecutionContext> {
        @Override
        public J.MethodInvocation visitMethodInvocation(J.MethodInvocation mi, ExecutionContext ctx) {
            J.MethodInvocation m = super.visitMethodInvocation(mi, ctx);

            if (TO_STRING_MATCHER.matches(m)) {
                String builder_string = "Arrays.toString(#{anyArray(java.lang.String)})";
                Expression select = m.getSelect();
                assert select != null;

                return buildReplacement(builder_string, m, select);
            }else if (PRINTLN_MATCHER.matches(m)) {
                Expression select = m.getArguments().get(0);
                String builder_string = "System.out.println(Arrays.toString(#{anyArray(java.lang.String)}))";

                return buildReplacement(builder_string, m, select);
            }else if (STR_FORMAT_MATCHER.matches(m)) {
                List<Expression> arguments = m.getArguments();
                StringBuilder builder_string = new StringBuilder("String.format(#{any(java.lang.String)}");
                boolean arrayExists = false;

                for (int i = 0; i<arguments.size(); i++) {
                    Expression argument = arguments.get(i);
                    if (i == 0) { continue; }
                    if (argument.getType() instanceof JavaType.Array) {
                        arrayExists = true;
                        String arg = ", Arrays.toString(#{anyArray(java.lang.Object)})";
                        builder_string.append(arg);
                        continue;
                    }
                    builder_string.append(", #{any(java.lang.String)}");
                }
                builder_string.append(")");

                if (!arrayExists) { return m; }

                J.MethodInvocation newInvocation = JavaTemplate.builder(builder_string.toString())
                        .imports("java.util.Arrays")
                        .build()
                        .apply(getCursor(), m.getCoordinates().replace(), arguments.toArray());
                maybeAddImport("java.util.Arrays");

                return newInvocation;
            }

            return m;
       }

        public J.MethodInvocation buildReplacement(String builder_string, J.MethodInvocation m, Expression select) {
            if (!(select.getType() instanceof JavaType.Array)) {
                return m;
            }

            J.MethodInvocation retVal = JavaTemplate.builder(builder_string)
                    .imports("java.util.Arrays")
                    .build()
                    .apply(getCursor(), m.getCoordinates().replace(), select);
            maybeAddImport("java.util.Arrays");
            return retVal;
        }
    }
}
