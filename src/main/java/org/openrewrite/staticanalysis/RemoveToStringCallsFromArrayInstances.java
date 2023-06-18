package org.openrewrite.staticanalysis;

import org.openrewrite.ExecutionContext;
import org.openrewrite.Recipe;
import org.openrewrite.TreeVisitor;
import org.openrewrite.java.JavaIsoVisitor;
import org.openrewrite.java.JavaTemplate;
import org.openrewrite.java.MethodMatcher;
import org.openrewrite.java.tree.Expression;
import org.openrewrite.java.tree.J;

public class RemoveToStringCallsFromArrayInstances extends Recipe {
    private static final MethodMatcher TOSTRING_MATCHER = new MethodMatcher("java.util.Arrays toString(..)");

    @Override
    public String getDisplayName() {
        return "hashCode and toString should not be called on array instances";
    }

    @Override
    public String getDescription() {
        return "hashCode and toString should not be called on array instances.";
    }

    public TreeVisitor<?, ExecutionContext> getVisitor() {
        return new RemoveToStringFromArraysVisitor();
    }

    private static class RemoveToStringFromArraysVisitor extends JavaIsoVisitor<ExecutionContext> {
        @Override
        public J.MethodInvocation visitMethodInvocation(J.MethodInvocation mi, ExecutionContext ctx) {
            J.MethodInvocation m = super.visitMethodInvocation(mi, ctx);

            if (!TOSTRING_MATCHER.matches(m)) {
                return m;
            }

            Expression select = m.getSelect();

            m = JavaTemplate.builder("Arrays.toString(#{java.util.Arrays}")
                    .imports("java.util.Arrays")
                    .build()
                    .apply(getCursor(), m.getCoordinates().replace());

            return m;
        }
    }
}
