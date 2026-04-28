/*
 * Copyright 2026 the original author or authors.
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
import org.jspecify.annotations.Nullable;
import org.openrewrite.ExecutionContext;
import org.openrewrite.Preconditions;
import org.openrewrite.Recipe;
import org.openrewrite.Tree;
import org.openrewrite.TreeVisitor;
import org.openrewrite.internal.ListUtils;
import org.openrewrite.java.JavaIsoVisitor;
import org.openrewrite.java.MethodMatcher;
import org.openrewrite.java.search.SemanticallyEqual;
import org.openrewrite.java.search.UsesJavaVersion;
import org.openrewrite.java.tree.*;
import org.openrewrite.marker.Markers;
import org.openrewrite.staticanalysis.java.JavaFileChecker;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;

import static java.util.Collections.*;

public class UseTryWithResources extends Recipe {

    private static final MethodMatcher CLOSE = new MethodMatcher("java.lang.AutoCloseable close()", true);
    private static final JavaType.ShallowClass AUTO_CLOSEABLE = JavaType.ShallowClass.build("java.lang.AutoCloseable");

    @Getter
    final String displayName = "Use try-with-resources";

    @Getter
    final String description = "Refactor try/finally blocks to use try-with-resources " +
            "when the finally block only closes an `AutoCloseable` resource. " +
            "Try-with-resources guarantees that resources are closed even when " +
            "exceptions occur, eliminating an entire class of resource-leak bugs " +
            "that manual `finally` blocks are prone to.";

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
                        return b.withStatements(ListUtils.map(stmts, (i, stmt) -> {
                            if (stmt instanceof J.Try) {
                                J.Try tryStmt = (J.Try) stmt;

                                // Case 1: Consecutive — varDecl immediately before try
                                if (i > 0 && stmts.get(i - 1) instanceof J.VariableDeclarations) {
                                    J.VariableDeclarations prevDecl = (J.VariableDeclarations) stmts.get(i - 1);
                                    if (canTransform(prevDecl, tryStmt)) {
                                        boolean usedAfter = isUsedAfter(prevDecl.getVariables().get(0).getSimpleName(), stmts, i);
                                        if (usedAfter) {
                                            return transformJava9(prevDecl, tryStmt);
                                        }
                                        return transform(prevDecl, tryStmt);
                                    }
                                }

                                // Case 2: Non-consecutive — varDecl separated by intervening statements
                                if (tryStmt.getFinally() != null) {
                                    String closedVar = extractClosedVarName(tryStmt.getFinally());
                                    if (closedVar != null) {
                                        int declIdx = findMatchingVarDecl(stmts, i, closedVar);
                                        if (declIdx >= 0 && declIdx < i - 1) {
                                            J.VariableDeclarations varDecl = (J.VariableDeclarations) stmts.get(declIdx);
                                            if (canTransform(varDecl, tryStmt) && !isReassignedBetween(closedVar, stmts, declIdx, i)) {
                                                return transformJava9(varDecl, tryStmt);
                                            }
                                        }
                                    }
                                }
                            }
                            // Remove varDecl that was merged into the following try-with-resources
                            if (stmt instanceof J.VariableDeclarations && i + 1 < stmts.size() &&
                                    stmts.get(i + 1) instanceof J.Try &&
                                    canTransform((J.VariableDeclarations) stmt, (J.Try) stmts.get(i + 1)) &&
                                    !isUsedAfter(((J.VariableDeclarations) stmt).getVariables().get(0).getSimpleName(), stmts, i + 1)) {
                                return null;
                            }
                            return stmt;
                        }));
                    }
                });
    }

    private static boolean canTransform(J.VariableDeclarations varDecl, J.Try tryStmt) {
        // Single variable only
        if (varDecl.getVariables().size() != 1) {
            return false;
        }
        J.VariableDeclarations.NamedVariable namedVar = varDecl.getVariables().get(0);
        String varName = namedVar.getSimpleName();

        // Must have a non-null initializer
        Expression init = namedVar.getInitializer();
        if (init == null || J.Literal.isLiteralValue(init, null)) {
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

        // Must not already be a resource in the try
        if (tryStmt.getResources() != null) {
            J.VariableDeclarations normalized = varDecl.withPrefix(Space.EMPTY);
            for (J.Try.Resource res : tryStmt.getResources()) {
                if (SemanticallyEqual.areEqual(res.getVariableDeclarations(), normalized)) {
                    return false;
                }
            }
        }

        // Finally block must contain a close for this variable
        if (!finallyContainsClose(tryStmt.getFinally(), varName)) {
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
        return addResource(varDecl.withPrefix(Space.EMPTY),
                varDecl.getVariables().get(0).getSimpleName(),
                tryStmt.withPrefix(varDecl.getPrefix()));
    }

    private static J.Try transformJava9(J.VariableDeclarations varDecl, J.Try tryStmt) {
        J.VariableDeclarations.NamedVariable namedVar = varDecl.getVariables().get(0);
        J.Identifier resourceRef = new J.Identifier(
                Tree.randomId(),
                Space.EMPTY,
                Markers.EMPTY,
                emptyList(),
                namedVar.getSimpleName(),
                namedVar.getType(),
                null
        );
        return addResource(resourceRef, namedVar.getSimpleName(), tryStmt);
    }

    private static J.Try addResource(TypedTree resourceExpr, String varName, J.Try tryStmt) {
        J.Try.Resource resource = new J.Try.Resource(
                Tree.randomId(),
                Space.EMPTY,
                Markers.EMPTY,
                resourceExpr,
                false
        );
        List<JRightPadded<J.Try.Resource>> existingResources = tryStmt.getPadding().getResources() != null ?
                tryStmt.getPadding().getResources().getPadding().getElements() : emptyList();
        List<JRightPadded<J.Try.Resource>> newResources;
        if (existingResources.isEmpty()) {
            newResources = singletonList(JRightPadded.build(resource));
        } else {
            newResources = new ArrayList<>(existingResources);
            // Set semicolon on the previous last resource
            int lastIdx = newResources.size() - 1;
            JRightPadded<J.Try.Resource> prev = newResources.get(lastIdx);
            newResources.set(lastIdx, prev.withElement(prev.getElement().withTerminatedWithSemicolon(true)));
            newResources.add(JRightPadded.build(resource.withPrefix(Space.SINGLE_SPACE)));
        }
        J.Try result = tryStmt.getPadding()
                .withResources(JContainer.build(
                        tryStmt.getPadding().getResources() != null ?
                                tryStmt.getPadding().getResources().getBefore() : Space.SINGLE_SPACE,
                        newResources,
                        Markers.EMPTY
                ));
        return result.getPadding()
                .withFinally(stripCloseFromFinally(result.getPadding().getFinally(), varName));
    }

    private static @Nullable String extractClosedVarName(J.Block finallyBlock) {
        for (Statement stmt : finallyBlock.getStatements()) {
            String name = extractVarNameFromCloseStatement(stmt);
            if (name != null) {
                return name;
            }
        }
        return null;
    }

    private static @Nullable String extractVarNameFromCloseStatement(Statement stmt) {
        // Direct: var.close()
        if (stmt instanceof J.MethodInvocation) {
            return extractVarNameFromClose((J.MethodInvocation) stmt);
        }

        // Null-guarded: if (var != null) { ... close ... }
        if (stmt instanceof J.If) {
            J.If ifStmt = (J.If) stmt;
            if (ifStmt.getElsePart() != null) {
                return null;
            }
            Statement inner;
            if (ifStmt.getThenPart() instanceof J.Block) {
                J.Block thenBlock = (J.Block) ifStmt.getThenPart();
                if (thenBlock.getStatements().size() != 1) {
                    return null;
                }
                inner = thenBlock.getStatements().get(0);
            } else {
                inner = ifStmt.getThenPart();
            }
            return extractVarNameFromCloseStatement(inner);
        }

        // Try-catch wrapped: try { var.close(); } catch (...) {}
        if (stmt instanceof J.Try) {
            J.Try innerTry = (J.Try) stmt;
            if (innerTry.getFinally() != null) {
                return null;
            }
            List<Statement> body = innerTry.getBody().getStatements();
            if (body.size() == 1 && body.get(0) instanceof J.MethodInvocation) {
                return extractVarNameFromClose((J.MethodInvocation) body.get(0));
            }
        }

        return null;
    }

    private static @Nullable String extractVarNameFromClose(J.MethodInvocation mi) {
        if (CLOSE.matches(mi) && mi.getSelect() instanceof J.Identifier) {
            return ((J.Identifier) mi.getSelect()).getSimpleName();
        }
        return null;
    }

    private static int findMatchingVarDecl(List<Statement> stmts, int tryIndex, String varName) {
        for (int i = tryIndex - 1; i >= 0; i--) {
            if (stmts.get(i) instanceof J.VariableDeclarations) {
                J.VariableDeclarations decl = (J.VariableDeclarations) stmts.get(i);
                if (decl.getVariables().size() == 1 &&
                        decl.getVariables().get(0).getSimpleName().equals(varName)) {
                    Expression init = decl.getVariables().get(0).getInitializer();
                    if (init == null || J.Literal.isLiteralValue(init, null)) {
                        continue;
                    }
                    JavaType.FullyQualified type = TypeUtils.asFullyQualified(decl.getType());
                    if (type != null && TypeUtils.isAssignableTo(AUTO_CLOSEABLE, type)) {
                        return i;
                    }
                }
            }
        }
        return -1;
    }

    private static boolean isReassignedBetween(String varName, List<Statement> stmts, int fromIndex, int toIndex) {
        for (int i = fromIndex + 1; i < toIndex; i++) {
            if (isReassigned(varName, stmts.get(i))) {
                return true;
            }
        }
        return false;
    }

    private static boolean finallyContainsClose(J.Block finallyBlock, String varName) {
        // The target close must be reachable without any non-close statements before it.
        // Try-with-resources closes the resource before the finally block runs, so any
        // statement that originally ran before the close in the finally would otherwise
        // run after it. Other close statements (for variables that will also be merged
        // into the try-with-resources) are tolerated.
        for (Statement stmt : finallyBlock.getStatements()) {
            if (isCloseStatement(stmt, varName)) {
                return true;
            }
            if (extractVarNameFromCloseStatement(stmt) == null) {
                return false;
            }
        }
        return false;
    }

    /**
     * Remove the close statement from the finally block. Returns null if the finally block
     * becomes empty (so it can be removed entirely), otherwise returns the pruned finally block.
     */
    @SuppressWarnings("DataFlowIssue")
    private static JLeftPadded<J.Block> stripCloseFromFinally(@Nullable JLeftPadded<J.Block> finallyPadded, String varName) {
        if (finallyPadded == null) {
            return null;
        }
        J.Block finallyBlock = finallyPadded.getElement();
        List<Statement> remaining = ListUtils.map(finallyBlock.getStatements(),
                stmt -> isCloseStatement(stmt, varName) ? null : stmt);
        if (remaining.isEmpty()) {
            return null;
        }
        return finallyPadded.withElement(finallyBlock.withStatements(remaining));
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
            Statement inner;
            if (ifStmt.getThenPart() instanceof J.Block) {
                J.Block thenBlock = (J.Block) ifStmt.getThenPart();
                if (thenBlock.getStatements().size() != 1) {
                    return false;
                }
                inner = thenBlock.getStatements().get(0);
            } else {
                inner = ifStmt.getThenPart();
            }
            return isDirectClose(inner, varName) || isTryCatchClose(inner, varName);
        }

        // Try-catch wrapped: try { var.close(); } catch (...) {}
        return isTryCatchClose(stmt, varName);
    }

    private static boolean isDirectClose(Statement stmt, String varName) {
        return stmt instanceof J.MethodInvocation && isCloseInvocation((J.MethodInvocation) stmt, varName);
    }

    private static boolean isCloseInvocation(J.MethodInvocation mi, String varName) {
        return CLOSE.matches(mi) &&
                mi.getSelect() instanceof J.Identifier &&
                ((J.Identifier) mi.getSelect()).getSimpleName().equals(varName);
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
        if (isIdentifier(binary.getLeft(), varName) && J.Literal.isLiteralValue(binary.getRight(), null)) {
            return true;
        }
        return J.Literal.isLiteralValue(binary.getLeft(), null) && isIdentifier(binary.getRight(), varName);
    }

    private static boolean isIdentifier(Expression expr, String name) {
        return expr instanceof J.Identifier && ((J.Identifier) expr).getSimpleName().equals(name);
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
