/*
 * Copyright 2024 the original author or authors.
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

import lombok.EqualsAndHashCode;
import lombok.Value;
import org.openrewrite.Tree;
import org.openrewrite.internal.ListUtils;
import org.openrewrite.java.JavaIsoVisitor;
import org.openrewrite.java.style.FallThroughStyle;
import org.openrewrite.java.tree.*;
import org.openrewrite.marker.Markers;

import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Predicate;
import java.util.regex.Pattern;

@Value
@EqualsAndHashCode(callSuper = false)
public class FallThroughVisitor<P> extends JavaIsoVisitor<P> {
    /**
     * Ignores any fall-through commented with a text matching the regex pattern.
     * This is currently non-user-configurable, though held within {@link FallThroughStyle}.
     */
    private static final Pattern RELIEF_PATTERN = Pattern.compile("falls?[ -]?thr(u|ough)");


    FallThroughStyle style;

    private static boolean isLastCase(J.Case case_, J.Switch switch_) {
        J.Block switchBlock = switch_.getCases();
        return case_ == switchBlock.getStatements().get(switchBlock.getStatements().size() - 1);
    }

    @Override
    public J.Case visitCase(J.Case case_, P p) {
        J.Case c = super.visitCase(case_, p);
        if (getCursor().firstEnclosing(J.Switch.class) != null) {
            J.Switch switch_ = getCursor().dropParentUntil(J.Switch.class::isInstance).getValue();
            if (Boolean.TRUE.equals(style.getCheckLastCaseGroup()) || !isLastCase(case_, switch_)) {
                if (FindLastLineBreaksOrFallsThroughComments.find(switch_, c).isEmpty() && FindGuaranteedReturns.find(switch_, c).isEmpty()) {
                    c = (J.Case) new AddBreak<>(c).visitNonNull(c, p, getCursor().getParentOrThrow());
                }
            }
        }
        return c;
    }

    private static class AddBreak<P> extends JavaIsoVisitor<P> {
        private final J.Case scope;

        public AddBreak(J.Case scope) {
            this.scope = scope;
        }

        @Override
        public J.Case visitCase(J.Case case_, P p) {
            if (scope.isScope(case_)) {
                List<Statement> statements = case_.getStatements();
                if (statements.size() == 1 && statements.get(0) instanceof J.Block) {
                    return super.visitCase(case_, p);
                }
                J.Break breakToAdd = autoFormat(
                        new J.Break(Tree.randomId(), Space.EMPTY, Markers.EMPTY, null),
                        p
                );
                statements.add(breakToAdd);
                return case_.withStatements(ListUtils.map(statements, stmt -> autoFormat(stmt, p, getCursor())));
            }
            return case_;
        }

        @Override
        public J.Block visitBlock(J.Block block, P p) {
            J.Block b = block;
            if (getCursor().isScopeInPath(scope)) {
                List<Statement> statements = b.getStatements();
                if (statements.size() == 1 && statements.get(0) instanceof J.Block) {
                    return super.visitBlock(b, p);
                }
                J.Break breakToAdd = autoFormat(
                        new J.Break(Tree.randomId(), Space.EMPTY, Markers.EMPTY, null),
                        p
                );
                statements.add(breakToAdd);
                b = b.withStatements(ListUtils.map(statements, stmt -> autoFormat(stmt, p, getCursor())));
            }
            return b;
        }
    }

    private static class FindLastLineBreaksOrFallsThroughComments {
        private FindLastLineBreaksOrFallsThroughComments() {
        }

        /**
         * If no results are found, it means we should append a {@link J.Break} to the provided {@link J.Case}.
         * A result is added to the set when the last line of the provided {@link J.Case} scope is either an acceptable "break"-able type,
         * specifically {@link J.Return}, {@link J.Break}, {@link J.Continue}, or {@link J.Throw}, or a "fallthrough" {@link Comment} matching a regular expression.
         *
         * @param enclosingSwitch The enclosing {@link J.Switch} subtree to search.
         * @param scope           the {@link J.Case} to use as a target.
         * @return A set representing whether the last {@link Statement} is an acceptable "break"-able type or has a "fallthrough" comment.
         */
        private static Set<J> find(J.Switch enclosingSwitch, J.Case scope) {
            Set<J> references = new HashSet<>();
            new FindLastLineBreaksOrFallsThroughCommentsVisitor(scope).visit(enclosingSwitch, references);
            return references;
        }

        private static class FindLastLineBreaksOrFallsThroughCommentsVisitor extends JavaIsoVisitor<Set<J>> {
            private static final Predicate<Comment> HAS_RELIEF_PATTERN_COMMENT = comment ->
                    comment instanceof TextComment &&
                            RELIEF_PATTERN.matcher(((TextComment) comment).getText()).find();
            private final J.Case scope;

            public FindLastLineBreaksOrFallsThroughCommentsVisitor(J.Case scope) {
                this.scope = scope;
            }

            private static boolean lastLineBreaksOrFallsThrough(List<? extends Statement> trees) {
                return trees.stream()
                        .reduce((s1, s2) -> s2) // last statement
                        .map(s -> breaks(s) || // https://github.com/openrewrite/rewrite-static-analysis/issues/173
                                s.getComments().stream().anyMatch(HAS_RELIEF_PATTERN_COMMENT) ||
                                s instanceof J.Block && ((J.Block) s).getEnd().getComments().stream().anyMatch(HAS_RELIEF_PATTERN_COMMENT)
                        ).orElse(false);
            }

            private static boolean breaks(Statement s) {
                if (s instanceof J.Block) {
                    List<Statement> statements = ((J.Block) s).getStatements();
                    return !statements.isEmpty() && breaks(statements.get(statements.size() - 1));
                } else if (s instanceof J.If) {
                    J.If iff = (J.If) s;
                    return iff.getElsePart() != null && breaks(iff.getThenPart());
                } else if (s instanceof J.Label) {
                    return breaks(((J.Label) s).getStatement());
                } else if (s instanceof J.Try) {
                    J.Try try_ = (J.Try) s;
                    if (try_.getFinally() != null && breaks(try_.getFinally())) {
                        return true;
                    }
                    if (!breaks(try_.getBody())) {
                        return false;
                    }
                    for (J.Try.Catch c : try_.getCatches()) {
                        if (!breaks(c.getBody())) {
                            return false;
                        }
                    }
                    return true;
                }
                return s instanceof J.Return ||
                        s instanceof J.Break ||
                        s instanceof J.Continue ||
                        s instanceof J.Throw ||
                        s instanceof J.Switch;
            }

            @Override
            public J.Switch visitSwitch(J.Switch switch_, Set<J> ctx) {
                J.Switch s = super.visitSwitch(switch_, ctx);
                List<Statement> statements = s.getCases().getStatements();
                for (int i = 0; i < statements.size() - 1; i++) {
                    if (!(statements.get(i) instanceof J.Case)) {
                        continue;
                    }

                    J.Case case_ = (J.Case) statements.get(i);
                    /*
                     * {@code i + 1} because a last-line comment for a J.Case gets attached as a prefix comment in the next case
                     *
                     * <pre>
                     * SWITCH(..) {
                     *  CASE 1:
                     *      someStatement1; // fallthrough
                     *  CASE 2:
                     *      someStatement2;
                     * }
                     * </pre>
                     * <p>
                     * In order to know whether "CASE 1" ended with the comment "fallthrough", we have to check
                     * the "prefix" of CASE 2, because the CASE 2 prefix is what has the comments associated for CASE 1.
                     **/
                    if (case_ == scope && statements.get(i + 1).getPrefix().getComments().stream().anyMatch(HAS_RELIEF_PATTERN_COMMENT)) {
                        ctx.add(s);
                    }
                }
                return s;
            }

            @Override
            public J.Case visitCase(J.Case case_, Set<J> ctx) {
                if (case_ == scope) {
                    if (case_.getStatements().isEmpty() || lastLineBreaksOrFallsThrough(case_.getStatements())) {
                        ctx.add(case_);
                    }
                }
                return case_;
            }

        }

    }

    private static class FindGuaranteedReturns {
        private FindGuaranteedReturns() {
        }

        /**
         * If no results are found, it means we should append a {@link J.Break} to the provided {@link J.Case}.
         * A result is added to the set when a {@link J.Return} statement is found in the {@link J.Case} scope which is guaranteed to execute.
         *
         * @param enclosingSwitch The enclosing {@link J.Switch} subtree to search.
         * @param scope           the {@link J.Case} to use as a target.
         * @return A set representing whether the case contains any guaranteed {@link J.Return} statements.
         */
        private static Set<J> find(J.Switch enclosingSwitch, J.Case scope) {
            Set<J> references = new HashSet<>();
            new FindGuaranteedReturnsVisitor(scope).visit(enclosingSwitch, references);
            return references;
        }

        private static class FindGuaranteedReturnsVisitor extends JavaIsoVisitor<Set<J>> {
            private final J.Case scope;

            public FindGuaranteedReturnsVisitor(J.Case scope) {
                this.scope = scope;
            }


            private static boolean hasGuaranteedReturn(List<? extends Statement> trees) {
                return trees.stream()
                        .anyMatch(s -> returns(s));
            }

            private static boolean returns(Statement s) {
                if (s instanceof J.ForLoop) {
                    J.ForLoop forLoop = (J.ForLoop) s;
                    Expression condition = forLoop.getControl().getCondition();
                    if (condition == null || condition instanceof J.Empty) {
                        Statement body = forLoop.getBody();
                        if (body instanceof J.Block) {
                            return hasGuaranteedReturn(((J.Block) body).getStatements());
                        } else {
                            return hasGuaranteedReturn(Collections.singletonList(forLoop.getBody()));
                        }
                    }
                } else if (s instanceof J.WhileLoop) {
                    J.WhileLoop whileLoop = (J.WhileLoop) s;
                    Expression condition = whileLoop.getCondition();
                    if (((J.ControlParentheses<?>) condition).getTree() instanceof J.Literal) {
                        J.Literal value = (J.Literal) ((J.ControlParentheses<?>) condition).getTree();
                        if (value.getValue() == Boolean.TRUE) {
                            Statement body = whileLoop.getBody();
                            if (body instanceof J.Block) {
                                return !hasBreak(((J.Block) body).getStatements()) && hasGuaranteedReturn(((J.Block) body).getStatements());
                            } else {
                                return hasGuaranteedReturn(Collections.singletonList(whileLoop.getBody()));
                            }
                        }
                    }
                }
                return s instanceof J.Return;
            }

            private static boolean hasBreak(List<Statement> statements) {
                for(Statement s : statements) {
                    if(s instanceof J.Break) {
                        return true;
                    }
                }
                return false;
            }

            @Override
            public J.Case visitCase(J.Case case_, Set<J> ctx) {
                if (case_ == scope) {
                    if (case_.getStatements().isEmpty() || hasGuaranteedReturn(case_.getStatements())) {
                        ctx.add(case_);
                    }
                }
                return case_;
            }
        }
    }
}
