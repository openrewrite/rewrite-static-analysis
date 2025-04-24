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
import org.jspecify.annotations.Nullable;
import org.openrewrite.Tree;
import org.openrewrite.internal.ListUtils;
import org.openrewrite.java.JavaIsoVisitor;
import org.openrewrite.java.style.DefaultComesLastStyle;
import org.openrewrite.java.tree.J;
import org.openrewrite.java.tree.Space;
import org.openrewrite.java.tree.Statement;
import org.openrewrite.marker.Markers;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import static java.util.stream.Collectors.toList;

@Value
@EqualsAndHashCode(callSuper = false)
public class DefaultComesLastVisitor<P> extends JavaIsoVisitor<P> {
    DefaultComesLastStyle style;

    @Override
    public J.Switch visitSwitch(J.Switch switch_, P p) {
        J.Switch s = visitAndCast(switch_, p, super::visitSwitch);

        if (!isDefaultCaseLastOrNotPresent(switch_)) {
            List<J.Case> cases = s.getCases().getStatements().stream().map(J.Case.class::cast).collect(toList());
            List<J.Case> casesWithDefaultLast = maybeReorderCases(cases, p);
            if (casesWithDefaultLast != null) {
                boolean changed = true;
                if (cases.size() == casesWithDefaultLast.size()) {
                    changed = false;
                    for (int i = 0; i < cases.size(); i++) {
                        if (cases.get(i) != casesWithDefaultLast.get(i)) {
                            changed = true;
                            break;
                        }
                    }
                }

                if (changed) {
                    s = s.withCases(s.getCases().withStatements(casesWithDefaultLast.stream().map(Statement.class::cast).collect(toList())));
                }
            }
        }
        return s;
    }

    private @Nullable List<J.Case> maybeReorderCases(List<J.Case> cases, P p) {
        List<J.Case> fallThroughCases = new ArrayList<>();
        List<J.Case> defaultCases = new ArrayList<>();
        List<J.Case> casesWithDefaultLast = new ArrayList<>();

        boolean defaultCaseFound = false;
        for (int i = 0; i < cases.size(); i++) {
            J.Case aCase = cases.get(i);
            if (aCase.getBody() != null) {
                return null;
            }
            fallThroughCases.add(aCase);
            if (isDefaultCase(aCase)) {
                defaultCaseFound = true;
            }
            if (i == cases.size() - 1 || !isFallthroughCase(aCase.getStatements())) {
                if (defaultCaseFound) {
                    defaultCases.addAll(fallThroughCases);
                    fallThroughCases.clear();
                    defaultCaseFound = false;
                } else {
                    casesWithDefaultLast.addAll(fallThroughCases);
                    fallThroughCases.clear();
                }
            }
        }

        casesWithDefaultLast = addBreakToLastCase(casesWithDefaultLast, p);
        casesWithDefaultLast.addAll(maybeReorderFallthroughCases(defaultCases, p));
        casesWithDefaultLast = ListUtils.mapLast(casesWithDefaultLast, this::removeBreak);
        return casesWithDefaultLast;
    }

    private List<J.Case> maybeReorderFallthroughCases(List<J.Case> cases, P p) {
        List<J.Case> preDefaultCases = new ArrayList<>();
        List<J.Case> postDefaultCases = new ArrayList<>();
        J.Case defaultCase = null;
        for (int i = 0; i < cases.size(); i++) {
            J.Case aCase = cases.get(i);
            if (isDefaultCase(aCase)) {
                if (!aCase.getStatements().isEmpty()) {
                    return cases;
                }
                defaultCase = aCase;
            } else if (defaultCase != null) {
                if (!aCase.getStatements().isEmpty() && i != cases.size() - 1) {
                    return cases;
                } else {
                    postDefaultCases.add(aCase);
                }
            } else {
                preDefaultCases.add(aCase);
            }
        }

        List<J.Case> fixedCases = new ArrayList<>();
        fixedCases.addAll(preDefaultCases);
        if (!postDefaultCases.isEmpty()) {
            List<Statement> statements = postDefaultCases.get(postDefaultCases.size() - 1).getStatements();
            defaultCase = defaultCase.withStatements(statements);
            fixedCases.addAll(ListUtils.mapLast(postDefaultCases, e -> e.withStatements(Collections.emptyList())));
        }
        fixedCases.add(defaultCase);
        return fixedCases;
    }

    private List<J.Case> addBreakToLastCase(List<J.Case> cases, P p) {
        return ListUtils.mapLast(cases, e -> {
            if (isFallthroughCase(e.getStatements())) {
                return addBreak(e, p);
            }
            return e;
        });
    }

    private boolean isFallthroughCase(List<Statement> statements) {
        if (statements.isEmpty()) {
            return true;
        }
        Statement lastStatement = statements.get(statements.size() - 1);
        if (lastStatement instanceof J.Block) {
            return isFallthroughCase(((J.Block) lastStatement).getStatements());
        }
        return !(lastStatement instanceof J.Break ||
              lastStatement instanceof J.Continue ||
              lastStatement instanceof J.Return ||
              lastStatement instanceof J.Throw);
    }

    private J.Case addBreak(J.Case e, P p) {
        J.Break breakStatement = autoFormat(new J.Break(Tree.randomId(), Space.EMPTY, Markers.EMPTY, null), p);
        List<Statement> statements = e.getStatements();
        statements.add(breakStatement);
        return e.withStatements(ListUtils.map(statements, stmt -> autoFormat(stmt, p, getCursor())));
    }

    private J.Case removeBreak(J.Case aCase) {
        if (!aCase.getStatements().isEmpty() && aCase.getStatements().get(aCase.getStatements().size() - 1) instanceof J.Break && ((J.Break) aCase.getStatements().get(aCase.getStatements().size() - 1)).getLabel() == null) {
            aCase = aCase.withStatements(aCase.getStatements().subList(0, aCase.getStatements().size() - 1));
        }
        return aCase;
    }

    private boolean isDefaultCaseLastOrNotPresent(J.Switch switch_) {
        J.Case defaultCase = null;
        J.Case prior = null;
        for (Statement aCaseStmt : switch_.getCases().getStatements()) {
            if (!(aCaseStmt instanceof J.Case)) {
                continue;
            }

            J.Case aCase = (J.Case) aCaseStmt;

            if (defaultCase != null) {
                // default case was not last
                return false;
            }

            if (isDefaultCase(aCase)) {
                defaultCase = aCase;
            }

            if (defaultCase != null && prior != null && Boolean.TRUE.equals(style.getSkipIfLastAndSharedWithCase()) && prior.getStatements().isEmpty()) {
                return true;
            }

            prior = aCase;
        }

        // either default was not present or it was last
        return true;
    }

    private boolean isDefaultCase(J.Case case_) {
        J elem = case_.getCaseLabels().get(0);
        return elem instanceof J.Identifier && ((J.Identifier) elem).getSimpleName().equals("default");
    }

}
