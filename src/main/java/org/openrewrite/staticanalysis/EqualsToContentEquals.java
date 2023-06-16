/*
 * Copyright 2021 the original author or authors.
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
import org.openrewrite.Preconditions;
import org.openrewrite.Recipe;
import org.openrewrite.TreeVisitor;
import org.openrewrite.java.JavaIsoVisitor;
import org.openrewrite.java.MethodMatcher;
import org.openrewrite.java.search.UsesType;
import org.openrewrite.java.tree.*;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class EqualsToContentEquals extends Recipe {
    private static final MethodMatcher equals_matcher = new MethodMatcher("java.lang.String equals(..)");
    private static final TreeVisitor<?, ExecutionContext> PRECONDITION = Preconditions.or(
            new UsesType<>("java.lang.StringBuffer", false),
            new UsesType<>("java.lang.StringBuilder", false),
            new UsesType<>("java.lang.CharSequence", false));
    private static final List<MethodMatcher> TOSTRING_MATCHERS = Arrays.asList(
            new MethodMatcher("java.lang.String toString()"),
            new MethodMatcher("java.lang.StringBuffer toString()"),
            new MethodMatcher("java.lang.StringBuilder toString()"));

    @Override
    public String getDisplayName() {
        return "Use contentEquals to compare StringBuilder to a String";
    }
    @Override
    public String getDescription() {
        return "Use contentEquals to compare StringBuilder to a String.";
    }

    public TreeVisitor<?, ExecutionContext> getVisitor() {
        return Preconditions.check(PRECONDITION, new EqualsToContentEqualsVisitor());
    }

    private static class EqualsToContentEqualsVisitor extends JavaIsoVisitor<ExecutionContext> {
        @Override
        public J.MethodInvocation visitMethodInvocation(J.MethodInvocation mi, ExecutionContext ctx) {
            J.MethodInvocation m = super.visitMethodInvocation(mi, ctx);
            J.Identifier methodName = m.getName();
            // create method matcher on equals(String)
            if (equals_matcher.matches(m)) {
                Expression argument = m.getArguments().get(0);

                // checks whether the argument is a toString() method call on a StringBuffer or CharSequence
                if (toString_matchers.stream().anyMatch(matcher -> matcher.matches(argument))) {
                    J.MethodInvocation inv = (J.MethodInvocation) argument;
                    Expression newArg = inv.getSelect();
                    if (inv.getSelect() == null) { return m; }

                    Stream<JavaType> TYPES = Stream.of(
                            JavaType.buildType("java.lang.StringBuilder"),
                            JavaType.buildType("java.lang.StringBuffer"),
                            JavaType.buildType("java.lang.CharSequence")
                    );

                    if (TYPES.anyMatch(type -> TypeUtils.isOfType(newArg.getType(), type))) {
                        // strip out the toString() on the argument
                        List<Expression> args = new ArrayList<>(1);
                        args.add(newArg);
                        m = m.withArguments(args);
                        // rename the method to contentEquals
                        methodName = m.getName().withSimpleName("contentEquals");
                    }
                }
            }

            return m.withName(methodName);
        }
    }
}
