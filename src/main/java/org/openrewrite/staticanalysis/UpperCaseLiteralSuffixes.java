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
import org.jspecify.annotations.Nullable;
import org.openrewrite.ExecutionContext;
import org.openrewrite.Preconditions;
import org.openrewrite.Recipe;
import org.openrewrite.TreeVisitor;
import org.openrewrite.java.JavaIsoVisitor;
import org.openrewrite.java.search.UsesType;
import org.openrewrite.java.tree.J;
import org.openrewrite.java.tree.JavaType;
import org.openrewrite.staticanalysis.java.JavaFileChecker;

import java.time.Duration;
import java.util.Set;

import static java.util.Collections.singleton;

public class UpperCaseLiteralSuffixes extends Recipe {
    @Getter
    final String displayName = "Upper case literal suffixes";

    @Getter
    final String description = "Using upper case literal suffixes for declaring literals is less ambiguous, e.g., `1l` versus `1L`.";

    @Getter
    final Set<String> tags = singleton("RSPEC-S818");

    @Override
    public @Nullable Duration getEstimatedEffortPerOccurrence() {
        return Duration.ofMinutes(2);
    }

    @Override
    public TreeVisitor<?, ExecutionContext> getVisitor() {
        return Preconditions.check(
                Preconditions.and(
                        new JavaFileChecker<>(),
                        Preconditions.or(
                                new UsesType<>("long", false),
                                new UsesType<>("java.lang.Long", false),
                                new UsesType<>("double", false),
                                new UsesType<>("java.lang.Double", false),
                                new UsesType<>("float", false),
                                new UsesType<>("java.lang.Float", false)
                        )
                ), new JavaIsoVisitor<ExecutionContext>() {
                    @Override
                    public J.VariableDeclarations.NamedVariable visitVariable(J.VariableDeclarations.NamedVariable variable, ExecutionContext ctx) {
                        J.VariableDeclarations.NamedVariable nv = super.visitVariable(variable, ctx);
                        if (nv.getInitializer() instanceof J.Literal && nv.getInitializer().getType() != null) {
                            J.Literal initializer = (J.Literal) nv.getInitializer();
                            if (initializer.getType() == JavaType.Primitive.Double ||
                                initializer.getType() == JavaType.Primitive.Float ||
                                initializer.getType() == JavaType.Primitive.Long) {
                                String upperValueSource = upperCaseSuffix(initializer.getValueSource());
                                if (upperValueSource != null && !upperValueSource.equals(initializer.getValueSource())) {
                                    nv = nv.withInitializer(initializer.withValueSource(upperValueSource));
                                }
                            }
                        }
                        return nv;
                    }

                    private @Nullable String upperCaseSuffix(@Nullable String valueSource) {
                        if (valueSource == null || valueSource.length() < 2) {
                            return valueSource;
                        }
                        return valueSource.substring(0, valueSource.length() - 1) + valueSource.substring(valueSource.length() - 1).toUpperCase();
                    }
                });
    }
}
