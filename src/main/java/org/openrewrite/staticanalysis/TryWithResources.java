/*
 * Copyright 2025 the original author or authors.
 * <p>
 * Licensed under the Moderne Source Available License (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * <p>
 * https://docs.moderne.io/licensing/moderne-source-available-license
 * <p>
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.openrewrite.staticanalysis;

import lombok.Getter;
import org.openrewrite.ExecutionContext;
import org.openrewrite.Preconditions;
import org.openrewrite.Recipe;
import org.openrewrite.Tree;
import org.openrewrite.TreeVisitor;
import org.openrewrite.java.JavaIsoVisitor;
import org.openrewrite.java.MethodMatcher;
import org.openrewrite.java.search.UsesJavaVersion;
import org.openrewrite.java.tree.*;
import org.openrewrite.marker.Markers;
import org.openrewrite.staticanalysis.java.JavaFileChecker;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;

import static java.util.Collections.singleton;
import static java.util.Collections.singletonList;

public class TryWithResources extends Recipe {

    private static final MethodMatcher CLOSE = new MethodMatcher("java.lang.AutoCloseable close()", true);
    private static final JavaType.ShallowClass AUTO_CLOSEABLE = JavaType.ShallowClass.build("java.lang.AutoCloseable");

    @Getter
    final String displayName = "Use try-with-resources";

    @Getter
    final String description = "Refactor try/finally blocks to use try-with-resources when the finally block only closes an `AutoCloseable` resource.";

    @Getter
    final Duration estimatedEffortPerOccurrence = Duration.ofMinutes(5);

    @Getter
    final Set<String> tags = singleton("RSPEC-S2093");

    @Override
    public TreeVisitor<?, ExecutionContext> getVisitor() {
        return Preconditions.check(
                Preconditions.and(new UsesJavaVersion<>(9), new JavaFileChecker<>()),
                new JavaIsoVisitor<ExecutionContext>() {
                    @Override
                    public J.Block visitBlock(J.Block block, ExecutionContext ctx) {
                        J.Block b = super.visitBlock(block, ctx);
                        List<Statement> stmts = b.getStatements();

                        boolean changed = false;
                        List<Statement> newStmts = new ArrayList<>(stmts.size());

                        for (int i = 0; i < stmts.size(); i++) {
                            if (i + 1 < stmts.size()
                                    && stmts.get(i) instanceof J.VariableDeclarations
                                    && stmts.get(i + 1) instanceof J.Try) {

                                J.VariableDeclarations varDecl = (J.VariableDeclarations) stmts.get(i);
                                J.Try tryStmt = (J.Try) stmts.get(i + 1);

                                if (canTransform(varDecl, tryStmt, stmts, i)) {
                                    newStmts.add(transform(varDecl, tryStmt));
                                    i++; // skip the try statement
                                    changed = true;
                                    continue;
                                }
                            }
                            newStmts.add(stmts.get(i));
                        }

                        return changed ? b.withStatements(newStmts) : b;
                    }
                });
    }

    private static boolean canTransform(J.VariableDeclarations varDecl, J.Try tryStmt,
                                        List<Statement> stmts, int varDeclIndex) {
        // Single variable only
        if (varDecl.getVariables().size() != 1) {
            return false;
        }
        J.VariableDeclarations.NamedVariable namedVar = varDecl.getVariables().get(0);
        String varName = namedVar.getSimpleName();

        // Must have a non-null initializer
        Expression init = namedVar.getInitializer();
        if (init == null || (init instanceof J.Literal && ((J.Literal) init).getValue() == null)) {
            return false;
        }

        // Must implement AutoCloseable
        JavaType.FullyQualified type = TypeUtils.asFullyQualified(varDecl.getType());
        if (type == null || !TypeUtils.isAssignableTo(AUTO_CLOSEABLE, type)) {
            return false;
        }

        // Must have a finally block
        if (tryStmt.getFinally() == null) {
            return false;
        }

        // Must not already have resources
        if (tryStmt.getResources() != null && !tryStmt.getResources().isEmpty()) {
            return false;
        }

        // Finally block must only close this variable
        if (!isFinallyOnlyClosing(tryStmt.getFinally(), varName)) {
            return false;
        }

        // Variable must not be used after the try
        if (isUsedAfter(varName, stmts, varDeclIndex + 1)) {
            return false;
        }

        // Variable must not be reassigned in try body
        if (isReassigned(varName, tryStmt.getBody())) {
            return false;
        }

        // Variable must not be closed in catch blocks
        for (J.Try.Catch aCatch : tryStmt.getCatches()) {
            if (containsClose(varName, aCatch.getBody())) {
                return false;
            }
        }

        return true;
    }

    private static J.Try transform(J.VariableDeclarations varDecl, J.Try tryStmt) {
        J.VariableDeclarations resourceDecl = varDecl.withPrefix(Space.EMPTY);

        J.Try.Resource resource = new J.Try.Resource(
                Tree.randomId(),
                Space.EMPTY,
                Markers.EMPTY,
                resourceDecl,
                false
        );

        JContainer<J.Try.Resource> resources = JContainer.build(
                Space.SINGLE_SPACE,
                singletonList(JRightPadded.build(resource)),
                Markers.EMPTY
        );

        return tryStmt.getPadding()
                .withResources(resources)
                .withFinally(null)
                .withPrefix(varDecl.getPrefix());
    }

    private static boolean isFinallyOnlyClosing(J.Block finallyBlock, String varName) {
        List<Statement> stmts = finallyBlock.getStatements();
        return stmts.size() == 1 && isCloseStatement(stmts.get(0), varName);
    }

    private static boolean isCloseStatement(Statement stmt, String varName) {
        // Direct: var.close()
        if (isDirectClose(stmt, varName)) {
            return true;
        }

        // Null-guarded: if (var != null) { ... close ... }
        if (stmt instanceof J.If) {
            J.If ifStmt = (J.If) stmt;
            if (ifStmt.getElsePart() != null) {
                return false;
            }
            if (!isNullCheck(ifStmt.getIfCondition().getTree(), varName)) {
                return false;
            }
            if (!(ifStmt.getThenPart() instanceof J.Block)) {
                return false;
            }
            J.Block thenBlock = (J.Block) ifStmt.getThenPart();
            if (thenBlock.getStatements().size() != 1) {
                return false;
            }
            Statement inner = thenBlock.getStatements().get(0);
            return isDirectClose(inner, varName) || isTryCatchClose(inner, varName);
        }

        // Try-catch wrapped: try { var.close(); } catch (...) {}
        return isTryCatchClose(stmt, varName);
    }

    private static boolean isDirectClose(Statement stmt, String varName) {
        return stmt instanceof J.MethodInvocation && isCloseInvocation((J.MethodInvocation) stmt, varName);
    }

    private static boolean isCloseInvocation(J.MethodInvocation mi, String varName) {
        return CLOSE.matches(mi)
                && mi.getSelect() instanceof J.Identifier
                && ((J.Identifier) mi.getSelect()).getSimpleName().equals(varName);
    }

    private static boolean isTryCatchClose(Statement stmt, String varName) {
        if (!(stmt instanceof J.Try)) {
            return false;
        }
        J.Try innerTry = (J.Try) stmt;
        if (innerTry.getFinally() != null) {
            return false;
        }
        List<Statement> body = innerTry.getBody().getStatements();
        return body.size() == 1 && isDirectClose(body.get(0), varName);
    }

    private static boolean isNullCheck(Expression condition, String varName) {
        if (!(condition instanceof J.Binary)) {
            return false;
        }
        J.Binary binary = (J.Binary) condition;
        if (binary.getOperator() != J.Binary.Type.NotEqual) {
            return false;
        }
        return (isIdentifier(binary.getLeft(), varName) && isNullLiteral(binary.getRight()))
                || (isNullLiteral(binary.getLeft()) && isIdentifier(binary.getRight(), varName));
    }

    private static boolean isIdentifier(Expression expr, String name) {
        return expr instanceof J.Identifier && ((J.Identifier) expr).getSimpleName().equals(name);
    }

    private static boolean isNullLiteral(Expression expr) {
        return expr instanceof J.Literal && ((J.Literal) expr).getValue() == null;
    }

    private static boolean isUsedAfter(String varName, List<Statement> stmts, int tryIndex) {
        for (int i = tryIndex + 1; i < stmts.size(); i++) {
            if (new JavaIsoVisitor<AtomicBoolean>() {
                @Override
                public J.Identifier visitIdentifier(J.Identifier identifier, AtomicBoolean f) {
                    if (identifier.getSimpleName().equals(varName)) {
                        f.set(true);
                    }
                    return super.visitIdentifier(identifier, f);
                }
            }.reduce(stmts.get(i), new AtomicBoolean()).get()) {
                return true;
            }
        }
        return false;
    }

    private static boolean isReassigned(String varName, J tree) {
        return new JavaIsoVisitor<AtomicBoolean>() {
            @Override
            public J.Assignment visitAssignment(J.Assignment assignment, AtomicBoolean f) {
                if (isIdentifier(assignment.getVariable(), varName)) {
                    f.set(true);
                }
                return super.visitAssignment(assignment, f);
            }

            @Override
            public J.AssignmentOperation visitAssignmentOperation(J.AssignmentOperation assignOp, AtomicBoolean f) {
                if (isIdentifier(assignOp.getVariable(), varName)) {
                    f.set(true);
                }
                return super.visitAssignmentOperation(assignOp, f);
            }

            @Override
            public J.Unary visitUnary(J.Unary unary, AtomicBoolean f) {
                if (unary.getOperator().isModifying() && isIdentifier(unary.getExpression(), varName)) {
                    f.set(true);
                }
                return super.visitUnary(unary, f);
            }
        }.reduce(tree, new AtomicBoolean()).get();
    }

    private static boolean containsClose(String varName, J tree) {
        return new JavaIsoVisitor<AtomicBoolean>() {
            @Override
            public J.MethodInvocation visitMethodInvocation(J.MethodInvocation mi, AtomicBoolean f) {
                if (isCloseInvocation(mi, varName)) {
                    f.set(true);
                }
                return super.visitMethodInvocation(mi, f);
            }
        }.reduce(tree, new AtomicBoolean()).get();
    }
}
