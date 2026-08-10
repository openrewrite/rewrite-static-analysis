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
import org.jspecify.annotations.Nullable;
import org.openrewrite.ExecutionContext;
import org.openrewrite.Recipe;
import org.openrewrite.Repeat;
import org.openrewrite.TreeVisitor;
import org.openrewrite.internal.ListUtils;
import org.openrewrite.java.JavaIsoVisitor;
import org.openrewrite.java.JavaVisitor;
import org.openrewrite.java.tree.Comment;
import org.openrewrite.java.tree.J;
import org.openrewrite.java.tree.Space;
import org.openrewrite.java.tree.Statement;

import java.time.Duration;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

import static java.util.Collections.singletonList;

public class UnwrapElseAfterReturn extends Recipe {

    @Getter
    final String displayName = "Unwrap else block after return or throw statement";

    @Getter
    final String description = "Unwraps the else block when the if block ends with a return or throw statement, " +
            "reducing nesting and improving code readability.";

    @Getter
    final Duration estimatedEffortPerOccurrence = Duration.ofMinutes(1);

    @Override
    public TreeVisitor<?, ExecutionContext> getVisitor() {
        JavaVisitor<ExecutionContext> javaVisitor = new JavaVisitor<ExecutionContext>() {
            @Override
            public J.Block visitBlock(J.Block block, ExecutionContext ctx) {
                J.Block b = visitAndCast(block, ctx, super::visitBlock);
                AtomicReference<@Nullable Space> endWhitespace = new AtomicReference<>(null);
                J.Block alteredBlock = b.withStatements(ListUtils.flatMap(b.getStatements(), (index, statement) -> {
                    if (statement instanceof J.If) {
                        J.If ifStatement = (J.If) statement;
                        if (ifStatement.getElsePart() != null && endsWithReturnOrThrow(ifStatement.getThenPart())) {
                            List<Statement> laterStatements = b.getStatements().subList(index + 1, b.getStatements().size());
                            Statement elsePart = ifStatement.getElsePart().getBody();
                            if (elsePart instanceof J.If) {
                                // Else-if chain: find and unwrap the innermost else
                                J.If innermost = findInnermostIfWithElse((J.If) elsePart);
                                if (innermost != null &&
                                        innermost.getElsePart() != null &&
                                        endsWithReturnOrThrow(innermost.getThenPart()) &&
                                        !(innermost.getElsePart().getBody() instanceof J.If)) {
                                    // Unwrap the innermost else
                                    Statement innermostElseBody = innermost.getElsePart().getBody();
                                    if (!collidesWithLaterScope(innermostElseBody, laterStatements)) {
                                        J.If modifiedChain = removeInnermostElse(ifStatement);
                                        return flatten(innermost, innermostElseBody, endWhitespace, modifiedChain);
                                    }
                                }
                            } else if (!collidesWithLaterScope(elsePart, laterStatements)) {
                                // Plain else block: unwrap directly
                                J.If newIf = ifStatement.withElsePart(null);
                                return flatten(ifStatement, elsePart, endWhitespace, newIf);
                            }
                        }
                    }
                    return statement;
                }));

                Space end = endWhitespace.get();
                if (end != null) {
                    List<Comment> mergedComments = ListUtils.concatAll(end.getComments(), b.getEnd().getComments());
                    alteredBlock = alteredBlock.withEnd(b.getEnd().withComments(mergedComments).withWhitespace(end.getWhitespace()));
                }

                return maybeAutoFormat(b, alteredBlock, ctx);
            }

            private List<Statement> flatten(J.If tailIf, Statement tailElse, AtomicReference<@Nullable Space> endWhitespace, J.If ifWithoutElse) {
                if (tailElse instanceof J.Block) {
                    J.Block elseBlock = (J.Block) tailElse;
                    endWhitespace.set(elseBlock.getEnd());
                    return ListUtils.concat(ifWithoutElse, ListUtils.mapFirst(elseBlock.getStatements(), elseStmt -> {
                        List<Comment> elseComments = elseBlock.getPrefix().getComments();
                        List<Comment> stmtComments = elseStmt.getPrefix().getComments();
                        if (!elseComments.isEmpty() || !stmtComments.isEmpty()) {
                            return elseStmt.withComments(ListUtils.concatAll(elseComments, stmtComments));
                        }
                        String whitespace = tailIf.getElsePart().getPrefix().getWhitespace();
                        return elseStmt.withPrefix(elseStmt.getPrefix().withWhitespace(whitespace));
                    }));
                }
                return Arrays.asList(ifWithoutElse, tailElse.withPrefix(tailIf.getElsePart().getPrefix()));
            }

            /**
             * Statements hoisted out of the else block move into the enclosing block, where the names they
             * declare stay in scope until the end of that block. Unwrapping is therefore skipped when that
             * larger scope could change how a name in the statements after the {@code if} resolves:
             * <ul>
             * <li>A hoisted name that is declared again in a later statement, at any nesting depth, would
             * usually no longer compile, since Java does not allow local variables or local classes of a
             * method to shadow each other; that covers later locals, loop variables, catch parameters,
             * resources, lambda parameters, pattern variables and local types.</li>
             * <li>A later unqualified use of a hoisted name currently resolves to something else, such as
             * a field or a statically imported member, and would be captured by the hoisted declaration,
             * silently changing semantics or breaking compilation. Uses whose resolution cannot be
             * affected by a local variable or local class coming into scope, such as method invocation
             * names, qualified field accesses and labels, are exempt.</li>
             * </ul>
             * The names a hoisted statement introduces are its declared variables and local types, plus
             * every {@code instanceof} pattern variable anywhere inside it: flow scoping (JLS 6.3.2) can
             * extend a pattern variable past its statement once that statement sits directly in the
             * enclosing block, e.g. {@code if (!(o instanceof String s)) return;} leaves {@code s} in
             * scope for the rest of the block. Not every pattern variable escapes its statement, so this
             * errs on the side of keeping the else block.
             */
            private boolean collidesWithLaterScope(Statement elseBody, List<Statement> laterStatements) {
                if (laterStatements.isEmpty()) {
                    return false;
                }
                Set<String> hoistedNames = new HashSet<>();
                JavaIsoVisitor<Set<String>> patternVariableCollector = new JavaIsoVisitor<Set<String>>() {
                    @Override
                    public J.InstanceOf visitInstanceOf(J.InstanceOf instanceOf, Set<String> names) {
                        if (instanceOf.getPattern() instanceof J.Identifier) {
                            names.add(((J.Identifier) instanceOf.getPattern()).getSimpleName());
                        }
                        return super.visitInstanceOf(instanceOf, names);
                    }

                    @Override
                    public J.VariableDeclarations.NamedVariable visitVariable(J.VariableDeclarations.NamedVariable variable, Set<String> names) {
                        // The bindings of a record deconstruction pattern are variable declarations nested inside the pattern
                        if (getCursor().firstEnclosing(J.DeconstructionPattern.class) != null) {
                            names.add(variable.getSimpleName());
                        }
                        return super.visitVariable(variable, names);
                    }
                };
                List<Statement> hoistedStatements = elseBody instanceof J.Block ? ((J.Block) elseBody).getStatements() : singletonList(elseBody);
                for (Statement hoisted : hoistedStatements) {
                    if (hoisted instanceof J.VariableDeclarations) {
                        for (J.VariableDeclarations.NamedVariable variable : ((J.VariableDeclarations) hoisted).getVariables()) {
                            hoistedNames.add(variable.getSimpleName());
                        }
                    } else if (hoisted instanceof J.ClassDeclaration) {
                        hoistedNames.add(((J.ClassDeclaration) hoisted).getSimpleName());
                    }
                    patternVariableCollector.visit(hoisted, hoistedNames);
                }
                if (hoistedNames.isEmpty()) {
                    return false;
                }

                AtomicBoolean collides = new AtomicBoolean(false);
                JavaIsoVisitor<AtomicBoolean> nameScanner = new JavaIsoVisitor<AtomicBoolean>() {
                    @Override
                    public J.Identifier visitIdentifier(J.Identifier identifier, AtomicBoolean found) {
                        if (hoistedNames.contains(identifier.getSimpleName())) {
                            // Both declarations and unqualified uses appear as identifiers; only identifiers
                            // that resolve in another namespace or through a qualifier are unaffected
                            Object parent = getCursor().getParentTreeCursor().getValue();
                            boolean unaffected = parent instanceof J.MethodInvocation && identifier == ((J.MethodInvocation) parent).getName() ||
                                    parent instanceof J.FieldAccess && identifier == ((J.FieldAccess) parent).getName() ||
                                    parent instanceof J.MemberReference && identifier == ((J.MemberReference) parent).getReference() ||
                                    parent instanceof J.MethodDeclaration && identifier == ((J.MethodDeclaration) parent).getName() ||
                                    parent instanceof J.Label ||
                                    parent instanceof J.Break ||
                                    parent instanceof J.Continue;
                            if (!unaffected) {
                                found.set(true);
                            }
                        }
                        return super.visitIdentifier(identifier, found);
                    }
                };
                for (Statement laterStatement : laterStatements) {
                    nameScanner.visit(laterStatement, collides);
                    if (collides.get()) {
                        return true;
                    }
                }
                return false;
            }

            private J.@Nullable If findInnermostIfWithElse(J.If ifStatement) {
                if (ifStatement.getElsePart() == null) {
                    return null;
                }
                Statement elseBody = ifStatement.getElsePart().getBody();
                if (elseBody instanceof J.If) {
                    J.If result = findInnermostIfWithElse((J.If) elseBody);
                    return result != null ? result : ifStatement;
                }
                return ifStatement;
            }

            private J.If removeInnermostElse(J.If ifStatement) {
                if (ifStatement.getElsePart() == null) {
                    return ifStatement;
                }
                Statement elseBody = ifStatement.getElsePart().getBody();
                if (elseBody instanceof J.If) {
                    J.If innerIf = (J.If) elseBody;
                    if (innerIf.getElsePart() != null && !(innerIf.getElsePart().getBody() instanceof J.If)) {
                        // This is the innermost if with a non-if else, remove its else
                        return ifStatement.withElsePart(
                                ifStatement.getElsePart().withBody(innerIf.withElsePart(null))
                        );
                    }
                    // Recurse deeper into the chain
                    return ifStatement.withElsePart(
                            ifStatement.getElsePart().withBody(removeInnermostElse(innerIf))
                    );
                }
                // Direct else (not else-if), remove it
                return ifStatement.withElsePart(null);
            }

            private boolean endsWithReturnOrThrow(Statement statement) {
                if (statement instanceof J.Return || statement instanceof J.Throw) {
                    return true;
                }
                if (statement instanceof J.Block) {
                    J.Block block = (J.Block) statement;
                    if (!block.getStatements().isEmpty()) {
                        Statement lastStatement = block.getStatements().get(block.getStatements().size() - 1);
                        return lastStatement instanceof J.Return || lastStatement instanceof J.Throw;
                    }
                }
                return false;
            }
        };
        return Repeat.repeatUntilStable(javaVisitor);
    }
}
