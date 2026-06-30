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

import org.openrewrite.ExecutionContext;
import org.openrewrite.Preconditions;
import org.openrewrite.Recipe;
import org.openrewrite.TreeVisitor;
import org.openrewrite.internal.ListUtils;
import org.openrewrite.java.JavaIsoVisitor;
import org.openrewrite.java.NoMissingTypes;
import org.openrewrite.java.tree.J;
import org.openrewrite.java.tree.JRightPadded;
import org.openrewrite.java.tree.JavaType;
import org.openrewrite.java.tree.NameTree;
import org.openrewrite.java.tree.Space;
import org.openrewrite.java.tree.TypeTree;
import org.openrewrite.java.tree.TypeUtils;

import java.util.ArrayList;
import java.util.List;

public class RemoveUnreachableMultiCatchAlternative extends Recipe {

    @Override
    public String getDisplayName() {
        return "Remove unreachable `catch` alternatives shadowed by earlier `catch` clauses";
    }

    @Override
    public String getDescription() {
        return "When an earlier `catch` clause already covers a type, any later `catch` (including a " +
                "multi-catch alternative) for the same type or a subtype is unreachable and is a Java " +
                "compile error. This commonly appears after type-substitution migrations (for example, " +
                "renaming an exception so that two `catch` clauses end up overlapping). This recipe drops " +
                "the unreachable alternatives from later multi-catches, collapses a multi-catch to a " +
                "regular `catch` when only one alternative remains, and removes the entire `catch` clause " +
                "when all of its declared types are already covered.";
    }

    @Override
    public TreeVisitor<?, ExecutionContext> getVisitor() {
        return Preconditions.check(new NoMissingTypes(), new JavaIsoVisitor<ExecutionContext>() {
            @Override
            public J.Try visitTry(J.Try tryable, ExecutionContext ctx) {
                J.Try t = super.visitTry(tryable, ctx);

                List<JavaType> covered = new ArrayList<>();
                return t.withCatches(ListUtils.map(t.getCatches(), aCatch -> {
                    TypeTree typeExpression = aCatch.getParameter().getTree().getTypeExpression();
                    if (typeExpression == null) {
                        return aCatch;
                    }

                    if (typeExpression instanceof J.MultiCatch) {
                        J.MultiCatch multiCatch = (J.MultiCatch) typeExpression;
                        List<NameTree> retained = new ArrayList<>(multiCatch.getAlternatives().size());
                        for (NameTree alt : multiCatch.getAlternatives()) {
                            if (alt.getType() == null || isCoveredBy(alt.getType(), covered)) {
                                maybeRemoveImport(TypeUtils.asFullyQualified(alt.getType()));
                                continue;
                            }
                            retained.add(alt);
                        }

                        if (retained.isEmpty()) {
                            return null;
                        }

                        for (NameTree alt : retained) {
                            covered.add(alt.getType());
                        }

                        if (retained.size() == multiCatch.getAlternatives().size()) {
                            return aCatch;
                        }

                        Space firstPrefix = multiCatch.getAlternatives().get(0).getPrefix();
                        if (retained.size() == 1 && retained.get(0) instanceof TypeTree) {
                            TypeTree only = ((TypeTree) retained.get(0)).withPrefix(firstPrefix);
                            return aCatch.withParameter(aCatch.getParameter().withTree(
                                    aCatch.getParameter().getTree().withTypeExpression(only)));
                        }

                        List<NameTree> withFirstPrefix = ListUtils.mapFirst(retained, first -> first.withPrefix(firstPrefix));
                        J.MultiCatch.Padding padding = multiCatch.withAlternatives(withFirstPrefix).getPadding();
                        List<JRightPadded<NameTree>> withLastTrimmed = ListUtils.mapLast(padding.getAlternatives(), last -> last.withAfter(Space.EMPTY));
                        return aCatch.withParameter(aCatch.getParameter().withTree(
                                aCatch.getParameter().getTree().withTypeExpression(padding.withAlternatives(withLastTrimmed))));
                    }

                    JavaType type = typeExpression.getType();
                    if (type != null && isCoveredBy(type, covered)) {
                        maybeRemoveImport(TypeUtils.asFullyQualified(type));
                        return null;
                    }
                    if (type != null) {
                        covered.add(type);
                    }
                    return aCatch;
                }));
            }

            private boolean isCoveredBy(JavaType type, List<JavaType> covered) {
                for (JavaType c : covered) {
                    if (TypeUtils.isAssignableTo(c, type)) {
                        return true;
                    }
                }
                return false;
            }
        });
    }
}
