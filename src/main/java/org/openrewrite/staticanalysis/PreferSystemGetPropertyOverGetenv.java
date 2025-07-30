package org.openrewrite.staticanalysis;


import org.openrewrite.ExecutionContext;
import org.openrewrite.Recipe;
import org.openrewrite.TreeVisitor;
import org.openrewrite.java.JavaIsoVisitor;
import org.openrewrite.java.JavaTemplate;
import org.openrewrite.java.tree.J;

public class PreferSystemGetPropertyOverGetenv extends Recipe {

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
                if (method.getSimpleName().equals("getenv")
                        && method.getArguments().size() == 1
                        && method.getArguments().get(0).printTrimmed().equals("\"HOME\"")) {

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
