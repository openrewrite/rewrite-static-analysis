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

public class EqualsToContentEquals extends Recipe {
    private static final MethodMatcher equals_matcher = new MethodMatcher("java.lang.String equals(..)");
    private static final List<String> TYPE_NAMES = Arrays.asList(
            "java.lang.StringBuffer",
            "java.lang.StringBuilder",
            "java.lang.CharSequence"
    );
    @SuppressWarnings("unchecked")
    private static final TreeVisitor<?, ExecutionContext> PRECONDITION =
            Preconditions.or(TYPE_NAMES.stream().map(s -> new UsesType<>(s, false)).toArray(UsesType[]::new));
    private static final List<MethodMatcher> toString_matchers = TYPE_NAMES.stream()
            .map(obj -> new MethodMatcher(obj + " toString()")).collect(Collectors.toList());

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

                    JavaType sb_type = JavaType.buildType("java.lang.StringBuilder");
                    JavaType string_buffer_type = JavaType.buildType("java.lang.StringBuffer");
                    JavaType char_sq_type = JavaType.buildType("java.lang.CharSequence");

                    if (
                            TypeUtils.isOfType(newArg.getType(), sb_type)            ||
                            TypeUtils.isOfType(newArg.getType(), string_buffer_type) ||
                            TypeUtils.isOfType(newArg.getType(), char_sq_type)
                    ) {
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
