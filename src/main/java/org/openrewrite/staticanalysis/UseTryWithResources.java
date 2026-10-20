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
import org.openrewrite.java.marker.JavaVersion;
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
import static java.util.Objects.requireNonNull;

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
                        boolean unnamedAvailable = getCursor().firstEnclosingOrThrow(JavaSourceFile.class)
                                .getMarkers().findFirst(JavaVersion.class)
                                .filter(v -> v.getMajorVersion() >= 22)
                                .isPresent();
                        List<@Nullable Statement> result = new ArrayList<>(stmts);
                        for (int i = 0; i < stmts.size(); i++) {
                            if (!(stmts.get(i) instanceof J.Try)) {
                                continue;
                            }
                            J.Try tryStmt = (J.Try) stmts.get(i);
                            // Try-with-resources closes its resources in reverse order before the finally runs,
                            // so each leading close of the finally becomes the new first resource. The merge stops
                            // at the first close it can't merge, as a later close moved into the header would run before it.
                            while (tryStmt.getFinally() != null && !tryStmt.getFinally().getStatements().isEmpty()) {
                                List<Statement> finallyStmts = tryStmt.getFinally().getStatements();
                                String closedVar = extractVarNameFromCloseStatement(finallyStmts.get(0));
                                if (closedVar == null || !isCloseStatement(finallyStmts.get(0), closedVar)) {
                                    break;
                                }
                                int declIdx = findMatchingVarDecl(stmts, i, closedVar);
                                if (declIdx < 0 || isResource(closedVar, tryStmt) || isReassignedAfter(closedVar, stmts, declIdx) ||
                                        // Catches run after the resources close
                                        tryStmt.getCatches().stream().anyMatch(c -> isReferenced(closedVar, c.getBody()))) {
                                    break;
                                }
                                J.VariableDeclarations varDecl = (J.VariableDeclarations) stmts.get(declIdx);
                                // A declaration moved into the header is out of scope in the finally and after the try
                                boolean declarationForm = declIdx == i - 1 && !isUsedAfter(closedVar, stmts, i) &&
                                        finallyStmts.stream().skip(1).noneMatch(s -> isReferenced(closedVar, s));
                                if (declarationForm) {
                                    tryStmt = transform(varDecl, tryStmt, unnamedAvailable);
                                    result.set(declIdx, null);
                                } else {
                                    tryStmt = transformJava9(varDecl, tryStmt);
                                }
                            }
                            result.set(i, tryStmt);
                        }
                        return b.withStatements(ListUtils.map(stmts, (i, stmt) -> result.get(i)));
                    }
                });
    }

    private static boolean isResource(String varName, J.Try tryStmt) {
        if (tryStmt.getResources() != null) {
            for (J.Try.Resource res : tryStmt.getResources()) {
                TypedTree resource = res.getVariableDeclarations();
                if (resource instanceof J.Identifier && ((J.Identifier) resource).getSimpleName().equals(varName) ||
                        resource instanceof J.VariableDeclarations &&
                        ((J.VariableDeclarations) resource).getVariables().get(0).getSimpleName().equals(varName)) {
                    return true;
                }
            }
        }
        return false;
    }

    private static J.Try transform(J.VariableDeclarations varDecl, J.Try tryStmt, boolean unnamedAvailable) {
        String varName = varDecl.getVariables().get(0).getSimpleName();
        J.VariableDeclarations resourceDecl = varDecl.withPrefix(Space.EMPTY);
        // When only the close in the finally reads the resource, name it with the unnamed variable `_`
        // (JEP 456 in Java 22, a preview in Java 21 that needs --enable-preview) so the rewritten code does
        // not emit an unused-variable warning that would fail `-Werror` builds.
        if (unnamedAvailable && !isReferenced(varName, tryStmt.withFinally(null))) {
            J.VariableDeclarations.NamedVariable namedVar = resourceDecl.getVariables().get(0);
            JavaType.Variable renamedType = namedVar.getVariableType() != null ?
                    namedVar.getVariableType().withName("_") : null;
            J.Identifier renamedName = namedVar.getName().withSimpleName("_")
                    .withFieldType(renamedType);
            resourceDecl = resourceDecl.withVariables(singletonList(
                    namedVar.withName(renamedName).withVariableType(renamedType)));
        }
        return addResource(resourceDecl, tryStmt.withPrefix(varDecl.getPrefix()));
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
        return addResource(resourceRef, tryStmt);
    }

    /**
     * Prepend the resource to the try's resources and remove the first statement of the finally,
     * which is the resource's close. The finally is removed when that leaves it empty.
     */
    private static J.Try addResource(TypedTree resourceExpr, J.Try tryStmt) {
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
            newResources = ListUtils.concat(
                    JRightPadded.build(resource.withPrefix(existingResources.get(0).getElement().getPrefix())
                            .withTerminatedWithSemicolon(true)),
                    ListUtils.mapFirst(existingResources, first -> first.withElement(first.getElement().withPrefix(
                            existingResources.size() > 1 ? existingResources.get(1).getElement().getPrefix() : Space.SINGLE_SPACE))));
        }
        J.Try result = tryStmt.getPadding()
                .withResources(JContainer.build(
                        tryStmt.getPadding().getResources() != null ?
                                tryStmt.getPadding().getResources().getBefore() : Space.SINGLE_SPACE,
                        newResources,
                        Markers.EMPTY
                ));
        JLeftPadded<J.Block> finallyPadded = requireNonNull(result.getPadding().getFinally());
        List<Statement> remaining = ListUtils.map(finallyPadded.getElement().getStatements(), (j, stmt) -> j == 0 ? null : stmt);
        return result.getPadding()
                .withFinally(remaining.isEmpty() ? null : finallyPadded.withElement(finallyPadded.getElement().withStatements(remaining)));
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

    /** A resource must be final or effectively final (JLS 14.20.3). */
    private static boolean isReassignedAfter(String varName, List<Statement> stmts, int declIndex) {
        for (int i = declIndex + 1; i < stmts.size(); i++) {
            if (isReassigned(varName, stmts.get(i))) {
                return true;
            }
        }
        return false;
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

    private static boolean isReferenced(String varName, J tree) {
        return new JavaIsoVisitor<AtomicBoolean>() {
            @Override
            public J.Identifier visitIdentifier(J.Identifier identifier, AtomicBoolean f) {
                if (identifier.getSimpleName().equals(varName)) {
                    f.set(true);
                }
                return super.visitIdentifier(identifier, f);
            }
        }.reduce(tree, new AtomicBoolean()).get();
    }
}
