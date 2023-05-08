package org.openrewrite.staticanalysis;

import java.time.Duration;
import java.util.Arrays;
import java.util.Collections;
import java.util.Set;

import org.openrewrite.ExecutionContext;
import org.openrewrite.Recipe;
import org.openrewrite.Tree;
import org.openrewrite.TreeVisitor;
import org.openrewrite.internal.lang.Nullable;
import org.openrewrite.java.JavaTemplate;
import org.openrewrite.java.JavaVisitor;
import org.openrewrite.java.tree.Expression;
import org.openrewrite.java.tree.J;
import org.openrewrite.java.tree.JavaSourceFile;
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
    public Set<String> getTags() {
        return Collections.singleton("RSPEC-3358");
    }

    @Override
    public TreeVisitor<?, ExecutionContext> getVisitor() {
        return new TernaryOperatorsShouldNotBeNestedVisitor();
    }

    private static class TernaryOperatorsShouldNotBeNestedVisitor extends JavaVisitor<ExecutionContext> {

        final JavaTemplate iffTemplate = JavaTemplate.builder(this::getCursor, "if(#{any(boolean)}) {}").build();

        @Override
        public @Nullable J visit(@Nullable final Tree tree, final ExecutionContext executionContext) {
            J result = super.visit(tree, executionContext);
            if (tree instanceof JavaSourceFile) {
                result = (J) new RemoveUnneededBlock().getVisitor().visit(result, executionContext);
            }
            return result;
        }

        @Override
        public J visitReturn(final J.Return retrn, final ExecutionContext executionContext) {
            J possiblyTernary = retrn.getExpression();
            if (possiblyTernary instanceof J.Ternary) {
                J.Ternary ternary = (J.Ternary) possiblyTernary;
                if (ternary.getFalsePart() instanceof J.Ternary || ternary.getTruePart() instanceof J.Ternary) {
                    J.If iff = retrn.withTemplate(
                            iffTemplate,
                            retrn.getCoordinates().replace(),
                            ternary.getCondition()
                    );
                    iff = iff.withThenPart(blockOf(returnOf(ternary.getTruePart())));
                    J result = blockOf(iff, returnOf(ternary.getFalsePart()));
                    return autoFormat(result, executionContext);
                }
            }
            return super.visitReturn(retrn, executionContext);
        }
    }

    private static J.Return returnOf(Expression expression) {
        return new J.Return(Tree.randomId(), Space.EMPTY, Markers.EMPTY, expression);
    }

    private static J.Block blockOf(Statement... statements) {
        return J.Block.createEmptyBlock().withStatements(Arrays.asList(statements));
    }
}
