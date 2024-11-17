package org.openrewrite.staticanalysis;

import org.openrewrite.ExecutionContext;
import org.openrewrite.Recipe;
import org.openrewrite.TreeVisitor;
import org.openrewrite.java.JavaIsoVisitor;
import org.openrewrite.java.tree.J;
import org.openrewrite.java.tree.Statement;

import java.util.ArrayList;
import java.util.List;

public class UnnecessaryReturnAsLastStatement extends Recipe {
    @Override
    public String getDisplayName() {
        return "Unnecessary `return` as last statement in void method";
    }

    @Override
    public String getDescription() {
        return "Removes `return` from a `void` method if it's the last statement.";
    }

    @Override
    public TreeVisitor<?, ExecutionContext> getVisitor() {
        return new JavaIsoVisitor<ExecutionContext>() {
            private Statement maybeRemoveReturnAsLastStatement(Statement s) {
                if (s instanceof J.Block) {
                    return maybeRemoveReturnAsLastStatement((J.Block) s);
                } else {
                    return s;
                }
            }

            private J.Block maybeRemoveReturnAsLastStatement(J.Block b) {
                if (b == null) {
                    return null;
                }

                List<Statement> statements = b.getStatements();
                if (statements.isEmpty()) {
                    return b;
                }
                Statement lastStatement = statements.get(statements.size() - 1);
                List<Statement> allButLast = statements.subList(0, statements.size() - 1);
                if (lastStatement instanceof J.Return) {
                    return b.withStatements(allButLast);
                } else if (lastStatement instanceof J.If) {
                    J.If ifStatement = (J.If) lastStatement;
                    J.If.Else elze = ifStatement.getElsePart();
                    J.If newIf = ifStatement
                            .withThenPart(maybeRemoveReturnAsLastStatement(ifStatement.getThenPart()))
                            .withElsePart(elze.withBody(maybeRemoveReturnAsLastStatement(elze.getBody())));
                    List<Statement> newStatements = new ArrayList<>(allButLast);
                    newStatements.add(newIf);
                    return b.withStatements(newStatements);
                } else {
                    return b;
                }
            }

            @Override
            public J.MethodDeclaration visitMethodDeclaration(J.MethodDeclaration method, ExecutionContext executionContext) {
                J.MethodDeclaration m = super.visitMethodDeclaration(method, executionContext);
                if (m == null) {
                    return m;
                }
                return m.withBody(maybeRemoveReturnAsLastStatement(m.getBody()));
            }
        };
    }
}
