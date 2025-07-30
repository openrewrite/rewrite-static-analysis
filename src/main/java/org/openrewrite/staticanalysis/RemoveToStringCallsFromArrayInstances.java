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

import org.openrewrite.*;
import org.openrewrite.java.JavaTemplate;
import org.openrewrite.java.JavaVisitor;
import org.openrewrite.java.MethodMatcher;
import org.openrewrite.java.tree.Expression;
import org.openrewrite.java.tree.J;
import org.openrewrite.java.tree.JavaType;
import org.openrewrite.java.tree.TypedTree;

import java.util.Arrays;
import java.util.List;
import java.util.Set;

import static java.util.Collections.singleton;
import static java.util.stream.Collectors.toList;

public class RemoveToStringCallsFromArrayInstances extends Recipe {
    private static final MethodMatcher VALUEOF_MATCHER = new MethodMatcher("java.lang.String valueOf(java.lang.Object)");
    private static final MethodMatcher OBJECTS_TOSTRING_MATCHER = new MethodMatcher("java.util.Objects toString(Object)");
    private static final MethodMatcher TOSTRING_MATCHER = new MethodMatcher("java.lang.Object toString()");

    private static final List<String> PATTERNS = Arrays.asList(
            "java.io.PrintStream print*(Object)",
            "java.lang.String format*(..)",
            "java.lang.StringBuilder insert(int, Object)",
            "java.lang.StringBuilder append(Object)",
            "java.io.PrintStream format(String, Object[])",
            "java.io.PrintWriter print*(..)",
            "java.io.PrintWriter format(..)"
    );
    private static final List<MethodMatcher> METHOD_MATCHERS = PATTERNS.stream().map(MethodMatcher::new).collect(toList());

    @Override
    public Set<String> getTags() {
        return singleton("RSPEC-S2116");
    }

    @Override
    public String getDisplayName() {
        return "Remove `toString()` calls on arrays";
    }

    @Override
    public String getDescription() {
        return "The result from `toString()` calls on arrays is largely useless. The output does not actually reflect " +
               "the contents of the array. `Arrays.toString(array)` should be used instead as it gives the contents of the array.";
    }

    @Override
    public TreeVisitor<?, ExecutionContext> getVisitor() {
        return new RemoveToStringFromArraysVisitor();
    }

    private static class RemoveToStringFromArraysVisitor extends JavaVisitor<ExecutionContext> {
        @Override
        public J visitMethodInvocation(J.MethodInvocation mi, ExecutionContext ctx) {
            if (TOSTRING_MATCHER.matches(mi)) {
                Expression select = mi.getSelect();
                if (select == null) {
                    return mi;
                }

                return buildReplacement(select, mi);
            }
            if (METHOD_MATCHERS.stream().anyMatch(matcher -> matcher.matches(mi))) {
                // deals with edge cases where .toString() is called implicitly
                JavaType.Method methodType = mi.getMethodType();
                if (methodType == null) {
                    return mi;
                }
                List<JavaType> parameterTypes = methodType.getParameterTypes();
                List<Expression> arguments = mi.getArguments();
                for (int i = 0; i < arguments.size(); i++) {
                    Expression arg = arguments.get(i);
                    if (arg.getType() instanceof JavaType.Array &&
                            (i > parameterTypes.size() - 1 ||
                                    !(parameterTypes.get(i) instanceof JavaType.Array))) {
                        getCursor().putMessage("METHOD_KEY", mi);
                        break;
                    }
                }
            } else if (OBJECTS_TOSTRING_MATCHER.matches(mi) || VALUEOF_MATCHER.matches(mi)) {
                // method is static
                Expression select = mi.getArguments().get(0);
                maybeRemoveImport("java.util.Objects");

                return buildReplacement(select, mi);
            }

            return super.visitMethodInvocation(mi, ctx);
        }

        public J buildReplacement(Expression select, J.MethodInvocation mi) {
            if (!(select.getType() instanceof JavaType.Array)) {
                return mi;
            }

            maybeAddImport("java.util.Arrays");
            return JavaTemplate.builder("Arrays.toString(#{anyArray(java.lang.Object)})")
                    .imports("java.util.Arrays")
                    .build()
                    .apply(getCursor(), mi.getCoordinates().replace(), select);
        }

        @Override
        public Expression visitExpression(Expression exp, ExecutionContext ctx) {
            Expression e = (Expression) super.visitExpression(exp, ctx);
            if (e instanceof TypedTree && e.getType() instanceof JavaType.Array) {
                Cursor c = getCursor().dropParentWhile(is -> is instanceof J.Parentheses || !(is instanceof Tree));
                if (c.getMessage("METHOD_KEY") != null || c.getMessage("BINARY_FOUND") != null) {
                    maybeAddImport("java.util.Arrays");
                    return JavaTemplate.builder("Arrays.toString(#{anyArray(java.lang.Object)})")
                            .imports("java.util.Arrays")
                            .build()
                            .apply(getCursor(), e.getCoordinates().replace(), e);
                }
            }

            return e;
        }

        @Override
        public J.Binary visitBinary(J.Binary binary, ExecutionContext ctx) {
            Expression left = binary.getLeft();
            Expression right = binary.getRight();

            if (binary.getOperator() == J.Binary.Type.Addition && (left.getType() instanceof JavaType.Array || right.getType() instanceof JavaType.Array)) {
                getCursor().putMessage("BINARY_FOUND", binary);
            }

            return (J.Binary) super.visitBinary(binary, ctx);
        }
    }
}
