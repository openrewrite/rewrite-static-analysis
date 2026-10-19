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
import org.openrewrite.Cursor;
import org.openrewrite.ExecutionContext;
import org.openrewrite.Preconditions;
import org.openrewrite.Recipe;
import org.openrewrite.TreeVisitor;
import org.openrewrite.java.JavaIsoVisitor;
import org.openrewrite.java.tree.Expression;
import org.openrewrite.java.tree.Flag;
import org.openrewrite.java.tree.J;
import org.openrewrite.java.tree.JavaType;
import org.openrewrite.java.tree.Statement;
import org.openrewrite.java.tree.TypeUtils;
import org.openrewrite.marker.SearchResult;
import org.openrewrite.staticanalysis.java.JavaFileChecker;

import java.time.Duration;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;

import static java.util.Collections.singleton;

@Getter
public class FindNonCaseLabelsInSwitch extends Recipe {

    final String displayName = "`switch` statements should not contain non-case labels";

    final String description = "Marks statement labels placed directly among the `case` labels of a `switch`. " +
            "Such a label is often a `case` with a missing keyword, which compiles but never matches, " +
            "or a labeled loop whose `break` is easily misread as leaving the `switch`. " +
            "Labels nested in a block or statement within a case are not marked. " +
            "Review these before running `RemoveUnusedLabels`, which would remove unused ones.";

    final Set<String> tags = singleton("RSPEC-S1219");

    final Duration estimatedEffortPerOccurrence = Duration.ofMinutes(10);

    @Override
    public TreeVisitor<?, ExecutionContext> getVisitor() {
        return Preconditions.check(new JavaFileChecker<>(), new JavaIsoVisitor<ExecutionContext>() {
            @Override
            public J.Label visitLabel(J.Label label, ExecutionContext ctx) {
                J.Label l = super.visitLabel(label, ctx);
                Cursor caseCursor = getCursor().getParentTreeCursor();
                if (!(caseCursor.getValue() instanceof J.Case) ||
                        ((J.Case) caseCursor.getValue()).getType() != J.Case.Type.Statement) {
                    return l;
                }
                String name = l.getLabel().getSimpleName();
                Object switchTree = caseCursor.getParentTreeCursor().getParentTreeCursor().getValue();
                Expression selector = switchTree instanceof J.Switch ?
                        ((J.Switch) switchTree).getSelector().getTree() :
                        ((J.SwitchExpression) switchTree).getSelector().getTree();
                JavaType.Class selectorType = TypeUtils.asClass(selector.getType());
                if (selectorType != null && selectorType.getKind() == JavaType.FullyQualified.Kind.Enum &&
                        selectorType.getMembers().stream().anyMatch(m -> m.hasFlags(Flag.Enum) && m.getName().equals(name)) &&
                        !hasCase(caseCursor.getParentTreeCursor().getValue(), name) && !isReferenced(l.getStatement(), name)) {
                    return SearchResult.found(l, "`" + name + "` is a statement label, not a `case`, so `" + name +
                            "` never matches. Add the missing `case` keyword.");
                }
                return SearchResult.found(l, "Label `" + name + "` in a `switch` case is easily confused with a `case` label.");
            }

            private boolean hasCase(J.Block cases, String name) {
                for (Statement s : cases.getStatements()) {
                    for (J caseLabel : ((J.Case) s).getCaseLabels()) {
                        if (caseLabel instanceof J.Identifier && ((J.Identifier) caseLabel).getSimpleName().equals(name) ||
                                caseLabel instanceof J.FieldAccess && ((J.FieldAccess) caseLabel).getSimpleName().equals(name)) {
                            return true;
                        }
                    }
                }
                return false;
            }

            private boolean isReferenced(Statement labeled, String name) {
                return new JavaIsoVisitor<AtomicBoolean>() {
                    @Override
                    public J.Break visitBreak(J.Break breakStatement, AtomicBoolean found) {
                        if (breakStatement.getLabel() != null && name.equals(breakStatement.getLabel().getSimpleName())) {
                            found.set(true);
                        }
                        return breakStatement;
                    }

                    @Override
                    public J.Continue visitContinue(J.Continue continueStatement, AtomicBoolean found) {
                        if (continueStatement.getLabel() != null && name.equals(continueStatement.getLabel().getSimpleName())) {
                            found.set(true);
                        }
                        return continueStatement;
                    }

                    // A break or continue in these cannot target the label, which may reuse its name
                    @Override
                    public J.Lambda visitLambda(J.Lambda lambda, AtomicBoolean found) {
                        return lambda;
                    }

                    @Override
                    public J.ClassDeclaration visitClassDeclaration(J.ClassDeclaration classDecl, AtomicBoolean found) {
                        return classDecl;
                    }

                    @Override
                    public J.NewClass visitNewClass(J.NewClass newClass, AtomicBoolean found) {
                        return newClass;
                    }
                }.reduce(labeled, new AtomicBoolean()).get();
            }
        });
    }
}
