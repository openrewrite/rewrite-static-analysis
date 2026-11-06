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
import org.openrewrite.ExecutionContext;
import org.openrewrite.Recipe;
import org.openrewrite.SourceFile;
import org.openrewrite.TreeVisitor;
import org.openrewrite.internal.ListUtils;
import org.openrewrite.java.JavaVisitor;
import org.openrewrite.java.marker.Semicolon;
import org.openrewrite.java.search.SemanticallyEqual;
import org.openrewrite.java.tree.Expression;
import org.openrewrite.java.tree.J;
import org.openrewrite.java.tree.JRightPadded;
import org.openrewrite.java.tree.Space;
import org.openrewrite.java.tree.Statement;
import org.openrewrite.kotlin.tree.K;
import org.openrewrite.staticanalysis.javascript.JavascriptFileChecker;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Set;

import static java.util.Collections.emptyList;
import static java.util.Collections.singleton;
import static org.openrewrite.Tree.randomId;
import static org.openrewrite.staticanalysis.SideEffects.mayHaveSideEffects;

@Getter
public class AllBranchesIdentical extends Recipe {

    final String displayName = "All branches in a conditional should not have the same implementation";

    final String description = "When every branch of an `if`/`else` chain executes the same code, " +
            "the condition serves no purpose and the code block can be used directly.";

    final Set<String> tags = singleton("RSPEC-S3923");

    final Duration estimatedEffortPerOccurrence = Duration.ofMinutes(15);

    @Override
    public TreeVisitor<?, ExecutionContext> getVisitor() {
        return new JavaVisitor<ExecutionContext>() {

            @Override
            public J visitBlock(J.Block block, ExecutionContext ctx) {
                J.Block bl = (J.Block) super.visitBlock(block, ctx);
                return bl.getPadding().withStatements(ListUtils.flatMap(bl.getPadding().getStatements(), rp -> {
                    if (!(rp.getElement() instanceof J.If)) {
                        return rp;
                    }
                    J.If if_ = (J.If) rp.getElement();
                    Expression condition = if_.getIfCondition().getTree();
                    if (if_.getElsePart() == null ||
                            if_.getElsePart().getBody() instanceof J.If ||
                            !(condition instanceof J.MethodInvocation) ||
                            !SemanticallyEqual.areEqual(if_.getThenPart(), if_.getElsePart().getBody())) {
                        return rp;
                    }

                    Statement body = if_.getThenPart();
                    JRightPadded<Statement> bodyRp = if_.getPadding().getThenPart();
                    List<JRightPadded<Statement>> bodyStatements = body instanceof J.Block ? ((J.Block) body).getPadding().getStatements() : emptyList();
                    if (body instanceof J.Block && !bodyStatements.isEmpty() &&
                            getCursor().firstEnclosing(K.CompilationUnit.class) != null &&
                            bodyStatements.stream().anyMatch(s -> s.getElement() instanceof J.VariableDeclarations)) {
                        // A block that `RemoveUnneededBlock` keeps for scoping would become an unused lambda in Kotlin
                        return rp;
                    }
                    boolean emptyBody = body instanceof J.Empty || body instanceof J.Block && bodyStatements.isEmpty();
                    // The hoisted call borrows the padding of the body's first statement, which carries e.g. the JS/TS `Semicolon` marker
                    JRightPadded<Statement> conditionRp = body instanceof J.Block ? (emptyBody ? rp : bodyStatements.get(0)) : bodyRp;
                    if (JavascriptFileChecker.isJavaScript(getCursor().firstEnclosing(SourceFile.class)) &&
                            !conditionRp.getMarkers().findFirst(Semicolon.class).isPresent()) {
                        conditionRp = conditionRp.withMarkers(conditionRp.getMarkers().add(new Semicolon(randomId())));
                    }
                    JRightPadded<Statement> hoisted = conditionRp.withElement(((Statement) condition).withPrefix(if_.getPrefix()));
                    if (emptyBody) {
                        return hoisted;
                    }
                    if (body instanceof J.Block) {
                        doAfterVisit(new RemoveUnneededBlock.RemoveUnneededBlockStatementVisitor((J.Block) body));
                    }
                    return Arrays.asList(hoisted,
                            bodyRp.withElement(body.withPrefix(Space.build(if_.getPrefix().getLastWhitespace(), body.getComments()))));
                }));
            }

            @Override
            public J visitIf(J.If if_, ExecutionContext ctx) {
                J.If if__ = (J.If) super.visitIf(if_, ctx);

                if (if__.getElsePart() == null) {
                    return if__;
                }

                List<Statement> bodies = new ArrayList<>();
                List<Expression> conditions = new ArrayList<>();
                J.If current = if__;

                while (current != null) {
                    bodies.add(current.getThenPart());
                    conditions.add(current.getIfCondition().getTree());
                    if (current.getElsePart() == null) {
                        return if__;
                    }
                    Statement elseBody = current.getElsePart().getBody();
                    if (elseBody instanceof J.If) {
                        current = (J.If) elseBody;
                    } else {
                        bodies.add(elseBody);
                        current = null;
                    }
                }

                Statement first = bodies.get(0);
                for (int i = 1; i < bodies.size(); i++) {
                    if (!SemanticallyEqual.areEqual(first, bodies.get(i))) {
                        return if__;
                    }
                }

                // Collapsing the chain stops evaluating the conditions, which is only safe when they are pure
                for (Expression condition : conditions) {
                    if (mayHaveSideEffects(condition)) {
                        return if__;
                    }
                }

                if (first instanceof J.Block) {
                    doAfterVisit(new RemoveUnneededBlock.RemoveUnneededBlockStatementVisitor((J.Block) first));
                }
                return first.withPrefix(if__.getPrefix());
            }
        };
    }
}
