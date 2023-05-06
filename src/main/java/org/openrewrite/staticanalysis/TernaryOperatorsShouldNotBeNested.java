package org.openrewrite.staticanalysis;

import java.time.Duration;

import org.openrewrite.ExecutionContext;
import org.openrewrite.Recipe;
import org.openrewrite.TreeVisitor;
import org.openrewrite.java.JavaVisitor;
import org.openrewrite.java.tree.J;

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

        @Override
        public J visitTernary(final J.Ternary ternary, final ExecutionContext executionContext) {
            if(ternary.getFalsePart() instanceof J.Ternary){
                System.out.println("Ternary nesting found: " + ternary.getFalsePart());
                //replace with:
                // if(${ternary.getCondition()}){
                //    return ${ternary.getTruePart()};
                // }
            }
            return super.visitTernary(ternary, executionContext);
        }
    }
}
