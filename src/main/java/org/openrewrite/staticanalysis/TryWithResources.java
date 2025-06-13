/*
 * Copyright 2025 the original author or authors.
 * <p>
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * <p>
 * https://www.apache.org/licenses/LICENSE-2.0
 * <p>
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.openrewrite.staticanalysis;

import org.openrewrite.ExecutionContext;
import org.openrewrite.Recipe;
import org.openrewrite.Tree;
import org.openrewrite.TreeVisitor;
import org.openrewrite.internal.ListUtils;
import org.openrewrite.java.JavaIsoVisitor;
import org.openrewrite.java.tree.*;
import org.openrewrite.marker.Markers;

import java.util.*;

/**
 * Transforms code using manual resource management with finally blocks to use the Java 7+ try-with-resources pattern.
 * This transformation improves code safety and readability by ensuring resources are properly closed.
 */
public class TryWithResources extends Recipe {

    private static final JavaType.ShallowClass AUTO_CLOSEABLE_TYPE = JavaType.ShallowClass.build("java.lang.AutoCloseable");

    @Override
    public String getDisplayName() {
        return "Use try-with-resources";
    }

    @Override
    public String getDescription() {
        return "Converts code using manual resource management with finally blocks to use the Java 7+ try-with-resources pattern. " +
                "This transformation improves code safety and readability by ensuring resources are properly closed.";
    }

    @Override
    public TreeVisitor<?, ExecutionContext> getVisitor() {
        return new JavaIsoVisitor<ExecutionContext>() {
            @Override
            public J.Block visitBlock(J.Block block, ExecutionContext ctx) {
                J.Block b = super.visitBlock(block, ctx);
                // Process the method body to find try blocks and transform them
                return maybeAutoFormat(block, processBlock(b), ctx);
            }

            @Override
            public J.Try visitTry(J.Try tryable, ExecutionContext ctx) {
                // First, visit the try block normally to process any nested try blocks
                J.Try t = super.visitTry(tryable, ctx);

                // Only process try blocks with a finally block
                if (t.getFinally() == null) {
                    return t;
                }

                // Find variable declarations in the try block
                List<J.VariableDeclarations> variableDeclarations = new ArrayList<>();
                for (Statement statement : t.getBody().getStatements()) {
                    if (statement instanceof J.VariableDeclarations) {
                        variableDeclarations.add((J.VariableDeclarations) statement);
                    }
                }

                if (variableDeclarations.isEmpty()) {
                    return t;
                }

                // Find resources that are closed in the finally block
                Map<String, J.VariableDeclarations> resourcesThatAreClosed = findResourcesThatAreClosedInFinally(variableDeclarations, t.getFinally());

                if (resourcesThatAreClosed.isEmpty()) {
                    return t;
                }

                // Check for resources initialized to null and assigned in the try block
                Map<String, Expression> resourceInitializers = findResourceInitializers(t, resourcesThatAreClosed.keySet());

                // Transform the try block to use try-with-resources
                return transformToTryWithResources(t, resourcesThatAreClosed, resourceInitializers);
            }

            private J.Block processBlock(J.Block body) {
                // Find all try blocks in the method body
                List<J.Try> tryBlocks = new ArrayList<>();
                findTryBlocks(body, tryBlocks);

                if (tryBlocks.isEmpty()) {
                    return body;
                }

                // Process each try block
                J.Block newBody = body;
                for (J.Try tryBlock : tryBlocks) {
                    // Only process try blocks with a finally block
                    if (tryBlock.getFinally() == null) {
                        continue;
                    }

                    // Find variable declarations in the method body that are used in the try block
                    List<J.VariableDeclarations> variableDeclarations = findVariableDeclarationsBeforeTry(newBody, tryBlock);

                    if (variableDeclarations.isEmpty()) {
                        continue;
                    }

                    // Find resources that are closed in the finally block
                    Map<String, J.VariableDeclarations> resourcesThatAreClosed = findResourcesThatAreClosedInFinally(variableDeclarations, tryBlock.getFinally());

                    if (resourcesThatAreClosed.isEmpty()) {
                        continue;
                    }

                    // Check for resources initialized to null and assigned in the try block
                    Map<String, Expression> resourceInitializers = findResourceInitializers(tryBlock, resourcesThatAreClosed.keySet());

                    // Transform the try block to use try-with-resources
                    J.Try newTryBlock = transformToTryWithResources(tryBlock, resourcesThatAreClosed, resourceInitializers);

                    // Replace the old try block with the new one and remove the variable declarations
                    newBody = replaceTryBlockAndRemoveDeclarations(newBody, tryBlock, newTryBlock, resourcesThatAreClosed.values());
                }

                return newBody;
            }

            private Map<String, Expression> findResourceInitializers(J.Try tryBlock, Set<String> resourceNames) {
                Map<String, Expression> resourceInitializers = new HashMap<>();

                // Check the first few statements in the try block for assignments to resources
                for (Statement statement : tryBlock.getBody().getStatements()) {
                    if (statement instanceof J.Assignment) {
                        J.Assignment assignment = (J.Assignment) statement;
                        if (assignment.getVariable() instanceof J.Identifier) {
                            J.Identifier identifier = (J.Identifier) assignment.getVariable();
                            String varName = identifier.getSimpleName();
                            if (resourceNames.contains(varName)) {
                                resourceInitializers.put(varName, assignment.getAssignment());
                            }
                        }
                    }
                }

                return resourceInitializers;
            }

            private void findTryBlocks(J.Block block, List<J.Try> tryBlocks) {
                for (Statement statement : block.getStatements()) {
                    if (statement instanceof J.Try) {
                        tryBlocks.add((J.Try) statement);
                    }
                }
            }

            private List<J.VariableDeclarations> findVariableDeclarationsBeforeTry(J.Block block, J.Try tryBlock) {
                List<J.VariableDeclarations> variableDeclarations = new ArrayList<>();

                // Find the index of the try block
                int tryIndex = -1;
                for (int i = 0; i < block.getStatements().size(); i++) {
                    if (block.getStatements().get(i) == tryBlock) {
                        tryIndex = i;
                        break;
                    }
                }

                if (tryIndex == -1) {
                    return variableDeclarations;
                }

                // Collect all variable declarations before the try block
                for (int i = 0; i < tryIndex; i++) {
                    Statement stmt = block.getStatements().get(i);
                    if (stmt instanceof J.VariableDeclarations) {
                        variableDeclarations.add((J.VariableDeclarations) stmt);
                    }
                }

                return variableDeclarations;
            }

            private Map<String, J.VariableDeclarations> findResourcesThatAreClosedInFinally(List<J.VariableDeclarations> variableDeclarations, J.Block finallyBlock) {
                Map<String, J.VariableDeclarations> resourcesThatAreClosed = new HashMap<>();

                // Find variable declarations that implement AutoCloseable
                for (J.VariableDeclarations varDecl : variableDeclarations) {
                    // Check if the variable type implements AutoCloseable
                    JavaType.FullyQualified type = TypeUtils.asFullyQualified(varDecl.getType());
                    if (type != null && TypeUtils.isAssignableTo(AUTO_CLOSEABLE_TYPE, type)) {
                        for (J.VariableDeclarations.NamedVariable namedVar : varDecl.getVariables()) {
                            String varName = namedVar.getSimpleName();

                            // Check if this variable is closed in the finally block
                            if (isClosedInFinally(varName, finallyBlock)) {
                                resourcesThatAreClosed.put(varName, varDecl);
                            }
                        }
                    }
                }

                return resourcesThatAreClosed;
            }

            private boolean isClosedInFinally(String varName, J.Block finallyBlock) {
                for (Statement statement : finallyBlock.getStatements()) {
                    if (isCloseStatement(statement, varName)) {
                        return true;
                    }
                }
                return false;
            }

            private J.Block replaceTryBlockAndRemoveDeclarations(J.Block block, J.Try oldTry, J.Try newTry, Collection<J.VariableDeclarations> declarations) {
                Set<Statement> declarationsToRemove = new HashSet<>(declarations);
                return block.withStatements(ListUtils.map(block.getStatements(), statement -> {
                    if (statement == oldTry) {
                        return newTry;
                    } else if (declarationsToRemove.contains(statement)) {
                        return null;
                    }
                    return statement;
                }));
            }

            private boolean isCloseStatement(Statement statement, String varName) {
                if (statement instanceof J.If) {
                    // Check for null check before close
                    J.If ifStatement = (J.If) statement;
                    if (isNullCheckForVariable(ifStatement.getIfCondition().getTree(), varName)) {
                        Statement thenPart = ifStatement.getThenPart();
                        if (thenPart instanceof J.Block) {
                            J.Block thenBlock = (J.Block) thenPart;
                            for (Statement thenStatement : thenBlock.getStatements()) {
                                if (isCloseMethodCall(thenStatement, varName) || isNestedTryWithClose(thenStatement, varName)) {
                                    return true;
                                }
                            }
                        } else {
                            return isCloseMethodCall(thenPart, varName) || isNestedTryWithClose(thenPart, varName);
                        }
                    }
                } else if (isCloseMethodCall(statement, varName)) {
                    return true;
                } else {
                    return isNestedTryWithClose(statement, varName);
                }

                return false;
            }

            private boolean isNestedTryWithClose(Statement statement, String varName) {
                if (statement instanceof J.Try) {
                    J.Try tryStatement = (J.Try) statement;

                    // Check if the variable is closed in the try block
                    for (Statement tryBodyStatement : tryStatement.getBody().getStatements()) {
                        if (isCloseMethodCall(tryBodyStatement, varName)) {
                            return true;
                        }
                    }

                    // Check if the variable is closed in any catch blocks
                    for (J.Try.Catch catchBlock : tryStatement.getCatches()) {
                        for (Statement catchBodyStatement : catchBlock.getBody().getStatements()) {
                            if (isCloseMethodCall(catchBodyStatement, varName)) {
                                return true;
                            }
                        }
                    }

                    // Check if the variable is closed in the finally block
                    if (tryStatement.getFinally() != null) {
                        for (Statement finallyStatement : tryStatement.getFinally().getStatements()) {
                            if (isCloseMethodCall(finallyStatement, varName)) {
                                return true;
                            }
                        }
                    }
                }

                return false;
            }

            private boolean isNullCheckForVariable(Expression expression, String varName) {
                if (expression instanceof J.Binary) {
                    J.Binary binary = (J.Binary) expression;
                    if (binary.getOperator() == J.Binary.Type.NotEqual || binary.getOperator() == J.Binary.Type.Equal) {
                        boolean leftIsVar = isVariable(binary.getLeft(), varName);
                        boolean rightIsNull = isNull(binary.getRight());
                        boolean leftIsNull = isNull(binary.getLeft());
                        boolean rightIsVar = isVariable(binary.getRight(), varName);

                        return (leftIsVar && rightIsNull) || (leftIsNull && rightIsVar);
                    }
                }

                return false;
            }

            private boolean isVariable(Expression expression, String varName) {
                if (expression instanceof J.Identifier) {
                    J.Identifier identifier = (J.Identifier) expression;
                    return identifier.getSimpleName().equals(varName);
                }

                return false;
            }

            private boolean isNull(Expression expression) {
                if (expression instanceof J.Literal) {
                    J.Literal literal = (J.Literal) expression;
                    return literal.getValue() == null;
                }

                return false;
            }

            private boolean isCloseMethodCall(Statement statement, String varName) {
                if (statement instanceof J.MethodInvocation) {
                    J.MethodInvocation methodInvocation = (J.MethodInvocation) statement;

                    // Check if it's a call to close()
                    if (methodInvocation.getSimpleName().equals("close")) {
                        // Check if it's called on the variable
                        if (methodInvocation.getSelect() instanceof J.Identifier) {
                            J.Identifier identifier = (J.Identifier) methodInvocation.getSelect();
                            return identifier.getSimpleName().equals(varName);
                        }
                    }
                }

                return false;
            }

            private J.Try transformToTryWithResources(J.Try tryable, Map<String, J.VariableDeclarations> resourcesThatAreClosed, Map<String, Expression> resourceInitializers) {
                // Create resources for the try-with-resources statement
                List<J.Try.Resource> resources = new ArrayList<>();

                List<Map.Entry<String, J.VariableDeclarations>> entries = new ArrayList<>(resourcesThatAreClosed.entrySet());
                for (int i = 0; i < entries.size(); i++) {
                    Map.Entry<String, J.VariableDeclarations> entry = entries.get(i);
                    String varName = entry.getKey();
                    J.VariableDeclarations varDecl = entry.getValue();

                    // Find the named variable
                    for (J.VariableDeclarations.NamedVariable namedVar : varDecl.getVariables()) {
                        if (namedVar.getSimpleName().equals(varName)) {
                            // Create a new variable declaration with just this variable
                            J.VariableDeclarations singleVarDecl = varDecl;
                            if (varDecl.getVariables().size() > 1) {
                                singleVarDecl = varDecl.withVariables(Collections.singletonList(namedVar));
                            }

                            // If the resource is initialized to null and assigned in the try block,
                            // use the assigned value as the initializer
                            if (resourceInitializers.containsKey(varName)) {
                                Expression initializer = resourceInitializers.get(varName);
                                // Create a new list of variables with the updated initializer
                                List<J.VariableDeclarations.NamedVariable> newVars = new ArrayList<>();
                                for (J.VariableDeclarations.NamedVariable var : singleVarDecl.getVariables()) {
                                    if (var.getSimpleName().equals(varName)) {
                                        newVars.add(var.withInitializer(initializer));
                                    } else {
                                        newVars.add(var);
                                    }
                                }
                                singleVarDecl = singleVarDecl.withVariables(newVars);
                            }

                            // Create a resource with proper spacing
                            // First resource gets no prefix, others get a newline and indentation
                            Space prefix;
                            if (i == 0) {
                                prefix = Space.EMPTY;
                            } else {
                                // For multiple resources, format with newline and indentation for better readability
                                prefix = entries.size() > 1 ? Space.format("\n             ") : Space.format(" ");
                            }

                            // Create the resource - only the last one should not have a semicolon
                            J.Try.Resource resource = new J.Try.Resource(
                                    Tree.randomId(),
                                    prefix,
                                    Markers.EMPTY,
                                    singleVarDecl.withPrefix(Space.EMPTY),
                                    i < entries.size() - 1 // Only the last resource should not have a semicolon
                            );

                            resources.add(resource);
                            break;
                        }
                    }
                }

                // Process the finally block to remove close statements
                J.Block finallyBlock = removeCloseStatementsFromFinally(Objects.requireNonNull(tryable.getFinally()), resourcesThatAreClosed.keySet());

                // Create a new try-with-resources statement
                J.Try tryWithResources = tryable
                        .withResources(resources)
                        .withFinally(finallyBlock);

                // If the finally block is now empty, remove it
                if (finallyBlock.getStatements().isEmpty()) {
                    tryWithResources = tryWithResources.withFinally(null);
                }

                // Remove assignments to resources in the try block
                List<Statement> newBodyStatements = new ArrayList<>();
                for (Statement statement : tryWithResources.getBody().getStatements()) {
                    if (!(statement instanceof J.Assignment) ||
                            !isAssignmentToResource(statement, resourcesThatAreClosed.keySet())) {
                        newBodyStatements.add(statement);
                    }
                }
                tryWithResources = tryWithResources.withBody(tryWithResources.getBody().withStatements(newBodyStatements));

                return tryWithResources;
            }

            private boolean isAssignmentToResource(Statement statement, Set<String> resourceNames) {
                if (statement instanceof J.Assignment) {
                    J.Assignment assignment = (J.Assignment) statement;
                    if (assignment.getVariable() instanceof J.Identifier) {
                        J.Identifier identifier = (J.Identifier) assignment.getVariable();
                        return resourceNames.contains(identifier.getSimpleName());
                    }
                }
                return false;
            }

            private J.Block removeCloseStatementsFromFinally(J.Block finallyBlock, Set<String> resourceNames) {
                List<Statement> newStatements = new ArrayList<>();

                for (Statement statement : finallyBlock.getStatements()) {
                    boolean shouldKeep = true;

                    if (statement instanceof J.If) {
                        // Check if it's a null check for a resource
                        J.If ifStatement = (J.If) statement;
                        for (String varName : resourceNames) {
                            if (isNullCheckForVariable(ifStatement.getIfCondition().getTree(), varName)) {
                                shouldKeep = false;
                                break;
                            }
                        }
                    } else if (statement instanceof J.MethodInvocation) {
                        // Check if it's a close call on a resource
                        J.MethodInvocation methodInvocation = (J.MethodInvocation) statement;
                        if (methodInvocation.getSimpleName().equals("close")) {
                            if (methodInvocation.getSelect() instanceof J.Identifier) {
                                J.Identifier identifier = (J.Identifier) methodInvocation.getSelect();
                                if (resourceNames.contains(identifier.getSimpleName())) {
                                    shouldKeep = false;
                                }
                            }
                        }
                    }

                    if (shouldKeep) {
                        newStatements.add(statement);
                    }
                }

                return finallyBlock.withStatements(newStatements);
            }
        };
    }
}
