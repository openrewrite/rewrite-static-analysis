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

import lombok.Getter;
import org.openrewrite.ExecutionContext;
import org.openrewrite.Preconditions;
import org.openrewrite.Recipe;
import org.openrewrite.TreeVisitor;
import org.openrewrite.internal.ListUtils;
import org.openrewrite.java.JavaIsoVisitor;
import org.openrewrite.java.tree.Comment;
import org.openrewrite.java.tree.J;
import org.openrewrite.java.tree.Space;
import org.openrewrite.staticanalysis.java.JavaFileChecker;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

public class CleanupTryWithResources extends Recipe {

    @Getter
    final String displayName = "Clean up try-with-resources";

    @Getter
    final String description = "Remove the redundant `final` modifier from resources declared in a " +
            "try-with-resources statement. Such resources are implicitly final, so the modifier adds no meaning.";

    @Getter
    final Duration estimatedEffortPerOccurrence = Duration.ofMinutes(1);

    @Override
    public TreeVisitor<?, ExecutionContext> getVisitor() {
        return Preconditions.check(new JavaFileChecker<>(), new JavaIsoVisitor<ExecutionContext>() {
            @Override
            public J.Try.Resource visitTryResource(J.Try.Resource resource, ExecutionContext ctx) {
                J.Try.Resource r = super.visitTryResource(resource, ctx);
                if (!(r.getVariableDeclarations() instanceof J.VariableDeclarations)) {
                    return r;
                }

                J.VariableDeclarations vd = (J.VariableDeclarations) r.getVariableDeclarations();
                if (vd.getModifiers().stream().noneMatch(CleanupTryWithResources::isBareFinal)) {
                    return r;
                }

                // `final` only owns the whitespace opening the declaration when nothing precedes it;
                // behind a leading annotation that gap belongs to the annotation and must survive.
                boolean finalLedDeclaration = vd.getLeadingAnnotations().isEmpty() &&
                        isBareFinal(vd.getModifiers().get(0));

                // The modifier's own prefix can hold comments, which would be lost along with it.
                Space accumulated = Space.EMPTY;
                for (J.Modifier modifier : vd.getModifiers()) {
                    if (isBareFinal(modifier)) {
                        accumulated = concat(accumulated, modifier.getPrefix());
                    }
                }
                Space carried = accumulated;

                J.VariableDeclarations updated = vd.withModifiers(
                        ListUtils.map(vd.getModifiers(), m -> isBareFinal(m) ? null : m));

                // Hand the carried comments, and the gap `final` left behind, to the next token along.
                if (updated.getTypeExpression() != null) {
                    updated = updated.withTypeExpression(updated.getTypeExpression().withPrefix(
                            reflow(concat(carried, updated.getTypeExpression().getPrefix()), finalLedDeclaration)));
                } else {
                    updated = updated.withVariables(ListUtils.mapFirst(updated.getVariables(),
                            v -> v.withPrefix(reflow(concat(carried, v.getPrefix()), finalLedDeclaration))));
                }

                // A declaration wrapped around `final` is left straddling lines once it is gone; close that up.
                // Only the gap before each variable name is touched -- the initializer keeps its own layout.
                return r.withVariableDeclarations(updated.withVariables(ListUtils.map(updated.getVariables(),
                        v -> v.withPrefix(collapseToSingleSpace(v.getPrefix())))));
            }
        });
    }

    private static boolean isBareFinal(J.Modifier modifier) {
        // A modifier carrying its own annotations cannot be dropped without losing them.
        return modifier.getType() == J.Modifier.Type.Final && modifier.getAnnotations().isEmpty();
    }

    /**
     * Joins two adjacent stretches of formatting into one, keeping every comment in source order.
     */
    private static Space concat(Space first, Space second) {
        if (first.getComments().isEmpty()) {
            return Space.build(first.getWhitespace() + second.getWhitespace(), second.getComments());
        }
        List<Comment> comments = new ArrayList<>(first.getComments());
        int last = comments.size() - 1;
        comments.set(last, comments.get(last).withSuffix(
                tighten(comments.get(last).getSuffix() + second.getWhitespace())));
        comments.addAll(second.getComments());
        return Space.build(first.getWhitespace(), comments);
    }

    /**
     * Drops the whitespace that `final` used to occupy while leaving any comments in place.
     */
    private static Space reflow(Space space, boolean leadsDeclaration) {
        return Space.build(leadsDeclaration ? "" : tighten(space.getWhitespace()), space.getComments());
    }

    private static Space collapseToSingleSpace(Space space) {
        // Leave anything holding a comment alone; reflowing it would move or lose the comment.
        return space.getComments().isEmpty() && space.getWhitespace().contains("\n") ?
                Space.SINGLE_SPACE : space;
    }

    private static String tighten(String whitespace) {
        return whitespace.isEmpty() || whitespace.contains("\n") ? whitespace : " ";
    }
}
