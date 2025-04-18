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
import org.jspecify.annotations.NonNull;
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
import java.util.HashSet;
import java.util.List;
import java.util.stream.Collectors;

@Value
@EqualsAndHashCode(callSuper = false)
public class DefaultComesLastVisitor<P> extends JavaIsoVisitor<P> {
    DefaultComesLastStyle style;

    @Override
    public J.Switch visitSwitch(J.Switch switch_, P p) {
        J.Switch s = visitAndCast(switch_, p, super::visitSwitch);

        if (!isDefaultCaseLastOrNotPresent(switch_)) {
            List<J.Case> cases = s.getCases().getStatements().stream().map(J.Case.class::cast).collect(Collectors.toList());
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
                    s = s.withCases(s.getCases().withStatements(casesWithDefaultLast.stream().map(Statement.class::cast).collect(Collectors.toList())));
                }
            }
        }
        return s;
    }

    private @Nullable List<J.Case> maybeReorderCases(List<J.Case> cases, P p) {
        List<J.Case> fallThroughCases = new ArrayList<>(cases.size());
        List<J.Case> defaultCases = new ArrayList<>(cases.size());
        List<J.Case> casesWithDefaultLast = new ArrayList<>(cases.size());

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
            if (i == cases.size() - 1 || !isFallthroughCase(aCase)) {
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

    private @NonNull List<J.Case> maybeReorderFallthroughCases(List<J.Case> cases, P p) {
        J.Case defaultCase = null;
        List<J.Case> preDefault = new ArrayList<>();
        List<J.Case> postDefault = new ArrayList<>();
        List<Statement> defaultStatements = new ArrayList<>();
        for (J.Case aCase : cases) {
            if (isDefaultCase(aCase)) {
                defaultCase = aCase;
            } else if (defaultCase == null) {
                preDefault.add(aCase);
            } else {
                postDefault.add(aCase);
                defaultStatements.addAll(aCase.getStatements());
            }
        }
        List<Statement> defaultCaseStatements = defaultCase.getStatements();
        defaultStatements.addAll(0, defaultCaseStatements);
        List<J.Case> fixedCases = new ArrayList<>(preDefault.size() + postDefault.size() + 1);
        fixedCases.addAll(maybeUpdatePreDefaultCases(preDefault, defaultCaseStatements));
        fixedCases.addAll(postDefault);
        fixedCases = ListUtils.mapLast(fixedCases, e -> {
            List<Statement> lastStatements = e.getStatements();
            if (!lastStatements.isEmpty()) {
                if (!new HashSet<>(lastStatements).containsAll(defaultStatements)) {
                    return addBreak(e, p);
                } else {
                    return e.withStatements(Collections.emptyList());
                }
            }
            return e;
        });
        fixedCases.add(defaultCase.withStatements(ListUtils.map(defaultStatements, stmt -> autoFormat(stmt, p, getCursor()))));
        return fixedCases;
    }

    private List<J.Case> maybeUpdatePreDefaultCases(List<J.Case> preDefault, List<Statement> defaultCaseStatements) {
        List<J.Case> result = new ArrayList<>();
        if (!preDefault.isEmpty()) {
            result.addAll(preDefault.subList(0, preDefault.size() - 1));
            J.Case last = preDefault.get(preDefault.size() - 1);
            List<Statement> statements = last.getStatements();
            statements.addAll(defaultCaseStatements);
            result.add(last.withStatements(statements));
        }
        return result;
    }

    private List<J.Case> addBreakToLastCase(List<J.Case> cases, P p) {
        return ListUtils.mapLast(cases, e -> {
            if (isFallthroughCase(e)) {
                return addBreak(e, p);
            }
            return e;
        });
    }

    private boolean isFallthroughCase(J.Case aCase) {
        return aCase.getStatements().isEmpty() ||
              !(aCase.getStatements().get(aCase.getStatements().size() - 1) instanceof J.Break ||
                    aCase.getStatements().get(aCase.getStatements().size() - 1) instanceof J.Continue ||
                    aCase.getStatements().get(aCase.getStatements().size() - 1) instanceof J.Return ||
                    aCase.getStatements().get(aCase.getStatements().size() - 1) instanceof J.Throw);
    }

    private J.Case addBreak(J.Case e, P p) {
        J.Break breakStatement = autoFormat(
              new J.Break(Tree.randomId(), Space.EMPTY, Markers.EMPTY, null),
              p
        );
        List<Statement> statements = e.getStatements();
        statements.add(breakStatement);
        return e.withStatements(ListUtils.map(statements, stmt -> autoFormat(stmt, p, getCursor())));
    }

    private J.Case removeBreak(J.Case aCase) {
        if (!aCase.getStatements().isEmpty() && aCase.getStatements().get(aCase.getStatements().size() - 1) instanceof J.Break) {
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
