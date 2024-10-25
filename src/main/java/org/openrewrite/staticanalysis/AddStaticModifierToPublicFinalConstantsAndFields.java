/*
 * Copyright 2024 the original author or authors.
 * <p>
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * <p>
 * https://www.apache.org/licenses/LICENSE-2.0
 * <p>
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.openrewrite.staticanalysis;

import org.openrewrite.ExecutionContext;
import org.openrewrite.Recipe;
import org.openrewrite.Tree;
import org.openrewrite.java.JavaIsoVisitor;
import org.openrewrite.java.tree.J;
import org.openrewrite.java.tree.J.Modifier;
import org.openrewrite.java.tree.Space;
import org.openrewrite.marker.Markers;

import java.time.Duration;
import java.util.Collections;
import java.util.Set;

import static java.util.Collections.emptyList;

public class AddStaticModifierToPublicFinalConstantsAndFields extends Recipe {

    @Override
    public String getDisplayName() {
        return "Add `static` to `public final` variables";
    }

    @Override
    public String getDescription() {
        return "Finds fields declared as `public final` and adds `static` modifier, meanwhile sort all the modifiers.";
    }

    @Override
    public Set<String> getTags() {
        return Collections.singleton("RSPEC-S1170");
    }

    @Override
    public Duration getEstimatedEffortPerOccurrence() {
        return Duration.ofMinutes(3);
    }

    @Override
    public JavaIsoVisitor<ExecutionContext> getVisitor() {
        return new JavaIsoVisitor<ExecutionContext>() {

            @Override
            public J.VariableDeclarations visitVariableDeclarations(J.VariableDeclarations multiVariable,
                                                                    ExecutionContext executionContext) {

                if (multiVariable.hasModifier(Modifier.Type.Public) &&
                        multiVariable.hasModifier(Modifier.Type.Final) &&
                        !multiVariable.hasModifier(Modifier.Type.Static)) {
                    J.VariableDeclarations v = super.visitVariableDeclarations(multiVariable, executionContext);

                    multiVariable.getModifiers().add(new J.Modifier(Tree.randomId(),
                            Space.format(" "), Markers.EMPTY, " ",
                            Modifier.Type.Static, emptyList()));

                    return v.withModifiers(ModifierOrder.sortModifiers(multiVariable.getModifiers()));

                }
                return super.visitVariableDeclarations(multiVariable, ctx);
}
        };
    }
}