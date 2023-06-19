package org.openrewrite.staticanalysis;

import org.openrewrite.ExecutionContext;
import org.openrewrite.Preconditions;
import org.openrewrite.Recipe;
import org.openrewrite.TreeVisitor;
import org.openrewrite.java.JavaIsoVisitor;
import org.openrewrite.java.JavaTemplate;
import org.openrewrite.java.MethodMatcher;
import org.openrewrite.java.search.UsesType;
import org.openrewrite.java.tree.Expression;
import org.openrewrite.java.tree.J;
import org.openrewrite.java.tree.JavaType;

import java.util.ArrayList;
import java.util.List;

public class RemoveToStringCallsFromArrayInstances extends Recipe {
    private static final MethodMatcher TO_STRING_MATCHER = new MethodMatcher("java.lang.Object toString(..)");
    private static final MethodMatcher PRINTLN_MATCHER = new MethodMatcher("java.io.PrintStream println(..)");
    private static final MethodMatcher STR_FORMAT_MATCHER = new MethodMatcher("java.lang.String format(..)");

    @Override
    public String getDisplayName() {
        return "hashCode and toString should not be called on array instances";
    }

    @Override
    public String getDescription() {
        return "hashCode and toString should not be called on array instances.";
    }

    public TreeVisitor<?, ExecutionContext> getVisitor() {
        //return Preconditions.check(new UsesType<>("java.lang.Object[]", false), new RemoveToStringFromArraysVisitor());
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

                for (Expression arg : arguments) {
                    if (arg.getType() instanceof JavaType.Array) {
                        arg = JavaTemplate.builder("Arrays.toString(#{anyArray(java.lang.String)})")
                                .imports("java.util.Arrays")
                                .build()
                                .apply(getCursor(), arg.getCoordinates().replace(), arg);
                    }
                }
                maybeAddImport("java.util.Arrays");

                return m.withArguments(arguments);
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
