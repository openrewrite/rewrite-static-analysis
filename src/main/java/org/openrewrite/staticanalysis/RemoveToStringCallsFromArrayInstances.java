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
import org.openrewrite.java.JavaTemplate;
import org.openrewrite.java.JavaVisitor;
import org.openrewrite.java.MethodMatcher;
import org.openrewrite.java.search.UsesMethod;
import org.openrewrite.java.tree.Expression;
import org.openrewrite.java.tree.J;
import org.openrewrite.java.tree.JavaType;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

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
    private static final TreeVisitor<?, ExecutionContext> PRECONDITION = Preconditions.or(
            PATTERNS.stream().map(UsesMethod::new).toArray(UsesMethod[]::new)
    );
    private static final List<MethodMatcher> METHOD_MATCHERS = PATTERNS.stream().map(MethodMatcher::new).collect(Collectors.toList());

    @Override
    public Set<String> getTags() {
        return Collections.singleton("RSPEC-2116");
    }

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
        return new RemoveToStringFromArraysVisitor();
// FIXME not sure we actually need a precondition here, but it currently doesn't cover OBJECT_TOSTRING_MATCHER and OBJECTS_TOSTRING_MATCHER
//        return Preconditions.check(PRECONDITION, new RemoveToStringFromArraysVisitor());
    }

    private static class RemoveToStringFromArraysVisitor extends JavaVisitor<ExecutionContext> {
        @Override
        public J visitMethodInvocation(J.MethodInvocation mi, ExecutionContext ctx) {
            if (OBJECT_TOSTRING_MATCHER.matches(mi) || OBJECTS_TOSTRING_MATCHER.matches(mi)) {
                Expression select = mi.getSelect();
                assert select != null;

                if (!(select.getType() instanceof JavaType.Array)) {
                    return mi;
                }

                J.MethodInvocation newInv = JavaTemplate.builder("Arrays.toString(#{anyArray(java.lang.Object)})")
                        .imports("java.util.Arrays")
                        .build()
                        .apply(getCursor(), mi.getCoordinates().replace(), select);
                maybeAddImport("java.util.Arrays");
                return newInv;
            } else if (METHOD_MATCHERS.stream().anyMatch(matcher -> matcher.matches(mi))) {
                // deals with edge cases where .toString() is called implicitly
                List<Expression> arguments = mi.getArguments();
                for (Expression arg : arguments) {
                    if (arg.getType() instanceof JavaType.Array) {
                        Cursor c = getCursor();
                        c.putMessage("METHOD_KEY", mi);
                        break;
                    }
                }
            }

            return super.visitMethodInvocation(mi, ctx);
        }

        @Override
        public Expression visitExpression(Expression exp, ExecutionContext ctx) {
            Expression e = (Expression) super.visitExpression(exp, ctx);
            if (e.getType() instanceof JavaType.Array) {
                Cursor c = getCursor().dropParentWhile(is -> is instanceof J.Parentheses || !(is instanceof Tree));
                if (c.getMessage("METHOD_KEY") != null) {
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
