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
import org.jetbrains.annotations.NotNull;
import org.openrewrite.Tree;
import org.openrewrite.internal.ListUtils;
import org.openrewrite.java.JavaIsoVisitor;
import org.openrewrite.java.style.DefaultComesLastStyle;
import org.openrewrite.java.tree.J;
import org.openrewrite.java.tree.Space;
import org.openrewrite.java.tree.Statement;
import org.openrewrite.marker.Markers;

import java.util.ArrayList;
import java.util.Arrays;
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
            List<J.Case> fallThroughCases = new ArrayList<>(cases.size());
            List<J.Case> defaultCases = new ArrayList<>(cases.size());
            List<J.Case> fixedCases = new ArrayList<>(cases.size());

            boolean defaultCaseFound = false;
            for (int i = 0; i < cases.size(); i++) {
                J.Case aCase = cases.get(i);
                if (aCase.getBody() != null) {
                    return s;
                }
                fallThroughCases.add(aCase);
                if (isDefaultCase(aCase)) {
                    defaultCaseFound = true;
                }
                if (i == cases.size() - 1 || !aCase.getStatements().isEmpty() && (aCase.getStatements().get(aCase.getStatements().size() - 1) instanceof J.Break || aCase.getStatements().get(aCase.getStatements().size() - 1) instanceof J.Continue || aCase.getStatements().get(aCase.getStatements().size() - 1) instanceof J.Return || aCase.getStatements().get(aCase.getStatements().size() - 1) instanceof J.Throw)) {
                    if(defaultCaseFound) {
                        defaultCases.addAll(fallThroughCases);
                        fallThroughCases.clear();
                        defaultCaseFound = false;
                    } else {
                        fixedCases.addAll(fallThroughCases);
                        fallThroughCases.clear();
                    }
                }
            }

            J.Case aCase = fixedCases.remove(fixedCases.size() - 1);
            if(aCase.getStatements().isEmpty() || !(aCase.getStatements().get(aCase.getStatements().size() - 1) instanceof J.Break || aCase.getStatements().get(aCase.getStatements().size() - 1) instanceof J.Continue || aCase.getStatements().get(aCase.getStatements().size() - 1) instanceof J.Return || aCase.getStatements().get(aCase.getStatements().size() - 1) instanceof J.Throw)) {
                J.Break breakStatement = autoFormat(
                      new J.Break(Tree.randomId(), Space.EMPTY, Markers.EMPTY, null),
                      p
                );
                List<Statement> statements = aCase.getStatements();
                statements.add(breakStatement);
                aCase = aCase.withStatements(ListUtils.map(statements, stmt -> autoFormat(stmt, p, getCursor())));
            }
            fixedCases.add(aCase);

            fixedCases.addAll(defaultLast(defaultCases));

            boolean changed = true;
            if (cases.size() == fixedCases.size()) {
                changed = false;
                for (int i = 0; i < cases.size(); i++) {
                    if (cases.get(i) != fixedCases.get(i)) {
                        changed = true;
                        break;
                    }
                }
            }

            if (changed) {
                s = s.withCases(s.getCases().withStatements(fixedCases.stream().map(Statement.class::cast).collect(Collectors.toList())));
            }

            /*int defaultCaseIndex = -1;
            J.Case defaultCase = null;

            for (int i = 0; i < cases.size(); i++) {
                J.Case aCase = cases.get(i);

                // skip cases with bodies for now
                if (aCase.getBody() != null) {
                    return s;
                }
                if (isDefaultCase(aCase)) {
                    defaultCaseIndex = i;
                    defaultCase = aCase;
                }
            }

            List<J.Case> casesGroupedWithDefault = new ArrayList<>();
            boolean foundNonEmptyCase = false;
            for (int i = defaultCaseIndex - 1; i >= 0; i--) {
                J.Case aCase = cases.get(i);
                if (aCase.getStatements().isEmpty() && !foundNonEmptyCase) {
                    casesGroupedWithDefault.add(0, aCase);
                } else {
                    foundNonEmptyCase = true;
                    fixedCases.add(0, aCase);
                }
            }

            foundNonEmptyCase = false;
            for (int i = defaultCaseIndex + 1; i < cases.size(); i++) {
                J.Case aCase = cases.get(i);
                if (defaultCase != null && defaultCase.getStatements().isEmpty() &&
                    aCase.getStatements().isEmpty() && !foundNonEmptyCase) {
                    casesGroupedWithDefault.add(aCase);
                } else {
                    if (defaultCase != null && defaultCase.getStatements().isEmpty() && !foundNonEmptyCase) {
                        // the last case grouped with default can be non-empty. it will be flipped with
                        // the default case, including its statements
                        casesGroupedWithDefault.add(aCase);
                    }
                    foundNonEmptyCase = true;
                    fixedCases.add(aCase);
                }
            }

            if (defaultCase != null && !casesGroupedWithDefault.isEmpty()) {
                J.Case lastGroupedWithDefault = casesGroupedWithDefault.get(casesGroupedWithDefault.size() - 1);
                if (!lastGroupedWithDefault.getStatements().isEmpty()) {
                    casesGroupedWithDefault.set(casesGroupedWithDefault.size() - 1,
                            lastGroupedWithDefault.withStatements(Collections.emptyList()));
                    defaultCase = defaultCase.withStatements(lastGroupedWithDefault.getStatements());
                }
            }

            J.Case lastNotGroupedWithDefault = fixedCases.get(fixedCases.size() - 1);
            if (!lastNotGroupedWithDefault.getStatements().stream().reduce((s1, s2) -> s2)
                    .map(stat -> stat instanceof J.Break || stat instanceof J.Continue ||
                                 stat instanceof J.Return || stat instanceof J.Throw)
                    .orElse(false)) {

                // add a break statement since this case is now no longer last and would fall through
                List<Statement> statementsOfCaseBeingMoved = new ArrayList<>(lastNotGroupedWithDefault.getStatements());
                J.Break breakStatement = autoFormat(
                        new J.Break(Tree.randomId(), Space.EMPTY, Markers.EMPTY, null),
                        p
                );
                statementsOfCaseBeingMoved.add(breakStatement);

                lastNotGroupedWithDefault = lastNotGroupedWithDefault.withStatements(
                        ListUtils.map(statementsOfCaseBeingMoved, stmt -> autoFormat(stmt, p, getCursor()))
                );
                fixedCases.set(fixedCases.size() - 1, lastNotGroupedWithDefault);
            }

            fixedCases.addAll(casesGroupedWithDefault);
            if (defaultCase != null) {
                if (defaultCase.getStatements().stream().reduce((s1, s2) -> s2)
                        .map(stat -> stat instanceof J.Break || stat instanceof J.Continue || isVoidReturn(stat))
                        .orElse(false)) {
                    List<Statement> fixedDefaultStatements = new ArrayList<>(defaultCase.getStatements());
                    fixedDefaultStatements.remove(fixedDefaultStatements.size() - 1);
                    fixedCases.add(defaultCase.withStatements(fixedDefaultStatements));
                } else {
                    fixedCases.add(defaultCase);
                }
            }

            boolean changed = true;
            if (cases.size() == fixedCases.size()) {
                changed = false;
                for (int i = 0; i < cases.size(); i++) {
                    if (cases.get(i) != fixedCases.get(i)) {
                        changed = true;
                        break;
                    }
                }
            }

            if (changed) {
                s = s.withCases(s.getCases().withStatements(fixedCases.stream().map(Statement.class::cast).collect(Collectors.toList())));
            }*/
        }

        return s;
    }

    private List<J.Case> defaultLast(List<J.Case> defaultCases) {
        if(defaultCases.isEmpty()) {
            return defaultCases;
        }
        if(defaultCases.size() == 1) {
            ArrayList<J.Case> cases = new ArrayList<>();
            cases.add(removeBreak(defaultCases.get(0)));
            return cases;
        }
        List<J> lastCaseLabels = defaultCases.get(defaultCases.size() - 1).getCaseLabels();
        List<J.Case> fixedCases = new ArrayList<>(defaultCases.size());
        for(int i = 0; i < defaultCases.size(); i++) {
            J.Case aCase = defaultCases.get(i);
            if(isDefaultCase(aCase)) {
                aCase = aCase.withCaseLabels(lastCaseLabels);
            }
            if(i == defaultCases.size() - 1) {
                aCase = aCase.withCaseLabels(Arrays.asList(new J.Identifier(Tree.randomId(), Space.EMPTY, Markers.EMPTY, null, "default", null, null)));
                aCase = removeBreak(aCase);
            }
            fixedCases.add(aCase);
        }
        return fixedCases;
    }

    private J.@NotNull Case removeBreak(J.Case aCase) {
        if(!aCase.getStatements().isEmpty() && (aCase.getStatements().get(aCase.getStatements().size() - 1) instanceof J.Break)){
            aCase = aCase.withStatements(aCase.getStatements().subList(0, aCase.getStatements().size() - 1));
        }
        return aCase;
    }

    private boolean isVoidReturn(Statement stat) {
        return stat instanceof J.Return && ((J.Return) stat).getExpression() == null;
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
