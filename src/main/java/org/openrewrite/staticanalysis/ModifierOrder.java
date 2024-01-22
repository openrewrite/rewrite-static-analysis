/*
 * Copyright 2020 the original author or authors.
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
import org.openrewrite.TreeVisitor;
import org.openrewrite.internal.ListUtils;
import org.openrewrite.java.JavaIsoVisitor;
import org.openrewrite.java.tree.J;
import org.openrewrite.java.tree.J.Modifier.Type;

import java.time.Duration;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Set;
import java.util.function.ToIntFunction;

import static java.util.stream.Collectors.toList;

public class ModifierOrder extends Recipe {
    @Override
    public String getDisplayName() {
        return "Modifier order";
    }

    @Override
    public String getDescription() {
        return "Modifiers should be declared in the correct order as recommended by the JLS.";
    }

    @Override
    public Set<String> getTags() {
        return Collections.singleton("RSPEC-1124");
    }

    @Override
    public Duration getEstimatedEffortPerOccurrence() {
        return Duration.ofMinutes(2);
    }

    @Override
    public TreeVisitor<?, ExecutionContext> getVisitor() {
        return new JavaIsoVisitor<ExecutionContext>() {
            @Override
            public J.ClassDeclaration visitClassDeclaration(J.ClassDeclaration classDecl, ExecutionContext ctx) {
                J.ClassDeclaration c = super.visitClassDeclaration(classDecl, ctx);
                return c.withModifiers(sortModifiers(c.getModifiers()));
            }

            @Override
            public J.MethodDeclaration visitMethodDeclaration(J.MethodDeclaration method, ExecutionContext ctx) {
                J.MethodDeclaration m = super.visitMethodDeclaration(method, ctx);
                return m.withModifiers(sortModifiers(m.getModifiers()));
            }

            @Override
            public J.VariableDeclarations visitVariableDeclarations(J.VariableDeclarations multiVariable, ExecutionContext ctx) {
                J.VariableDeclarations v = super.visitVariableDeclarations(multiVariable, ctx);
                return v.withModifiers(sortModifiers(v.getModifiers()));
            }
        };
    }

    public static List<J.Modifier> sortModifiers(List<J.Modifier> modifiers) {
        for (J.Modifier mod : modifiers) {
            if (mod.getType() == J.Modifier.Type.LanguageExtension) {
                // avoid harmful changes with modifiers not seen in Java
                return modifiers;
            }
        }

        List<J.Modifier.Type> sortedTypes = modifiers.stream()
                .map(J.Modifier::getType)
                .sorted(Comparator.comparingInt(createModifierTypeToPositionFunction()))
                .collect(toList());


        return ListUtils.map(modifiers, (i, mod) -> mod.getType() == sortedTypes.get(i) ? mod : mod.withType(sortedTypes.get(i)));
    }
    
    private static ToIntFunction<Type> createModifierTypeToPositionFunction() {
        final int DEFAULT_MOD_POSITION = 4;
        return type -> {
            if (type == Type.Default) {
                return DEFAULT_MOD_POSITION;
            }
            int ordinal = type.ordinal();
            if (ordinal <= DEFAULT_MOD_POSITION) {
                return ordinal - 1;
            }
            return ordinal;
        };
    }
}
