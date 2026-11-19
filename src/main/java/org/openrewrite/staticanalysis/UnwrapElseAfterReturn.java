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
import org.openrewrite.Cursor;
import org.openrewrite.ExecutionContext;
import org.openrewrite.Recipe;
import org.openrewrite.Repeat;
import org.openrewrite.TreeVisitor;
import org.openrewrite.internal.ListUtils;
import org.openrewrite.java.JavaIsoVisitor;
import org.openrewrite.java.format.ShiftFormat;
import org.openrewrite.java.service.ScopeService;
import org.openrewrite.java.trait.Scope;
import org.openrewrite.java.tree.*;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
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
        JavaIsoVisitor<ExecutionContext> javaVisitor = new JavaIsoVisitor<ExecutionContext>() {
            @Override
            public J.Block visitBlock(J.Block block, ExecutionContext ctx) {
                J.Block b = super.visitBlock(block, ctx);
                AtomicReference<@Nullable Space> blockEnd = new AtomicReference<>(null);
                J.Block alteredBlock = b.withStatements(ListUtils.flatMap(b.getStatements(), statement -> {
                    if (statement instanceof J.If) {
                        J.If ifStatement = (J.If) statement;
                        if (ifStatement.getElsePart() != null && leavesByReturnOrThrow(ifStatement.getThenPart())) {
                            Statement elsePart = ifStatement.getElsePart().getBody();
                            if (elsePart instanceof J.If) {
                                J.If innermost = findInnermostIfWithElse((J.If) elsePart);
                                if (innermost != null &&
                                        innermost.getElsePart() != null &&
                                        leavesByReturnOrThrow(innermost.getThenPart()) &&
                                        !(innermost.getElsePart().getBody() instanceof J.If)) {
                                    J.If modifiedChain = removeInnermostElse(ifStatement);
                                    Statement innermostElseBody = innermost.getElsePart().getBody();
                                    if (widenedScopeCollides(b, ifStatement, innermostElseBody)) {
                                        return statement;
                                    }
                                    return flatten(ifStatement, innermost, innermostElseBody, b.getEnd(), blockEnd, modifiedChain);
                                }
                            } else if (!widenedScopeCollides(b, ifStatement, elsePart)) {
                                J.If newIf = ifStatement.withElsePart(null);
                                return flatten(ifStatement, ifStatement, elsePart, b.getEnd(), blockEnd, newIf);
                            }
                        }
                    }
                    return statement;
                }));

                Space end = blockEnd.get();
                return end == null ? alteredBlock : alteredBlock.withEnd(end);
            }

            /**
             * Whether a name the else body declares would, once in scope for the rest of the enclosing block, clash
             * with a later declaration there or capture a name a later statement reads.
             */
            private boolean widenedScopeCollides(J.Block enclosing, J.If ifStatement, Statement elseBody) {
                if (!(elseBody instanceof J.Block)) {
                    return false;
                }
                Cursor elseCursor = new Cursor(new Cursor(getCursor(), ifStatement), elseBody);
                List<Cursor> later = new ArrayList<>();
                List<Statement> statements = enclosing.getStatements();
                for (Statement statement : statements.subList(statements.indexOf(ifStatement) + 1, statements.size())) {
                    later.add(new Cursor(getCursor(), statement));
                }
                return Scope.at(elseCursor).collidesAcross(later);
            }

            private List<Statement> flatten(J.If blockIf, J.If tailIf, Statement tailElse, Space end,
                                            AtomicReference<@Nullable Space> blockEnd, J.If ifWithoutElse) {
                Space elsePrefix = tailIf.getElsePart().getPrefix();
                String ifWhitespace = blockIf.getPrefix().getWhitespace();
                String statementIndent = ifWhitespace.substring(Math.max(0, ifWhitespace.lastIndexOf('\n')));
                List<Statement> unwrapped;
                List<Comment> leadingComments;
                if (tailElse instanceof J.Block) {
                    J.Block elseBlock = ShiftFormat.indent(tailElse, getCursor(), -1);
                    unwrapped = elseBlock.getStatements();
                    leadingComments = ListUtils.concatAll(elsePrefix.getComments(), elseBlock.getPrefix().getComments());
                    Space elseEnd = elseBlock.getEnd();
                    if (!elseEnd.getComments().isEmpty()) {
                        blockEnd.set(end.getComments().isEmpty() ? elseEnd : Space.build(elseEnd.getWhitespace(), ListUtils.concatAll(
                                ListUtils.mapLast(elseEnd.getComments(), c -> c.withSuffix(statementIndent)), end.getComments())));
                    }
                } else {
                    unwrapped = singletonList(ShiftFormat.indent(tailElse, getCursor(), -1));
                    leadingComments = elsePrefix.getComments();
                }
                return ListUtils.concat(ifWithoutElse, ListUtils.mapFirst(unwrapped, first -> {
                    Space prefix = first.getPrefix();
                    String whitespace = !elsePrefix.getComments().isEmpty() ? elsePrefix.getWhitespace() :
                            prefix.getWhitespace().contains("\n") ? prefix.getWhitespace() : statementIndent;
                    return first.withPrefix(Space.build(whitespace, ListUtils.concatAll(leadingComments, prefix.getComments())));
                }));
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
                        return ifStatement.withElsePart(
                                ifStatement.getElsePart().withBody(innerIf.withElsePart(null))
                        );
                    }
                    return ifStatement.withElsePart(
                            ifStatement.getElsePart().withBody(removeInnermostElse(innerIf))
                    );
                }
                return ifStatement.withElsePart(null);
            }

            /**
             * Whether the then part cannot complete normally and leaves only by return or throw, which the recipe
             * covers. A break counts as leaving unless a loop or switch inside the then part encloses it, and a
             * continue unless a loop does.
             */
            private boolean leavesByReturnOrThrow(Statement thenPart) {
                if (service(ScopeService.class).completesNormally(thenPart)) {
                    return false;
                }
                return !new JavaIsoVisitor<AtomicBoolean>() {
                    @Override
                    public J.Break visitBreak(J.Break breakStatement, AtomicBoolean leaves) {
                        if (getCursor().firstEnclosing(Loop.class) == null && getCursor().firstEnclosing(J.Switch.class) == null) {
                            leaves.set(true);
                        }
                        return breakStatement;
                    }

                    @Override
                    public J.Continue visitContinue(J.Continue continueStatement, AtomicBoolean leaves) {
                        if (getCursor().firstEnclosing(Loop.class) == null) {
                            leaves.set(true);
                        }
                        return continueStatement;
                    }
                }.reduce(thenPart, new AtomicBoolean()).get();
            }
        };
        return Repeat.repeatUntilStable(javaVisitor);
    }
}
