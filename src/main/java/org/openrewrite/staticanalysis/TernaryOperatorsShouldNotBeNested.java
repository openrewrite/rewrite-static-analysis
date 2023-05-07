package org.openrewrite.staticanalysis;

import java.time.Duration;
import java.util.List;

import org.openrewrite.ExecutionContext;
import org.openrewrite.Recipe;
import org.openrewrite.Tree;
import org.openrewrite.TreeVisitor;
import org.openrewrite.java.JavaTemplate;
import org.openrewrite.java.JavaVisitor;
import org.openrewrite.java.tree.J;
import org.openrewrite.java.tree.Space;
import org.openrewrite.java.tree.Statement;
import org.openrewrite.marker.Markers;


public class TernaryOperatorsShouldNotBeNested extends Recipe {
    @Override
    public String getDisplayName() {
        return "Ternary operators should not be nested";
    }

    @Override
    public String getDescription() {
        return "Just because you can do something, doesn’t mean you should, and that’s the case with nested ternary operations. " +
                "Nesting ternary operators results in the kind of code that may seem clear as day when you write it, " +
                "but six months later will leave maintainers (or worse - future you) scratching their heads and cursing.\n\n" +
                "Instead, err on the side of clarity, and use another line to express the nested operation as a separate statement.";
    }

    @Override
    public Duration getEstimatedEffortPerOccurrence() {
        return Duration.ofMinutes(5);
    }

    @Override
    public TreeVisitor<?, ExecutionContext> getVisitor() {
        return new TernaryOperatorsShouldNotBeNestedVisitor();
    }

    private static class TernaryOperatorsShouldNotBeNestedVisitor extends JavaVisitor<ExecutionContext> {

        private final JavaTemplate splitNestedFalsePart = JavaTemplate.builder(
                        this::getCursor,
                        "if(#{any(boolean)})"
                )
                .build();

        @Override
        public J visitStatement(final Statement statement, final ExecutionContext executionContext) {
            //if statement contains a nested ternary, clone "statement part" and split?
            // return "a".equals(a) ? "a" : "b".equals(b) ? "b" : "nope";



            return super.visitStatement(statement, executionContext);
        }

        @Override
        public J visitTernary(final J.Ternary ternary, final ExecutionContext executionContext) {
            if (ternary.getFalsePart() instanceof J.Ternary) {
                System.out.println("Ternary nesting found: " + ternary.getFalsePart());
                //todo replace with:
                // if(ternary.getCondition()){
                //    return ternary.getTruePart();
                // }
                // return ternary.getFalsePart();
                //
                // return is not actually part of the ternary, so how to "clone" that?
            }
            return super.visitTernary(ternary, executionContext);
        }
    }
}
