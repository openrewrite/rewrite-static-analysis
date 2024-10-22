package org.openrewrite.staticanalysis;

import java.time.Duration;
import java.util.Collections;
import java.util.Set;

import org.openrewrite.ExecutionContext;
import org.openrewrite.NlsRewrite.Description;
import org.openrewrite.Recipe;
import org.openrewrite.java.JavaTemplate;
import org.openrewrite.java.JavaVisitor;
import org.openrewrite.java.MethodMatcher;
import org.openrewrite.java.tree.Expression;
import org.openrewrite.java.tree.J;
import org.openrewrite.java.tree.J.FieldAccess;
import org.openrewrite.java.tree.J.Identifier;
import org.openrewrite.java.tree.JavaType;

public class ReplaceClassIsInstanceWithInstanceof extends Recipe {

    @Override
    public String getDisplayName() {
        return "Replace `A.class.isInstance(a)` with `a instanceof A`";
    }

    @Override
    public @Description
    String getDescription() {
        return "There should be no `A.class.isInstance(a)`, it should be replaced by `a instanceof A`.";
    }

    @Override
    public Set<String> getTags() {
        return Collections.singleton("RSPEC-S6202");
    }

    @Override
    public Duration getEstimatedEffortPerOccurrence() {
        return Duration.ofMinutes(3);
    }

    @Override
    public JavaVisitor<ExecutionContext> getVisitor() {
        //use JavaVisitor instead of JavaIsoVisitor because we changed the type of LST
        return new JavaVisitor<ExecutionContext>() {

            private final MethodMatcher matcher = new MethodMatcher("java.lang.Class isInstance(java.lang.Object)");
            private final JavaTemplate template = JavaTemplate.builder("#{any(org.openrewrite.java.tree.Expression)} instanceof #{}").build();

            @Override
            public J visitMethodInvocation(J.MethodInvocation method, ExecutionContext ctx) {

                //make sure we find the right method and the left part is something like "SomeClass.class"
                if (matcher.matches(method) && isObjectClass(method.getSelect())) {
                    //for code like "A.class.isInstance(a)", select is "String.class", name is "isInstance", argument is "a"  
                    Identifier objectExpression = (Identifier) method.getArguments().get(0);
                    
                    FieldAccess fieldAccessPart = (FieldAccess) method.getSelect();
                    String className = ((JavaType.Class) ((Identifier) fieldAccessPart.getTarget()).getType()).getClassName();
                    
                    return maybeAutoFormat(
                            (J) method,
                            (J) template.apply(updateCursor(method), method.getCoordinates().replace(), objectExpression, className),
                            ctx
                    );

                }
                return (J.MethodInvocation) super.visitMethodInvocation(method, ctx);
            }

            private boolean isObjectClass(Expression expression) {
                if (expression instanceof J.FieldAccess) {
                    J.FieldAccess fieldAccess = (J.FieldAccess) expression;
                    if (fieldAccess.getTarget() instanceof Identifier) {
                        Identifier identifier = (Identifier) fieldAccess.getTarget();
                        return identifier.getType() instanceof JavaType.Class;
                    }
                }
                return false;
            }
        };
    }
}