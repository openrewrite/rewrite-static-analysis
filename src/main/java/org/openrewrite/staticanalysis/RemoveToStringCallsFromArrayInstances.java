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

import org.openrewrite.*;
import org.openrewrite.java.JavaIsoVisitor;
import org.openrewrite.java.JavaTemplate;
import org.openrewrite.java.MethodMatcher;
import org.openrewrite.java.search.UsesMethod;
import org.openrewrite.java.search.UsesType;
import org.openrewrite.java.tree.*;
import org.openrewrite.marker.Marker;

import java.beans.Customizer;
import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class RemoveToStringCallsFromArrayInstances extends Recipe {
    private static final MethodMatcher OBJECT_TOSTRING_MATCHER = new MethodMatcher("java.lang.Object toString()");
    private static final MethodMatcher OBJECTS_TOSTRING_MATCHER = new MethodMatcher("java.lang.Objects toString()");
    private static final List<String> PATTERNS = Arrays.asList(
            "java.io.PrintStream print*(Object)",
            "java.lang.String format*(..)",
            "java.lang.String valueOf(java.lang.Object)",
            "java.lang.StringBuilder insert(int, Object)",
            "java.lang.StringBuilder append(Object)",
            "java.io.PrintStream format(String, Object[])"
    );
    private  static final TreeVisitor<?, ExecutionContext> PRECONDITION = Preconditions.or(
            PATTERNS.stream().map(UsesMethod::new).toArray(UsesMethod[]::new)
    );
    private static final List<MethodMatcher> METHOD_MATCHERS = PATTERNS.stream().map(MethodMatcher::new).collect(Collectors.toList());

    @Override
    public String getDisplayName() {
        return "Remove `toString()` calls on arrays";
    }

    @Override
    public String getDescription() {
        return "The result from `toString()` calls on arrays is largely useless. The output does not actually reflect" +
                " the contents of the array. `Arrays.toString(array)` give the contents of the array.";
    }

    public TreeVisitor<?, ExecutionContext> getVisitor() {
        return Preconditions.check(PRECONDITION, new RemoveToStringFromArraysVisitor());
    }

    private static class RemoveToStringFromArraysVisitor extends JavaIsoVisitor<ExecutionContext> {
        @Override
        public J.MethodInvocation visitMethodInvocation(J.MethodInvocation mi, ExecutionContext ctx) {
            J.MethodInvocation m = super.visitMethodInvocation(mi, ctx);

            if (OBJECT_TOSTRING_MATCHER.matches(m) || OBJECTS_TOSTRING_MATCHER.matches(m)) {
                Expression select = m.getSelect();
                assert select != null;

                if (!(select.getType() instanceof JavaType.Array)) {
                    return m;
                }

                J.MethodInvocation newInv = JavaTemplate.builder("Arrays.toString(#{anyArray(java.lang.Object)})")
                        .imports("java.util.Arrays")
                        .build()
                        .apply(getCursor(), m.getCoordinates().replace(), select);
                maybeAddImport("java.util.Arrays");
                return newInv;
            }else if (METHOD_MATCHERS.stream().anyMatch(matcher -> matcher.matches(m))) {
                // deals with edge cases where .toString() is called implicitly
                List<Expression> arguments = m.getArguments();
                for (Expression arg : arguments) {
                    if (arg.getType() instanceof JavaType.Array) {
                        Cursor c = getCursor();
                        c.putMessage("METHOD_KEY", m);
                        break;
                    }
                }
            }

            return m;
        }

        @Override
        public Expression visitExpression(Expression exp, ExecutionContext ctx) {
            Expression e = super.visitExpression(exp, ctx);
            Cursor c = getCursor().dropParentWhile(J.Parentheses.class::isInstance);
            System.out.println(c.getNearestMessage("METHOD_KEY") != null);
            if (c.getMessage("METHOD_KEY") != null) {
                if (e.getType() instanceof JavaType.Array) {
                    Expression newExpression = JavaTemplate.builder("Arrays.toString(#{anyArray(java.lang.Object)})")
                            .imports("java.util.Arrays")
                            .build()
                            .apply(getCursor(), e.getCoordinates().replace(), e);
                    maybeAddImport("java.util.Arrays");
                    return newExpression;
                }
            }

            return e;
        }
    }
}
